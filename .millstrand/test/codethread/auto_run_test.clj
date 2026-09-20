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
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.scheduler.alpha :as scheduler]
            [millstrand.api.spool.alpha :refer [attr-get]]
            [millstrand.api.weaver.alpha :as weaver]
            [millstrand.test.alpha :as t]))

(defn- world-options
  ([] (world-options (fn [_path source] source)))
  ([source-transform]
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
                     [path (source-transform path (slurp path))]))})))

(defn- without-human-review [path source]
  (case path
    "me/auto_run.clj"
    (-> source
        (str/replace "#{\"auto-full-land\" \"auto-human-review\"}"
                     "#{\"auto-full-land\"}")
        (str/replace
         #"(?s)\n\(lifecycle/defreconcile! auto-run-dispatcher.*$"
         "\n\n(defn open! [{:keys [runtime] :as context}]\n  (auto-run/configure! runtime (desired-config context)))\n\n(defn close! [{:keys [runtime]}]\n  (auto-run/stop! runtime))\n\n(lifecycle/defresource! auto-run-dispatcher\n  \"Own repository automatic delivery for the module lifetime.\"\n  {:open 'me.auto-run/open!\n   :close 'me.auto-run/close!})\n"))

    "me/auto_run_workflows.clj"
    (str/replace source
                 #"(?s)\n\(workflow/defworkflow! auto-human-review.*?\n\n(?=\(workflow/defworkflow! auto-full-land)"
                 "\n")

    source))

(defn- pending-auto-run-wakes [rt]
  (filter #(= "codethread/auto-run" (:key %)) (scheduler/pending rt)))

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
        (is (= ["auto-full-land" "auto-human-review"]
               (get-in status [:config :workflows])))
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
              ci (first (filter #(= "Wait for the PR checks" (:title %)) views))
              ci-gate (first (filter #(= (:id ci) (:id %)) strands))
              ci-argv (attr-get ci-gate :shell/argv)
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
            (is (= ["sh" "scripts/verify-pr-checks.sh" "allow-empty"
                    "auto/fixture-card" "120" "5"]
                   ci-argv))
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

(deftest source-refresh-reconciles-the-running-dispatcher
  (t/with-weaver-world
    [ctx (world-options without-human-review)]
    (let [rt (:runtime ctx)
          accepted (weaver/add!
                    rt {:title "Accepted worker"
                        :attributes {:harness/run "true"
                                     :harness/status "running"
                                     :harness/settled "false"}})]
      (is (= ["auto-full-land"]
             (get-in (auto-run/status rt) [:config :workflows])))
      (is (= 1 (count (pending-auto-run-wakes rt))))
      (doseq [path ["me/auto_run_workflows.clj" "me/auto_run.clj"]]
        (spit (io/file (:config-dir ctx) path) (slurp path)))
      (let [refresh-result (runtime/refresh! rt)]
        (is (= :applied
               (get-in refresh-result
                       [:modules :codethread/auto-run :lifecycle/outcomes
                        :auto-run-dispatcher :status]))
            refresh-result))
      (is (= ["auto-full-land" "auto-human-review"]
             (get-in (auto-run/status rt) [:config :workflows])))
      (is (= ((runtime/resolve-var rt 'me.auto-run/desired-config) {:runtime rt})
             ((runtime/resolve-var rt 'me.auto-run/actual-config) {:runtime rt})))
      (current/with-runtime rt
        (is (= #{"start"}
               (set (map name (:entrypoints
                               (workflow/resolve-workflow :auto-human-review)))))))
      (is (= 1 (count (pending-auto-run-wakes rt))))
      (is (= "running" (attr-get (weaver/show rt (:id accepted)) :harness/status)))
      (runtime/refresh! rt)
      (is (= 1 (count (pending-auto-run-wakes rt))))
      (is (= 1 (count (weaver/list rt [:= [:attr "harness/run"] "true"] {})))))))

(deftest human-review-delivery-stops-without-land
  (t/with-weaver-world
    [ctx (world-options)]
    (let [rt (:runtime ctx)]
      (current/with-runtime rt
        (let [result (workflow/start!
                      "test-auto-human-review" :auto-human-review
                      {:card "fixture-card"
                       :feature "Disposable feature"
                       :branch "auto/fixture-card"
                       :worktree (:config-dir ctx)})
              root (workflow/current-root "test-auto-human-review")
              strands (:strands (graph/subgraph rt [(:id root)]))
              views (map workflow/step-view strands)
              checkpoint (first (filter #(= "Human review: return the passing PR and stop"
                                            (:title %))
                                        views))]
          (testing "the allowed human workflow reuses delivery stages"
            (is (= ["Implement and verify the assigned feature"]
                   (mapv :title (:ready result))))
            (is (some #(= "Pass repository quality checks for published HEAD"
                          (:title %))
                      views))
            (is (some #(= "Wait for the PR checks" (:title %)) views))
            (is (some #(= "Move the verified feature into review" (:title %))
                      views)))
          (testing "human acceptance is a real stop boundary"
            (is (= "checkpoint" (:role checkpoint)))
            (is (str/includes? (:instruction checkpoint)
                               "Do not choose this checkpoint"))
            (is (str/includes? (:instruction checkpoint)
                               "launch a finisher"))
            (is (nil? (role-step strands "handoff-worker")))
            (is (nil? (role-step strands "finisher")))))))))

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
