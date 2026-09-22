(ns me.auto-run-workflows
  "Repository delivery contract for automatically assigned Codethread features."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ct.spools.codethread.auto-run-land :as autonomous]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.format.alpha :as format]))

(s/def ::text (s/and string? (complement str/blank?)))
(s/def ::card ::text)
(s/def ::feature ::text)
(s/def ::branch ::text)
(s/def ::worktree ::text)
(s/def ::params (s/keys :req-un [::card ::feature ::branch ::worktree]))

(defn- failure-instruction [autonomous?]
  (if autonomous?
    (fn [{:keys [card]}]
      (autonomous/failure-policy card))
    "Await this executor-owned gate. Inspect failures, repair the cause, then explicitly clear gate/error to retry. Never manually assert a passing result."))

(defn- shell-gate [id title dependencies argv timeout autonomous?]
  (workflow/gate
   id title :shell
   :depends-on dependencies
   :attributes {"shell/argv" argv
                "shell/cwd" (fn [{:keys [worktree]}] worktree)
                "shell/timeout-secs" timeout}
   (failure-instruction autonomous?)))

(defn- delivery [autonomous?]
  (workflow/workflow
   (if autonomous? "Deliver and land automatically" "Prepare for human review")
   (workflow/step
    :implement "Implement and verify the assigned feature" :self
    (fn [{:keys [card]}]
      (format/prose
       "
         Read card {card}, its tasks, and AGENTS.md. Claim the supplied card with
         your identity, branch, worktree, and Harnesses run ID. Work only in the
         supplied worktree; do not create another one or delegate implementation.

         Implement the scoped outcome, add focused tests, and record evidence on
         the card's tasks. Use disposable workspaces for workspace-backed tests.
         Run repository quality while iterating, commit the verified change, then
         complete this step. The following steps publish that commit before the
         quality gate and own the review handoff.

         {failure-policy}
       " {:card card
          :failure-policy (if autonomous?
                            (autonomous/failure-policy card)
                            "")})))
   (workflow/step
    :publish "Publish the committed branch before quality" :self
    :depends-on [:implement]
    (fn [{:keys [card branch]}]
      (format/prose
       "
         Publish the committed branch before any workflow quality gate. Establish
         its upstream with:

         ```text
         git push --set-upstream origin {branch}
         ```

         Confirm `HEAD` is the published commit, then record the exact HEAD on
         card {card}. Do not change the worktree after publishing: the next gate
         validates this remote candidate.

         {failure-policy}
       " {:card card :branch branch
          :failure-policy (if autonomous?
                            (autonomous/failure-policy card)
                            "")})))
   (shell-gate :quality "Pass repository quality checks for published HEAD" [:publish]
               (fn [{:keys [branch]}]
                 ["sh" "scripts/verify-published-candidate.sh" branch])
               5400 autonomous?)
   (workflow/step
    :prepare-pr "Prepare the published change for review" :self
    :depends-on [:quality]
    (fn [{:keys [card branch]}]
      (format/prose
       "
         Create or update branch {branch}'s ready PR against main. Include
         nonempty Summary, Walkthrough, and Verification sections. The walkthrough
         must explain the affected boundaries and data flow with a useful Mermaid
         diagram. Record the PR URL and exact HEAD on card {card}.

         Complete this step only when the published, quality-checked branch has a
         PR ready for review. The next gates independently verify CI and move the
         card.

         {failure-policy}
       " {:card card :branch branch
          :failure-policy (if autonomous?
                            (autonomous/failure-policy card)
                            "")})))
   (shell-gate :ci "Wait for the PR checks" [:prepare-pr]
               (fn [{:keys [branch]}]
                 ["sh" "scripts/verify-pr-checks.sh"
                  "allow-empty" branch "120" "5"])
               2100 autonomous?)
   (workflow/gate
    :review-card "Move the verified feature into review" :code
    :depends-on [:ci]
    :attributes {"code/fn" "millhouse.spools.land.card-actions/review-card!"
                 "code/params" (fn [{:keys [card]}] {:card card})}
    (if autonomous?
      (failure-instruction true)
      "This is an automatic card transition after the review-package checks."))
   (if autonomous?
     (workflow/call :land #'autonomous/autonomous-land {}
                    :depends-on [:review-card]
                    :title "Review and hand off autonomous landing")
     (workflow/checkpoint
      :human-acceptance "Human review: return the passing PR and stop"
      :depends-on [:review-card]
      :kind :human
      :choices [{:key :reviewed :label "Human review recorded"}]
      :attributes
      {"workflow/instruction"
       (format/prose
        "
          Stop here and return the PR URL, walkthrough, screenshots or their
          applicability explanation, verification evidence and open questions.
          Do not choose this checkpoint, merge, start land, finish the card,
          remove the worktree, launch a finisher, or remain running to poll for
          the user.

          The user will review and decide what happens next. Generic landing
          instructions do not override this explicit stop boundary.
        " {})}))))

(workflow/defworkflow! auto-human-review
  "Prepare a passing, documented PR and stop for the user's full review."
  {:entrypoints #{:start} :param-spec ::params}
  (delivery false))

(workflow/defworkflow! auto-full-land
  "Implement and verify a change, then hand landing to an independent finisher."
  {:entrypoints #{:start} :param-spec ::params}
  (delivery true))
