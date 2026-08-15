# Agents

Central config and coordination spool for siblings:

`../skein-src`: millstrand (under previous name, repo needs renaming but its millstrand source code)
`../millhouse.spool`: experimental spools not ready for millstrand repo
`../agent-harness.spool`: agentic handlers like agent-run spool and review rosters
`../devflow.spool`: custom spec driven workflow

<!-- mill:millstrand-prime -->

## Millstrand / strand

This repo uses Millstrand strands to track work. Orientation ships in the `mill` CLI:

- `mill prime strand` — the day-to-day strand workflow; run it before multi-step work.
- `mill prime millstrand` — read on demand, only when building on this repo's `.millstrand/` config or spools.
<!-- /mill:millstrand-prime -->
