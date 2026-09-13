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
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private deps-edn
  (pr-str
   {:deps
    {'codethread/config {:local/root (str project-root "/spools/config")}}}))

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
           (get-in config-deps ['millstrand.spools/batteries :git/sha])))
    (is (= "f487eb42ea9523e8bd405e64a7c319013217d988"
           (get-in config-deps ['millhouse.spools/workflow :git/sha])))
    (is (= "9548390ce621461ba0a289859fe9b0af963f5805"
           (get-in config-deps ['ct.spools/harnesses :git/sha])))
    (is (not-any? #{'ct.spools/agent-run 'ct.spools/delegation}
                  (keys config-deps)))
    (is (= "ceaa684499c6715ce0f10dba5806fd8ebef997da"
           (get-in config-deps ['codethread/devflow :git/sha])))))

(deftest bootstrap-registers-catalog-reviewers-and-agent-executor-in-order
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
      (testing "the official asynchronous workflow executor opens last"
        (current/with-runtime rt
          (is (contains? (set (keys (workflow/executors))) :agent)))))))

(deftest optional-workspace-config-keeps-the-devflow-kanban-election
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millstrand/spools-batteries
                       {:ns 'millstrand.spools.batteries})
      (codethread/register! rt)
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow
                                    :after [:millhouse/spools-workflow]})
      (runtime/module! rt :millhouse/spools-kanban
                       {:ns 'millhouse.spools.kanban})
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
        (current/with-runtime rt
          (is (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                 (workflow/workflow-definition :decompose))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.config-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
