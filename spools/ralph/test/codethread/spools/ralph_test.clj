(ns codethread.spools.ralph-test
  (:require [clojure.test :refer [deftest is]]
            [codethread.spools.ralph]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(deftest ralph-publishes-the-one-card-workflow-without-roster-or-landing-policy
  (t/with-weaver-world [ctx {:storage :sqlite-memory}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
      (let [result (runtime/module! rt :codethread/ralph
                                    {:ns 'codethread.spools.ralph})
            steps (current/with-runtime rt
                    (workflow/resolve-workflow :ralph-iterate))]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/ralph :status])))
        (is (= #{:start} (:entrypoints steps)))
        (is (current/with-runtime rt
              (some? (workflow/describe :ralph-iterate {:epic "e1"}))))
        (is (not-any? #(re-find #"roster|--workflow land|:workflow land"
                                (pr-str %))
                      (tree-seq coll? seq steps)))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'codethread.spools.ralph-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
