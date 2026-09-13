# Codethread shared workflow spools

This repository publishes two independently activatable Millstrand roots. The
`codethread/*` coordinates identify roots; their producer namespaces follow the
shared-spool `ct.spools.*` convention.

## Roots

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/config` | `ct.spools.codethread.bootstrap` | Register Harnesses, shared aliases and reviewers, and the Workflow `:agent` executor |
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

Activate the shared agent surface with one bootstrap call:

```clojure
(require '[ct.spools.codethread.bootstrap :as codethread])
(codethread/register! runtime)
```

The bootstrap owns ordering for the Harnesses providers and command surface,
the shared aliases, shared reviewer lenses, and the asynchronous Workflow
`:agent` executor. It registers shared policy before opening the executor, so
an initial scan of ready gates can resolve their seats. Repository-specific
workflows are not activated by the bootstrap.

The preferred role aliases are `luna`, `oracle`, `grunt`, `reviewer`, and
`coordinator`; useful effort-specific handles such as `luna-low`, `terra-med`,
and `sol-high` remain available. Claude and Cursor are registered but disabled
by default, matching the Harnesses workspace policy. A consumer can explicitly
enable them after startup with `strand agent config set harness/claude true` or
the equivalent Cursor flag. This is process-local configuration.

Inspect the resulting surface with:

```text
strand agent list
strand agent reviewers
```

The optional `ct.spools.codethread.config` module still selects this
repository's Batteries help rendering and the external Devflow Kanban adapter.
Consumers that want it must activate Batteries, Devflow, Kanban, and the
adapter first. This election stays outside the bootstrap so a catalog consumer
does not inherit Devflow workflows merely by selecting shared agents.

Headless Workflow gates use waiter `:agent` with `harness/alias` and an
optional `harness/prompt` and `harness/cwd`. The executor creates a tracked run
and closes the gate only after it delivers a successful non-blank result.
Waiting is done through `strand await` queries, not an `agent await` command.

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

`make lint` refreshes each project's dependency-provided clj-kondo imports and
lints that project with its own classpath. The imports and tool caches are
generated and ignored. Run only the refresh step with:

```text
make kondo-configs
```

Focused Clojure checks:

```text
cd spools/config && clojure -M:test
cd spools/ralph && clojure -M:test
```

The aggregate gate also runs pinned gofumpt v0.8.0, `go vet`, Go tests, and a
disposable Ralph build.
