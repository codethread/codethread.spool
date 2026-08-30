(ns ct.spools.codethread.ralph
  "The one-card-per-iteration Ralph workflow (family \"ralph\").

  Ralph is the coordinator of its epic: each run orients from live kanban
  state, claims exactly one feature, drives that feature through its validated
  slice and stops at a judgment point that closes the epic only when no feature
  cards remain. The Go binary supplies the polling loop; this workflow owns the
  work discipline inside one iteration. The doing-task note is the claim
  record used by the consumer-owned handoff."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [millstrand.api.format.alpha :as format-alpha]
            [millstrand.api.millstrand.alpha :as millstrand]
            [millhouse.spools.workflow :as workflow]))

(millstrand/defbin! ralph
  "Drive a Kanban epic through repeated headless agent runs."
  {:executable [:family "bin/ralph"]
   :build ["go" "build" "-o" "bin/ralph.bin" "."]})

(def ^:private ralph-arg-spec
  "Declared discovery surface for Ralph."
  {:op "ralph"
   :doc "Explain how to prepare and run a Ralph Kanban epic."
   :hook-class :read
   :deadline-class :standard})

(def ^:private ralph-meta
  "Ralph's Kanban preparation and ownership guidance."
  {:about
   (format-alpha/prose
    "
     Ralph is the repeated-run driver for one active Kanban epic carrying the
     `ralph` label. Its Go loop reads the epic between fresh headless agent
     runs. The `ralph-iterate` workflow owns one iteration's discipline:
     claim one feature, work and validate its tasks, then hand the committed
     slice to the consumer's landing policy.

     Ralph does not own landing. The consumer decides how review, merge, and
     card completion work. The epic closes only after its feature frontier is
     empty. Keep decisions and handover context on the epic, feature, and
     doing-task notes because each iteration starts with a fresh agent.

     Run `strand prime ralph` before preparing the epic. Build and start the
     loop through `mill bin build ralph` and `mill bin run ralph <epic-id>`.
     Remove the `ralph` label to stop new iterations after the current one.
     "
    {})
   :prime
   (format-alpha/prose
    "
     Prepare the whole epic before adding the `ralph` label.

     1. Create one active Kanban epic with a concrete body: outcome, scope,
        acceptance criteria, constraints, and links to the relevant design or
        source material.
     2. Add each independently landable slice as a feature under that epic.
        Give every feature a body that a fresh agent can act on without asking
        what the slice means.
     3. Decompose each feature into ordered task cards. Record task
        dependencies with `--depends-on`; Ralph chooses from the live ready
        frontier, so prose-only ordering is invisible to it.
     4. Make the consumer's landing policy and required validation discoverable
        from the cards or their source links. Ralph validates a slice and hands
        it off; it does not invent review or merge rules.
     5. Put steering, decisions, blockers, and resume points in immutable
        Kanban notes as the work changes. The doing-task's latest note is the
        next agent's first handover.

     When that graph is actionable, label only the epic:

     ```sh
     strand kanban label add <epic-id> ralph
     mill bin build ralph
     mill bin run ralph <epic-id>
     ```

     Do not use Ralph for an epic with unresolved refinement, missing task
     dependencies, or work that cannot be handed to the consumer's landing
     policy. Remove the label to prevent the next iteration from starting.
     "
    {})})

(millstrand/defop! ralph
  "Return the Ralph discovery entrypoint; use `strand about ralph` or `strand prime ralph`."
  {:arg-spec ralph-arg-spec
   :returns {:type :map
             :required {:operation :string
                        :next :string}}
   :about (:about ralph-meta)
   :prime (:prime ralph-meta)}
  [_]
  {:operation "ralph"
   :next "Run strand prime ralph before preparing an epic."})

(defn- non-blank-string?
  "Return true when value is a non-blank string."
  [value]
  (and (string? value) (not (str/blank? value))))

(s/def ::epic non-blank-string?)
(s/def ::ralph-iterate-params (s/keys :req-un [::epic]))
(workflow/defworkflow! ralph-iterate
  "Run one Ralph iteration for an epic (family \"ralph\").

  The iteration is deliberately one-card wide: orient from the epic's live
  frontier, claim exactly one feature, work its tasks with the repo's note and
  cold-gate discipline, and hand the validated slice to the consumer's own
  landing policy. The final judgment closes the epic only when its feature
  frontier is empty.
  Params: `epic` (required epic strand id)."
  {:entrypoints #{:start}
   :param-spec ::ralph-iterate-params
   :defaults {}
   :example {:epic "epic-id"}
   :param-docs {:epic "Epic strand id whose feature cards this iteration drives."}}
  (workflow/workflow
   (fn [{:keys [epic]}] (str "Ralph iteration: " epic))
   {:attributes {"workflow/family" "ralph"
                 "ralph/epic" (fn [{:keys [epic]}] epic)}}
   (workflow/step :orient
                  (fn [{:keys [epic]}] (str "Orient Ralph on epic " epic))
                  :self
                  :attributes {"workflow/action-ref" "ralph.orient"
                               "workflow/instruction"
                               (fn [{:keys [epic]}]
                                 (format-alpha/reflow
                                  (format
                                   "|Read `strand kanban card %s`, then run `strand ready --query
                                    |kanban-epic-pending --param epic=%s`. This live frontier is
                                    |the source of truth for the next feature card; do not choose
                                    |from memory or from a stale prompt. After choosing a feature,
                                    |run `strand ready --query kanban-feature-work --param
                                   |feature=<feature-id>` to see its direct task frontier."
                                   epic epic)))})
   (workflow/step :claim-feature
                  (fn [{:keys [epic]}] (str "Claim exactly one feature under " epic))
                  :self
                  :depends-on [:orient]
                  :attributes {"workflow/action-ref" "ralph.claim-feature"
                               "workflow/instruction"
                               (fn [_]
                                 (format-alpha/reflow
                                  "|Choose exactly ONE ready feature card from the epic
                                   |frontier. Claim it with your owner, branch, and worktree:
                                   |`strand kanban claim <feature-id> --owner <name> --branch
                                   |<branch> --worktree <absolute-path>`. Record the chosen
                                   |feature id, branch, and absolute worktree in the
                                   |doing-task note, including the claim command result as
                                   |evidence before completing this step.
                                   |Do not claim a second feature in this iteration."))})
   (workflow/step :work-tasks
                  (fn [_] "Work the claimed feature's ready tasks")
                  :self
                  :depends-on [:claim-feature]
                  :attributes {"workflow/action-ref" "ralph.work-tasks"
                               "workflow/instruction"
                               (format-alpha/reflow
                                "|Drive the claimed feature one ready task at a time. Read the
                                 |doing-task body and latest note before acting; append decisions,
                                 |findings, and resume points with `strand kanban note` as you go.
                                 |Use the repo's registered workflow or agent surface for real
                                 |delegation, keep sibling file scopes disjoint, verify each
                                 |implemented task yourself, and close it only after its
                                 |validation is green.")})
   (workflow/step :slice-gates
                  (fn [_] "Run cold validation for the claimed feature slice")
                  :self
                  :depends-on [:work-tasks]
                  :attributes {"workflow/action-ref" "ralph.slice-gates"
                               "workflow/instruction"
                               (format-alpha/reflow
                                "|Run the focused cold test command for every namespace touched
                                 |by the slice (`clojure -M:test <ns...>`), then the relevant
                                 |blocking quality gates. Warm output is not a Done-when gate.
                                 |Fix failures in the claimed worktree, commit the validated
                                 |slice, and re-run the checks before completing this step.")})
   (workflow/step :finish-feature
                  (fn [_] "Hand off the validated feature slice")
                  :self
                  :depends-on [:slice-gates]
                  :attributes {"workflow/action-ref" "ralph.hand-off"
                               "workflow/instruction"
                               (format-alpha/reflow
                                "|Use the feature id, branch, and worktree recorded in the
                                 |doing-task body and latest note as the claim evidence. Hand
                                 |the committed, validated slice and its evidence to the
                                 |consumer-owned landing policy. Do not mark the feature card
                                 |or epic done here, and do not claim it landed without the
                                 |consumer's landing evidence.")})
   (workflow/checkpoint :epic-judgment
                        (fn [{:keys [epic]}]
                          (str "Close " epic " only if no feature cards remain"))
                        :depends-on [:finish-feature]
                        :kind :agent
                        :choices [{:key :close-epic
                                   :label "Close the epic"
                                   :description
                                   (format-alpha/reflow
                                    "|Re-run `strand ready --query kanban-epic-pending --param
                                     |epic=<epic-id>`. Choose this only when the frontier is
                                     |empty: then `strand kanban finish <epic-id> --outcome done`
                                     |and record the evidence in the epic note.")}
                                  {:key :next-iteration
                                   :label "Leave the epic open"
                                   :description
                                   (format-alpha/reflow
                                    "|The epic still has active feature cards. Record the live
                                     |frontier and resume point on the epic, leave it open, and
                                     |end this one-card iteration so the Go loop can start the
                                    |next `ralph-iterate` run.")}]
                        :attributes {"workflow/decision-point" "ralph-epic-judgment"})))
