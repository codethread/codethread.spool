(ns ct.spools.codethread.config
  "Codethread shared workspace config.

  Sibling namespaces expose inert capabilities; this module selects the ones
  consumers get when they activate `codethread/config`."
  (:require [ct.spools.codethread.help :as help]
            [millstrand.api.lifecycle.alpha :as lifecycle]))

(lifecycle/use-resource! help/batteries-help-transform)
