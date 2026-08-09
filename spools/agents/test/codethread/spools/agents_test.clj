(ns codethread.spools.agents-test
  (:require [clojure.test :refer [deftest is testing]]
            [codethread.spools.agents]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.test.alpha :as t]))

(deftest agents-publishes-the-new-harness-declaration-shape
  (testing "the producer uses the authoring forms and carries luna-high"
    (is (some? (ns-resolve 'codethread.spools.agents 'harness-defs)))
    (is (some? (ns-resolve 'codethread.spools.agents 'alias-defs)))
    (is (some? (ns-resolve 'codethread.spools.agents 'open-harnesses!)))
    (is (some? (ns-resolve 'codethread.spools.agents 'harnesses-runtime)))))

(deftest agents-module-activation-is-a-composition-boundary
  (t/with-weaver-world [ctx {:storage :sqlite-memory}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :agent-run {:ns 'ct.spools.agent-run})
      (runtime/module! rt :delegation {:ns 'ct.spools.delegation
                                       :after [:agent-run]})
      (let [result (runtime/module! rt :codethread/agents
                                    {:ns 'codethread.spools.agents})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/agents :status])))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'codethread.spools.agents-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
