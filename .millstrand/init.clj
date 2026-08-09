(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

;; batteries load by default, see
;; https://codethread.github.io/millstrand/spools/batteries/ for details
;; adds common commands like `strand add` `strand list` etc
;; you can omit this `module!` and build entirely your own way, see
;; https://codethread.github.io/millstrand/docs/spools/customisation/
(runtime/module! runtime :millstrand/spools-batteries
                 {:ns 'millstrand.spools.batteries
                  :spools ['millstrand.spools/batteries]})

(runtime/module! runtime :module-me-help
                 {:file "me/help.clj"
                  :spools ['millstrand.spools/batteries]
                  :after [:millstrand/spools-batteries]})

(runtime/module! runtime :millhouse/spools-workflow
                 {:ns 'millhouse.spools.workflow
                  :spools ['millhouse.spools/workflow]
                  :required? true})

(runtime/module! runtime :ct/spools-agent-run
                 {:ns 'ct.spools.agent-run
                  :spools ['ct.spools/agent-run]
                  :required? true})
(runtime/module! runtime :ct/spools-delegation
                 {:ns 'ct.spools.delegation
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:ct/spools-agent-run]
                  :required? true})

(runtime/module! runtime :codethread/agents
                 {:ns 'codethread.spools.agents
                  :spools ['codethread/agents 'ct.spools/agent-run
                           'ct.spools/delegation]
                  :after [:ct/spools-agent-run :ct/spools-delegation]
                  :required? true})
(runtime/module! runtime :codethread/spool-bump
                 {:ns 'codethread.spools.spool-bump
                  :spools ['codethread/spool-bump 'millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})
(runtime/module! runtime :codethread/devflow
                 {:ns 'codethread.spools.devflow
                  :spools ['codethread/devflow 'millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})
(runtime/module! runtime :codethread/devflow-kanban
                 {:ns 'codethread.spools.devflow-kanban-adapter
                  :spools ['codethread/devflow 'codethread/kanban
                           'millhouse.spools/workflow]
                  :after [:codethread/devflow :millhouse/spools-workflow]
                  :required? true})
(runtime/module! runtime :codethread/ralph
                 {:ns 'codethread.spools.ralph
                  :spools ['codethread/ralph 'millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})
