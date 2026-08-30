# Ralph

Ralph drives a Kanban epic through repeated headless agent runs. Each run starts a fresh agent, points it at the same epic, and keeps its transcript. The agent works one feature card per iteration through the registered `ralph-iterate` workflow.

## Run Ralph

Start a Weaver for the workspace that owns the epic, then build and run the declared bin:

```sh
mill bin build ralph
mill bin run ralph <epic-id>
mill bin run ralph --harness codex <epic-id>
```

`mill bin run` supplies `MILLSTRAND_WORKSPACE` for the selected Weaver. The target must be an active Kanban epic carrying the `ralph` label. Add the label only when its feature cards are prepared for unattended iteration:

```sh
strand kanban label add <epic-id> ralph
```

Read `strand prime ralph` before preparing an epic. Read `strand about ralph` for the boundary between Ralph, Kanban, and the consumer's landing policy.

Put steering, decisions, and new context on the epic or feature cards as notes. A later iteration resumes from those strands, not from an earlier agent's prompt.

### Dashboard and stops

Ralph opens a full-screen dashboard by default. It shows the epic and loop state, active feature work, the live agent log, and one row per iteration. Enter expands the selected row. `e` opens run details and `?` lists keys.

- `s` arms a soft stop. The current iteration finishes, then Ralph exits. Press it again to cancel.
- `x` asks for confirmation before killing the current agent process group and exiting.
- `ctrl-c`, `ctrl-d`, and `q` open the same stop prompt. None ends a live agent run on its own.

Pass `--headless` for plain streamed output. In that mode, the first interrupt arms a soft stop and the second kills the run.

Ralph stops by itself when the epic becomes inactive, after the consecutive harness-failure limit, at `--max-iterations`, or when the final non-empty line of an agent reply is `RALPH-STOP: <reason>`. Removing the `ralph` label stops the loop before its next model run.

### Flags and environment

`--harness` selects `claude` (the default) or `codex`. `--model` and `--effort` use the selected harness's vocabulary. Empty values use that harness's default. Codex accepts `luna-high`, `luna-low`, and `sol-low` aliases as well as a direct model id.

`--max-iterations` caps the run; `0` means unlimited and the default is `30`. `--failure-limit` stops after that many consecutive failed runs; the default is `3`. `--log-dir` selects the transcript directory, defaulting to `$TMPDIR/ralph/<epic>-<timestamp>`. `--workspace` selects a non-default workspace, and `--strand` selects the `strand` binary Ralph reads through.

Headless runs bypass harness permission prompts by default because they cannot answer them. Pass `--skip-permissions=false` to keep the prompts. `--full-auth` appends an explicit operator authority grant for rebuilding and restarting mill/Weaver CLIs and bumping sibling spools. Use it only when that authority is intended.

`--poll` controls board refreshes (default `10s`) and `--pause` is the breather between iterations (default `3s`). Append `--` after the epic id to pass extra arguments to `claude` or `codex exec`.

`RALPH_HARNESS`, `RALPH_MODEL`, `RALPH_EFFORT`, `RALPH_MAX_ITERATIONS`, `RALPH_SKIP_PERMISSIONS`, `RALPH_LOG_DIR`, and `MILLSTRAND_WORKSPACE` provide the matching defaults. An invalid environment value is an error.

Every iteration writes its raw stream to `<log-dir>/iter-<n>.jsonl` and stderr to `<log-dir>/iter-<n>.stderr`.

### Agent contract and exit codes

The generated prompt starts `ralph-iterate`. In one iteration, the agent orients from live state, claims one feature, works its tasks, validates the slice, hands it to the consumer's landing policy, and closes the epic only when no feature cards remain.

Only the final non-empty agent-output line can trigger `RALPH-STOP:`. A marker without a reason is malformed and does not silently stop the loop.

Ralph only reads the board between iterations. Agents claim, update, and close Kanban work.

It exits with `0` after a clean or soft stop, `1` for harness failures, failed gates, exhausted iterations, or unexpected state, `2` for invalid invocation or environment, `3` for an agent emergency brake, and `130` after a hard stop.

## Development

The complete Go module, deterministic tests, and tracked launcher live in this spool root. The `ralph` bin is declared by `ct.spools.codethread.ralph`. From a live Weaver, `mill bin list` discovers it, `mill bin build ralph` compiles `bin/ralph.bin`, and `mill bin run ralph --help` runs the compiled tool with the trailing arguments unchanged. Running it without a build prints the build command and exits non-zero.

The `ralph-iterate` workflow uses Workflow's define-and-select entry form, so activating this root publishes it under the root's owner partition.

For local development, run `clojure -M:test` for the Clojure workflow publication test and run `go test ./...`, `go vet ./...`, and `go build .` from this directory. The root `make quality` target runs those checks as part of Codethread's quality gate.

Try the demo world with `MILLSTRAND_SOURCE_ROOT=/path/to/skein-src spools/ralph/demo-world.sh`.

## Layout

- `main.go` — flags, environment defaults, harness and strand-binary resolution, the opening epic gate. Nothing here knows how a run is rendered.
- `internal/board` — every read of live state, through the `strand` binary's JSON. `Gate` is the refusal boundary: not an epic, no `ralph` label, unreadable payload. `Snapshot` adds the epic's feature cards, detailing only the ones under active work. The client requests JSON error envelopes from `strand`, exposes failures as `CommandError`, and reissues one identical read only when the structured code is `weaver/restarted`; mutation-capable calls do not use that read helper.
- `internal/harness` — one `Harness` interface with `Claude` and `Codex` behind it: argv, stream decoding into a common `Event`, and where the run's final message comes from. The prompt addendum and the `RALPH-STOP` brake parser live here too.
- `internal/loop` — the engine. It owns iteration control, the stop reasons and exit codes, and the child process group a hard stop kills. It emits typed messages on a channel and never renders anything.
- `internal/ui` — the Bubble Tea dashboard and the plain headless renderer, both consuming the engine's message channel.

## Dashboard layout

The dashboard has three selectable panes: board, agent log, and iterations. The preview is always visible but never receives focus. It shows the selected row's detail, which is the same content that Enter opens in the scrollable detail view.

At 120 columns and wider, the preview occupies the full-height right column. Below 120 columns it moves below the three selectable panes. The board and iteration panes grow to fit their rows, up to about a third of the available height. The agent log fills the rows between them.

## Things worth knowing before you change it

The engine is the only place that decides when a loop ends. If you find yourself adding a stop condition to the UI, add it to `loop.Outcome` instead and let the UI report it.

Malformed or unsupported harness JSONL records stay in the transcript and produce a visible warning while the iteration continues. Transcript open, write, or close failures still fail the iteration, so Ralph never reports success without its stream evidence.

Children are started with `Setpgid` and a hard stop signals the whole group. Killing the leader alone leaves the agent's own tool processes running.

`ctrl-c`, `ctrl-d` and `q` must never end a live run on their own. `tea.WithoutSignalHandler()` is what stops Bubble Tea quitting on SIGINT; the tests in `internal/ui` assert that each of those keys produces no command and raises the stop prompt. Treat that as a contract, not a preference.

The log pane is a view onto one iteration's events, not a running tail of everything: the model keeps a log per iteration number and `showLog` swaps which one the pane holds. `logFollow` is what decides whether a new iteration takes the pane over, and it tracks the iterations pane's cursor sitting on the newest row.

The panes are hand-rolled cursor lists rather than `bubbles/list`, which brings filtering and pagination chrome that fights a live tail. A pane tails while its cursor is at the bottom and stops as soon as you scroll up.

Tests use a disposable fake `strand` script and a fake agent script, and drive the engine off its own message channel rather than sleeping. Board tests exercise the subprocess error boundary, the planned-replacement read reissue, the shared timeout, cancellation, and the no-retry mutation path. If you need a new loop behaviour covered, add a `newWorld` fixture in `internal/loop/loop_test.go` — do not reach for a timer.

The restart-aware client and repository tooling pin Millstrand `bbca5638bce72ad6a00b2ca916cabcfe99107828`. Build the `mill` and `strand` tooling from that exact checkout when running a real disposable replacement-world exercise. The normal Ralph gate remains:

```sh
cd spools/ralph && go test ./...
cd ../.. && make quality
```
