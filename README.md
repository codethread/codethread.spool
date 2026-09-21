# Codethread shared workflow spools

This repository publishes two independently activatable Millstrand roots. The
`codethread/*` coordinates identify roots; their producer namespaces follow the
shared-spool `ct.spools.*` convention.

## Shared processes

This repository also maintains [shared ecosystem documentation](docs/README.md).
Sibling repositories should implement and link to these procedures according to
their own layout:

- [Clojure lint and editor configuration](docs/processes/kondo-and-lsp.md)
- [Shared review and landing](docs/processes/shared-landing.md)
- [Repository automatic delivery](docs/processes/repository-auto-run.md)

## Roots

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/config` | `ct.spools.codethread.bootstrap` | Register Harnesses, shared aliases and reviewers, shared landing, then activate the Workflow `:agent` executor |
| `spools/ralph` | `ct.spools.codethread.ralph` | Publish the one-card-per-iteration `ralph-iterate` workflow and `ralph` executable |

Each consumer composes the roots it needs in `.millstrand/deps.edn` and
activates its selected modules in `.millstrand/init.clj`. Kanban comes from
`millhouse.spools/kanban`.

## Activation

For a checkout containing this repository, compose the local roots with:

```clojure
{:deps {codethread/config {:local/root "../spools/config"}
        codethread/ralph {:local/root "../spools/ralph"}}}
```

The roots are relative to `.millstrand`. Git consumers should use pinned
`codethread/config` and `codethread/ralph` dependencies instead. The config root
pins `ct.spools/harnesses` directly; it has no dependency on the superseded
`agent-harness.spool` roots.

Register the shared agent and landing surface before consumer-specific
configuration:

```clojure
(require '[ct.spools.codethread.bootstrap :as codethread])
(codethread/register! runtime)
```

`register!` owns ordering for the Harnesses providers and command surface, the
shared aliases and reviewer lenses, and the shared Millhouse landing workflow.
It deliberately does not activate the asynchronous Workflow `:agent` executor.
Register repository-specific aliases, flags, and workflows next, then activate
the executor last:

```clojure
(codethread/register-executor! runtime [:consumer/aliases
                                        :consumer/workflows])
```

The optional second argument adds explicit `:after` edges for consumer modules
that must reconcile before the executor's initial scan of restored ready
gates. `(codethread/register-executor! runtime)` is sufficient when there are
no consumer modules to name. The stable executor module id is
`:millstrand/spools-agent-executor`; the bootstrap owns that module, so
consumers must not register its namespace separately.

The stable catalog module ids, in order, are
`:millhouse/spools-identity`, `:millhouse/spools-workflow`,
`:millhouse/spools-kanban`, `:millstrand/spools-harnesses`,
`:codethread/config-agents`, `:codethread/config-reviewers`, and
`:millhouse/spools-land`. Kanban activates before Harnesses because assignment
prompts consume Kanban's current-ownership projections.
Repository-specific workflows are not activated by the catalog bootstrap.

Consumers that need landing without the Codethread agent catalog can depend on
the independent `millhouse.spools/land` root and register
`millhouse.spools.land.spool` after Millhouse Workflow and Kanban. The root
does not depend on Harnesses provider code; its reviewer seat is ordinary
workflow data, and the consumer supplies the `:agent` executor.

The preferred role aliases are `grunt`, `luna`, `oracle`, `reviewer`, and
`tui`. `grunt` prefers the `deepseek` (`deepseek-v4-flash`) seat and falls
back to `luna`; the `seat/allow-china` flag defaults true and makes every
DeepSeek-powered seat unavailable when set false. The delegated-coordination
aliases `coordinator`, `sub-coordinator`, and `sub-coordinator-sol` are not
elected by the catalog while they remain under test. Register the two
sub-coordinator aliases on demand through the additive seams in
`ct.spools.codethread.sub-coordinator`. The bounded `sub-coordinator`
carries its complete runbook as supported alias system guidance. See the
[rollout procedure](docs/processes/sub-coordinator-rollout.md) for its Codex
handoff, Terra/high fallback, and additive live-registration proof without
runtime mutation.

Claude and Cursor are registered but disabled by default, matching the
Harnesses workspace policy. A consumer can explicitly enable them after startup
with `strand agent config set harness/claude true` or the equivalent Cursor
flag. This is process-local configuration.

Inspect the resulting surface with:

```text
strand agent list
strand agent reviewers
strand workflow list
strand workflow show land
strand help merge-queue
```

The rollout smoke evaluates actual consumer `.millstrand/deps.edn`,
`.millstrand/init.clj`, and referenced workspace files in disposable in-memory
worlds. It applies local dependency overrides only inside those worlds and
checks module activation, the queue command, mandatory basic review, and all
three landing definitions:

```text
cd spools/config
clojure -M:consumer-smoke MILLHOUSE CODETHREAD HARNESSES DEVFLOW CONSUMER...
```

The first four paths identify producer checkouts. Pass each consumer checkout
as a remaining argument. The smoke never starts or mutates a canonical Weaver.

The optional `ct.spools.codethread.config` module still selects this
repository's Batteries help rendering and the external Devflow Kanban adapter.
Consumers that want it must activate Batteries, Devflow, and the adapter first;
Kanban is already part of the bootstrap. This election stays outside the
bootstrap so a catalog consumer
does not inherit Devflow workflows merely by selecting shared agents.

Headless Workflow gates use waiter `:agent` with `harness/alias` and an
optional `harness/prompt` and `harness/cwd`. The executor creates a tracked run
and closes the gate only after it delivers a successful non-blank result.
Waiting is done through `strand await` queries, not an `agent await` command.

Opt-in [automatic card pickup](docs/processes/auto-run.md) is also available
from the config root. Repositories supply delivery workflows, worker defaults,
worktree preparation, and a concurrency limit; the dispatcher assigns labelled,
ready features once without a coordinator agent. It is not activated by the
shared bootstrap.

Activate `codethread/ralph` separately after Millhouse Workflow.

Ralph validates and hands a committed slice to consumer-owned landing policy.
It does not own landing or landing evidence. See
[`spools/ralph/README.md`](spools/ralph/README.md) for implementation and UI
details.

Build and run Ralph through the Weaver:

```text
mill bin build ralph
mill bin run ralph --help
```

`mill bin run` supplies `MILLSTRAND_WORKSPACE`.

## Development roots

Treat each Clojure root as an independent project. Editor tooling should select
the nearest `deps.edn`:

- `.millstrand/deps.edn` owns workspace configuration.
- `spools/config/deps.edn` owns the shared config spool.
- `spools/ralph/deps.edn` owns the Ralph spool.

The repository root has no aggregate Clojure classpath. Its `Makefile`
orchestrates project-local checks without merging their dependency graphs.

## Quality

Install `clj-kondo` v2026.08.04, then run the complete gate:

```text
make quality
```

`make kondo` refreshes each project's dependency-provided clj-kondo imports,
then lints that project with its own classpath. `make kondo-lint` uses existing
imports only; `make kondo-import` runs only the refresh step. The imports and
tool caches are generated and ignored.

Each package root owns the same targets, so focused checks do not accidentally
merge classpaths:

```text
make -C .millstrand kondo
make -C spools/config kondo
make -C spools/ralph kondo
```

Focused Clojure checks:

```text
cd spools/config && clojure -M:test
cd spools/ralph && clojure -M:test
```

The aggregate gate also runs pinned gofumpt v0.8.0, `go vet`, Go tests, and a
disposable Ralph build.
