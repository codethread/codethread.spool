# Millstrand sub-coordinator runbook

Use this procedure for one repository's eligible work. Keep the live ownership
map on a Kanban feature and the next action on its current coordinator task.
The parent coordinator owns cross-repository dependencies. Dated observations
and trial history belong in
[coordinator-field-notes.md](coordinator-field-notes.md), not in this procedure.

## Non-negotiable boundaries

- Never stop or restart the Mill; only the user may do so. Replace a Weaver only
  with explicit authorization and an exact operator handoff.
- Delegate only through tracked `strand agent` runs using Pi. Use Sol for source
  writers and preserve required Oracle direction and acceptance. Never use
  built-in Codex/ChatGPT delegates or native Pi helpers.
- Put direct-only constraints in the actual Oracle prompt **and** appended system
  prompt before publication. Frozen invocations do not absorb later notes.
- Never use the deprecated `agent-harness.spool`; maintained implementation and
  usage belong to `harnesses.spool`.
- Keep one local coordinator and one sole source writer for each feature
  worktree. Preserve user changes. Never edit or push `main`. Use disposable,
  explicit workspaces for workspace-backed tests.
- Stop only an identified run or PID. Never use a broad process-name kill.

## Launch a headed persistent coordinator

When persistent Goal mode is requested, the initial Pi prompt must begin with the
literal slash command and continue with the complete assignment:

```text
/goal <rest of prompt>
```

The `/goal` command is the activation path. `goal_complete`, `goal_blocked`, and
`goal_wait` manage an already active goal; they do not create one. Do not invent a
Strand goal record or copy an arbitrary ID into task attributes and call that Pi
persistence.

After launch, verify Goal mode in both the visible Pi TUI and the native session's
real goal-state record. Record the actual Pi goal ID and state, automatic guard
counter/default limit, and whether the kickoff was consumed. Leave automatic
guards at their defaults unless the user explicitly changes them. Activation in
a busy TUI may precede consumption of the queued kickoff, so verify both.

Record all launch coordinates separately:

- selected alias, resolved Pi harness, exact model and effort;
- stable request ID, tracked Strand run ID, native Pi session ID, identity, and
  named terminal;
- target coordinator task and assigned feature cards;
- canonical coordination workspace, execution repository, and feature worktree;
- local coordinator owner, sole Sol writer, and each role's current run pointer.

A visible TUI proves headed launch only after the user verifies it when that is
the acceptance condition. An active goal and first dispatch prove neither
sustained coordination nor end-to-end completion.

## Take ownership and dispatch

1. Read applicable `AGENTS.md`, `strand --help`, `mill prime millstrand`,
   `strand prime kanban`, `strand help agent`, and relevant workflow help.
   `strand prime agent` is stale advice: use the live `help agent` surface.
   Put global flags before the operation and use explicit canonical `--cwd` and
   `--workspace` values across checkouts.
2. Read the feature, coordinator task, latest notes, dependencies, actual active
   runs, and recorded worktree. Publish the coordinator's owner, task, run,
   branch, durable cwd, and assigned cards before acting. Preserve predecessor
   pointers and state which features belong to other coordinators.
3. Distinguish the canonical coordination workspace, where cards, notes, runs,
   and workflows live, from the execution worktree where source changes happen.
   An empty board in the source repository does not prove that repository idle.
4. Adopt existing workers and workflows before creating any. Confirm the target
   is active and dependency-eligible, its checkout exists, no local owner is
   already operating it, and no sole writer owns the worktree.
5. Publish one logical request with a stable idempotency key. If publication
   times out, look up that same request with
   `strand agent show --request <request-id>` and inspect actual runs before
   retrying. Equivalent retries reuse the key; distinct requests use new keys.

A run can be `ready` without an attempt because its target is closed or blocked.
Conversely, a worker can finish while its implementation target remains active,
preventing a dependent review from starting. Check target state, dependencies,
request publication, invocation, attempt, queue, process custody, and the current
run pointer. Never infer capacity or ownership from `ready` alone.

`agent assign` emits feature-claim guidance, so assign a feature only. For a task
under an already claimed feature, use an explicit tracked `agent run --target
TASK --prompt ...`; do not ask a worker to claim a task as a Kanban card.

## Sustain the loop

Use a bounded await, normally 45 seconds with a client deadline longer than the
inner wait. After every result or timeout:

1. inspect the current run, target lifecycle, dependencies, and workflow;
2. read `notes COORDINATOR_TASK` **and** `notes CHILD_TASK` in the canonical
   coordination workspace;
3. inspect source, test, review, or gate evidence for meaningful change;
4. act, record a concise next action, or begin another bounded await.

Useful progress is a substantive note, child attempt starting, source diff or
commit changing, a completed validation, review verdict, or gate transition. A
live PID, elapsed time, repeated reminder, or stale timestamp is not enough.
Ownership changes require explicit acknowledgement on the coordinator task,
including any conflicting operation already underway.

| Observed state | Required interpretation and action |
| --- | --- |
| Running with custody and progress | Continue bounded waits; advance independent eligible work. |
| Ready/pending | Check active-versus-closed target, dependencies, publication, invocation, attempt, queue, and custody. |
| Request timed out | Read back the same request ID and child runs before an idempotent retry. |
| Terminal | The run stopped or failed; this does not prove process settlement, success, or acceptance. |
| Settled | Provider process custody is gone; inspect semantic result and native-session eligibility. |
| Successful result | Verify the promised source and checks; process exit zero is not acceptance. |
| Gate appears failed | Read actual error, report, and executor custody; static instructions are not a verdict. |
| Review requires rework | Reopen the same unfinished implementation milestone and resume its sole Sol lineage. |
| Dependency blocks work | Record exact prerequisite, owner, evidence, and event that makes it ready. |

Use `agent-run-terminal` only for terminal state and `agent-run-settled` before
native continuation or writer transfer. Target completion is a third, separate
fact, and accepted source is a fourth. Never close unfinished work or remove a
dependency edge merely to force dispatch.

Do not finalize after one quiet timeout while a child, review, or workflow still
needs the coordinator. Finish only when eligible work is accepted and cleaned,
or every remaining item has a concrete blocker and an acknowledged next owner.

## Continue and rework safely

Native resume retains the original target, cwd, session, model, effort, and
frozen guidance. Before resuming, verify settlement, resumability, target
eligibility, cwd existence, checkout/ref, and ownership. Prompt text cannot
retarget a closed task or change a frozen model. Use a fresh open task/run when
the target, cwd, or model must change.

A material review finding against the same unfinished source milestone is the
exception: record the finding and immutable reviewed SHA, reopen that milestone,
then resume the same sole writer with the bounded repair and regression case.
Refresh the ordinary run pointer while preserving its predecessor. After the
changed candidate settles, reclose the implementation milestone and obtain the
required fresh focused review.

## Review, accept, land, and clean up

Ask tracked direct Oracle for a focused direction decision when current evidence
leaves a material contract, diagnosis, or ownership question unresolved. Supply
the exact error or question, relevant IDs, immutable commit/range, attempted
repairs, and frozen direct-only constraints. Do not substitute helper evidence,
an opening statement, or a broad optional review for a required verdict.

Keep these states distinct:

1. implementation process terminal and settled;
2. clean pushed candidate with relevant checks;
3. focused Oracle disposition where required;
4. required quality marker and one valid basic review;
5. accepted feature source;
6. shared Land completion and cleanup.

Before acceptance, require the same exact SHA at local `HEAD`, remote/PR head,
reviewed commit, and quality marker. If FIFO rebases or otherwise changes the
candidate, review the changed range or prove the required equivalence under the
workflow contract. Never describe a review of an older SHA as current.

Inspect and drive the installed shared `land` and merge-queue procedures. The
normal release requires quality, one basic review, finding resolution/sign-off,
FIFO merge, card completion, branch/worktree cleanup, and verification of the
actual canonical merge and workflow completion. Do not invent an optional gate,
duplicate unchanged broad reviews, or promise a pause between automatically
advancing gates.

Before deleting a checkout, identify its cleanup owner and recheck active runs,
Git common-directory dependencies, clean/pushed state, canonical ancestry, and
retained artifacts. A completed former feature does not authorize deleting a
root now backing another owner's worktree.

## Report failures honestly

Classify the concrete boundary before recovery:

- **Provider/network failure:** record the provider error even if a wrapper exits
  zero; preserve partial work, native session, request lineage, and worktree.
  Check actual availability once before a bounded continuation. Do not churn
  request IDs, change accounts, or rewrite frozen environment state.
- **Coordination failure:** examples include duplicate ownership, missed
  coordinator mailbox, stale target, wrong workspace, invalid request lookup,
  premature finalization, or late review constraints. Correct ownership or the
  supported lifecycle without blaming the provider.
- **Source/quality/review failure:** preserve the exact failing candidate and
  evidence, repair through the sole writer, and rerun only affected checks or
  required fresh review.
- **Long healthy work:** a timeout with custody and semantic progress is not a
  provider, coordination, or test failure.

A newly published successor does not own the work until it acknowledges custody.
Do not release the predecessor until it settles or relinquishes and the successor
is genuinely ready. Never fabricate process exits, settlement, callbacks, goal
state, review acceptance, or historical chronology.

## Handoff record

The latest coordinator note must contain: local owner and assigned cards;
coordinator task/run/request/native session/terminal/goal state; canonical
coordination workspace and execution worktrees; every live child and sole-writer
lineage; exact accepted or rejected SHA and checks; pending gate and next action;
concrete blockers and their owners; preserved artifacts; cleanup owner; and any
explicit runtime authorization. Keep it short enough for a cold start without
rereading the historical field log.
