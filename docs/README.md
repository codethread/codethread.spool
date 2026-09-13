# Shared ecosystem documentation

Codethread is the coordination home for processes shared by Millstrand and its
sibling repositories. Keep the procedure here; each repository implements it
for its own layout and links back to this documentation.

## Processes

- [Clojure lint and editor configuration](processes/kondo-and-lsp.md): macro
  exports, dependency imports, Make commands, and clojure-lsp verification.
- [Shared review and landing](processes/shared-landing.md): Kanban/worktree
  discipline, review evidence, FIFO landing, consumer activation, and rollout
  verification.
- [Sub-coordinator alias rollout](processes/sub-coordinator-rollout.md): bounded
  role guidance, additive live registration, frozen-setting proof, pilot
  criteria, and the supported model fallback.

Processes describe the maintained contract, the decisions a repository needs
to make, and how to verify the result. Add a process here when several
repositories need the same practice. Local READMEs should record their chosen
roots, aliases, commands, and exceptions without duplicating the procedure.

## Reports

`reports/` holds dated investigations and rollout evidence. Reports explain
specific decisions and results; they are not the source of current procedures.

- [Kondo resource investigation](reports/kondo-investigation/recommendation.md)
- [Headless editor verification and discovered pitfalls](reports/kondo-rollout/editor-verification.md)
- [Active-repository Kondo rollout results](reports/kondo-rollout/results.md)
