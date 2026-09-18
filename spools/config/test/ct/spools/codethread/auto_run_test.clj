(ns ct.spools.codethread.auto-run-test
  "Disposable Weaver tests for card admission and durable assignment receipts."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.auto-run :as auto-run]
            [ct.spools.codethread.auto-run-worktree :as worktree]
            [ct.spools.harnesses :as harnesses]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.scheduler.alpha :as scheduler]
            [millstrand.api.spool.alpha :refer [attr-get]]
            [millstrand.api.weaver.alpha :as weaver]
            [millstrand.test.alpha :as t]))

(def ^:private fixture
  "(ns auto-run.fixture
     (:require [clojure.java.io :as io]
               [clojure.spec.alpha :as s]
               [clojure.string :as str]
               [ct.spools.harnesses :as harnesses]
               [ct.spools.harnesses.assignment :as assignment]
               [millhouse.spools.workflow :as workflow]
               [millstrand.api.lifecycle.alpha :as lifecycle]
               [millstrand.api.spool.alpha :refer [attr-get]]
               [millstrand.api.weaver.alpha :as weaver]))
   (lifecycle/use-resource! harnesses/harness-core-runtime assignment/assignment-runtime)
   (s/def ::card (s/and string? (complement str/blank?)))
   (s/def ::delivery-params (s/keys :req-un [::card]))
   (workflow/defworkflow! deliver
     \"A worker-driven delivery ending at human acceptance.\"
     {:entrypoints #{:start} :param-spec ::delivery-params}
     (workflow/workflow \"Delivery\"
       (workflow/step :implement \"Implement\" :self)
       (workflow/checkpoint :accept \"Human acceptance\"
         :depends-on [:implement] :kind :human
         :choices [{:key :approved :label \"Approved\"}])))
   (defn prepare! [_rt {:keys [repo card]}]
     (let [cwd (io/file repo (:id card))]
       (.mkdirs cwd)
       {:cwd (.getCanonicalPath cwd) :branch (str \"auto/\" (:id card))}))
   (defn withdrawn! [rt {:keys [card] :as request}]
     (weaver/update! rt (:id card) {:attributes {:kanban/lane \"refinement\"}})
     (prepare! rt request))
   (defn revised! [rt {:keys [card] :as request}]
     (weaver/update! rt (:id card) {:attributes {:acme/review-scope \"prepared\"}})
     (prepare! rt request))
   (defn broken! [_rt _request] (throw (ex-info \"No worktree capacity\" {})))
   (defn start-params! [_rt {:keys [card settings prepared]}]
     {:repository-param (str (:id card) \"/\" (:workflow settings) \"/\"
                             (:branch prepared))
      :review-scope (attr-get card :acme/review-scope)})
   (defn withdraw-start-params! [rt {:keys [card]}]
     (weaver/update! rt (:id card) {:attributes {:kanban/lane \"refinement\"}})
     {:repository-param \"withdrawn\"})
   (defn corrupt-start-params! [rt {:keys [card]}]
     (weaver/update! rt (:id card)
                     {:attributes
                      (case (attr-get card :acme/mutation)
                        \"type\" {:kanban/type \"epic\"}
                        \"card\" {:kanban/card nil}
                        \"receipt\" {:auto-run/request-id \"replacement\"})})
     {:repository-param \"corrupt\"})
   (defn invalid-start-params! [_rt _request] [:not-a-map])
   (defn conflicting-start-params! [_rt _request] {:seat \"other\"})
   (defn broken-start-params! [_rt _request]
     (throw (ex-info \"No repository workflow parameters\" {})))")

(defn- with-world [f]
  (t/with-weaver-world
    [ctx {:storage :sqlite-memory
          :deps-edn (pr-str {:deps {'codethread/config
                                   {:local/root (.getCanonicalPath (io/file "."))}}})
          :init-clj
          "(require '[millstrand.api.current.alpha :as current]
                    '[millstrand.api.runtime.alpha :as runtime])
           (runtime/module! (current/runtime) :identity
             {:ns 'millhouse.spools.identity :required? true})
           (runtime/module! (current/runtime) :workflow
             {:ns 'millhouse.spools.workflow :required? true})
           (runtime/module! (current/runtime) :fixture
             {:file \"fixture.clj\" :after [:identity :workflow] :required? true})"
          :files {"fixture.clj" fixture}}]
    (let [rt (:runtime ctx)
          config {:repo (:config-dir ctx) :seat "fake" :effort "high"
                  :workflow "deliver" :workflows #{"deliver"}
                  :prepare 'auto-run.fixture/prepare!
                  :enabled? true :max-running 1 :interval-ms 3600000}]
      (harnesses/register-harness!
       rt :fake {:modes #{:headless}
                 :prepare 'ct.spools.harnesses/create!
                 :finish 'ct.spools.harnesses/finish!})
      (auto-run/configure! rt config)
      (f rt config))))

(defn- card! [rt attrs & [edges]]
  (weaver/add! rt (cond-> {:title "A bounded feature"
                         :attributes (merge {:kanban/card "true"
                                             :kanban/type "feature"
                                             :kanban/lane "pending"
                                             :kanban/priority "p2"
                                             :kanban.label/auto-run "true"}
                                            attrs)}
                   edges (assoc :edges edges))))

(defn- show [rt card key]
  (attr-get (weaver/show rt (:id card)) key))

(deftest eligibility-is-explicit-and-one-shot
  (let [base {:state "active"
              :attributes {:kanban/card "true" :kanban/type "feature"
                           :kanban/lane "pending" :kanban.label/auto-run "true"}}]
    (is (auto-run/eligible? base))
    (doseq [[key value] [[:kanban/type "epic"] [:kanban/lane "refinement"]
                        [:kanban.label/auto-run "false"] [:owner "someone"]
                        [:auto-run/status "assigned"] [:auto-run/status "error"]
                        [:auto-run/request-id "previous"]]]
      (is (not (auto-run/eligible? (assoc-in base [:attributes key] value)))))
    (is (not (auto-run/eligible? (assoc base :state "closed"))))))

(deftest admission-respects-dependencies-overrides-capacity-and-replay
  (with-world
    (fn [rt _config]
      (let [blocker (weaver/add! rt {:title "Prerequisite"})
            blocked (card! rt {} [{:type "depends-on" :to (:id blocker)}])
            unlabelled (card! rt {:kanban.label/auto-run nil})
            refinement (card! rt {:kanban/lane "refinement"})
            owner (card! rt {:owner "manual-worker"})
            selected (card! rt {:kanban/priority "p1" :auto-run/effort "low"})
            later (card! rt {:kanban/priority "p3"})
            result (auto-run/scan! rt)
            run (weaver/show rt (get-in result [:dispatched 0 :run]))]
        (is (= [(:id selected)] (mapv :card (:dispatched result))))
        (is (= "low" (attr-get run :harness/effort)))
        (is (= (:id selected) (attr-get run :harness/target)))
        (is (= "assigned" (show rt selected :auto-run/status)))
        (is (= "pending" (show rt selected :kanban/lane)) "Worker, not dispatcher, claims")
        (is (nil? (show rt selected :owner)))
        (is (= "fake" (show rt selected :auto-run/effective-seat)))
        (current/with-runtime rt
          (let [root (workflow/current-root (show rt selected :auto-run/workflow-run-id))]
            (is (some? root))
            (is (= #{:card :feature :worktree :branch :seat :effort}
                   (set (keys (attr-get root :workflow/context)))))
            (is (= (:id selected)
                   (get (attr-get root :workflow/context) :card)))))
        (doseq [untouched [blocked unlabelled refinement owner later]]
          (is (nil? (show rt untouched :auto-run/status))))
        (is (empty? (:dispatched (auto-run/scan! rt))))
        (testing "a terminal assignment does not automatically rearm its card"
          (weaver/update! rt (:id run)
                          {:state "closed"
                           :attributes {:harness/status "stopped" :harness/settled "true"}})
          (weaver/update! rt (:id blocker) {:state "closed"})
          (is (= [(:id blocked)] (mapv :card (:dispatched (auto-run/scan! rt)))))
        (is (= 2 (count (weaver/list rt [:= [:attr "harness/run"] "true"] {})))))))))

(deftest repository-parameters-reach-workflows-without-recovery-duplication
  (with-world
    (fn [rt config]
      (auto-run/configure! rt (assoc config
                                   :prepare 'auto-run.fixture/revised!
                                   :start-params 'auto-run.fixture/start-params!))
      (let [card (card! rt {:acme/review-scope "initial"})
            run-id (get-in (auto-run/scan! rt) [:dispatched 0 :run])
            workflow-run-id (show rt card :auto-run/workflow-run-id)]
        (current/with-runtime rt
          (let [root (workflow/current-root workflow-run-id)]
            (is (= (str (:id card) "/deliver/auto/" (:id card))
                   (get (attr-get root :workflow/context) :repository-param)))
            (is (= "prepared"
                   (get (attr-get root :workflow/context) :review-scope)))))
        (weaver/update! rt (:id card)
                        {:attributes {:auto-run/status "preparing" :auto-run/run-id nil}})
        (auto-run/scan! rt)
        (is (= run-id (show rt card :auto-run/run-id)))
        (is (= 1 (count (weaver/list rt [:= [:attr "harness/run"] "true"] {}))))))))

(deftest card-edits-during-workflow-parameter-callback-cancel-admission
  (with-world
    (fn [rt config]
      (auto-run/configure! rt (assoc config
                                   :start-params 'auto-run.fixture/withdraw-start-params!))
      (let [card (card! rt {})]
        (auto-run/scan! rt)
        (is (= "error" (show rt card :auto-run/status)))
        (is (= "refinement" (show rt card :kanban/lane)))
        (is (empty? (weaver/list rt [:= [:attr "harness/run"] "true"] {})))
        (current/with-runtime rt
          (is (nil? (workflow/current-root (show rt card :auto-run/workflow-run-id)))))))))

(deftest callback-cannot-corrupt-card-or-dispatch-receipt
  (with-world
    (fn [rt config]
      (auto-run/configure! rt (assoc config
                                   :start-params 'auto-run.fixture/corrupt-start-params!))
      (doseq [mutation ["type" "card" "receipt"]]
        (let [card (card! rt {:acme/mutation mutation})]
          (auto-run/scan! rt)
          (is (= "error" (show rt card :auto-run/status)))
          (is (empty? (weaver/list rt [:= [:attr "harness/run"] "true"] {})))
          (current/with-runtime rt
            (is (nil? (workflow/current-root
                       (show rt card :auto-run/workflow-run-id))))))))))

(deftest invalid-workflow-parameter-callbacks-stall-without-assignment
  (with-world
    (fn [rt config]
      (doseq [[callback error]
              [['auto-run.fixture/invalid-start-params! "Invalid auto-run workflow parameter result"]
               ['auto-run.fixture/conflicting-start-params!
                "Auto-run workflow parameters cannot override dispatcher fields"]
               ['auto-run.fixture/broken-start-params! "No repository workflow parameters"]]]
        (auto-run/configure! rt (assoc config :start-params callback))
        (let [card (card! rt {})]
          (auto-run/scan! rt)
          (is (= "error" (show rt card :auto-run/status)))
          (is (re-find (re-pattern error) (show rt card :auto-run/error)))
          (is (nil? (show rt card :auto-run/run-id)))
          (is (empty? (:dispatched (auto-run/scan! rt))))
          (current/with-runtime rt
            (is (nil? (workflow/current-root (show rt card :auto-run/workflow-run-id)))))))
      (is (empty? (weaver/list rt [:= [:attr "harness/run"] "true"] {}))))))

(deftest invalid-card-and-preparation-errors-stall-without-retry
  (with-world
    (fn [rt config]
      (let [invalid (card! rt {:auto-run/workflow "not-allowed"})]
        (is (seq (:dispatched (auto-run/scan! rt))))
        (is (= "error" (show rt invalid :auto-run/status)))
        (is (nil? (show rt invalid :auto-run/run-id)))
        (is (empty? (:dispatched (auto-run/scan! rt)))))
      (auto-run/configure! rt (assoc config :prepare 'auto-run.fixture/broken!))
      (let [broken (card! rt {})]
        (auto-run/scan! rt)
        (is (= "No worktree capacity" (show rt broken :auto-run/error)))
        (is (empty? (:dispatched (auto-run/scan! rt))))
        (is (empty? (weaver/list rt [:= [:attr "harness/run"] "true"] {})))))))

(deftest board-edits-during-preparation-do-not-pour-an-unused-workflow
  (with-world
    (fn [rt config]
      (auto-run/configure! rt (assoc config :prepare 'auto-run.fixture/withdrawn!))
      (let [card (card! rt {})]
        (auto-run/scan! rt)
        (is (= "error" (show rt card :auto-run/status)))
        (is (= "refinement" (show rt card :kanban/lane)))
        (is (empty? (weaver/list rt [:= [:attr "harness/run"] "true"] {})))
        (current/with-runtime rt
          (is (nil? (workflow/current-root (show rt card :auto-run/workflow-run-id)))))))))

(deftest interrupted-publication-adopts-but-incomplete-preparation-needs-intervention
  (with-world
    (fn [rt _config]
      (let [card (card! rt {})
            run-id (get-in (auto-run/scan! rt) [:dispatched 0 :run])]
        (weaver/update! rt (:id card)
                        {:attributes {:auto-run/status "preparing" :auto-run/run-id nil}})
        (auto-run/scan! rt)
        (is (= run-id (show rt card :auto-run/run-id)))
        (is (= "assigned" (show rt card :auto-run/status)))
        (is (= 1 (count (weaver/list rt [:= [:attr "harness/run"] "true"] {})))))
      (let [card (card! rt {:auto-run/status "preparing" :auto-run/request-id "interrupted"})]
        (auto-run/scan! rt)
        (is (= "error" (show rt card :auto-run/status)))
        (is (re-find #"interrupted" (show rt card :auto-run/error)))))))

(deftest disable-and-stale-wakes-cannot-admit-work
  (with-world
    (fn [rt config]
      (let [card (card! rt {})
            wake (some #(when (= "codethread/auto-run" (:key %)) %) (scheduler/pending rt))]
        (auto-run/configure! rt (assoc config :enabled? false))
        (auto-run/wake! {:runtime rt :payload (:payload wake)})
        (is (empty? (:dispatched (auto-run/scan! rt))))
        (is (nil? (show rt card :auto-run/status)))
        (auto-run/configure! rt config)
        (auto-run/wake! {:runtime rt :payload (:payload wake)})
        (is (nil? (show rt card :auto-run/status)))
        (let [fresh (some #(when (= "codethread/auto-run" (:key %)) %) (scheduler/pending rt))]
          (auto-run/wake! {:runtime rt :payload (:payload fresh)})
          (is (= "assigned" (show rt card :auto-run/status))))
        (auto-run/stop! rt)
        (is (false? (:enabled (auto-run/status rt))))))))

(deftest wktree-output-is-a-strict-boundary
  (is (= {:cwd "/tmp/feature" :branch "auto/abc" :script nil}
         (worktree/ready-result
          "{\"kind\":\"ready\",\"worktree_path\":\"/tmp/feature\",\"branch\":\"auto/abc\"}"
          "auto/abc")))
  (doseq [text ["{\"kind\":\"pool_full\"}"
                "{\"kind\":\"ready\",\"worktree_path\":\"/tmp/f\",\"branch\":\"main\"}"]]
    (is (thrown? clojure.lang.ExceptionInfo (worktree/ready-result text "auto/abc")))))
