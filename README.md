# Codethread shared workflow spools

Codethread publishes four small producer roots. The producer namespaces use the shared-spool convention `ct.spools.*`; the `codethread/*` coordinates identify this family's independently approved roots.

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/agents` | `ct.spools.codethread.agents` | Harness declarations and model-seat aliases for delegation and reviews |
| `spools/spool-bump` | `ct.spools.codethread.spool-bump` | Third-party spool bump workflow |
| `spools/devflow` | `ct.spools.codethread.devflow-setup` | Consumer composition seed for the external Devflow Kanban adapter |
| `spools/ralph` | `ct.spools.codethread.ralph` | One-card-per-iteration Ralph workflow |

The Devflow setup root contains no Devflow implementation or guidance. Consumers approve and activate the external `codethread/devflow` and `codethread/devflow-kanban-adapter` roots, then activate this setup root after the adapter.

Each root has its own `deps.edn`, source tree, and focused test. The family manifest is advisory for tooling; consumers still record explicit approval in their own `.millstrand/spools.edn` and activate only the modules they choose.

## Activation

The following is the trusted consumer shape for a local checkout. The consumer owns the runtime, provider approvals, and module ordering. Local roots are resolved relative to `.millstrand`, so the family checkout is `..` and sibling provider checkouts use `../../<provider>.spool`.

```clojure
{:spools
 {codethread/spools
  {:local/root ".."
   :roots {codethread/agents "spools/agents"
           codethread/spool-bump "spools/spool-bump"
           codethread/devflow-setup "spools/devflow"
           codethread/ralph "spools/ralph"}}
  codethread/devflow
  {:local/root "../../devflow.spool"
   :roots {codethread/devflow "."
           codethread/devflow-kanban-adapter "kanban-adapter"}}
  codethread/kanban
  {:local/root "../../kanban.spool"
   :roots {codethread/kanban "."}}}}
```

```clojure
(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def rt (current/runtime))

(runtime/module! rt :millhouse/spools-workflow
  {:ns 'millhouse.spools.workflow
   :spools ['millhouse.spools/workflow]
   :required? true})
(runtime/module! rt :millhouse/spools-workflow-cli
  {:ns 'millhouse.spools.workflow.cli
   :spools ['millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! rt :millhouse/spools-shell
  {:ns 'millhouse.spools.executors.shell
   :spools ['millhouse.spools.executors/shell 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! rt :ct/spools-agent-run
  {:ns 'ct.spools.agent-run
   :spools ['ct.spools/agent-run]
   :required? true})
(runtime/module! rt :ct/spools-delegation
  {:ns 'ct.spools.delegation
   :spools ['ct.spools/delegation 'ct.spools/agent-run]
   :after [:ct/spools-agent-run]
   :required? true})
(runtime/module! rt :ct/spools-harness-core
  {:ns 'ct.spools.harness-core
   :spools ['ct.spools/harness-core]
   :after [:ct/spools-agent-run]
   :required? true})
(runtime/module! rt :ct/spools-codex-harness
  {:ns 'ct.spools.codex-harness
   :spools ['ct.spools/codex-harness 'ct.spools/harness-core]
   :after [:ct/spools-harness-core]
   :required? true})
(runtime/module! rt :ct/spools-agent-cli
  {:ns 'ct.spools.agent-cli
   :spools ['ct.spools/agent-cli 'ct.spools/harness-core]
   :after [:ct/spools-harness-core :ct/spools-codex-harness]
   :required? true})
(runtime/module! rt :devflow
  {:ns 'ct.spools.devflow
   :spools ['codethread/devflow 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! rt :kanban
  {:ns 'ct.spools.kanban
   :spools ['codethread/kanban]
   :required? true})
(runtime/module! rt :devflow/kanban-adapter
  {:ns 'ct.spools.devflow-kanban-adapter
   :spools ['codethread/devflow-kanban-adapter 'codethread/devflow 'codethread/kanban 'millhouse.spools/workflow]
   :after [:devflow :kanban :millhouse/spools-workflow]
   :required? true})
(runtime/module! rt :codethread/agents
  {:ns 'ct.spools.codethread.agents
   :spools ['codethread/agents 'ct.spools/agent-run 'ct.spools/delegation]
   :after [:ct/spools-agent-run :ct/spools-delegation]
   :required? true})
(runtime/module! rt :codethread/spool-bump
  {:ns 'ct.spools.codethread.spool-bump
   :spools ['codethread/spool-bump 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! rt :codethread/devflow-setup
  {:ns 'ct.spools.codethread.devflow-setup
   :spools ['codethread/devflow-setup]
   :after [:devflow/kanban-adapter]
   :required? true})
(runtime/module! rt :codethread/ralph
  {:ns 'ct.spools.codethread.ralph
   :spools ['codethread/ralph 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
```

The external adapter owns the Kanban-bound `:decompose` workflow. The local setup root contributes only the lifecycle seed that calls `ct.spools.devflow-kanban-adapter/repoint-decompose-seed!`.

The agents root publishes harness and alias declarations, including `:luna-high` and read-only seats. The current agent-run default-contract API does not accept an explicit runtime, so this first release does not bind default worker or review contract text from shared code; a consumer may provide that runtime-aware policy separately.

Ralph validates and hands a committed slice to the consumer-owned landing policy. It does not own landing, roster review, or evidence for a landed card, and it must not mark a card or epic done without the consumer's landing evidence.

## Quality

Run all root-local tests with `make quality`.

Focused runs are `clojure -M:test` from each root directory. Focused tests use disposable embedded runtimes and explicitly supply the provider roots needed for their activation boundaries.
