(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime]
         '[ct.spools.codethread.bootstrap :as codethread])

(def runtime (current/runtime))

(runtime/module! runtime :millstrand/spools-batteries
                 {:ns 'millstrand.spools.batteries
                  :required? true})
(codethread/register! runtime)

(runtime/module! runtime :millhouse/spools-workflow-providers
                 {:ns 'millhouse.spools.workflow.spool
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :devflow
                 {:ns 'ct.spools.devflow
                  :after [:millhouse/spools-workflow]
                  :required? true})
(runtime/module! runtime :devflow/kanban-adapter
                 {:ns 'ct.spools.devflow-kanban-adapter
                  :after [:devflow
                          :millhouse/spools-kanban
                          :millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :codethread/config-help
                 {:ns 'ct.spools.codethread.help
                  :after [:millstrand/spools-batteries]
                  :required? true})
(runtime/module! runtime :codethread/config-devflow
                 {:ns 'ct.spools.codethread.devflow
                  :required? true})
(runtime/module! runtime :codethread/config
                 {:ns 'ct.spools.codethread.config
                  :after [:codethread/config-help
                          :codethread/config-devflow
                          :millstrand/spools-batteries
                          :devflow/kanban-adapter]
                  :required? true})
(runtime/module! runtime :codethread/ralph
                 {:ns 'ct.spools.codethread.ralph
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :codethread/auto-run-workflows
                 {:file "me/auto_run_workflows.clj"
                  :after [:millhouse/spools-workflow-providers]
                  :required? true})
(runtime/module! runtime :codethread/auto-run
                 {:file "me/auto_run.clj"
                  :after [:codethread/auto-run-workflows
                          :millstrand/spools-harnesses]
                  :required? true})

(codethread/register-executor!
 runtime [:millhouse/spools-workflow-providers
          :devflow/kanban-adapter
          :codethread/config
          :codethread/ralph
          :codethread/auto-run])
