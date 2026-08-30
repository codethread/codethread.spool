(ns ct.spools.codethread.config-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.agent-run :as shuttle]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.test.alpha :as t]
            [millhouse.spools.workflow :as workflow]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private deps-edn
  (pr-str
   {:deps
    {'millstrand.spools/batteries
     {:git/url "https://github.com/codethread/millstrand.git"
      :git/sha "bbca5638bce72ad6a00b2ca916cabcfe99107828"
      :deps/root "spools/batteries"}
     'codethread/config {:local/root (str project-root "/spools/config")}
     'ct.spools/agent-run
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "agent-run"}
     'ct.spools/harness-core
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "harness-core"}
     'ct.spools/claude-harness
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "claude-harness"}
     'ct.spools/codex-harness
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "codex-harness"}
     'ct.spools/pi-harness
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "pi-harness"}
     'ct.spools/delegation
     {:git/url "https://github.com/codethread/agent-harness.spool.git"
      :git/sha "fd75bf50ef823e1df520ead410780961d6313474"
      :deps/root "delegation"}
     'codethread/devflow
     {:git/url "https://github.com/codethread/devflow.spool.git"
      :git/sha "90799b8c950b4509167137562fbf18853524d41c"
      :deps/root "."}
     'codethread/devflow-kanban-adapter
     {:git/url "https://github.com/codethread/devflow.spool.git"
      :git/sha "90799b8c950b4509167137562fbf18853524d41c"
      :deps/root "kanban-adapter"}
     'millhouse.spools/workflow
     {:git/url "https://github.com/codethread/millhouse.spool.git"
      :git/sha "f487eb42ea9523e8bd405e64a7c319013217d988"
      :deps/root "spools/workflow"}
     'millhouse.spools/kanban
     {:git/url "https://github.com/codethread/millhouse.spool.git"
      :git/sha "f487eb42ea9523e8bd405e64a7c319013217d988"
      :deps/root "spools/kanban"}
     'millhouse.spools/identity
     {:git/url "https://github.com/codethread/millhouse.spool.git"
      :git/sha "f487eb42ea9523e8bd405e64a7c319013217d988"
      :deps/root "spools/identity"}}}))

(deftest workspace-deps-compose-library-roots-and-landed-pins
  (let [{:keys [deps]} (edn/read-string
                        (slurp (io/file project-root ".millstrand/deps.edn")))
        workspace-root (io/file project-root ".millstrand")
        config-root (io/file workspace-root (get-in deps ['codethread/config :local/root]))
        ralph-root (io/file workspace-root (get-in deps ['codethread/ralph :local/root]))]
    (is (= {:local/root "../spools/config"}
           (get deps 'codethread/config)))
    (is (= {:local/root "../spools/ralph"}
           (get deps 'codethread/ralph)))
    (is (.isFile (io/file config-root "deps.edn")))
    (is (.isFile (io/file ralph-root "deps.edn")))
    (is (.isFile (io/file ralph-root "bin/ralph")))
    (is (= "bbca5638bce72ad6a00b2ca916cabcfe99107828"
           (get-in deps ['millstrand.spools/batteries :git/sha])))
    (is (= "f487eb42ea9523e8bd405e64a7c319013217d988"
           (get-in deps ['millhouse.spools/workflow :git/sha])))
    (is (= "fd75bf50ef823e1df520ead410780961d6313474"
           (get-in deps ['ct.spools/agent-run :git/sha])))
    (is (= "90799b8c950b4509167137562fbf18853524d41c"
           (get-in deps ['codethread/devflow :git/sha])))))

(deftest config-activates-shared-elections
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :batteries {:ns 'millstrand.spools.batteries})
      (runtime/module! rt :identity {:ns 'millhouse.spools.identity})
      (runtime/module! rt :agent-run {:ns 'ct.spools.agent-run})
      (runtime/module! rt :delegation {:ns 'ct.spools.delegation :after [:agent-run]})
      (runtime/module! rt :harness-core {:ns 'ct.spools.harness-core
                                         :after [:identity]})
      (runtime/module! rt :claude-harness {:ns 'ct.spools.claude-harness
                                           :after [:harness-core]})
      (runtime/module! rt :codex-harness {:ns 'ct.spools.codex-harness
                                          :after [:harness-core]})
      (runtime/module! rt :pi-harness {:ns 'ct.spools.pi-harness
                                       :after [:harness-core]})
      (runtime/module! rt :workflow {:ns 'millhouse.spools.workflow})
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow :after [:workflow]})
      (runtime/module! rt :kanban {:ns 'millhouse.spools.kanban})
      (runtime/module! rt :adapter {:ns 'ct.spools.devflow-kanban-adapter
                                    :after [:devflow :kanban]})
      (runtime/module! rt :config-agents {:ns 'ct.spools.codethread.agents
                                          :after [:agent-run :claude-harness
                                                  :codex-harness :pi-harness]})
      (runtime/module! rt :config-help {:ns 'ct.spools.codethread.help
                                        :after [:batteries]})
      (runtime/module! rt :config-devflow {:ns 'ct.spools.codethread.devflow})
      (let [result (runtime/module! rt :config
                                    {:ns 'ct.spools.codethread.config
                                     :after [:config-agents :config-help :config-devflow
                                             :batteries :delegation :adapter]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :config :status])))
        (current/with-runtime rt
          (testing "agent seats are selected"
            (is (= :codex (:name (shuttle/resolve-harness :luna-high))))
            (is (= :codex-ro (:name (shuttle/resolve-harness :luna-low-ro)))))
          (testing "the Devflow Kanban route is selected"
            (is (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                   (workflow/workflow-definition :decompose)))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.config-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
