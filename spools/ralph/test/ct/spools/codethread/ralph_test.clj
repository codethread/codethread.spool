(ns ct.spools.codethread.ralph-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [ct.spools.codethread.ralph]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private millhouse-root
  (.getCanonicalPath (io/file "../../../millhouse.spool")))
(def ^:private spools-edn
  {:spools
   {'codethread/ralph {:local/root project-root
                       :roots {'codethread/ralph "spools/ralph"}}
    'millhouse/spools {:local/root millhouse-root
                       :roots {'millhouse.spools/workflow "spools/workflow"}}}})

(deftest ralph-publishes-the-one-card-workflow-without-roster-or-landing-policy
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :spools-edn spools-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
      (let [result (runtime/module! rt :codethread/ralph
                                    {:ns 'ct.spools.codethread.ralph})
            steps (current/with-runtime rt
                    (workflow/resolve-workflow :ralph-iterate))]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/ralph :status])))
        (is (= #{:start} (:entrypoints steps)))
        (let [definition (current/with-runtime rt
                           (workflow/resolve-workflow :ralph-iterate))]
          (is (str/includes? (pr-str definition)
                             "consumer-owned landing policy"))
          (is (str/includes? (pr-str definition)
                             "Do not mark the feature card"))
          (is (not (re-find #"roster|--workflow land|:workflow land"
                            (pr-str definition)))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.ralph-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
