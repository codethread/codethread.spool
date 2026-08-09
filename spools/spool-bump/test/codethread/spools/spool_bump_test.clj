(ns codethread.spools.spool-bump-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [codethread.spools.spool-bump]
            [codethread.spools.spool-bump.support :as support]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millhouse.spools.workflow :as workflow]
            [millstrand.test.alpha :as t]))

(deftest spool-bump-keeps-its-workflow-and-only-needed-script-support
  (t/with-weaver-world [ctx {:storage :sqlite-memory}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millhouse/workflow {:ns 'millhouse.spools.workflow})
      (let [result (runtime/module! rt :codethread/spool-bump
                                    {:ns 'codethread.spools.spool-bump})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/spool-bump :status])))
        (current/with-runtime rt
          (is (= #{:start}
                 (:entrypoints (workflow/resolve-workflow :spool-bump)))))
        (is (str/includes? support/feature-ci-watch-script "gh pr checks"))
        (is (nil? (ns-resolve 'codethread.spools.spool-bump.support
                             'land-quality-gate-script)))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'codethread.spools.spool-bump-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
