(ns codethread.auto-run-test
  "Exercise repository auto-run startup in a disposable Weaver world."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests testing]]
            [ct.spools.codethread.auto-run :as auto-run]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.graph.alpha :as graph]
            [millstrand.api.spool.alpha :refer [attr-get]]
            [millstrand.test.alpha :as t]))

(defn- world-options []
  (let [deps (:deps (edn/read-string (slurp "deps.edn")))]
    {:storage :sqlite-memory
     :deps-edn
     (pr-str
      {:deps
       (update-vals deps
                    #(if-let [root (:local/root %)]
                       (assoc % :local/root (.getCanonicalPath (io/file root)))
                       %))})
     :init-clj (slurp "init.clj")
     :files (into {}
                  (for [path ["me/auto_run_workflows.clj" "me/auto_run.clj"]]
                    [path (slurp path)]))}))

(defn- role-step [strands role]
  (first (filter #(= role (attr-get % :auto-run/role)) strands)))

(defn- temp-dir []
  (let [path (java.io.File/createTempFile "codethread-auto-run-" "")]
    (.delete path)
    (.mkdir path)
    path))

(defn- delete-tree! [root]
  (doseq [file (reverse (file-seq root))]
    (.delete file)))

(defn- git! [dir & args]
  (let [{:keys [exit out err]} (apply shell/sh "git" (concat args [:dir dir]))]
    (when-not (zero? exit)
      (throw (ex-info "Git fixture command failed" {:args args :out out :err err})))
    out))

(defn- candidate-gate! [dir]
  (shell/sh "sh"
            (.getCanonicalPath (io/file ".." "scripts" "verify-published-candidate.sh"))
            "auto/fixture-card"
            :dir dir))

(defn- published-candidate! [remote candidate]
  (git! candidate "init" "-b" "auto/fixture-card")
  (git! candidate "config" "user.email" "test@example.com")
  (git! candidate "config" "user.name" "Test User")
  (spit (io/file candidate "candidate.txt") "candidate\n")
  (spit (io/file candidate "Makefile")
        ".PHONY: quality\n\nquality:\n\t@printf '%s\\n' \"$(CURDIR)\" > quality-ran-from.txt\n")
  (git! candidate "add" "candidate.txt" "Makefile")
  (git! candidate "commit" "-m" "initial candidate")
  (git! candidate "remote" "add" "origin" (.getPath remote))
  (git! candidate "push" "-u" "origin" "auto/fixture-card"))

(deftest published-candidate-gate-accepts-clean-matching-candidate
  (let [remote (temp-dir)
        candidate (temp-dir)]
    (try
      (git! remote "init" "--bare")
      (published-candidate! remote candidate)
      (let [result (candidate-gate! candidate)]
        (testing "a clean candidate matching its fetched upstream succeeds"
          (is (zero? (:exit result)) result))
        (testing "the quality target runs from the candidate worktree"
          (is (= (str (.getCanonicalPath candidate) "\n")
                 (slurp (io/file candidate "quality-ran-from.txt"))))))
      (finally
        (delete-tree! candidate)
        (delete-tree! remote)))))

(deftest repository-activation-and-autonomous-delivery-contract
  (t/with-weaver-world
    [ctx (world-options)]
    (let [rt (:runtime ctx)
          status (auto-run/status rt)]
      (testing "bounded dispatcher configuration"
        (is (:enabled status))
        (is (= 2 (get-in status [:config :max-running])))
        (is (= "sol" (get-in status [:config :seat])))
        (is (= "high" (get-in status [:config :effort])))
        (is (= "auto-full-land" (get-in status [:config :workflow])))
        (is (= ["auto-full-land"] (get-in status [:config :workflows])))
        (is (empty? (:cards status)))
        (is (empty? (:dispatched (auto-run/scan! rt)))))
      (current/with-runtime rt
        (let [result (workflow/start!
                      "test-auto-full-land" :auto-full-land
                      {:card "fixture-card"
                       :feature "Disposable feature"
                       :branch "auto/fixture-card"
                       :worktree (:config-dir ctx)})
              root (workflow/current-root "test-auto-full-land")
              strands (:strands (graph/subgraph rt [(:id root)]))
              views (map workflow/step-view strands)
              gates (set (keep #(attr-get % :workflow/gate) strands))
              publish (first (filter #(= "Publish the committed branch before quality"
                                       (:title %))
                                    views))
              quality (first (filter #(= "Pass repository quality checks for published HEAD"
                                       (:title %))
                                    views))
              quality-gate (first (filter #(= (:id quality) (:id %)) strands))
              handoff (workflow/step-view (role-step strands "handoff-worker"))
              finisher (workflow/step-view (role-step strands "finisher"))]
          (testing "implementation and publication precede quality, CI, and review"
            (is (= ["Implement and verify the assigned feature"]
                   (mapv :title (:ready result))))
            (is (= ["Publish the committed branch before quality"]
                   (mapv :title (:ready (workflow/complete! "test-auto-full-land")))))
            (is (str/includes? (:instruction publish)
                               "git push --set-upstream origin auto/fixture-card"))
            (is (= ["Pass repository quality checks for published HEAD"]
                   (mapv :title (:ready (workflow/complete! "test-auto-full-land")))))
            (is (= ["sh" "scripts/verify-published-candidate.sh" "auto/fixture-card"]
                   (attr-get quality-gate :shell/argv)))
            (is (contains? gates "shell"))
            (is (contains? gates "code"))
            (is (not (contains? gates "agent"))))
          (testing "landing uses distinct worker and finisher steps"
            (is (= "step" (:role handoff) (:role finisher)))
            (is (not= (:id handoff) (:id finisher)))
            (is (str/includes? (:instruction handoff)
                               "STOP at land's signoff checkpoint BEFORE choosing approved"))
            (is (str/includes? (:instruction handoff)
                               "auto-land-finisher/FINISHER_STEP_ID"))
            (is (str/includes? (:instruction finisher)
                               "You are the independent landing finisher"))
            (is (str/includes? (:instruction finisher)
                               "turn, validation, merge, main update and cleanup"))
            (is (str/includes? (:instruction finisher)
                               "card is closed with outcome done")))
          (testing "autonomous failures stop without invented recovery"
            (doseq [view (concat [handoff finisher] (filter :gate views))]
              (is (str/includes? (:instruction view) "`auto-run-failure`"))
              (is (str/includes? (:instruction view)
                                 "Stop and leave the card open")))))))))

(deftest published-candidate-gate-rejects-invalid-repository-state
  (let [remote (temp-dir)
        candidate (temp-dir)
        peer (temp-dir)]
    (try
      (git! remote "init" "--bare")
      (published-candidate! remote candidate)
      (spit (io/file candidate "untracked.txt") "dirty\n")
      (testing "a dirty worktree is rejected"
        (is (not (zero? (:exit (candidate-gate! candidate))))))
      (.delete (io/file candidate "untracked.txt"))
      (git! candidate "switch" "-c" "wrong-branch")
      (testing "a different checked-out branch is rejected"
        (is (not (zero? (:exit (candidate-gate! candidate))))))
      (git! candidate "switch" "auto/fixture-card")
      (git! peer "clone" "--branch" "auto/fixture-card" (.getPath remote) ".")
      (git! peer "config" "user.email" "test@example.com")
      (git! peer "config" "user.name" "Test User")
      (spit (io/file peer "candidate.txt") "remote change\n")
      (git! peer "commit" "-am" "remote change")
      (git! peer "push")
      (testing "a HEAD that differs from the fetched upstream is rejected"
        (is (not (zero? (:exit (candidate-gate! candidate))))))
      (finally
        (delete-tree! peer)
        (delete-tree! candidate)
        (delete-tree! remote)))))

(defn -main
  "Run disposable workspace activation tests."
  [& _]
  (let [{:keys [fail error]} (run-tests 'codethread.auto-run-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
