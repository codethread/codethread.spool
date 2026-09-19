# Repository automatic delivery

This repository activates Codethread's opt-in dispatcher from
`.millstrand/me/auto_run.clj`. It admits at most two graph-ready, pending,
unowned feature cards carrying the `auto-run` label.

## Policy

The default and only allowed workflow is `auto-full-land`, using the `sol` seat
at `high` effort. Cards may override `auto-run/seat` and `auto-run/effort` with
valid Harnesses values. A workflow override must still name `auto-full-land`.
Malformed or unavailable overrides fail visibly and never fall back.

The workflow owns these stages:

1. The assigned worker implements and tests the feature in its prepared worktree.
2. A shell gate runs `make quality`.
3. The worker publishes a ready PR, and a shell gate waits for CI.
4. A code gate moves the verified card into review.
5. Shared autonomous land runs mandatory review. The worker stops before
   sign-off and accepts a distinct canonical-root `grunt` against the dependent
   finisher step. That finisher owns sign-off, FIFO merge, cleanup, and final
   card closure.

The dispatcher uses `wktree` to create `auto/<card-id>`. It records preparation
before assignment, honors dependency readiness, and retains one-shot receipts;
it never retries a possibly partial filesystem operation.

## Operations

Inspect the live surface before opting in work:

```text
strand prime auto-run
strand auto-run status
strand workflow show auto-full-land
```

`assigned` is a durable admission receipt, not worker liveness. Inspect the
recorded Harnesses run and workflow run for current state. An autonomous failure
adds `auto-run-failure`, preserves resources for inspection, and requires an
explicit recovery decision.

Startup is covered by a disposable in-memory Weaver world:

```text
cd .millstrand
clojure -M:test
```

The test loads the checked-in dependency and init files, verifies bounded
Sol/high configuration, starts `auto-full-land`, and proves that the handoff and
finisher are distinct targets. It does not launch agents or mutate the live
workspace.

Source-only policy changes use normal module refresh. If dependency pins change,
a supported Weaver restart requires explicit operator approval. Never stop Mill.
