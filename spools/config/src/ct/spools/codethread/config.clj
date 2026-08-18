(ns ct.spools.codethread.config
  "Codethread shared workspace config.

  Sibling namespaces hold capability data; this module declares and selects the
  capabilities consumers get when they activate `codethread/config`."
  (:require [ct.spools.agent-run :as shuttle]
            [ct.spools.codethread.agents :as agents]
            [ct.spools.codethread.agents-next :as agents-next]
            [ct.spools.codethread.devflow :as devflow]
            [ct.spools.codethread.help :as help]
            [millstrand.api.lifecycle.alpha :as lifecycle]))

(shuttle/defharnesses harness-defs
  "Codethread-shared harness tools."
  agents/harness-options)
(shuttle/defaliases alias-defs
  "Codethread-shared seats."
  agents/alias-options)
(lifecycle/defresource batteries-help-transform
  "Own this world's Batteries help-transform election for the module lifetime."
  help/batteries-help-transform-options)
(lifecycle/defseed devflow-kanban-adapter-binding
  "Route the external Devflow decompose stage through its Kanban adapter."
  devflow/devflow-kanban-adapter-binding-options)

(shuttle/use-harnesses! harness-defs)
(shuttle/use-aliases! alias-defs)
(lifecycle/use-resource! agents-next/harness-next-runtime)
(lifecycle/use-resource! batteries-help-transform)
(lifecycle/use-seed! devflow-kanban-adapter-binding)
