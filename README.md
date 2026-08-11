# Codethread shared workflow spools

Codethread publishes four producer roots. The producer namespaces use the shared-spool convention `ct.spools.*`; the `codethread/*` coordinates identify this family's independently approved roots.

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/agents` | `ct.spools.codethread.agents` | Harness declarations and model-seat aliases for delegation and reviews |
| `spools/devflow-setup` | `ct.spools.codethread.devflow-setup` | Consumer composition seed for the external Devflow Kanban adapter |
| `spools/ralph` | `ct.spools.codethread.ralph` | One-card-per-iteration Ralph workflow plus executable |

The Devflow setup root contains no Devflow implementation or guidance. Consumers approve and activate the external `codethread/devflow` and `codethread/devflow-kanban-adapter` roots, then activate this setup root after the adapter.

Each root has its own `deps.edn`, source tree, and focused test. The family manifest is advisory for tooling; consumers still record explicit approval in their own `.millstrand/spools.edn` and activate only the modules they choose.

## Activation

The following is the trusted consumer shape for a local checkout. The consumer owns the runtime, provider approvals, and module ordering. Local roots are resolved relative to `.millstrand`, so the family checkout is `..` and sibling provider checkouts use `../../<provider>.spool`.

```clojure
{:spools
 {millstrand.spools/batteries {:millstrand/source-root "spools/batteries"}
  codethread/spools
  {:local/root ".."
   :roots {codethread/agents "spools/agents"
           codethread/devflow-setup "spools/devflow-setup"
           codethread/ralph "spools/ralph"}}
  millhouse/spools
  {:local/root "../../millhouse.spool"
   :roots {millhouse.spools/workflow "spools/workflow"
           millhouse.spools/millstrand-workflows "spools/millstrand-workflows"
           millhouse.spools.executors/shell "spools/shell-executor"}}
  ct.spools/agent-run
  {:local/root "../../agent-harness.spool"
   :roots {ct.spools/agent-run "agent-run"
           ct.spools/harness-core "harness-core"
           ct.spools/claude-harness "claude-harness"
           ct.spools/codex-harness "codex-harness"
           ct.spools/pi-harness "pi-harness"
           ct.spools/agent-cli "agent-cli"
           ct.spools/delegation "delegation"}}
  codethread/devflow
  {:local/root "../../devflow.spool"
   :roots {codethread/devflow "."
           codethread/devflow-kanban-adapter "kanban-adapter"}}
  codethread/kanban {:local/root "../../kanban.spool"
                     :roots {codethread/kanban "."}}}}
```

```clojure
(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

(runtime/module! runtime :millstrand/spools-batteries
  {:ns 'millstrand.spools.batteries
   :spools ['millstrand.spools/batteries]
   :required? true})

(runtime/module! runtime :millhouse/spools-workflow
  {:ns 'millhouse.spools.workflow
   :spools ['millhouse.spools/workflow]
   :required? true})
(runtime/module! runtime :millhouse/spools-workflow-cli
  {:ns 'millhouse.spools.workflow.cli
   :spools ['millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! runtime :millhouse/spools-millstrand-workflows
  {:ns 'millhouse.spools.millstrand-workflows
   :spools ['millhouse.spools/millstrand-workflows
            'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! runtime :millhouse/spools-shell
  {:ns 'millhouse.spools.executors.shell
   :spools ['millhouse.spools.executors/shell 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})

(runtime/module! runtime :millstrand/spools-agent-run
  {:ns 'ct.spools.agent-run
   :spools ['ct.spools/agent-run]
   :required? true})
(runtime/module! runtime :millstrand/spools-delegation
  {:ns 'ct.spools.delegation
   :spools ['ct.spools/delegation 'ct.spools/agent-run]
   :after [:millstrand/spools-agent-run]
   :required? true})
(runtime/module! runtime :millstrand/spools-harness-core
  {:ns 'ct.spools.harness-core
   :spools ['ct.spools/harness-core]
   :after [:millstrand/spools-agent-run]
   :required? true})
(runtime/module! runtime :millstrand/spools-claude-harness
  {:ns 'ct.spools.claude-harness
   :spools ['ct.spools/claude-harness 'ct.spools/harness-core]
   :after [:millstrand/spools-harness-core]
   :required? true})
(runtime/module! runtime :millstrand/spools-codex-harness
  {:ns 'ct.spools.codex-harness
   :spools ['ct.spools/codex-harness 'ct.spools/harness-core]
   :after [:millstrand/spools-harness-core]
   :required? true})
(runtime/module! runtime :millstrand/spools-pi-harness
  {:ns 'ct.spools.pi-harness
   :spools ['ct.spools/pi-harness 'ct.spools/harness-core]
   :after [:millstrand/spools-harness-core]
   :required? true})
(runtime/module! runtime :millstrand/spools-agent-cli
  {:ns 'ct.spools.agent-cli
   :spools ['ct.spools/agent-cli 'ct.spools/harness-core]
   :after [:millstrand/spools-harness-core
           :millstrand/spools-claude-harness
           :millstrand/spools-codex-harness
           :millstrand/spools-pi-harness]
   :required? true})

(runtime/module! runtime :devflow
  {:ns 'ct.spools.devflow
   :spools ['codethread/devflow 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
(runtime/module! runtime :millstrand/spools-kanban
  {:ns 'ct.spools.kanban
   :spools ['codethread/kanban]
   :required? true})
(runtime/module! runtime :devflow/kanban-adapter
  {:ns 'ct.spools.devflow-kanban-adapter
   :spools ['codethread/devflow-kanban-adapter
            'codethread/devflow 'codethread/kanban
            'millhouse.spools/workflow]
   :after [:devflow :millstrand/spools-kanban
           :millhouse/spools-workflow]
   :required? true})

(runtime/module! runtime :codethread/agents
  {:ns 'ct.spools.codethread.agents
   :spools ['codethread/agents 'ct.spools/agent-run 'ct.spools/delegation]
   :after [:millstrand/spools-agent-run
           :millstrand/spools-delegation]
   :required? true})
(runtime/module! runtime :millstrand/spools-subagent
  {:ns 'ct.spools.executors.subagent
   :spools ['ct.spools/agent-run 'millhouse.spools/workflow]
   :after [:millstrand/spools-agent-run
           :millhouse/spools-workflow
           :codethread/agents
           :devflow
           :devflow/kanban-adapter]
   :required? true})
(runtime/module! runtime :codethread/devflow-setup
  {:ns 'ct.spools.codethread.devflow-setup
   :spools ['codethread/devflow-setup]
   :after [:devflow/kanban-adapter]
   :required? true})
(runtime/module! runtime :codethread/ralph
  {:ns 'ct.spools.codethread.ralph
   :spools ['codethread/ralph 'millhouse.spools/workflow]
   :after [:millhouse/spools-workflow]
   :required? true})
```

The external adapter owns the Kanban-bound `:decompose` workflow. The local setup root contributes only the lifecycle seed that calls `ct.spools.devflow-kanban-adapter/repoint-decompose-seed!`.

The subagent executor bridges Workflow `:subagent` gates to durable `agent-run` runs, so activate it after the shared agents and external Devflow adapter modules.

The agents root publishes harness and alias declarations, including `:luna-high` and read-only seats. The current agent-run default-contract API does not accept an explicit runtime, so this first release does not bind default worker or review contract text from shared code; a consumer may provide that runtime-aware policy separately.

Ralph validates and hands a committed slice to the consumer-owned landing policy. It does not own landing, roster review, or evidence for a landed card, and it must not mark a card or epic done without the consumer's landing evidence.

The Ralph spool publishes the `ralph` workflow plus executable. In a live Weaver, use `mill bin list` to confirm the declaration, `mill bin build ralph` to compile the local Go module, and `mill bin run ralph --help` to pass arguments to the tool. The wrapper requires `MILLSTRAND_WORKSPACE`, which `mill bin run` supplies for the selected Weaver.

## Quality

Run all root-local tests with `make quality`.

The quality target runs the Ralph Clojure test, the pinned gofumpt v0.8.0 format check, `go vet`, `go test ./...`, and a disposable Go build from `spools/ralph`.

Focused runs are `clojure -M:test` from each root directory. Focused tests use disposable embedded runtimes and explicitly supply the provider roots needed for their activation boundaries.
