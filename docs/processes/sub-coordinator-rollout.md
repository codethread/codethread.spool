# Sub-coordinator alias rollout

The shared `sub-coordinator` seat is a bounded coordination role, not another
writer. Its alias-provided system guidance is the complete operational runbook.
It initially resolves through Pi to `openai-codex/gpt-5.6-luna` at explicit
`max` effort. Existing `coordinator`, Sol worker, and reviewer aliases are not
changed. The runbook also makes the global Mill user-controlled: an agent must
never stop or restart it and must propagate that rule to every child. Deprecated
`agent-harness.spool` workspaces, source, and APIs are read-only and must never
be used; active harness work belongs in `harnesses.spool`.

A process-local `seat/sub-coordinator-terra` flag selects the authorized
fallback: Pi with `openai-codex/gpt-5.6-terra` at explicit `high` effort and the
same runbook. Use that fallback only after repeated, recorded coordination
mistakes persist under clear corrective guidance. Provider/runtime failures,
timeouts, and one slow response are not model-quality evidence.

## Additive registration in a running world

`ct.spools.harnesses/register-alias!` changes one runtime-local registry entry
immediately. It does not refresh modules, alter flags, mutate existing runs, or
restart the Weaver. The checked-in startup module registers the alias durably on
a later ordinary activation. Until consumers update their Codethread pin, the
candidate namespace can be loaded from a reviewed checkout and its narrow
`register!` function called through the supported live Weaver nREPL.

Parent coordinator `x4y0z`, through pilot task `irfb7`, owns authorization and
execution of this recipe. Run it only at a selected handoff boundary; the
implementation worker must not mutate shared running worlds. The separate
one-line repository-instruction rollout is owned by feature `7qxp9` and must not
be duplicated here.

```nu
let coord_ws = "/absolute/path/to/canonical/.millstrand"
let candidate = "/absolute/path/to/reviewed/codethread/spools/config/src/ct/spools/codethread/sub_coordinator.clj"

let registry_before = (^strand --workspace $coord_ws agent list --full | from json)
if (($registry_before | where name == "sub-coordinator" | length) != 0) {
  error make {msg: "sub-coordinator is already registered; refusing to replace it"}
}

let active_ids = (
  ^strand --workspace $coord_ws agent runs --active
  | from json
  | get id
)
let runs_before = (
  $active_ids
  | each {|run_id| ^strand --workspace $coord_ws show $run_id | from json }
)

let source_literal = ($candidate | to json)
let registration_template = r#'
(do
  (load-file __SOURCE__)
  (let [runtime ((requiring-resolve 'millstrand.api.current.alpha/runtime))
        register! (requiring-resolve
                   'ct.spools.codethread.sub-coordinator/register!)]
    (register! runtime)))
'#
let registration = (
  $registration_template | str replace __SOURCE__ $source_literal
)

$registration | ^mill weaver repl --workspace $coord_ws --stdin

let registry_after = (^strand --workspace $coord_ws agent list --full | from json)
let runs_after = (
  $active_ids
  | each {|run_id| ^strand --workspace $coord_ws show $run_id | from json }
)

if $registry_before != ($registry_after | where name != "sub-coordinator") {
  error make {msg: "registration changed an existing alias"}
}

let frozen_keys = [
  "harness/after"
  "harness/alias"
  "harness/appended-system-prompts"
  "harness/context"
  "harness/cwd"
  "harness/effort"
  "harness/env"
  "harness/extra-argv"
  "harness/generated"
  "harness/harness"
  "harness/logical-id"
  "harness/mode"
  "harness/model"
  "harness/overrides"
  "harness/prompt"
  "harness/published"
  "harness/request-fingerprint"
  "harness/request-id"
  "harness/resumes"
  "harness/root-targets"
  "harness/run"
  "harness/session-id"
  "harness/target"
  "identity/id"
  "identity/prompt"
]

let frozen_before = (
  $runs_before
  | each {|run|
      {
        id: $run.id
        settings: (
          $run.attributes
          | transpose key value
          | where {|entry| $entry.key in $frozen_keys }
          | sort-by key
        )
      }
    }
)
let frozen_after = (
  $runs_after
  | each {|run|
      {
        id: $run.id
        settings: (
          $run.attributes
          | transpose key value
          | where {|entry| $entry.key in $frozen_keys }
          | sort-by key
        )
      }
    }
)

if $frozen_before != $frozen_after {
  error make {msg: "registration changed frozen settings of an existing run"}
}

^strand --workspace $coord_ws agent list --full
```

The top-level `strand show RUN_ID` calls are intentional. Batteries `show`
returns the full raw strand with the `attributes` map consumed by the frozen
settings proof. `strand agent show RUN_ID` returns a lifecycle summary and omits
that map; substituting it would break the proof.

The pre-registration guard fails before `register!` when `sub-coordinator`
already exists. Run it only at a safe handoff where the parent owns alias
registration and has excluded concurrent registrants. The catalog API replaces
by name and does not offer an atomic create-only operation, so this procedure
must not invent one or claim safety while another owner can race the guard.
Compare an existing descriptor to the reviewed candidate and escalate instead
of replacing it. Lifecycle fields can change naturally while runs execute, so
the proof compares their frozen launch settings rather than whole run records.

## Pilot and fallback

After review and disposable-world proof, assign one bounded coordination slice
at an ownership boundary. The parent records the selected canonical workspace,
execution worktree, target, stable request ID, run ID, initial registry, and
frozen settings for existing runs. Observe whether the alias follows its own
runbook for ownership, task-versus-feature dispatch, payload-safe prompts,
bounded waits, progress checks, rework, and Oracle direction.

Do not switch models for ordinary latency or infrastructure failure. If Luna
repeats concrete coordination mistakes after clear steering, record the errors
and corrections, stop the exact run, and await `agent-run-settled`. The parent
may then enable the fallback and verify its resolution:

```nu
^strand --workspace $coord_ws agent config set seat/sub-coordinator-terra true
^strand --workspace $coord_ws agent list --full
```

Start a fresh `sub-coordinator` assignment or targeted run with a new stable
request ID. Do not use native resume for the switch: resume retains the frozen
Luna model and settings. The Terra candidate receives the same alias runbook.

Roll out to another running world only at its own safe handoff after the first
pilot is accepted. Preserve existing owners, run pointers, settings, dirty
files, workflow gates, and FIFO position. Durable availability still requires a
reviewed Codethread pin and the repository's normal coordinated activation;
live registration is additive staging, not a substitute for pin rollout.
