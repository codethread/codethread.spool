# Automatic card pickup

The opt-in dispatcher in `ct.spools.codethread.auto-run` starts one Harnesses
assignment per ready feature. The worker drives a repository-owned Millhouse
workflow. There is no coordinator agent, per-lane trigger language, or automatic
worker retry. Repository policy decides where delivery stops.

## Card contract

A card is eligible when it is an active pending feature, has the `auto-run`
label, has no current explicit ownership claim or previous dispatch receipt, and
is graph-ready. Current ownership comes from Kanban's durable latest-claim
projection, not the legacy scalar `owner` attribute. An unresolved latest owner
still excludes the card; reporter, note author, workflow actor, worker, and other
historical participation alone do not. Existing `depends-on` edges remain
authoritative. Epics and refinement cards never run. Pending features are ordered
by priority, creation time, then ID.

Optional card overrides are:

| Attribute | Meaning |
| --- | --- |
| `auto-run/seat` | Registered Harnesses alias, e.g. `sol` or `astra`. |
| `auto-run/effort` | Provider effort, e.g. `high` or `low`. |
| `auto-run/workflow` | Repo-allowed registered workflow with a `start` entrypoint. |

Omitted values inherit repository configuration. Malformed values, unavailable
seats, and disallowed workflows produce a visible card error rather than a
fallback. Effort is passed through the existing Harnesses overlay contract.

The dispatcher writes `auto-run/status` (`preparing`, `assigned`, or `error`),
`auto-run/request-id`, `auto-run/run-id`, `auto-run/workflow-run-id`,
`auto-run/worktree`, `auto-run/branch`, and `auto-run/error`. The accepted
selection is recorded as `auto-run/effective-seat`, `auto-run/effective-effort`,
and `auto-run/effective-workflow`. These are receipts, not agent activity:
`assigned` remains after a worker exits. Inspect the linked Harnesses run for
its lifecycle, the workflow for its frontier, and Kanban for delivery state.

## Repository activation

Depend on `codethread/config` and activate the shared bootstrap as usual. Publish
your workflows first. A repo-owned module then selects the CLI and owns a
lifecycle resource:

```clojure
(ns acme.auto-run
  (:require [ct.spools.codethread.auto-run :as auto-run]
            [ct.spools.codethread.auto-run-worktree]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.millstrand.alpha :as millstrand]))

(millstrand/use-op! auto-run/auto-run)

(defn open! [{:keys [runtime]}]
  (auto-run/configure!
   runtime
   {:repo "/canonical/repository"
    :seat "luna"
    :effort "high"
    :workflow "prepare-for-review"
    :workflows #{"prepare-for-review" "deliver-autonomously"}
    :prepare 'ct.spools.codethread.auto-run-worktree/prepare!
    :start-params 'acme.auto-run/start-params!
    :enabled? true
    :max-running 2
    :interval-ms 15000}))

(defn close! [{:keys [runtime]}]
  (auto-run/stop! runtime))

(lifecycle/defresource! dispatcher
  "Own automatic card admission for this repository."
  {:open 'acme.auto-run/open!
   :close 'acme.auto-run/close!})
```

A repository callback can project and validate its own card attributes without
making them shared dispatcher policy:

```clojure
(ns acme.auto-run
  (:require [millstrand.api.spool.alpha :refer [attr-get fail!]]))

(defn start-params! [_rt {:keys [card settings prepared]}]
  (let [review-scope (attr-get card :acme/review-scope)]
    (when-not (contains? #{"small" "full"} review-scope)
      (fail! "Invalid Acme review scope" {:card (:id card) :value review-scope}))
    {:review-scope review-scope
     :selected-workflow (:workflow settings)
     :prepared-branch (:branch prepared)}))
```

Register this file with `runtime/module!`, after the repo workflow module and
Harnesses. Definitions must be available before configuration validation. No
bootstrap activates dispatch automatically.

The compatible producer set is Millstrand
`8e220eab7de2fabe7880c6a4c71de6cd903c34bb`, Millhouse
`bd96f5357a335bd17cd22042da1be5bd2200f807`, and Harnesses
`6b5ad39d8711a033dc7f33fd52c78901393ea44e`. The accepted Millhouse identity
feature landed at `f17ad387b2825887b736cab597b33af84cff13cb`; the pinned
`bd96f5357a335bd17cd22042da1be5bd2200f807` is its reviewed descendant and
matches Harnesses' direct Workflow/Kanban coordinates. Keep independently
published tools.deps roots on the compatible commits. Activate Identity and
Workflow, then Kanban, then the ownership-aware Harnesses surface; register the
agent executor only after consumer aliases, workflows, and policy modules.

Source acceptance or a checked-in pin does not change a running Weaver.
Source-only module edits use normal refresh; changing a dependency pin requires
the supported Weaver restart with operator approval. No classloader or runtime
mutation bypass is supported.

The example uses a canonical absolute repository path for clarity. A portable
repo module should derive it from the selected runtime workspace metadata, not
from the agent's cwd or a worktree checkout. Configuring `enabled? false` prevents
new admission without stopping accepted work.

The optional wktree recipe creates `auto/<card-id>` using repository policy and
runs the returned post-create script. It refuses blocked allocation and the
canonical checkout. A repository may replace it with a qualified preparation
function accepting `[runtime {:repo ... :card ...}]` and returning
`{:cwd ... :branch ...}`. It must not claim the card. Preparation is trusted,
synchronous code: keep it bounded and move long build/test work into workflow
gates. A failure retains resources for inspection; nothing is silently deleted.

`:start-params` is optional. Its qualified callback receives `[runtime request]`
after preparation and the first intervening-edit check, where `request` is
`{:repo ... :card <live-card-map> :settings {:seat ... :effort ... :workflow ...}
:prepared {:cwd ... :branch ...}}`. The callback's `:card` is the full live
card map. It returns a map of additional workflow start parameters. The
callback owns parsing and validation of card attributes; the dispatcher does
not interpret repository policy. The dispatcher rechecks admission after the
callback returns. It must return a map and may not return
`:card` (the ID string), `:feature`, `:worktree`, `:branch`, `:seat`, or
`:effort`: conflicts fail the card with `auto-run/status=error` before either a
workflow or Harnesses assignment is created.

## Delivery workflows

The dispatcher starts the selected workflow with `card` (the card ID string),
`feature` (title), `branch`, `worktree`, `seat`, and `effort` parameters, plus
the declared `:start-params` output when configured, then assigns the worker in
that worktree.
Shared fields are reserved and are never overwritten by repository output.
Start with an ordinary worker-owned implementation step, not a second
worker-launching agent gate. The worker receives the exact workflow run ID and
must drive it rather than invent another process.

The shared assignment policy covers following that exact run, evidence-backed
completion, human checkpoints, and the [card signalling contract](#cause-and-current-attention).
Agents record those signals through ordinary Strand attributes, labels and
attributed notes; there is no separate signalling operation. Repository workflow
instructions own the actions, required outputs, delegation and completion rules.
The shared instructions require no pull request, landing workflow or particular
review package.

A review workflow can require implementation, browser evidence, PR creation,
automated quality/CI checks, a review package, and a human checkpoint. The worker
returns at that checkpoint, leaving the card and PR open. An autonomous workflow
can explicitly instruct the worker to drive shared `land`. The chosen workflow
is authoritative over generic card instructions to merge. Human checkpoints are
workflow guidance, not an access-control sandbox: trusted agents still have the
repository's normal command authority.

Useful review evidence includes test instructions, a scoped C4 walkthrough,
UI screenshots when appropriate, and unresolved questions. Validate mechanical
requirements with code/shell gates rather than accepting a successful agent exit
as evidence that a PR passes. Workflow owns these requirements; the dispatcher
has no built-in PR, screenshot, or landing policy.

## Operation and failure policy

```text
strand auto-run status
strand auto-run scan --by-identity YOUR_IDENTITY
strand agent show RUN_ID --by-identity YOUR_IDENTITY
strand workflow ready WORKFLOW_RUN_ID
```

A manual scan forwards the supplied friendly identity as best-effort assignment
caller attribution. It does not resolve, invent, or convert that string into
ownership. Scheduler-driven scans have no caller and must not fabricate one.

Normal operation uses durable scheduler wakes, not an agent polling loop. A
runtime-owned lock serializes scans and configuration; stale wake generations
cannot admit work. The concurrency limit counts unsettled dispatcher assignments,
not cards waiting for human acceptance. Existing active target writers are
excluded, and Harnesses enforces target exclusivity at assignment publication.

Admission begins when `auto-run/status=preparing` is recorded, before filesystem
preparation. Lane/label edits are not cancellation of admitted work. The dispatcher
checks for intervening edits before pouring the workflow, but that check is not
atomic with Harnesses assignment; a late edit can coexist with an accepted run.
The worker must still claim the pending card before doing work. To withdraw work,
disable admission, inspect its receipt, and stop the exact accepted run if needed.
Do not use board edits as a substitute for the Harnesses stop operation.

Every card is admitted once. Moving lanes, changing settings, removing/readding
the label, or restarting Weaver does not rearm it. A scan adopts an accepted run
whose receipt was interrupted. Interrupted preparation without an accepted run
becomes an error requiring operator inspection; the dispatcher never repeats a
possibly partial filesystem side effect.

For an assigned worker failure or a requested revision, use explicit Harnesses
continuation after settlement and name the existing workflow run in the new
instructions. For a preparation/configuration failure, inspect the retained
worktree and workflow, correct the cause, then explicitly arrange the assignment.
Do not erase receipts to simulate a retry. Repository shutdown/disable stops
admission only; use the normal exact-run stop API to stop an accepted worker.

## Consumer state reference

Auto-run composes existing Kanban, Workflow, and Harnesses contracts. Consumers
must not treat one stored attribute as a delivery state machine.

### Labels and Kanban lifecycle

| Stored field | Allowed values | Meaning |
| --- | --- | --- |
| `kanban.label/auto-run` | `"true"` or absent | Opts a card into consideration. It does not prove eligibility or trigger a retry. |
| `kanban.label/auto-run-failure` | `"true"` or absent | Independent cause signal, added only with positive execution/validation failure evidence. Never proof or recovery authority. |
| `kanban.label/needs-decision` | `"true"` or absent | Independent current attention signal; requires both decision fields below. |
| top-level `state` | `active`, `closed` | Graph lifecycle. Closed alone does not prove delivery bookkeeping finished. |
| `kanban/lane` | `refinement`, `pending`, `claimed`, `in_review`, `in_production` | Kanban lifecycle projection. Only `pending` is eligible for first admission. |
| completion outcome | consumer-defined recorded outcome | Kanban's recorded close outcome; interpret it with linked delivery evidence. |

First admission additionally requires an active feature/card marker, graph
readiness, no current ownership claim, no previous request or status receipt,
available receipt-based capacity, and allowed seat/workflow settings. The latest
Kanban ownership claim is authoritative; legacy `owner`, reporter, and actor
attributes are not.

### Cause and current attention

The canonical vocabulary is `auto-run-failure` and `needs-decision`. They are
independent labels, not delivery dispositions, and may coexist.

| Current decision field | Allowed values | Meaning |
| --- | --- | --- |
| `auto-run/decision-question` | nonblank string | The exact question requiring an answer. Preserve its wording. |
| `auto-run/decision-role` | `human`, `operator` | Responsibility for answering, **not** an actor identity. |

Both fields are required exactly while `needs-decision` is present. Remove both
when resolving that attention signal. Malformed labels, blank questions, other
roles, missing required fields and orphaned decision fields fail explanation
visibly rather than being guessed or silently repaired.

Before adding `auto-run-failure`, positively identify the failed operation,
concrete attempt and relevant current delivery/workflow. Append an attributed
note with the delivery/run/step, operation or command, attempt/custody reference,
evidence, retained resources and any merge reservation. Labels alone never
establish failure evidence or grant recovery authority.

For a design, scope or authority question, record `needs-decision` and the two
fields, plus an attributed decision note containing the question and context.
Preserve who raised it and who answered it through existing note attribution;
do not use the responsibility role as an identity. Resolving a decision appends
the answer and actor in a note and removes only that label and its two fields.
Evidence-backed failure resolution removes only the specifically resolved
failure signal. Preserve notes, actual Harnesses settlement, Workflow history,
retained dispatcher errors and unrelated attention in either case.

Consumer examples (not additional types):

| Example | Signals | Diagnostic interpretation |
| --- | --- | --- |
| Design decision only | `needs-decision` with question and role | Waiting if no active/failing producer evidence; not a failure. |
| Genuine execution/validation failure | `auto-run-failure` with attributed evidence | Failed when current producer evidence corroborates it. |
| Failure plus decision | Both labels and decision fields | Failed delivery and independent decision attention. |
| Healthy dependency/executor wait or ordinary human checkpoint | Neither required | Ordinary waiting; checkpoint already identifies the human. |
| Unknown evidence, including an uncorroborated failure label | No inferred failure | Unknown absent other evidence; explicit evidence gap, no recovery permission. |

These source rules apply to future guidance and records after acceptance. They
do not rewrite frozen assignments/workflows, clean existing labels, activate
policy, reset gates or launch recovery. Millhouse source alignment is separately
owned by `/Users/ct/dev/projects/millhouse.spool/.millstrand :: 6kzu7`; recovery
consumer `hqqrk` and rollout `s7bec` remain blocked until both source slices are
accepted. This contract neither expands recovery scope nor changes its budgets.

### Dispatcher attributes

| Attribute | Values or shape | Authority and meaning |
| --- | --- | --- |
| `auto-run/seat` | registered alias string | Optional requested override. |
| `auto-run/effort` | provider effort string | Optional requested override. |
| `auto-run/workflow` | allowed workflow name | Optional requested override. |
| `auto-run/status` | `preparing`, `assigned`, `error`, or absent | Dispatcher receipt phase. Absent is not by itself proof of eligibility. |
| `auto-run/request-id` | string | Immutable assignment publication key. |
| `auto-run/run-id` | Harnesses run ID | Original accepted run receipt; retain it when continuations or finishers exist. |
| `auto-run/workflow-run-id` | Workflow run ID | Selected delivery run. |
| `auto-run/worktree` | absolute path string | Preparation receipt, not a live filesystem check. |
| `auto-run/branch` | branch string | Preparation receipt, not a freshly verified Git head. |
| `auto-run/error` | error text or absent | Retained dispatcher failure. It may be historical and is not automatically a current blocker. |
| `auto-run/effective-seat` | concrete accepted alias | Selection frozen at admission. |
| `auto-run/effective-effort` | concrete accepted effort | Selection frozen at admission. |
| `auto-run/effective-workflow` | concrete accepted workflow | Selection frozen at admission. |

The dispatcher writes these fields. `assigned` persists after process exit: it
does not mean running, successfully settled, or delivered. Worker/finisher
receipts used by a repository workflow or Land preset belong to that workflow;
they are not universal auto-run card attributes.

### Linked and derived state

Harnesses is authoritative for publication, logical lineage, target, identity,
provider/seat/model/effort, attempt, invocation, lifecycle, settlement, and
`resumes`/`continues` edges. Only published children become accepted lineage
heads. An unpublished child remains evidence and cannot make `assigned` mean
worker-running. A stale predecessor's generic resume eligibility is not
permission to bypass the accepted-head guard.

Workflow is authoritative for retained roots, parallel frontiers, gate executor
provenance, outcomes, failures, and human checkpoints. Kanban remains
authoritative for card lifecycle and ownership. Optional repository adapters
may add recorded PR, CI, quality, or Land evidence. The generic library does not
require those systems. Codethread's preset chooses its own quality and delivery
workflows outside this reusable contract.

`auto-run explain` derives, but never stores:

- disposition: `waiting`, `active`, `failed`, `completed`, or `unknown`;
- phase: `admission`, `publication`, `implementation`, `validation`, `review`,
  `human-checkpoint`, `handoff`, `merge`, `cleanup`, or `bookkeeping`;
- evidence availability: `unsupported`, `absent`, `unknown`, or `present`;
- merge boundary: `pre-merge`, `post-merge`, or `unknown`.

These values are diagnostics, not recovery authority. Future recovery episode
fields are not part of the shipping contract until implemented.

## Read-only delivery explanation

```text
strand auto-run explain CARD_ID
```

The JSON schema is `codethread.auto-run.explain/v1`. It reports the workspace,
card, observation time, admission predicates and receipt-based capacity,
accepted agent lineages, exact Workflow frontier/history, optional Land
evidence, recorded external references, runtime status, and the next responsible
role. `signals` reports the validated canonical board representation. `cause`
reports the `auto-run-failure` boolean independently of current producer failure
`evidence` (accepted failed Harnesses heads and ready gates with recorded
`gate/error`). `evidence-status` is `present` with such evidence, otherwise
`unknown`; absence is not proof of success. Gate evidence includes the retained
attributes identifying its request, executor result and attempt when recorded.
`attention` reports `needs-decision` independently, with the exact `question`,
responsible `role`, recorded basis and a command to inspect attributed notes.
An explicit decision selects the next responsible role but never erases failure
evidence or changes agent settlement. Neither projection grants retry authority.
Historical errors are separate from current accepted-head failures.
Recorded PR/head/review/check references are labelled `recorded`; this command
does not poll GitHub, inspect processes, run recovery, or scan the dispatcher.

Land evidence is `unsupported` when no adapter was selected, `absent` when a
successful adapter read proves no link, `unknown` when the adapter cannot read,
and `present` only with actual references. Missing adapters never imply
pre-merge safety. A consumer selects an adapter with
`:evidence-adapters {:land 'qualified.namespace/read-land}` in `configure!`.
The function receives `[runtime {:card ... :workflow ... :agents ...}]`; it
returns a JSON-safe reference map when linked evidence is present or nil after a
successful read proving no link. Throwing preserves the specific unavailable
reason as `unknown`. The reusable collector never requires or activates Land.

Use the emitted `mill weaver status --workspace ... --json` command when
in-process runtime status does not expose the needed generation evidence.

A normal human-review flow therefore reads as: opt-in label → `preparing` →
`assigned` → published Harnesses worker → Workflow implementation/validation →
human checkpoint. A dispatcher error stops at `error`; an assigned worker
failure is instead visible on its accepted Harnesses head. Completion needs
positive delivery evidence in addition to a closed card; post-merge cleanup or
bookkeeping remains visible rather than becoming wholly done.

Useful read queries are:

```text
strand auto-run status
strand auto-run explain CARD_ID
strand agent runs --task CARD_ID
strand workflow ready WORKFLOW_RUN_ID
strand workflow history WORKFLOW_RUN_ID
```

Consult `strand help auto-run`, the schema/version in the explanation, and
`ct.spools.codethread.auto-run` for the executable contract.

Tests use disposable in-memory Weaver worlds and non-executing fake providers.
They cover admission, dependency readiness, seat/effort propagation, optional
repository workflow parameters, reserved-field conflicts, capacity, one-shot
behavior, failure visibility, interrupted receipt adoption, stale wake/disable
behavior, accepted continuation heads, unpublished skeletons, and optional Land
evidence without launching paid agents.
