# Shared review and landing

Every ecosystem repository lands through the reusable
`millhouse.spools/land` root. The root owns the `review`, `land`, `land-merge`,
and `land-abort` workflows and the `merge-queue` operation. Repositories own
their quality command and any optional post-land cleanup.

## Working contract

1. Claim a Kanban feature card with its branch and worktree. Never make feature
   changes on `main`.
2. Iterate with the repository's quality command. The executable
   `.millstrand/land-quality.sh` is the authoritative landing gate.
3. Drive `land` with the parameters documented by
   `strand workflow show land`. Do not merge or push `main` manually.
4. The shared review path runs one configured agent seat and records an
   immutable reviewed HEAD plus explicit P1/P2 resolution. A repository may run
   richer review, but normal landing cannot omit the shared evidence.
5. Approved work joins the durable FIFO queue. The queue retains a failed turn
   for repair rather than allowing another landing to overtake it.
6. After a successful merge, landing updates canonical `main`, finishes the
   optional card, and removes the feature branch and worktree. Failed or aborted
   work remains available for follow-up unless cleanup already completed.

Inspect queue state with `strand merge-queue status`. Use the operation's live
help for await and withdrawal syntax. Withdrawal must stop owned shell work and
must refuse a turn whose merge may already have been submitted.

## Bootstrap consumption

The recommended consumer uses the shared Codethread bootstrap:

```clojure
(require '[ct.spools.codethread.bootstrap :as codethread])

(codethread/register! runtime)
;; Register repository-specific modules here.
(codethread/register-executor! runtime [:consumer/modules])
```

`register!` activates identity, Workflow, Harnesses, shared aliases and
reviewers, Kanban, and `:millhouse/spools-land`. The bootstrap leaves the sole
shared `:agent` executor until the explicit final call so restored review gates cannot
run before consumer policy has reconciled. Consumers must not register the land
module or agent executor a second time.

A consumer that does not want Codethread's agent catalog can depend directly on
the independent `millhouse.spools/land` root and register
`millhouse.spools.land.spool` after Millhouse Workflow and Kanban. The landing
root has no Harnesses provider dependency; that consumer supplies the `:agent`
executor and configured reviewer seat used by the review gate.

Published dependencies use immutable Git SHAs. Local roots are allowed only as
temporary overrides in disposable development worlds; do not check sibling
paths into a consumer workspace.

## Repository quality scripts

Each repository keeps an executable `.millstrand/land-quality.sh`:

- UI repositories run their package-manager quality gate, such as
  `pnpm quality`.
- Clojure spool repositories run the actual aggregate Make target when one
  exists; otherwise the script composes the repository's documented test and
  static-analysis commands.
- The script starts from the repository root and runs `git diff --check` before
  behavior gates.

The shared workflow invokes this file. Generic landing code must not guess a
repository's language, Make targets, warm processes, or generated resources.

## Verification

Before publishing a consumer pin, verify the actual checked-in workspace in a
disposable world. Codethread's `:consumer-smoke` alias accepts producer checkout
roots followed by consumer checkout roots and checks:

- `:millhouse/spools-land` activated successfully;
- `merge-queue` is a registered operation;
- `review` and `land` are listed;
- `land`, `land-merge`, and `land-abort` can be shown; and
- the public `land` description retains the mandatory review contract.

After reviewed branches reach `main`, restart each canonical Weaver in a
coordinated window. Verify `strand help merge-queue`, `strand workflow list`,
and `strand workflow show land` against every live workspace. Disposable smoke
is not a substitute for that final pin and restart check.
