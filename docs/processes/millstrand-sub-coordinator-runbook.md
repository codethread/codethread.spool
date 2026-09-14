# Millstrand sub-coordinator runbook

Use this procedure for one repository's eligible work. Keep the live ownership
map on a Kanban feature and the next action on its current coordinator task.
The parent coordinator owns cross-repository dependencies. Detailed trial
evidence is in [coordinator-field-notes.md](coordinator-field-notes.md).

## Non-negotiable boundaries

- Never stop or restart the Mill; only the user may do so. Agent processes belong
  to the Mill. A Weaver replacement is a separate operation and needs applicable
  user authorization. This recovery explicitly permits Weaver replacements.
- Delegate through tracked `strand agent` runs using Pi. Use Sol for writers.
  Preserve required Oracle direction and review. Never use built-in Codex/ChatGPT
  delegates or native Pi helpers. Include direct-only constraints in the actual
  Oracle dispatch, including its appended system prompt.
- Never use the deprecated `agent-harness.spool` repository or its worktrees.
  Maintained implementation and usage belong to `harnesses.spool`.
- Keep one writer per feature worktree. Preserve user changes. Never edit or push
  main. Use disposable explicit workspaces for workspace-backed tests.
- Stop only an identified run or PID. Never use a broad process-name kill.

## Take ownership

1. Read applicable `AGENTS.md`, live help/prime, the coordinator feature, current
   task, latest notes, dependencies, and actual active runs. Put global CLI flags
   before the operation. Use explicit canonical `--cwd` and `--workspace` when
   working across checkouts.
2. Record the coordinator's owner, current run, task, branch, and durable worktree.
   Preserve predecessor pointers. State which features belong to other owners.
3. Adopt existing workers and workflows before creating any. Confirm each target
   is open and each execution checkout exists. Published metadata alone does not
   prove a process launched.
4. Select eligible P1/P2 work with a concrete acceptance condition. Reconcile old
   blockers against maintained current source. Do not promote refinement or
   pursue the P3/P4 tail without a reason tied to the requested outcome.

Assignment policies are frozen worker guidance. The shipped `stop-on-complete`
and `close-on-complete` policies do not automatically close a target or restart
an agent that exits early. An alias alone therefore cannot guarantee continuity;
the coordinator must verify completion or arrange an acknowledged handoff.

## Sustain the loop

Use a bounded await, normally 45 seconds with a longer request deadline. After
each result, read current run state, the latest task notes, workflow readiness,
and source or gate progress. Record a concise next action when something changes.

Read both the coordinator task's notes and the active child's notes. The parent
may leave new scope or handoff guidance on the coordinator task; watching only a
child's output misses that mailbox. Acknowledge changed ownership explicitly.

| Observed state | Next action |
| --- | --- |
| Running with valid custody and progress | Continue waiting; advance independent eligible work. |
| Ready/pending | Check publication, identity, invocation, attempt, and process/queue evidence. Do not infer capacity. |
| Request timed out | Look up the same request ID and actual child runs before retrying. Reuse the idempotency key where supported. |
| Process failed or never launched | Classify the concrete error. Preserve source. Repair the cause and use an eligible retry or fresh target. |
| Worker settled successfully | Inspect its immutable candidate, validation, clean/pushed state, and acceptance gaps. Exit zero is not feature acceptance. |
| Gate appears failed | Read its actual error, report, and executor custody. Static instructions or warnings are not a failure verdict. |
| Review requires rework | Give one Sol writer the exact P1/P2 findings, retain accepted contracts, then review the changed candidate. |
| Dependencies block work | Record the owning repo, exact prerequisite, evidence, and event that makes the task ready. |

A timeout is not a completion condition. Do not finalize while a child, review,
or workflow still needs your next action. Continue until eligible work is
accepted and cleaned or each remaining item has a concrete blocker. Leaving a
list of active children is only a handoff after another owner has accepted it.

## Review, land, and activate

Ask tracked Oracle for direction when current evidence leaves a material scope
or contract decision unresolved. Give it an exact question and immutable source
range. Use the required ordinary review and quality gates; do not add repeated
reviews or unchanged suites without a new concern. Reject helper-based evidence
when the task requires a direct review.

Keep the acceptance scope explicit through rework. Retain concrete regression
cases and previously accepted contracts; distinguish a newly demonstrated defect
from inherited style debt or an optional design proposal. A fresh review does not
automatically expand a focused repair into unrelated refactoring. Record the
disposition and ask Oracle a bounded question when the contract itself is unclear.

Check the actual declared acceptance path. An optional review workflow must not
become an invented prerequisite for the required shared land/basic-review path.
Keep failed workflow history honest and resolve material findings either way.
If generated review setup times out, inspect the generated command: increasing
the parent's await deadline does not change a child's invocation deadline.
Candidate template edits also do not update an already loaded Weaver definition.

Inspect the installed shared `land` and merge-queue procedures. Drive their
quality, review, resolution/sign-off, FIFO merge, card completion, and cleanup.
Verify the actual merge and workflow completion. If another owner already
closed a card, read the evidence instead of repeatedly finishing it.

For a permitted Weaver replacement, inspect current pool membership, active
runs, source/config provenance, and the exact target. Record old/new PID and
generation, preserve the Mill, and test the installed behavior afterward. For
live upgrades, include old serialized runs completing under the new backend;
fresh-start tests alone cannot establish completion compatibility. Local source
changes can load differently while the dependency fingerprint stays unchanged.

## Handoff, cleanup, and model changes

Before transferring a checkout, identify and obtain acknowledgement from its
cleanup owner. Recheck current runs and Git common-directory dependencies before
removal. A completed old feature does not authorize deleting a root now backing
someone else's worktree. Retain recovered source and backups until acceptance.
Check branch/main layout after recovery or moving between clones and worktrees.

Native continuation retains its original target, cwd, and model. Use a fresh
open task/run when any of those must change. Close predecessor coordinator tasks
as superseded, not as evidence that their source features are complete.

Change a coordinator model based on concrete behavior. Luna failed to recover
closed-target launches in this trial. Terra delivered bounded tasks but twice
exited with owned work pending. The persistent Skein seat was therefore changed
to Sol/high; its first several timeout cycles continued correctly. These are
observations about particular assignments, not a universal model ranking.

The latest handoff note must contain: current owner/run/task/cwd; each live child
and its feature; exact accepted or rejected candidate; pending gate and next
action; concrete blockers; preserved artifacts; and any user-authorized runtime
scope. Keep it short enough for a cold start without rereading the entire log.
