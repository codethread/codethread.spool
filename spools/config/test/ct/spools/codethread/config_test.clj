(ns ct.spools.codethread.config-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.agent-run :as shuttle]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.test.alpha :as t]
            [millhouse.spools.workflow :as workflow]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private sibling-root
  (fn [name] (.getCanonicalPath (io/file (str "../../../" name)))))
(def ^:private deps-edn
  (pr-str
   {:deps
    {'millstrand.spools/batteries {:local/root (str (sibling-root "skein-src") "/spools/batteries")}
     'codethread/config {:local/root (str project-root "/spools/config")}
     'ct.spools/agent-run {:local/root (str (sibling-root "agent-harness.spool") "/agent-run")}
     'ct.spools/harness-core {:local/root (str (sibling-root "agent-harness.spool") "/harness-core")}
     'ct.spools/claude-harness {:local/root (str (sibling-root "agent-harness.spool") "/claude-harness")}
     'ct.spools/codex-harness {:local/root (str (sibling-root "agent-harness.spool") "/codex-harness")}
     'ct.spools/pi-harness {:local/root (str (sibling-root "agent-harness.spool") "/pi-harness")}
     'ct.spools/delegation {:local/root (str (sibling-root "agent-harness.spool") "/delegation")}
     'codethread/devflow {:local/root (sibling-root "devflow.spool")}
     'codethread/devflow-kanban-adapter {:local/root (str (sibling-root "devflow.spool") "/kanban-adapter")}
     'millhouse.spools/workflow {:local/root (str (sibling-root "millhouse.spool") "/spools/workflow")}
     'millhouse.spools/kanban {:local/root (str (sibling-root "millhouse.spool") "/spools/kanban")}
     'millhouse.spools/identity {:local/root (str (sibling-root "millhouse.spool") "/spools/identity")}}}))

(deftest workspace-deps-compose-local-roots-and-landed-pins
  (let [{:keys [paths deps]} (edn/read-string
                              (slurp (io/file project-root ".millstrand/deps.edn")))]
    (is (= ["../spools/config/src" "../spools/ralph/src"] paths))
    (is (= "71c0ed3d80fcad090b74a704a8eb165a3fad996e"
           (get-in deps ['millstrand.spools/batteries :git/sha])))
    (is (= "f487eb42ea9523e8bd405e64a7c319013217d988"
           (get-in deps ['millhouse.spools/workflow :git/sha])))
    (is (= "fd75bf50ef823e1df520ead410780961d6313474"
           (get-in deps ['ct.spools/agent-run :git/sha])))
    (is (= "90799b8c950b4509167137562fbf18853524d41c"
           (get-in deps ['codethread/devflow :git/sha])))))

(deftest config-activates-shared-elections
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :batteries {:ns 'millstrand.spools.batteries})
      (runtime/module! rt :identity {:ns 'millhouse.spools.identity})
      (runtime/module! rt :agent-run {:ns 'ct.spools.agent-run})
      (runtime/module! rt :delegation {:ns 'ct.spools.delegation :after [:agent-run]})
      (runtime/module! rt :harness-core {:ns 'ct.spools.harness-core
                                         :after [:identity]})
      (runtime/module! rt :claude-harness {:ns 'ct.spools.claude-harness
                                           :after [:harness-core]})
      (runtime/module! rt :codex-harness {:ns 'ct.spools.codex-harness
                                          :after [:harness-core]})
      (runtime/module! rt :pi-harness {:ns 'ct.spools.pi-harness
                                       :after [:harness-core]})
      (runtime/module! rt :workflow {:ns 'millhouse.spools.workflow})
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow :after [:workflow]})
      (runtime/module! rt :kanban {:ns 'millhouse.spools.kanban})
      (runtime/module! rt :adapter {:ns 'ct.spools.devflow-kanban-adapter
                                    :after [:devflow :kanban]})
      (runtime/module! rt :config-agents {:ns 'ct.spools.codethread.agents
                                          :after [:agent-run :claude-harness
                                                  :codex-harness :pi-harness]})
      (runtime/module! rt :config-help {:ns 'ct.spools.codethread.help
                                        :after [:batteries]})
      (runtime/module! rt :config-devflow {:ns 'ct.spools.codethread.devflow})
      (let [result (runtime/module! rt :config
                                    {:ns 'ct.spools.codethread.config
                                     :after [:config-agents :config-help :config-devflow
                                             :batteries :delegation :adapter]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :config :status])))
        (current/with-runtime rt
          (testing "agent seats are selected"
            (is (= :codex (:name (shuttle/resolve-harness :luna-high))))
            (is (= :codex-ro (:name (shuttle/resolve-harness :luna-low-ro)))))
          (testing "the Devflow Kanban route is selected"
            (is (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                   (workflow/workflow-definition :decompose)))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.config-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
