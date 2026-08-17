(ns ct.spools.codethread.ralph-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [ct.spools.codethread.ralph]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.weaver.alpha :as weaver]
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

(deftest ralph-selects-the-one-card-workflow-without-roster-or-landing-policy
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
        (is (current/with-runtime rt
              (contains? (set (keys (workflow/workflows))) :ralph-iterate)))
        (is (= [{:name "ralph"
                 :spool "ct.spools.codethread.ralph"
                 :doc "Drive a Kanban epic through repeated headless agent runs."
                 :executable "[:root \"bin/ralph\"]"
                 :build ["go" "build" "-o" "bin/ralph.bin" "."]}]
               (:bins (weaver/op! rt 'bins ["list"]))))
        (is (= #{:start} (:entrypoints steps)))
        (let [about (weaver/op! rt 'about ["ralph"])
              prime (weaver/op! rt 'prime ["ralph"])]
          (is (str/includes? (:about about) "Ralph does not own landing"))
          (is (str/includes? (:prime prime) "Prepare the whole epic"))
          (is (str/includes? (:prime prime) "strand kanban label add <epic-id> ralph")))
        (let [definition (current/with-runtime rt
                           (workflow/resolve-workflow :ralph-iterate))]
          (is (str/includes? (pr-str definition)
                             "consumer-owned landing policy"))
          (is (str/includes? (pr-str definition)
                             "doing-task body and latest note as the claim evidence"))
          (is (str/includes? (pr-str definition)
                             "do not claim it landed without the consumer's landing evidence"))
          (is (not (re-find #"ralph/(?:feature|branch|worktree|card)|--context"
                            (pr-str definition)))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.ralph-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
