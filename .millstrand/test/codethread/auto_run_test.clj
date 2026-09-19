(ns codethread.auto-run-test
  "Exercise repository auto-run startup in a disposable Weaver world."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
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
              handoff (workflow/step-view (role-step strands "handoff-worker"))
              finisher (workflow/step-view (role-step strands "finisher"))]
          (testing "implementation, quality, CI, and review precede landing"
            (is (= ["Implement and verify the assigned feature"]
                   (mapv :title (:ready result))))
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

(defn -main
  "Run disposable workspace activation tests."
  [& _]
  (let [{:keys [fail error]} (run-tests 'codethread.auto-run-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
