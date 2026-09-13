# Agents

Central config and coordination spool for millstrand and its siblings:

`../skein-src`: millstrand (under previous name, repo needs renaming but its millstrand source code)
`../millhouse.spool`: experimental spools not ready for millstrand repo
`../agent-harness.spool`: entirely deprecated and read-only; direct all updates and usage to `../harnesses.spool`
`../harnesses.spool`: replacement for agent-harness; the api is a breaking migration
`../devflow.spool`: custom spec driven workflow
`../millstrand-ui/`: not a spool, a web app for viewing kanban cards within the millstrand ecosystem

## Millstrand / strand

This repo uses Millstrand strands to track work. Start with `strand --help`. Run `mill prime millstrand` when building on this repo's `.millstrand/` config, or working with millstrand spools, weaver or REPL.

Target other repos with direct `--workspace` flag:

```bash
strand --workspace ~/dev/projects/harnesses.spool/.millstrand help
```

## Working here

- Run `strand prime kanban`, claim a feature card, and use its recorded worktree.
- Never edit `main` or push directly to `main`; feature-branch pushes are expected.
- Inspect `strand workflow show land` and `strand prime merge-queue`, then drive
  shared `land` for quality, one basic review, FIFO merge, card completion, and
  branch/worktree cleanup.

## Rules

- Never stop the mill; only the user may stop it.
- **Never launch work in or use `../agent-harness.spool`** — direct all work and usage to `../harnesses.spool`.
- **Never restart a running weaver** without explicit user sign-off
- **Kill by PID only** — never `pkill -f <pattern>` (prompts can quote the pattern and strafe siblings).
- **Disposable workspaces for workspace-backed tests** (weaver-world fixtures, smoke config) — never the shared `.millstrand` world. Use `--workspace` from `mktemp -d`; guard with `${ws:?}`.
