(ns codethread.spools.devflow-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [codethread.spools.devflow :as devflow]
            [codethread.spools.devflow-kanban-adapter :as adapter]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(deftest devflow-publishes-static-definitions-and-guidance
  (is (nil? (resolve 'codethread.spools.devflow/spool)))
  (is (str/includes? (devflow/guidance) "proposal"))
  (is (some? (ns-resolve 'codethread.spools.devflow 'decompose-open))))

(deftest kanban-seed-binds-the-default-decompose-route
  (t/with-weaver-world [ctx {:storage :sqlite-memory}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
      (runtime/module! rt :codethread/devflow {:ns 'codethread.spools.devflow})
      (let [result (runtime/module! rt :codethread/devflow-kanban
                                    {:ns 'codethread.spools.devflow-kanban-adapter
                                     :after [:codethread/devflow]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/devflow-kanban :status])))
        (current/with-runtime rt
          (is (= #{:continue :call}
                 (:entrypoints (workflow/resolve-workflow :decompose-kanban)))))
        (is (= :decompose (:repointed (adapter/repoint-decompose! {:runtime rt}))))))))

(deftest devflow-does-not-grow-a-run-driving-facade
  (doseq [sym '[start! ready complete! choose! advance!]]
    (is (nil? (ns-resolve 'codethread.spools.devflow sym)))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'codethread.spools.devflow-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
