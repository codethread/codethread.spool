# Agents

Central config and coordination spool for siblings:

`../skein-src`: millstrand (under previous name, repo needs renaming but its millstrand source code)
`../millhouse.spool`: experimental spools not ready for millstrand repo
`../agent-harness.spool`: agentic handlers like agent-run spool and review rosters
`../devflow.spool`: custom spec driven workflow

<!-- mill:millstrand-prime -->

## Millstrand / strand

This repo uses Millstrand strands to track work. Orientation ships in the `mill` CLI:

Start with `strand --help`. Run `mill prime millstrand` on demand when building on this repo's `.millstrand` config or spools.
<!-- /mill:millstrand-prime -->
