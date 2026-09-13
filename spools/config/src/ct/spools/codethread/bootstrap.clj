(ns ct.spools.codethread.bootstrap
  "Register the shared Codethread Harnesses module stack.

  Consumers call `register!` once from their workspace `init.clj`. The
  bootstrap owns provider, command, query, alias, reviewer, and workflow-agent
  executor ordering. Repository-specific workflows remain consumer-owned."
  (:require [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.spool.alpha :refer [fail!]]))

(def module-definitions
  "Ordered modules that implement the shared Harnesses catalog.

  Alias and reviewer policy must reconcile before the workflow executor opens:
  its initial scan may immediately encounter ready `:agent` gates."
  [[:millhouse/spools-identity
    {:ns 'millhouse.spools.identity
     :required? true}]
   [:millhouse/spools-workflow
    {:ns 'millhouse.spools.workflow
     :required? true}]
   [:millstrand/spools-harnesses
    {:ns 'ct.spools.harnesses.spool
     :after [:millhouse/spools-identity]
     :required? true}]
   [:codethread/config-agents
    {:ns 'ct.spools.codethread.agents
     :after [:millstrand/spools-harnesses]
     :required? true}]
   [:codethread/config-reviewers
    {:ns 'ct.spools.codethread.reviewers
     :after [:codethread/config-agents]
     :required? true}]
   [:millstrand/spools-agent-executor
    {:ns 'ct.spools.harnesses.executors.agent.spool
     :after [:millhouse/spools-workflow
             :millstrand/spools-harnesses
             :codethread/config-agents
             :codethread/config-reviewers]
     :required? true}]])

(defn register!
  "Register the complete shared Harnesses catalog in `runtime`.

  Return the ordered module ids after every registration succeeds. Repeated
  calls are safe because Millstrand module registration is idempotent for an
  unchanged descriptor."
  [runtime]
  (doseq [[module-id options] module-definitions]
    (let [result (runtime/module! runtime module-id options)
          status (get-in result [:modules module-id :status])]
      (when-not (contains? #{:applied :unchanged} status)
        (fail! "Shared Harnesses module registration failed"
               {:module module-id :status status :result result}))))
  {:registered (mapv first module-definitions)})
