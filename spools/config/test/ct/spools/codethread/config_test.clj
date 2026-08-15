(ns ct.spools.codethread.config-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.agent-run :as shuttle]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.test.alpha :as t]
            [millhouse.spools.workflow :as workflow]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private sibling-root
  (fn [name] (.getCanonicalPath (io/file (str "../../../" name)))))
(def ^:private spools-edn
  {:spools
   {'millstrand.spools/batteries {:millstrand/source-root "spools/batteries"}
    'codethread/spools {:local/root project-root
                        :roots {'codethread/config "spools/config"}}
    'ct.spools/agent-run {:local/root (sibling-root "agent-harness.spool")
                          :roots {'ct.spools/agent-run "agent-run"
                                  'ct.spools/delegation "delegation"}}
    'codethread/devflow {:local/root (sibling-root "devflow.spool")
                         :roots {'codethread/devflow "."
                                 'codethread/devflow-kanban-adapter "kanban-adapter"}}
    'codethread/kanban {:local/root (sibling-root "kanban.spool")
                        :roots {'codethread/kanban "."}}
    'millhouse/spools {:local/root (sibling-root "millhouse.spool")
                       :roots {'millhouse.spools/workflow "spools/workflow"}}}})

(deftest config-activates-shared-elections
  (t/with-weaver-world [ctx {:storage :sqlite-memory :spools-edn spools-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :batteries {:ns 'millstrand.spools.batteries})
      (runtime/module! rt :agent-run {:ns 'ct.spools.agent-run})
      (runtime/module! rt :delegation {:ns 'ct.spools.delegation :after [:agent-run]})
      (runtime/module! rt :workflow {:ns 'millhouse.spools.workflow})
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow :after [:workflow]})
      (runtime/module! rt :kanban {:ns 'ct.spools.kanban})
      (runtime/module! rt :adapter {:ns 'ct.spools.devflow-kanban-adapter
                                    :after [:devflow :kanban]})
      (let [result (runtime/module! rt :config
                                    {:ns 'ct.spools.codethread.config
                                     :after [:batteries :delegation :adapter]})]
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
