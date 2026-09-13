(ns ct.spools.codethread.config-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.bootstrap :as codethread]
            [ct.spools.harnesses :as harnesses]
            [ct.spools.harnesses.reviewers :as reviewers]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.weaver.alpha :as weaver]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private deps-edn
  (pr-str
   {:deps
    {'codethread/config {:local/root (str project-root "/spools/config")}
     'codethread/ralph {:local/root (str project-root "/spools/ralph")}}}))
(def ^:private workspace-init-clj
  (slurp (io/file project-root ".millstrand/init.clj")))

(deftest workspace-deps-compose-library-roots-and-current-harnesses
  (let [{:keys [deps]} (edn/read-string
                        (slurp (io/file project-root ".millstrand/deps.edn")))
        workspace-root (io/file project-root ".millstrand")
        config-root (io/file workspace-root (get-in deps ['codethread/config :local/root]))
        ralph-root (io/file workspace-root (get-in deps ['codethread/ralph :local/root]))
        config-deps (:deps (edn/read-string (slurp (io/file config-root "deps.edn"))))]
    (is (= {:local/root "../spools/config"}
           (get deps 'codethread/config)))
    (is (= {:local/root "../spools/ralph"}
           (get deps 'codethread/ralph)))
    (is (.isFile (io/file config-root "deps.edn")))
    (is (.isFile (io/file ralph-root "deps.edn")))
    (is (.isFile (io/file ralph-root "bin/ralph")))
    (is (= "310368dff9174bd889ad21d4ed8196952684eaf9"
           (get-in config-deps ['io.millstrand/batteries :git/sha])))
    (is (not (contains? config-deps 'millstrand.spools/batteries)))
    (doseq [[library sha] [['millhouse.spools/workflow
                            "3132c8f7f10455c893da28fef0e9ca0047560f82"]
                           ['millhouse.spools/identity
                            "89e5e32f8a948547c233d5dd183bb73f9c5abe4a"]
                           ['millhouse.spools/kanban
                            "3132c8f7f10455c893da28fef0e9ca0047560f82"]
                           ['millhouse.spools/land
                            "89e5e32f8a948547c233d5dd183bb73f9c5abe4a"]]]
      (is (= sha (get-in config-deps [library :git/sha]))))
    (is (= "e8a26477852216bca2579b050a2356c86af132b7"
           (get-in config-deps ['ct.spools/harnesses :git/sha])))
    (is (not-any? #{'ct.spools/agent-run 'ct.spools/delegation}
                  (keys config-deps)))
    (is (= "99313b48f14ab0892cb90264d100dce4ff2a25e0"
           (get-in config-deps ['codethread/devflow :git/sha])))
    (is (= "99313b48f14ab0892cb90264d100dce4ff2a25e0"
           (get-in config-deps
                   ['codethread/devflow-kanban-adapter :git/sha])))))

(deftest bootstrap-registers-catalog-and-reviewers-without-an-executor
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)
          expected (mapv first codethread/module-definitions)
          result (codethread/register! rt)]
      (is (= expected (:registered result)))
      (is (= expected (:registered (codethread/register! rt))))
      (testing "the preferred Pi-backed role seats resolve"
        (is (= {:alias "luna" :harness "pi"}
               (select-keys (harnesses/resolve-harness rt :luna)
                            [:alias :harness])))
        (is (= "openai-codex/gpt-5.6-luna"
               (get-in (harnesses/resolve-harness rt :luna)
                       [:generated :harness/model])))
        (is (= "openai-codex/gpt-6-astra"
               (get-in (harnesses/resolve-harness rt :oracle)
                       [:generated :harness/model])))
        (is (= "low"
               (get-in (harnesses/resolve-harness rt :luna-low)
                       [:generated :harness/effort]))))
      (testing "provider defaults preserve the authoritative workspace policy"
        (is (false? (harnesses/flag rt :harness/claude)))
        (is (false? (harnesses/flag rt :harness/cursor)))
        (is (false? (:available (harnesses/availability rt :opus)))))
      (testing "shared review lenses resolve through available aliases"
        (let [catalog (reviewers/reviewers rt)]
          (is (= ["docs-and-tests" "runtime-correctness" "source-form"]
                 (mapv :name catalog)))
          (is (every? :available catalog))
          (is (some #{"spools/*/src/**"}
                    (:glob (some #(when (= "source-form" (:name %)) %)
                                 catalog))))))
      (testing "landing is active while executor activation stays deferred"
        (current/with-runtime rt
          (is (some? (workflow/workflow-definition :review)))
          (is (some? (workflow/workflow-definition :land)))
          (is (not (contains? (set (keys (workflow/executors))) :agent))))))))

(deftest consumer-modules-reconcile-before-explicit-executor-activation
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (codethread/register! rt)
      (let [consumer-result
            (runtime/module! rt :consumer/aliases
                             {:ns 'ct.spools.codethread.consumer-fixture
                              :after [:codethread/config-agents]
                              :required? true})]
        (is (= :applied
               (get-in consumer-result
                       [:modules :consumer/aliases :status])))
        (is (= :applied
               (get-in consumer-result
                       [:modules :consumer/aliases :lifecycle/outcomes
                        :consumer-alias :status])))
        (is (= "openai-codex/gpt-5.6-luna"
               (get-in (harnesses/resolve-harness rt :consumer-luna)
                       [:generated :harness/model]))))
      (let [result (codethread/register-executor! rt [:consumer/aliases])
            status (runtime/status rt)]
        (is (= [codethread/executor-module-id] (:registered result)))
        (is (= :consumer/aliases (last (:after result))))
        (is (= (:after result)
               (get-in status
                       [:modules codethread/executor-module-id :after])))
        (is (= :applied
               (get-in status
                       [:last-refresh :modules codethread/executor-module-id
                        :lifecycle/outcomes :agent-engine :status])))
        (current/with-runtime rt
          (is (contains? (set (keys (workflow/executors))) :agent)))))))

(deftest workspace-init-stages-and-activates-the-complete-cli-surface
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :deps-edn deps-edn
                             :init-clj workspace-init-clj}]
    (let [rt (:runtime ctx)
          status (runtime/status rt)
          aliases (weaver/op! rt 'agent ["list"])
          reviewer-result (weaver/op! rt 'agent ["reviewers"])
          workflow-result (weaver/op! rt 'workflow ["list"])
          land-result (weaver/op! rt 'workflow ["show" "land"])
          op-names (set (map :name (weaver/ops rt)))]
      (is (= {:status :applied :mode :full}
             (select-keys (:last-refresh status) [:status :mode])))
      (is (every? #{:applied}
                  (map :status (vals (get-in status
                                             [:last-refresh :modules])))))
      (is (= :applied
             (get-in status
                     [:last-refresh :modules codethread/executor-module-id
                      :lifecycle/outcomes :agent-engine :status])))
      (is (every? (set (map :name aliases))
                  ["coordinator" "grunt" "luna" "oracle" "reviewer"]))
      (is (= ["docs-and-tests" "runtime-correctness" "source-form"]
             (mapv :name (:reviewers reviewer-result))))
      (is (= #{"intake" "land" "publish-spool-kondo" "ralph-iterate" "review"}
             (set (map :name (:definitions workflow-result)))))
      (is (= "land" (:name land-result)))
      (is (= "reviewer" (get-in land-result [:params :defaults :reviewer])))
      (is (contains? op-names "merge-queue")))))

(deftest optional-workspace-config-keeps-the-devflow-kanban-election
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millstrand/spools-batteries
                       {:ns 'millstrand.spools.batteries})
      (codethread/register! rt)
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow
                                    :after [:millhouse/spools-workflow]})
      (runtime/module! rt :devflow/kanban-adapter
                       {:ns 'ct.spools.devflow-kanban-adapter
                        :after [:devflow :millhouse/spools-kanban
                                :millhouse/spools-workflow]})
      (runtime/module! rt :codethread/config-help
                       {:ns 'ct.spools.codethread.help
                        :after [:millstrand/spools-batteries]})
      (runtime/module! rt :codethread/config-devflow
                       {:ns 'ct.spools.codethread.devflow})
      (let [result (runtime/module! rt :codethread/config
                                    {:ns 'ct.spools.codethread.config
                                     :after [:codethread/config-help
                                             :codethread/config-devflow
                                             :devflow/kanban-adapter]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/config :status])))
        (codethread/register-executor!
         rt [:devflow/kanban-adapter :codethread/config])
        (current/with-runtime rt
          (is (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                 (workflow/workflow-definition :decompose))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.config-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
