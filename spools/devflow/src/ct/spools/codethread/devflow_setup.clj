(ns ct.spools.codethread.devflow-setup
  "Workspace composition for the external Devflow and Kanban adapter roots.

  This root owns no Devflow definitions or guidance. It only declares the
  consumer election that routes the external adapter's Kanban-bound decompose
  workflow through the external lifecycle seed. Consumers still approve and
  activate `codethread/devflow` and `codethread/devflow-kanban-adapter`."
  (:require [millstrand.api.lifecycle.alpha :as lifecycle]))

(lifecycle/defseed devflow-kanban-adapter-binding
  "Route the external Devflow decompose stage through its Kanban adapter."
  {:apply 'ct.spools.devflow-kanban-adapter/repoint-decompose-seed!})
