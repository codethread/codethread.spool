(ns ct.spools.codethread.devflow
  "Workspace composition for the external Devflow and Kanban adapter roots.

  This root owns no Devflow definitions or guidance. It only declares the
  consumer election that routes the external adapter's Kanban-bound decompose
  workflow through the external lifecycle seed. Consumers still approve and
  activate `codethread/devflow` and `codethread/devflow-kanban-adapter`."
)

(def devflow-kanban-adapter-binding-options
  "Route the external Devflow decompose stage through its Kanban adapter."
  {:apply 'ct.spools.devflow-kanban-adapter/repoint-decompose-seed!})
