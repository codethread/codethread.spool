(ns ct.spools.codethread.spool-bump-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.spool-bump]
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
          (let [definition (:value (workflow/resolve-workflow :spool-bump))
                step (fn [id]
                       (some #(when (= id (:id %)) %) (:steps definition)))
                ci-argv ((get-in (step :wait-for-green) [:attributes "shell/argv"])
                         {:branch "branch"})
                pull-argv (get-in (step :pull-main) [:attributes "shell/argv"])]
            (is (= #{:start} (:entrypoints definition)))
            (testing "the registered CI gate retains its packaged script support"
              (is (= ["sh" "-c"] (subvec ci-argv 0 2)))
              (is (= "spool-bump-ci-watch" (nth ci-argv 3)))
              (is (= ["branch" "180" "5"] (subvec ci-argv 4)))
              (is (str/includes? (nth ci-argv 2) "gh pr checks")))
            (testing "the registered pull gate retains its inline script support"
              (is (= ["sh" "-c"] (subvec pull-argv 0 2)))
              (is (str/includes? (nth pull-argv 2)
                                 "git -C \"$root\" pull --ff-only origin main")))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.spool-bump-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
