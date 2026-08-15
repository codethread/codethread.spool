# Codethread shared workflow spools

This repository publishes two independently activatable Millstrand roots. The
`codethread/*` coordinates identify roots; their producer namespaces follow the
shared-spool `ct.spools.*` convention.

## Roots

| Root | Namespace | Purpose |
| --- | --- | --- |
| `spools/config` | `ct.spools.codethread.config` | Select shared harness aliases, Batteries help rendering, and the external Devflow Kanban adapter |
| `spools/ralph` | `ct.spools.codethread.ralph` | Publish the one-card-per-iteration `ralph-iterate` workflow and `ralph` executable |

`spool.edn` is advisory family metadata. Consumers explicitly approve roots in
their own `.millstrand/spools.edn` and activate the modules they need. The
family requires agent-run v27 and Kanban v24 or newer.

## Activation

For a checkout containing this repository, the family approval is:

```clojure
codethread/spools
{:local/root ".."
 :roots {codethread/config "spools/config"
         codethread/ralph "spools/ralph"}}
```

The path is relative to `.millstrand`. Git consumers should use a pinned family
coordinate instead.

Consumers own provider approval and module ordering:

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

## Quality

Run the complete gate:

```text
make quality
```

Focused Clojure checks:

```text
cd spools/config && clojure -M:test
cd spools/ralph && clojure -M:test
```

The aggregate gate also runs pinned gofumpt v0.8.0, `go vet`, Go tests, and a
disposable Ralph build.
