(ns ct.spools.codethread.ralph
  "The one-card-per-iteration Ralph workflow (family \"ralph\").

  Ralph is the coordinator of its epic: each run orients from live kanban
  state, claims exactly one feature, drives that feature through its validated
  slice and stops at a judgment point that closes the epic only when every
  feature has a recorded done outcome. The Go binary supplies the polling loop;
  this workflow owns the work discipline inside one iteration. Durable Kanban
  claims remain the ownership authority; task notes carry consumer-owned handoff
  evidence."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [millstrand.api.format.alpha :as format-alpha]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.millstrand.alpha :as millstrand]
            [millhouse.spools.workflow :as workflow]))

(lifecycle/defresource completion-guard
  "Keep Ralph's checked receipt and epic closure on one transaction boundary."
  {:open 'ct.spools.codethread.ralph.completion/open-completion-guard!
   :close 'ct.spools.codethread.ralph.completion/close-completion-guard!})
(lifecycle/use-resource! completion-guard)

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
     card completion work. The epic closes only after every direct feature is
     closed with outcome done.
     An empty runnable frontier is not completion evidence. Keep decisions and
     handover context on the epic, feature, and doing-task notes because each
     iteration starts with a fresh agent.

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

#_{:clj-kondo/ignore [:redefined-var]}
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
(s/def ::feature non-blank-string?)
(s/def ::ralph-iterate-params (s/keys :req-un [::epic]))
(s/def ::selected-feature (s/keys :req-un [::feature]))
(s/def ::feature-params (s/keys :req-un [::epic ::feature]))

(workflow/defworkflow! ralph-finish-epic
  "Verify all feature outcomes and record Ralph epic completion."
  {:entrypoints #{:continue} :param-spec ::ralph-iterate-params}
  (workflow/workflow
   "Check epic completion"
   (workflow/gate
    :finish-epic "Check every feature before closing the epic" :code
    :attributes
    {"code/fn" "ct.spools.codethread.ralph.completion/finish-epic!"
     "code/params" (fn [{:keys [epic]}] {:epic epic})}
    (format-alpha/prose
     "
       Await this checked completion gate. It reads all direct feature children,
       requires each to be closed with outcome done, and records the child
       snapshot as ralph/completion on the epic. It never closes children.

       If it fails, leave the epic open and report the unfinished or unaccepted
       children. Do not bypass it with kanban finish or manual gate completion.
       Reconcile with the landing owner before explicitly retrying the gate.
     " {}))))

(defn- epic-judgment [dependencies]
  (workflow/checkpoint
   :epic-judgment "Close the epic only after accepted feature outcomes"
   :depends-on dependencies
   :kind :agent
   :choices
   [{:key :close-epic :label "Check and close the epic"
     :next :ralph-finish-epic
     :description "All direct features are closed with outcome done; run the checked close boundary."}
    {:key :next-iteration :label "Leave the epic open"
     :description "More runnable work exists, or unfinished work needs landing, waiting, or blocker resolution."}]
   :attributes
   {"workflow/decision-point" "ralph-epic-judgment"
    "workflow/instruction"
    (fn [{:keys [epic]}]
      (format-alpha/prose
       "
         Read `strand kanban card {epic}` and its direct feature children,
         including closed children and their kanban/outcome. Separately read
         `strand ready --query kanban-epic-pending --param epic={epic}`.
         The ready query selects new work; it does not prove completion.

         Choose close-epic only when every feature is closed with outcome done.
         The choice starts a checked continuation, not a terminal assertion.
         Claimed, in-review, in-production, blocked, or other unfinished children
         prevent completion. Abandoned or unactioned outcomes need coordinator
         reconciliation rather than automatic acceptance.

         Otherwise record child states, the selected feature's landing handoff,
         and the next owner/action on the epic, then choose next-iteration. If
         runnable work exists, the Go loop can select it next. If none exists,
         leave the epic open and end the reply with `RALPH-STOP: <reason>` naming
         the landing wait or blocker. Relaunch after that owner resolves it;
         do not busy-loop or reclaim a feature already handed off.
       " {:epic epic}))}))

(workflow/defworkflow! ralph-judge-epic
  "Judge an epic with no runnable pending feature, without claiming new work."
  {:entrypoints #{:continue} :param-spec ::ralph-iterate-params}
  (workflow/workflow "Judge the epic's remaining work" (epic-judgment [])))

(workflow/defworkflow! ralph-work-feature
  "Claim one selected feature, validate its slice, and record a landing handoff."
  {:entrypoints #{:continue} :param-spec ::feature-params}
  (workflow/workflow
   (fn [{:keys [feature]}] (str "Ralph feature: " feature))
   (workflow/step
    :claim-feature "Claim exactly the selected feature" :self
    (fn [{:keys [epic feature]}]
      (format-alpha/prose
       "
         Re-read feature {feature} under epic {epic} and its current ownership.
         It must still be a ready pending feature. Claim it with your explicit
         owner/actor, branch, and absolute worktree; assignment is not ownership:

         ```text
         strand kanban claim {feature} --owner <owner> --by-identity <actor> --branch <branch> --worktree <absolute-path>
         ```

         Record the claim result on the feature before completing this step.
         If another owner claimed it, stop and report the conflict; do not reclaim
         it. Reporter and prior ownership history survive an explicit handoff.
         Do not claim a second feature in this iteration.
       " {:epic epic :feature feature})))
   (workflow/step
    :work-tasks "Work the claimed feature's ready tasks" :self
    :depends-on [:claim-feature]
    (fn [{:keys [feature]}]
      (format-alpha/prose
       "
         Read `strand kanban card {feature}` and its durable current-ownership
         projection, then `strand ready --query kanban-feature-work --param
         feature={feature}`. Drive one ready task at a time from its body and
         latest note. Task assignment is not another feature claim.

         Keep decisions, findings, and resume points in attributed task notes.
         Use the repository's supported delegation surface, keep sibling scopes
         disjoint, verify each implemented task, and close it only after its
         validation is green.
       " {:feature feature})))
   (workflow/step
    :slice-gates "Validate and commit the claimed feature slice" :self
    :depends-on [:work-tasks]
    (format-alpha/prose
     "
       Run focused cold tests for touched namespaces, then relevant blocking
       quality gates. Full suites use `flock -w 180 /tmp/millstrand-test.lock`
       with exactly one lock owner; do not wrap scripts that already own it.
       Fix failures in the claimed worktree and commit the validated slice.
       Record commands, results, and the exact commit on the feature.
     " {}))
   (workflow/step
    :finish-feature "Record the selected feature's landing handoff" :self
    :depends-on [:slice-gates]
    (fn [{:keys [epic feature]}]
      (format-alpha/prose
       "
         Read feature {feature}'s current ownership and latest notes before
         launching anything. Reuse an already accepted landing handoff rather
         than launching another one. Hand the committed, validated slice to the
         consumer-owned landing policy; Ralph does not own review or merge.

         Before completing, record an attributed note on feature {feature} and
         epic {epic}: selected feature, owner, branch, worktree, exact commit,
         validation evidence, landing run/PR or other durable consumer receipt,
         receiving owner, and next action. Record whether the handoff is accepted
         or still blocked. If blocked, leave this step open and report the cause.

         Do not mark the feature or epic done here, and do not claim it landed
         without the consumer's landing evidence. The next judgment must read
         these notes and live child states, not assume this handoff finished it.
       " {:epic epic :feature feature})))
   (epic-judgment [:finish-feature])))

(workflow/defworkflow! ralph-iterate
  "Orient on live epic state, then work one feature or judge an empty frontier."
  {:entrypoints #{:start}
   :param-spec ::ralph-iterate-params
   :defaults {}
   :example {:epic "epic-id"}
   :param-docs {:epic "Epic strand id whose feature cards this iteration drives."}}
  (workflow/workflow
   (fn [{:keys [epic]}] (str "Ralph iteration: " epic))
   {:attributes {"workflow/family" "ralph"
                 "ralph/epic" (fn [{:keys [epic]}] epic)}}
   (workflow/step
    :orient "Read the epic and runnable pending frontier" :self
    (fn [{:keys [epic]}]
      (format-alpha/prose
       "
         Read `strand kanban card {epic}`, its handoff notes and direct features,
         then `strand ready --query kanban-epic-pending --param epic={epic}`.
         Select at most one ready pending feature from that live frontier.
         Claimed, review, and blocked children may be absent from this query;
         an empty result does not mean the epic is complete.
       " {:epic epic})))
   (workflow/checkpoint
    :frontier "Work one runnable feature or judge the remaining children"
    :depends-on [:orient]
    :kind :agent
    :choices
    [{:key :work-feature :label "Work one ready pending feature"
      :next :ralph-work-feature
      :input {:spec ::selected-feature
              :doc "The selected live ready pending feature id."}}
     {:key :no-runnable :label "No runnable pending feature"
      :next :ralph-judge-epic}]
    :attributes
    {"workflow/instruction"
     (format-alpha/prose
      "
        Choose work-feature with the selected feature id, or no-runnable to judge
        all children without claiming. Never infer completion from an empty
        ready frontier.
      " {})})))
