(ns ct.spools.codethread.devflow-setup-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private devflow-root
  (.getCanonicalPath (io/file "../../../devflow.spool")))
(def ^:private kanban-root
  (.getCanonicalPath (io/file "../../../kanban.spool")))
(def ^:private millhouse-root
  (.getCanonicalPath (io/file "../../../millhouse.spool")))
(def ^:private spools-edn
  {:spools
   {'codethread/spools {:local/root project-root
                        :roots {'codethread/devflow-setup "spools/devflow"}}
    'codethread/devflow {:local/root devflow-root
                         :roots {'codethread/devflow "."
                                 'codethread/devflow-kanban-adapter "kanban-adapter"}}
    'codethread/kanban {:local/root kanban-root
                        :roots {'codethread/kanban "."}}
    'millhouse/spools {:local/root millhouse-root
                       :roots {'millhouse.spools/workflow "spools/workflow"}}}})

(deftest setup-activates-the-external-kanban-route
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :spools-edn spools-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow
                       {:ns 'millhouse.spools.workflow})
      (runtime/module! rt :devflow
                       {:ns 'ct.spools.devflow
                        :after [:millhouse/workflow]})
      (runtime/module! rt :kanban
                       {:ns 'ct.spools.kanban})
      (runtime/module! rt :devflow/kanban-adapter
                       {:ns 'ct.spools.devflow-kanban-adapter
                        :after [:devflow :kanban]})
      (let [result (runtime/module! rt :codethread/devflow-setup
                                    {:ns 'ct.spools.codethread.devflow-setup
                                     :after [:devflow/kanban-adapter]})]
        (testing "setup is a separate lifecycle-only module"
          (is (contains? #{:applied :unchanged}
                         (get-in result [:modules :codethread/devflow-setup :status]))))
        (testing "the external adapter owns the selected routed definition"
          (is (current/with-runtime rt
                (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                   (workflow/workflow-definition :decompose)))))
        (testing "the copied provider namespace is not part of this root"
          (is (nil? (find-ns 'codethread.spools.devflow))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.devflow-setup-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
