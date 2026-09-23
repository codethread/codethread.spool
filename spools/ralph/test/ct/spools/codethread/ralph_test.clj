(ns ct.spools.codethread.ralph-test
  "Exercise Ralph's routed ready surface and checked completion in disposable worlds."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.ralph]
            [ct.spools.codethread.ralph.completion :as completion]
            [millstrand.api.batch.alpha :as batch]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.graph.alpha :as graph]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.spool.alpha :refer [attr-get]]
            [millstrand.api.weaver.alpha :as weaver]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private deps-edn
  (pr-str {:deps {'codethread/ralph {:local/root (str project-root "/spools/ralph")}}}))

(defn- activate! [rt]
  (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
  (runtime/module! rt :codethread/ralph {:ns 'ct.spools.codethread.ralph}))

(defn- fixture! [rt attributes]
  (:refs
   (batch/apply!
    rt {:strands [{:ref :epic :title "Epic"
                   :attributes {:kanban/card "true" :kanban/type "epic"
                                :kanban/lane "pending"}}
                  {:ref :feature :title "Feature" :attributes
                   (merge {:kanban/card "true" :kanban/type "feature"}
                          attributes)}]
        :edges [{:op :upsert :from :epic :to :feature :type "parent-of"}]})))

(deftest iteration-selects-one-feature-through-a-fresh-continuation
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (activate! rt)
      (current/with-runtime rt
        (let [started (workflow/start! "iteration" :ralph-iterate {:epic "epic"})]
          (is (= ["step"] (mapv :role (:ready started))))
          (is (= "frontier" (:checkpoint (first (:ready (workflow/complete! "iteration"))))))
          (is (thrown? clojure.lang.ExceptionInfo
                       (workflow/choose! "iteration" :work-feature {})))
          (let [selected (workflow/choose! "iteration" :work-feature {:feature "selected"})]
            (is (= 1 (count (:ready selected))))
            (is (re-find #"selected" (:instruction (first (:ready selected))))))
          (doseq [expected ["Work the claimed feature's ready tasks"
                            "Validate and commit the claimed feature slice"
                            "Record the selected feature's landing handoff"
                            "Close the epic only after accepted feature outcomes"]]
            (is (= expected (:title (first (:ready (workflow/complete! "iteration")))))))
          (let [left-open (workflow/choose! "iteration" :next-iteration {})]
            (is (:done left-open))))))))

(deftest empty-runnable-frontier-routes-to-checked-completion-not-claim
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)
          {:keys [epic feature]} (fixture! rt {:kanban/lane "claimed"})]
      (activate! rt)
      (current/with-runtime rt
        (workflow/start! "empty-frontier" :ralph-iterate {:epic epic})
        (workflow/complete! "empty-frontier")
        (let [judgment (workflow/choose! "empty-frontier" :no-runnable {})]
          (is (= ["epic-judgment"] (mapv :checkpoint (:ready judgment)))))
        (let [chosen (workflow/choose! "empty-frontier" :close-epic {})
              gate (first (:ready chosen))
              stored (weaver/show rt (:id gate))]
          (is (false? (:done chosen)))
          (is (= "code" (:gate gate)))
          (is (= {:epic epic} (attr-get stored :code/params)))
          (is (= "ct.spools.codethread.ralph.completion/finish-epic!"
                 (attr-get stored :code/fn)))
          (is (thrown? clojure.lang.ExceptionInfo
                       (completion/finish-epic! (attr-get stored :code/params))))
          (is (= "active" (:state (weaver/show rt epic))))
          (is (= "claimed" (attr-get (weaver/show rt feature) :kanban/lane)))
          (weaver/update! rt feature {:state "closed" :attributes {:kanban/outcome "done"}})
          (let [receipt (completion/finish-epic! {:epic epic})]
            (is (= [{:id feature :state "closed" :outcome "done"}] (:features receipt)))
            (is (= receipt (attr-get (weaver/show rt epic) :ralph/completion)))
            (is (= "closed" (:state (weaver/show rt epic))))
            (is (= receipt (completion/finish-epic! {:epic epic})))
            (is (:done (workflow/complete! "empty-frontier"
                                           {:step (:id gate) :executor "code"
                                            :attributes {"code/result" receipt}})))))))))

(deftest all-direct-feature-outcomes-not-runnable-status-control-completion
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (current/with-runtime rt
        (doseq [lane ["pending" "claimed" "in_review" "in_production"]]
          (testing lane
            (let [{:keys [epic feature]} (fixture! rt {:kanban/lane lane})
                  blocker (weaver/add! rt {:title "Blocker"})]
              (batch/apply! rt {:refs {:feature feature :blocker (:id blocker)}
                                :edges [{:op :upsert :from :feature :to :blocker
                                         :type "depends-on"}]})
              (is (thrown? clojure.lang.ExceptionInfo (completion/finish-epic! {:epic epic})))
              (is (= "active" (:state (weaver/show rt feature))))
              (is (nil? (attr-get (weaver/show rt epic) :ralph/completion))))))
        (doseq [outcome [nil "abandoned" "unactioned"]]
          (testing (str "unaccepted outcome " outcome)
            (let [{:keys [epic feature]} (fixture! rt {:kanban/outcome outcome})]
              (weaver/update! rt feature {:state "closed"})
              (is (thrown? clojure.lang.ExceptionInfo (completion/finish-epic! {:epic epic})))
              (is (= "active" (:state (weaver/show rt epic)))))))))))

(deftest completed-feature-does-not-hide-an-unfinished-sibling
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)
          {:keys [epic feature]} (fixture! rt {:kanban/outcome "done"})
          sibling (:id (weaver/add! rt {:title "Awaiting landing"
                                        :attributes {:kanban/card "true"
                                                     :kanban/type "feature"
                                                     :kanban/lane "in_review"}}))]
      (weaver/update! rt feature {:state "closed"})
      (batch/apply! rt {:refs {:epic epic :sibling sibling}
                        :edges [{:op :upsert :from :epic :to :sibling :type "parent-of"}]})
      (current/with-runtime rt
        (is (thrown? clojure.lang.ExceptionInfo (completion/finish-epic! {:epic epic}))))
      (is (= "active" (:state (weaver/show rt epic))))
      (is (= "in_review" (attr-get (weaver/show rt sibling) :kanban/lane)))
      (is (= "done" (attr-get (weaver/show rt feature) :kanban/outcome))))))

(deftest code-executor-persists-completion-before-the-run-finishes
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)
          {:keys [epic feature]} (fixture! rt {:kanban/outcome "done"})]
      (weaver/update! rt feature {:state "closed"})
      (activate! rt)
      (current/with-runtime rt
        (workflow/start! "executor" :ralph-iterate {:epic epic})
        (workflow/complete! "executor")
        (workflow/choose! "executor" :no-runnable {})
        (let [gate (first (:ready (workflow/choose! "executor" :close-epic {})))]
          (runtime/module! rt :providers {:ns 'millhouse.spools.workflow.spool})
          (let [result (workflow/await! "executor" {:timeout-secs 10})]
            (is (:done result) (pr-str result (weaver/show rt (:id gate)))))
          (let [closed-epic (weaver/show rt epic)
                receipt (attr-get closed-epic :ralph/completion)]
            (is (= "closed" (:state closed-epic)))
            (is (= [{:id feature :state "closed" :outcome "done"}] (:features receipt)))
            (is (= receipt (attr-get (weaver/show rt (:id gate)) :code/result)))))))))

(deftest completion-rejects-a-child-change-between-check-and-update
  (t/with-weaver-world [ctx {:storage :sqlite-file :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (activate! rt)
      (doseq [change [:add-child :reopen-child :add-done-child]]
        (testing (name change)
          (let [{:keys [epic feature]} (fixture! rt {:kanban/outcome "done"})
                checked (promise)
                resume (promise)
                update! weaver/update!]
            (weaver/update! rt feature {:state "closed"})
            ;; Pause only the scheduling of the real public update. Both actors
            ;; still persist through the production graph APIs, without stubs.
            (with-redefs [weaver/update!
                          (fn [runtime id patch & context]
                            (when (= epic id)
                              (deliver checked true)
                              (when (= ::timeout (deref resume 10000 ::timeout))
                                (throw (ex-info "Interleaving was not released" {}))))
                            (apply update! runtime id patch context))]
              (let [closing (future
                              (current/with-runtime rt
                                (try
                                  (completion/finish-epic! {:epic epic})
                                  (catch Exception error error))))]
                (try
                  (is (= true (deref checked 10000 ::timeout)))
                  (case change
                    (:add-child :add-done-child)
                    (batch/apply! rt
                                  {:refs {:epic epic}
                                   :strands [{:ref :new :title "New child"
                                              :state (if (= :add-done-child change) "closed" "active")
                                              :attributes {:kanban/card "true"
                                                           :kanban/type "feature"
                                                           :kanban/lane "pending"
                                                           :kanban/outcome (when (= :add-done-child change) "done")}}]
                                   :edges [{:op :upsert :from :epic :to :new
                                            :type "parent-of"}]})
                    :reopen-child
                    (weaver/update! rt feature {:state "active"
                                                :attributes {:kanban/lane "claimed"
                                                             :kanban/outcome nil}}))
                  (finally (deliver resume true)))
                (let [result (deref closing 10000 ::timeout)]
                  (is (instance? clojure.lang.ExceptionInfo result))
                  (is (= :strand/update-before-commit (:hook/type (ex-data result))))
                  (is (= (if (= :add-done-child change)
                           "Ralph completion snapshot changed before epic closure"
                           "Ralph epic still has unfinished or unaccepted features")
                         (some-> result ex-cause ex-message))))
                (is (= "active" (:state (weaver/show rt epic))))
                (is (nil? (attr-get (weaver/show rt epic) :ralph/completion)))
                (is (= (if (= :reopen-child change) 1 2)
                       (count (graph/outgoing-edges rt [epic] "parent-of"))))
                (is (= (if (= :reopen-child change) "active" "closed")
                       (:state (weaver/show rt feature))))))))))))

(defn -main
  "Run Ralph workflow regression tests."
  [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.ralph-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
