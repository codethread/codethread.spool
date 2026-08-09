(ns ct.spools.codethread.agents-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.agent-run :as shuttle]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private agent-harness-root
  (.getCanonicalPath (io/file "../../../agent-harness.spool")))
(def ^:private spools-edn
  {:spools
   {'codethread/agents {:local/root project-root
                        :roots {'codethread/agents "spools/agents"}}
    'ct.spools/agent-run {:local/root agent-harness-root
                          :roots {'ct.spools/agent-run "agent-run"
                                  'ct.spools/delegation "delegation"}}}})

(deftest agents-module-activation-resolves-live-seats
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :spools-edn spools-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :agent-run {:ns 'ct.spools.agent-run})
      (runtime/module! rt :delegation {:ns 'ct.spools.delegation
                                       :after [:agent-run]})
      (let [result (runtime/module! rt :codethread/agents
                                    {:ns 'ct.spools.codethread.agents
                                     :after [:delegation]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/agents :status])))
        (testing "the live registry resolves a model seat and a read-only seat"
          (current/with-runtime rt
            (let [luna (shuttle/resolve-harness :luna-high)
                  read-only (shuttle/resolve-harness :luna-low-ro)]
              (is (= :codex (:name luna)))
              (is (= :codex-ro (:name read-only)))
              (is (some #{"gpt-5.6-luna"} (:extra-args luna)))
              (is (some #{"gpt-5.6-luna"} (:extra-args read-only))))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.agents-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
