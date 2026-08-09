(ns ct.spools.codethread.spool-bump-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.spool-bump]
            [ct.spools.codethread.spool-bump.internal.support :as support]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private millhouse-root
  (.getCanonicalPath (io/file "../../../millhouse.spool")))
(def ^:private spools-edn
  {:spools
   {'codethread/spool-bump {:local/root project-root
                            :roots {'codethread/spool-bump "spools/spool-bump"}}
    'millhouse/spools {:local/root millhouse-root
                       :roots {'millhouse.spools/workflow "spools/workflow"}}}})

(deftest spool-bump-keeps-its-workflow-and-only-needed-script-support
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :spools-edn spools-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
      (let [result (runtime/module! rt :codethread/spool-bump
                                    {:ns 'ct.spools.codethread.spool-bump})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/spool-bump :status])))
        (current/with-runtime rt
          (is (= #{:start}
                 (:entrypoints (workflow/resolve-workflow :spool-bump)))))
        (is (str/includes? support/feature-ci-watch-script "gh pr checks"))
        (is (= ["sh" "-c" support/feature-ci-watch-script "watch" "branch" "180" "5"]
               (support/sh-gate support/feature-ci-watch-script
                                "watch" "branch" "180" "5")))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.spool-bump-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
