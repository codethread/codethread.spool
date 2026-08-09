# Codethread shared workflow spools

Codethread is the shared producer for the personal Millstrand workflow base. It
publishes one family with four independently approved roots:

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/agents` | `codethread.spools.agents` | Harness tools/seats and default worker/review contracts |
| `spools/spool-bump` | `codethread.spools.spool-bump` | Third-party spool bump workflow |
| `spools/devflow` | `codethread.spools.devflow` | Devflow lifecycle, guidance, and Kanban-bound decompose default |
| `spools/ralph` | `codethread.spools.ralph` | One-card-per-iteration Ralph workflow |

Each root has its own `deps.edn`, source tree, and local tests. The family
manifest is advisory for tooling; consumers still record explicit approval in
their own `.millstrand/spools.edn` and activate only the modules they choose.

## Activation

The following is the trusted consumer shape for a local checkout. The consumer
owns the runtime, provider approvals, and module ordering:

```clojure
;; .millstrand/spools.edn — one explicit family approval
{:spools
 {codethread/spools
  {:local/root "../codethread.spool"
   :roots {codethread/agents "spools/agents"
           codethread/spool-bump "spools/spool-bump"
           codethread/devflow "spools/devflow"
           codethread/ralph "spools/ralph"}}}}
```

```clojure
(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def rt (current/runtime))

(runtime/module! rt :codethread/agents
  {:ns 'codethread.spools.agents
   :spools ['codethread/agents 'ct.spools/agent-run 'ct.spools/delegation]
   :required? true})
(runtime/module! rt :codethread/spool-bump
  {:ns 'codethread.spools.spool-bump
   :spools ['codethread/spool-bump 'millhouse.spools/workflow]
   :required? true})
(runtime/module! rt :codethread/devflow
  {:ns 'codethread.spools.devflow
   :spools ['codethread/devflow 'millhouse.spools/workflow]
   :required? true})
(runtime/module! rt :codethread/devflow-kanban
  {:ns 'codethread.spools.devflow-kanban-adapter
   :spools ['codethread/devflow 'codethread/kanban 'millhouse.spools/workflow]
   :after [:codethread/devflow]
   :required? true})
(runtime/module! rt :codethread/ralph
  {:ns 'codethread.spools.ralph
   :spools ['codethread/ralph 'millhouse.spools/workflow]
   :required? true})
```

The Kanban module carries an idempotent lifecycle seed that re-points the
generic `:decompose` workflow name to the Kanban-bound definition. It is
deliberately a separate activation so a consumer can review that provider
boundary. The producer does not publish reviewer rosters, landing policy, or
other workspace policy.

## Quality

Run all root-local tests with:

```sh
make quality
```

Focused runs are `clojure -M:test` from each root directory. The tests use
embedded disposable runtimes for activation/composition checks and do not
contact live providers.
