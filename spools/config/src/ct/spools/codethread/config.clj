(ns ct.spools.codethread.config
  "Codethread shared workspace config.

  Sibling namespaces expose inert capabilities; this module selects the ones
  consumers get when they activate `codethread/config`."
  (:require [ct.spools.agent-run :as shuttle]
            [ct.spools.codethread.agents :as agents]
            [ct.spools.codethread.devflow :as devflow]
            [ct.spools.codethread.help :as help]
            [millstrand.api.lifecycle.alpha :as lifecycle]))

(shuttle/use-harnesses! agents/harness-defs)
(shuttle/use-aliases! agents/alias-defs)
(lifecycle/use-resource! help/batteries-help-transform)
(lifecycle/use-seed! devflow/devflow-kanban-adapter-binding)
