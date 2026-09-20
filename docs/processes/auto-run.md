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

Tests use disposable in-memory Weaver worlds and non-executing fake providers.
They cover admission, dependency readiness, seat/effort propagation, optional
repository workflow parameters, reserved-field conflicts, capacity, one-shot
behavior, failure visibility, interrupted receipt adoption, and stale
wake/disable behavior without launching paid agents.
