# Agents

Central config and coordination spool for millstrand and its siblings:

`../skein-src`: millstrand (under previous name, repo needs renaming but its millstrand source code)
`../millhouse.spool`: experimental spools not ready for millstrand repo
`../agent-harness.spool`: agentic handlers like agent-run spool and review rosters
`../harnesses.spool`: supersedes agent-harness, the api is a breaking migration
`../devflow.spool`: custom spec driven workflow
`../millstrand-ui/`: not a spool, a web app for viewing kanban cards within the millstrand ecosystem

## Millstrand / strand

This repo uses Millstrand strands to track work. Start with `strand --help`. Run `mill prime millstrand` when building on this repo's `.millstrand/` config, or working with millstrand spools, weaver or REPL.

Target other repos with direct `--workspace` flag:

```bash
strand --workspace ~/dev/projects/harnesses.spool/.millstrand help
```

## Working here

- Always track work through a kanban card, in a worktree — `strand prime kanban`.

## Rules

- **Never restart a running weaver** without explicit user sign-off
- **Kill by PID only** — never `pkill -f <pattern>` (prompts can quote the pattern and strafe siblings).
- **Disposable workspaces for workspace-backed tests** (weaver-world fixtures, smoke config) — never the shared `.millstrand` world. Use `--workspace` from `mktemp -d`; guard with `${ws:?}`.
