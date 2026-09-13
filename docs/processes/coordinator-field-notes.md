# Millstrand coordinator field notes

Working observations from the sibling recovery and open-card execution on
2026-09-13. These notes supply the sub-coordinator harness alias and runbook now
being built under feature `jxvxu`. Keep adding concrete outcomes and
counterexamples while the work runs and the alias is piloted.

Tracking: Codethread epic `w3oqr`, coordinator feature `x4y0z`, notes task
`kejtr`. The user requested delegation through `strand agent`; delegated
execution uses Pi. Built-in Codex/ChatGPT delegates were stopped.

## Give the next coordinator a complete starting point

- Put the coordinator feature under its epic. Give it an owner, branch,
  recorded worktree, current run pointer, and child tasks.
- Keep a short ownership map on the feature and detailed evidence on the task
  being driven. Its latest note must say what to do next, not just what happened.
- Record the canonical coordination workspace separately from the execution
  repository/worktree. A card in Harnesses can own work in Millhouse or Agents.
  An empty local board therefore does not establish that the repository is idle.
- Preserve prior owners/run IDs in notes when changing the primary dashboard
  pointer. A custom recovery pointer alone leaves ordinary views showing an old
  coordinator.
- State the outcome and non-overlapping scope of each coordinator. In this run,
  Harnesses `129ar` owns the identity epic `k5zd2`; Millhouse's generic board
  coordinator must not also operate its identity worktree.

## Discover, dispatch, and keep one writer

Start with live help and prime: `strand --help`, `mill prime millstrand`,
`strand prime kanban`, `strand prime agent`, and the relevant workflow help.
Use an explicit `--workspace` for cross-repository coordination.

Inspect the board, dependencies, card notes, recorded worktree, and active runs
before dispatch. Check actual worktree ownership; registry entries can outlive
their processes. A worker exiting does not establish that its feature is done.

Verify the resolved harness/model with `strand agent list`. Use `strand agent`
for delegation and Pi as the harness. Preserve explicitly required reviewer
roles, including Oracle; a recovery Sol review cannot waive an Oracle gate.

`agent assign` generates a feature-claim instruction. Assigning coordinator task
`538nl` generated a failed `kanban claim` against a task. For future launches,
assign a feature or use an explicit `agent run --target TASK --prompt ...` under
an already claimed feature; do not tell a worker to claim a task as a card.

Prepare the isolated worktree before launch. Use a stable request ID for each
logical dispatch, retain the returned run ID, and distinguish coordinator work
from the implementation worker's scope. Record the first substantive dispatch
or action, then continue through the dependency graph.

A launch can succeed after its client reports a deadline. Millstrand's
coordinator read back run `jx15x` and confirmed it was running after that exact
case. Inspect the request/run before retrying; reuse the logical request ID
instead of starting a second writer because the first response was uncertain.

Use payload files or direct structured argv for rich card bodies and prompts.
Archive coordinator `v0zpb` observed shell command substitution corrupt a body
containing backticks and shell parameter syntax, then repaired the owning card
using a payload file. JSON encoding is not shell escaping. Verify the stored
body when a command result suggests quoting or interpolation went wrong.

Be explicit about delegation depth. Millhouse repo coordinator `d23f7` asked
`4a8yy` to delegate implementation, so that run became a feature coordinator and
launched writer `i0scn`. This is a valid division only while the two coordinators
avoid editing the writer's worktree. A writer prompt should say to implement the
bounded change, not repeat the parent's instruction to delegate implementation.
Do not accidentally create another coordinator at every level.

## Wait for evidence, with bounded timeouts

Example of a bounded observation, using placeholders for the selected world
and returned run ID:

```sh
strand --workspace "$coord_ws" --timeout 55s await \
  --query agent-run-terminal --param run-id="$run_id" \
  --min-count 1 --timeout-secs 45
strand --workspace "$coord_ws" agent show "$run_id"
```

The client timeout should exceed the inner await timeout. A timeout means the
condition has not yet been observed; it is not a failure verdict. Reissue the
wait after inspecting relevant progress, instead of tight polling.

- `agent-run-terminal` means stopped or failed, not successful work.
- `agent-run-settled` additionally requires process settlement. Use it before
  same-session continuation or handing the same worktree to another writer.
- Target completion queries require actual target closure. With
  `stop-on-complete`, await the run, read its result, and accept the target;
  awaiting only the open target can wait forever for the coordinator's own act.
- For workflow execution, use `workflow ready` and bounded `workflow await`.
  Let healthy executor-owned gates run. Advance only the ordinary step or
  checkpoint for which evidence and the live input contract are available.
- Useful progress is a task note, a delegated child starting, a reviewed commit,
  a validation result, or a gate transition. A live PID alone is weak evidence.
  Long validation can be healthy without frequent card mutations.

## Accept, rework, and escalate to Oracle

Keep implementation, review acceptance, and landing evidence distinct. Match
the reviewed commit to HEAD, remote/PR head, and the quality marker where the
shared workflow requires it. Changed HEAD requires review of the changed range.

Dashboard audit `8502s` returned passing quality and browser checks but four
independent P2 review findings. It left the task/feature open with a bounded
repair recommendation. This distinguishes a completed audit from acceptance of
the product. For a scope-ambiguous finding, ask Oracle for disposition against
the actual card contract instead of automatically adding unrelated work or
waiving the finding.

On a concrete P1/P2 finding, record the finding and immutable reviewed commit,
delegate a bounded repair, await settlement, then obtain the required fresh
review. Keep the card open and preserve dependency gates until acceptance and
required integration have actually happened.

Escalate to Oracle for a diagnosis or decision when the failure needs technical
judgment, ownership is unclear, or repeated attempts do not explain the cause.
Give Oracle the exact error, relevant run/gate IDs, current commit, attempted
repairs, and a bounded question. The coordinator turns its verdict into the
next action; it should not replace that verdict with an improvised acceptance.

Observed example: identity `sfc79` progressed through a Sol documentation fix,
an Oracle BLOCKED verdict on a workflow-test observation race, a bounded Sol
diagnosis/fix, and a fresh Oracle ACCEPTED verdict `xgt8o` at
`5bc5c8be5d7c96252cf6813e27da35806e4fa73b`. The final wrapper passed 436 tests
and 3,274 assertions, with the marker and remote/PR head matching. Acceptance
still precedes the existing shared landing workflow.

At 17:01 UTC the coordinator recorded successful landing of that same accepted
candidate: PR 26 merged as `b1955a96ad91bf2909a407859fca1565ec4b9fdb`, workflow
root `3m79c` closed, canonical main updated, feature resources cleaned, and FIFO
released. `land-sfc79-k5zd2` lives in the Millhouse workspace even though its
feature and coordinator are tracked in Harnesses. Record a workflow's workspace
alongside its ID; the ID by itself is not globally addressable.

## Recover failures without losing ownership or evidence

The mill restart lost process custody facts that were held in its lifetime's
memory. Runs `ogx4f`, `f90fv`, and `v1fmr` became failed with settlement unknown.
Do not manufacture settlement or assume a failed registry entry proves that
the old process is gone. Preserve the historical records and examine concrete
native/process evidence before any new worktree ownership.

A distinct read-only recovery review was possible after an aborted native
session and absence of its exact process were established. It did not pretend
the original assignment had resumed or waive the original review requirement.

Passive task notes are not a live steering channel. Recovery worker `k6jgy`
continued investigating the original custody loss without rereading a newer
primer. A supported stop of that exact *new* run, observed graceful settlement,
and native resume as `129ar` delivered the corrected prompt directly, preserving
its session and model. Do not use this as a reason to interrupt healthy work
whose current prompt is still correct.

Keep a failed FIFO landing turn while repairing its cause. The recovered
`land-vz8a2-k5zd2` demonstrates two bounded poured-request repairs:

- `pull.rebase=true` made a fast-forward pull reject unrelated dirty files.
  After proving upstream paths and dirty paths were disjoint, the executor's
  command used explicit `--no-rebase --ff-only`. Nine dirty file hashes and
  staged state were unchanged afterward.
- Cleanup then fetched a deleted feature branch through a narrow configured
  refspec. An explicit main fetch repaired that gate without changing repository
  configuration. The actual workflow completed and released its FIFO lock.

Do not close a failed gate merely to make the board look clear. Repair the
request/cause through the supported executor path, then verify its real output
and downstream housekeeping. Do not discard, stash, or opportunistically commit
another owner's dirty files. Never use process-name kill patterns.

## Reconcile open cards rather than manufacture activity

Old open cards can contain unfinished work, completed but unaccepted outcomes,
or designs awaiting a decision. Reconcile each against its declared outcome.
Closed child tasks alone are insufficient; missing worktrees alone do not prove
that work was lost. Millhouse `wij2z`, for example, has a merged PR whose branch
and worktree were already cleaned.

Respect repository migration boundaries without dropping the requested outcome.
Archive coordinator `v0zpb` found CLI argument forwarding absent in both the
archive and active replacement. Instead of changing archived shipped source or
calling the audit completion, it created Harnesses feature `xj0mn`, linked the
old card, prepared an isolated worktree, and delegated implementation `ce3cw`.
The parent notified the existing Harnesses identity coordinator about possibly
overlapping CLI/provider surfaces so integration remains explicit and ordered.

The user subsequently made the boundary stricter: `agent-harness.spool` is
entirely deprecated and read-only; all updates and usage belong in
`harnesses.spool`. The parent stopped `v0zpb` by exact run ID, verified graceful
settlement and absent worker PIDs, and found no process with an archive cwd.
An old interactive registry row had no process handle and pointed at Agents;
its stop request is recorded without inventing settlement. Do not resume or
dispatch work through the deprecated workspace, even for further coordination.
The repository instruction correction is feature `wtqou` with Sol/Pi `26lul`.
Active replacement `xj0mn` and writer `ce3cw` continue in Harnesses. Parent-owned
task `irfb7` explicitly takes over their acceptance/landing supervision and is
a real handoff opportunity for the reviewed sub-coordinator alias. Deprecation
must transfer the remaining outcome and its supervisor, not abandon the work.

Millstrand coordinator `h1i9g` both launched implementation `are8d` for `1oks3`
and closed two completed audit cards after checking their actual evidence.
Empty local boards in Devflow and the UI do not need invented tasks or idle
workers. Refinement and ambiguous parked designs should receive a concrete
decision/blocker, not guessed requirements.

## Questions to resolve before creating the alias

- What exact prompt/target contract should coordinator launches use so task
  assignments never emit invalid feature-claim instructions?
- How should a parent deliver steering without relying on incidental note reads?
- What observation interval and escalation threshold keep long checks visible
  without treating slow output as failure or busy-polling the world?
- Which restart/repair permissions belong to a sub-coordinator, and which must
  return to its parent? The current user granted broad coordination permissions;
  a future alias should receive explicit permissions for its particular run.
- What minimal completion report proves the coordinator either drained eligible
  work or left precise dependency/decision blockers and a valid next owner?

The user first deferred alias creation, then explicitly requested live rollout
and sub-coordinator delegation during the work to test and refine it. That later
instruction controls: implement and review the alias, prove additive live
registration in a disposable world, then pilot it at a real handoff boundary and
extend to other running weavers. Keep existing workers' settings and ownership
intact. Record each pilot's behavior and revise the runbook from that evidence.

The user also clarified the model trial: start the new sub-coordinator role on
Luna at max effort, and try Terra at high effort if Luna repeatedly performs
poorly after clear guidance. Existing Sol implementation workers remain Sol.
Judge actual coordination: bounded waits, ownership, correct workspace, ready
dispatch, review/rework, and acting on Oracle direction. Separate model errors
from infrastructure failures and ordinary long-running checks. Record specific
mistakes, corrective guidance, and the outcome. Do not persevere indefinitely
with a model that keeps failing these duties. A model change needs a supported
fresh handoff after settlement; native resume preserves prior model/settings.

## User ownership of mill shutdown

The user explicitly required this line in every active repository's `AGENTS.md`:
"Never stop the mill; only the user may stop it." This supersedes older broad
coordinator permissions to stop or restart the global mill. A restart includes
a stop, so agents must not use it as a workaround. Feature `7qxp9`, Sol/Pi
coordinator `iabiu`, owns the eight-repository instruction rollout. Preserve
existing user edits, especially the dirty Agents and Notes instruction files;
the deprecated archive remains read-only and unused.

For reliable delivery to running coordinators, the parent stopped only their
exact agent runs, verified graceful settlement, and resumed their native
sessions with the user rule in the actual prompt. Implementation children kept
running. Current continuations are alias worker `jjrt0`, Millstrand `3qdbe`,
Millhouse `56pqt`, and Harnesses identity coordinator `in4r1`; their standard card
and task run pointers were updated. Never confuse pausing a coordinator agent
with stopping its world or the global mill.

## Review the executable runbook

Alias candidate `3980f78` passed focused tests and aggregate quality but Oracle
`rk78g` rejected its Nushell examples: Bash-style backslash continuations were
invalid in the advertised shell. Luna reviewer `vrx1i` separately found that
the live-registration proof omitted frozen prompt and lineage attributes.
Validate rendered command examples in their actual shell, and compare all
relevant immutable launch bindings when proving existing runs are unchanged.
These are implementation/review findings, not evidence against the new Luna
coordinator role, whose live trial has not yet started. Apply the concrete
findings, review the new exact commit, and only then deploy the candidate.

## Additive live rollout and initial trials

Oracle `vbi6w` accepted revised alias commit `55e79eb`. Its rendered Nushell
examples parse, aggregate quality passes, and the config suite has 6 tests and
88 assertions. The parent loaded an immutable copy of its source blob and called
the documented narrow `register!` through each live Weaver nREPL. All eight
active worlds now resolve `sub-coordinator` to Pi/Luna max: Codethread, Skein,
Millhouse, Harnesses, Devflow, UI, Agents, and Notes. Every existing alias entry
and captured run's frozen launch bindings remained unchanged. No world or Mill
restart, broad bootstrap reload, flag change, or deprecated archive usage was
needed. The Mill remained PID `64448`.

Per-world before/after evidence is stored under
`/var/folders/6w/lnly9x394flgz3q7zty955500000gn/T/sub-coordinator-live-20260913-b_uaavy0/`.
The source blob's SHA-256 is recorded alongside the reviewed commit in the
Harnesses proof. Live availability is staging; shared source landing remains
required for durable startup configuration.

Three initial Luna max pilots own distinct slices:

- `vlf1t` / `irfb7`: Harnesses provider-argv review, rework and landing. It
  preserved Sol writer `16yfb`, observed 45-second timeouts with 55-second outer
  bounds, checked concrete diff/commit progress, and waited for settlement
  rather than accepting an active writer's clean pushed commit prematurely.
- `pg0pc` / `sl9o5`: land the accepted alias source. It respected the separate
  parent rollout task and started shared land without the optional feature card,
  leaving parent acceptance open. Its PR is Codethread #15.
- `8ccxd` / `7qxp9`: finish and verify the eight-repository Mill ownership rule.
  Six repo changes had landed before the predecessor `iabiu` was gracefully
  stopped at a read-only wait boundary. The fresh handoff included every source
  task/card/run/merge pointer, two remaining checks, and preservation evidence
  for the dirty Agents and Notes instruction files. Source workers stayed live.

These observations support the initial ownership/waiting gate, not a blanket
model-quality verdict. Continue observing review/rework and actual failure
recovery. Switch to Terra high only for repeated concrete coordination failures
after correction, through a fresh settled handoff with the same runbook.

## Adjudicate review claims against evidence

Shared-land reviewer `2heu9` incorrectly claimed that top-level `strand show`
does not exist and recommended `strand agent show`. Landing pilot `pg0pc`
forwarded that claim as an established P2; Sol `5wow8` made the two suggested
substitutions. Direct readback shows why that proposed fix is wrong: the
top-level command returns the raw record with `attributes`, while the agent
command returns a summary without them. The original eight-world proof had
already executed the raw-record commands successfully.

The parent stopped those two exact runs, verified graceful settlement, and
delivered evidence through native continuations. Current source writer is
`9bqwu`; landing pilot is `wvdeh`. They must preserve the raw-record calls,
clarify the documentation, strengthen the runbook, and obtain Oracle disposition
of the disputed finding before accepting a candidate. No incorrect replacement
was accepted or deployed. Treat findings as claims to investigate, particularly
when they contradict an observed API result. This is the first concrete pilot
correction, not repeated model failure warranting Terra yet.

Other pilot evidence remains useful: `vlf1t` waited for the provider-argv writer
to settle, then dispatched fresh tracked Oracle `3qenq` for pushed candidate
`6680740`. `8ccxd` distinguished repeated 480-second Skein test timeouts from
assertion failures and retained a progressing test process while seeking Oracle
direction. It separately identified the Millhouse provider boundary as stuck
after its 600-second command timeout, with no new session event or child process,
and chose exact worker stop/settlement/native continuation. A timeout alone is
not that diagnosis; combine elapsed bounds, semantic progress, native events,
and process custody before intervening.

## Live availability versus durable startup

All eight live worlds have the reviewed alias, but seven consumer dependency
files still pin older Codethread configuration: Skein, Millhouse, Harnesses,
Devflow, and UI at `b23d84b`; Agents and Notes at `252eeaee`. Codethread itself
uses local roots. Live registration alone would not make a future ordinary
start load the new source. Feature `evzz8`, Sol/Pi coordinator `7uxj0`, owns
scoped consumer pin delivery. Its delivery task `n4vra` depends on actual source
landing `sl9o5`; inventory task `7f2wt` can proceed independently.

Use the actual accepted merge commit, preserve unrelated pins and custom
configuration, and prove the consumer setup in a disposable world. Older direct
Harnesses overrides in Agents/Notes require compatibility evidence, not assumed
compatibility or a broad upgrade. Keep paired Codethread roots at the intended
revision where applicable. Serialize heavy checks after the current host-heavy
gates drain; repeated timeouts with live progress are a reason to coordinate
capacity and seek direction, not to stop the Mill or manufacture a passing gate.

The source refinement after the disputed review is `0f5ca2e`. Oracle `lr0az`
invalidated the command finding and found no source P1/P2, but withheld landing
acceptance because the required marker still named `55e79eb`. This distinction
matters: passing a quality script directly is not necessarily the supported
wrapper's exact-HEAD evidence. Landing coordinator `wvdeh` owns refreshing that
evidence through the shared workflow, renewed review, and actual source landing.

## Pilot acceptance and ownership wording

Harnesses pilot `vlf1t` completed its bounded provider-argv assignment through
shared land, including fresh Oracle acceptance after FIFO rebased the candidate.
PR 8 merged as `2b2f40fc4c69f7a3413451f7a3aa271619fd5ad9`; the feature, review,
and supervision tasks are closed, the queue is empty, the feature worktree is
gone, and canonical main is clean. This is completed pilot evidence, rather
than merely a successfully launched coordinator.

Landing pilot `wvdeh` subsequently verified a different review finding against
the actual alias catalog: registration replaces an existing entry by name.
It commissioned a narrow pre-registration guard repair, preserved the proven
raw-record calls, and obtained fresh exact-candidate review. Source continuation
`k9294` settled at `a4805c23c0a40d228ae3bfb342715b6e375d0dba`; reviewer `wml9a`
reports no P1/P2, with the shared quality marker matching that SHA. Source
landing still requires its remaining supported workflow and acceptance gates.
This is evidence that the pilot applied the earlier correction, not repeated
failure warranting a model switch.

Be precise when handing off run-pointer custody. Preserve the primary writer's
ownership role; do not point an implementation feature at its supervising
coordinator. Advance that feature's primary run pointer whenever the same writer
is natively resumed, preserving the predecessor in a note. A coordinator-owned
umbrella feature instead points at its coordinator. Earlier parent wording to
"preserve the writer pointer" was ambiguous; distinguish role preservation from
leaving a stopped predecessor as the current run before judging model quality.

Millhouse's user-only Mill rule landed through PR 27 as
`d2fe9e3808eca872894d9a412a9eeea55fb71cd2`: quality passed 436 tests and 3274
assertions, basic review found no P1/P2, and canonical AGENTS.md contains the
exact instruction. Seven active repos now have the rule; Skein remains under
the Oracle-directed quieter-window retry. Its check must actually pass before
that eighth source task can be accepted.

## Complete instruction rollout and refine the live seat

Skein's authorized quieter-window retry passed the full quality contract, then
normal review and land merged PR 473 as
`bfce35e1b89dc4b6c14426b56ac0c636373033d1`. The exact user-only Mill instruction
is now independently verified in canonical AGENTS.md across all eight active
repos: Codethread, Skein, Millhouse, Harnesses, Devflow, UI, Agents, and Notes.
The deprecated archive was not used or edited.

Oracle `6beyy` accepted exact alias source `a4805c2`. The parent then refreshed
only its own previously registered alias in every active world. Each preflight
required an exact match to the saved reviewed `55e79eb` descriptor, followed by
a second in-REPL descriptor guard before registration. The parent exclusively
owned live registration; delegated scopes excluded it. All eight postchecks
proved the intended review-verification paragraph was the only candidate change,
other aliases and flags were unchanged, and captured runs retained their frozen
launch settings. Evidence is under
`/var/folders/6w/lnly9x394flgz3q7zty955500000gn/T/sub-coordinator-refinement-20260913-zcn1aq2_/`.
Existing sessions keep frozen guidance; this refinement applies to new runs.

The new guidance is being exercised by pilot `ah23n` (`young-young-stoat`),
Pi/Luna max, coordinating durable startup-pin feature `evzz8`. Its predecessor
Sol `7uxj0` completed inventory and settled through exact managed cancellation
at a bounded read-only await. No pin writer was active. The new feature owner
and run pointer were recorded immediately, with predecessor and seven prepared
consumer tasks preserved. Its prompt explicitly separates umbrella coordinator
ownership from per-source writer pointers and serializes heavy checks.

## Observe automatic workflow boundaries before promising a hold

Shared land rebased the accepted alias candidate to `6e8cf5b`, then its executor
merged PR 15 as `3dcf5f051842512d444b9d111d3998f4078081f6` at 18:41:28 UTC.
The coordinator's proposed instruction to hold the squash step for fresh
exact-SHA review was recorded at 18:41:37, after the merge. A note is not an
executor pause. Inspect the actual workflow graph before promising a review
boundary between automatically advancing steps.

Keep the evidence chronology honest: prior review of `a4805c2` is not a fresh
pre-merge review of `6e8cf5b`. Landing owner `wvdeh` retains task `sl9o5` for
exact-range review and Oracle adjudication of the rebased/merged result, with
patch/tree equivalence and preservation of the user-only Mill rule. Consumer
pins remain blocked until that acceptance is real. Do not fabricate a paused
gate, forge a past acceptance, or revert an otherwise valid result merely to
make the historical workflow resemble the intended sequence.

## Exercise the refined role and make a bounded fallback

Skein's Sol coordinator `3qdbe` reached a read-only worker await after rebasing
the formatting candidate onto the landed user-only Mill instruction. The parent
settled that coordinator and handed repository coordination to refined Luna
pilot `2gs5l` (`merry-smart-yak`), preserving source writer `ocx6k`, quality gate
`43ov6`, reviews and worktrees. Its first observations correctly distinguished
native semantic progress from a stale registry timestamp and timed-out await.
The umbrella and coordination task pointers were advanced immediately.

The alias landing pilot received a bounded Terra fallback after repeated
review-evidence corrections. It had first forwarded a false API review claim,
then later cancelled required Oracle `7u47g` as "optional" and ended without its
verdict. That Oracle had actual read/bash activity followed by an untracked
native subagent wait; `process-phase=starting` did not establish provider failure.
The Oracle's opening statement was not acceptance. Record these concrete events
without generalizing them into a claim that every Luna assignment fails: the
provider-argv and eight-repo instruction pilots completed accepted work.

The fresh fallback is task `negj4`, initially run `1mehk`, Pi/Terra high with the
same refined shared runbook. The parent selected the existing fallback candidate
only for publication, verified the frozen Terra/high launch, and restored the
prior runtime flags. Other worlds and successful Luna sessions were not changed.
The fallback must produce an actual required Oracle verdict, retain the honest
post-landing chronology, and clean its own audit checkout only after settlement.

## Preflight native continuation and announce ownership first

A verified usable native session does not prove that its frozen working
directory still exists. After land removed the source checkout, pin coordinator
`ah23n` resumed the settled source coordinator as `pe45i`. The launch failed
before any provider started because its retained cwd was gone. It retained a
pending process handle and `no-terminal-evidence`; exact stop did not establish
settlement. Do not forge that evidence or apply `retry!` blindly: current
Harnesses rejects unsettled and request-bound in-place retries. Bounded Sol
diagnostic `17u1h`, task `zotlf`, owns read-only recovery/source-contract diagnosis.

Before native resume, check both the reported session eligibility and the actual
cwd, required checkout/ref and remaining owner resources. Restore an owned
disposable cwd only through a supported, evidenced recovery; otherwise use an
explicitly authorized fresh task/assignment in an available workspace. Preserve
the original failure record and separate its custody issue from source acceptance.

The parent was preparing its fallback while the pin coordinator attempted that
source recovery. The parent should have published exclusive handoff ownership
before dispatching. Record this as a coordination race, not a model-quality
failure. Once the parent took ownership, `ah23n` preserved the correct pin gate,
stopped further recovery attempts, and reported the concrete external blocker.

## Concurrent roles need distinct tracked targets

One active managed run owns a target. The parent's first Terra prompt explicitly
required its Oracle to use the coordinator's own `negj4` target; the runtime
correctly rejected that concurrent assignment. Terra read the request back,
preserved its healthy coordinator and audit checkout, and reported the conflict.
That was correct behavior under an over-specific parent instruction.

Create a real reviewer child task before dispatch. Dedicated target `fa2se` now
belongs to the required Oracle, while the Terra continuation owns `negj4`.
Context records the relationship; it does not replace `--target`. Do not drop the
target or stop the healthy coordinator to work around exclusivity. Keep each
task's current run pointer and the parent feature's primary ownership distinct.

Also distinguish direct and inherited alias resolution: `sol-high` selects
`sol`, which selects Pi. A preflight that requires every alias's immediate
parent to be `pi` incorrectly rejects this valid Sol/Pi route. Follow the selected
alias chain to the available concrete harness, then verify the published run.

## Accept actual outcomes and keep independent defects visible

Terra continuation `90onc` completed the corrected acceptance assignment. It
dispatched required Oracle `1uzjw` against dedicated task `fa2se`, retained the
owned audit checkout until settlement, and received explicit **ACCEPT FOR
CONTINUED ROLLOUT**, with no P1/P2, for exact `92b4363..6e8cf5b` and merged
`3dcf5f0`. The trees match `6d47a66`. It then cleaned its own audit resources and
closed only `fa2se` and `negj4`, leaving parent-owned tasks alone. This is an
accepted Terra fallback outcome after correcting the parent's target contract.

The parent accepted and closed `sl9o5`, the live-pilot task `ibudy`, and source
feature `jxvxu`. Merged source hash `5ed2b8f3ec824af6752b66057eed9129a9936befd923046415f77425b1b9d362`
matches the refined alias already proved live in all eight worlds. Pin delivery
feature `evzz8` is independently active: native Luna continuation `8djua` resumes
`ah23n` in its verified existing cwd with frozen Luna/max settings. Its first
source workers are `b2uk1` for Agents task `12vie` and `y8osn` for Notes task
`2biz1`, both tracked Sol/Pi, targeting the accepted merge `3dcf5f0`.

Sol diagnosis `17u1h` established that exact `process/malformed-launch` is a
typed pre-reservation rejection: Skein validates cwd before reserving custody,
and the still-running original Mill has no `pe45i/attempt-1` record. The existing
Harnesses crash-window marker nevertheless leaves the run unsettled. Current
CLI recovery cannot resolve it, and hand-supplying a custody fact would violate
the settlement contract. That old record remains untouched and visibly unresolved;
source acceptance does not pretend to settle it.

The narrow forward fix is now Harnesses feature `qlfu6`, epic `8qqa1`, assigned
to sole Sol/Pi writer `dgzpl` in its recorded isolated worktree. Only the exact
typed malformed-launch rejection may settle as launch failure; control loss and
generic/possibly post-reservation errors must remain ambiguous. The assignment
requires focused classification coverage and a disposable admitted missing-cwd
fixture proving the provider never starts and the run settles. It excludes
historical error-string repair, live reloads, shared service lifecycle actions,
and the identity epic's active branches. Parent coordinates the `execution.clj`
overlap and final review/landing; the implementation is not yet accepted.
