# Codethread shared workflow spools

This repository publishes two independently activatable Millstrand roots. The
`codethread/*` coordinates identify roots; their producer namespaces follow the
shared-spool `ct.spools.*` convention.

## Roots

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/config` | `ct.spools.codethread.config` | Select shared harness aliases, Batteries help rendering, and the external Devflow Kanban adapter |
| `spools/ralph` | `ct.spools.codethread.ralph` | Publish the one-card-per-iteration `ralph-iterate` workflow and `ralph` executable |

Each consumer composes the roots it needs in `.millstrand/deps.edn` and activates its selected modules in `.millstrand/init.clj`. Kanban comes from `millhouse.spools/kanban`.

## Activation

For a checkout containing this repository, compose the local roots with:

```clojure
{:deps {codethread/config {:local/root "../spools/config"}
        codethread/ralph {:local/root "../spools/ralph"}}}
```

The roots are relative to `.millstrand`. Git consumers should use pinned `codethread/config` and `codethread/ralph` dependencies instead.

Consumers own module ordering:

- Activate Batteries, agent-run/delegation, Millhouse Workflow, Devflow, Kanban,
  and the Devflow Kanban adapter before `codethread/config`.
- Activate `codethread/ralph` after Millhouse Workflow.

The config root contains no Devflow implementation. It selects the external
adapter's Kanban-bound `:decompose` workflow alongside shared harness aliases
and Batteries help rendering. Inspect the active aliases with
`strand agent harnesses`.

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
