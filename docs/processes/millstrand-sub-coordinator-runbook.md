# Millstrand sub-coordinator runbook

Use this provider-neutral procedure for one repository's eligible work. Keep the
live ownership map on its Kanban feature and the next action on the current
coordinator task. The parent coordinator owns cross-repository dependencies.
Dated observations and historical trial details belong in
[coordinator-field-notes.md](coordinator-field-notes.md), not here.

## Establish ownership and goals

1. Read applicable repository instructions and live `strand help` and
   `strand prime` output.
2. Read the feature, task graph, dependencies, latest notes, active runs,
   workflows, recorded branch and worktree, and actual source custody.
3. Distinguish the canonical coordination workspace, which owns cards, notes,
   runs, and workflows, from execution worktrees, which own source changes.
4. Establish one coordinator and at most one source writer per worktree. Adopt
   healthy existing runs before creating work and preserve unrelated owner,
   run, file, index, workflow, and queue state.
5. Set a real goal for each assigned card and keep it current until the declared
   outcome is accepted or handed off with an evidenced blocker.

Never stop or restart the global Mill. Never restart or replace a running Weaver
without explicit user sign-off. Never use the deprecated
`agent-harness.spool`; maintained harness work belongs in `harnesses.spool`.
Terminate processes only by an identified run or PID; never use a broad
process-name kill. Never edit or push `main`, and use disposable explicit
workspaces for workspace-backed tests.

## Dispatch tracked work safely

Delegate implementation, diagnosis, and review only through tracked Strand
runs. Give each run one active, dependency-ready target, one bounded
responsibility, an explicit source worktree, and a stable request ID. Retain the
returned run ID and record substantive dispatches and decisions on the target.

A task beneath an already claimed feature is not another claimable feature. Use
a targeted run for that task and state that its assigned worker is the sole
writer. Use feature assignment only for an assignable open feature.

If publication times out or delivery is uncertain, inspect the same request ID
and actual runs before an equivalent retry. Never create a second writer because
the first request's delivery is uncertain. Verify target lifecycle, dependency
readiness, request publication, invocation attempt, process custody, and current
run pointer; a run's `ready` status alone proves none of those facts.

Pass rich card, note, or prompt content as one structured argument, payload, or
raw-file value. Never interpolate rich prose into shell commands, and do not
confuse JSON encoding with shell escaping. Read stored content back whenever
quoting or delivery is uncertain.

Use common APIs such as `strand show`, `strand notes`, `strand ready`,
`strand agent`, and `strand workflow` to inspect the durable state. Put global
`--cwd`, `--workspace`, and timeout flags before the operation when crossing
checkouts.

## Sustain bounded observation

Wait for workers, reviews, and workflow gates with bounded `strand await` calls
against named queries. Reissue a bounded wait after checking meaningful
progress rather than tight-polling. A timeout means only that the condition was
not observed; it is not a failure verdict.

Choose query cardinality for the evidence needed:

- `agent-run-terminal --min-count 1` observes a terminal run, not success.
- `agent-run-settled --min-count 1` requires positive process-settlement
  evidence before transferring custody or continuing a run lineage.
- `agent-run-active --max-count 0` observes absence, not completion.
- `agent-work-complete --min-count 1` observes accepted assignment completion;
  `agent-work-complete-or-intervention` also identifies work needing
  intervention.

Missing IDs never satisfy positive-evidence waits, and no active run does not
prove completion. After every result or timeout, inspect the current run, parent
and child notes, target lifecycle, dependencies, source evidence, and workflow
readiness. Meaningful progress is a substantive note, child attempt, changed
source or commit, validation result, review verdict, or gate transition—not a
live PID or elapsed time.

| Observed state | Required action |
| --- | --- |
| Running with custody and progress | Continue bounded waits and eligible independent work. |
| Ready or pending | Inspect target, dependencies, request, attempt, queue, and custody. |
| Request timed out | Read the same request and runs before an idempotent retry. |
| Terminal | Inspect the semantic result and target; do not infer settlement or success. |
| Settled | Confirm the result and custody before continuation or transfer. |
| Review requires rework | Continue the same unfinished milestone with its sole writer. |
| Dependency blocks work | Record the prerequisite, owner, evidence, and readiness event. |

## Review, land, and clean up

Keep implementation, review, required quality, and landing evidence distinct.
Record the exact implementation SHA, immutable reviewed SHA, quality command and
result, required quality marker, and pushed remote or pull-request head. They
must identify the same candidate; changed source requires affected quality and
review against the changed candidate.

Require the repository's exact review and quality. Verify material findings at
the concrete contract boundary, assign one bounded repair to the source writer,
and repeat affected checks and review. Do not substitute optional review for a
required verdict or broaden accepted work.

Follow the installed shared Land workflow. Preserve every gate and strict FIFO
order, verify the canonical merged commit, complete the assigned cards, and
perform required branch and worktree cleanup. Repair failed gates through their
supported workflow path rather than bypassing them. Before deleting a checkout,
verify its cleanup owner, active runs, clean pushed state, canonical ancestry,
and retained artifacts.

## Report or hand off with evidence

Classify failures at the observed boundary: execution service, coordination,
source, quality, review, workflow, or a healthy long-running operation. Preserve
the exact candidate, request lineage, partial work, and error evidence. Do not
fabricate process exits, settlement, callbacks, review acceptance, or historical
chronology.

Finish only with accepted evidence or an acknowledged handoff. The latest note
must identify the coordination workspace; assigned cards; coordinator and child
targets, requests, and run IDs; exact candidate and checks; pending workflow
gate; blockers and owners; preserved artifacts; cleanup owner; next action; and
an acknowledged next owner. Otherwise report the concrete external action
required to continue.
