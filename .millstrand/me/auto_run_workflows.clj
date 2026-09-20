(ns me.auto-run-workflows
  "Repository delivery contract for automatically assigned Codethread features."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [millhouse.spools.land.autonomous :as autonomous]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.format.alpha :as format]))

(s/def ::text (s/and string? (complement str/blank?)))
(s/def ::card ::text)
(s/def ::feature ::text)
(s/def ::branch ::text)
(s/def ::worktree ::text)
(s/def ::params (s/keys :req-un [::card ::feature ::branch ::worktree]))

(defn- shell-gate [id title dependencies argv timeout]
  (workflow/gate
   id title :shell
   :depends-on dependencies
   :attributes {"shell/argv" argv
                "shell/cwd" (fn [{:keys [worktree]}] worktree)
                "shell/timeout-secs" timeout}
   (fn [{:keys [card]}]
     (autonomous/failure-policy card))))

(workflow/defworkflow! auto-full-land
  "Implement and verify a change, then hand landing to an independent finisher."
  {:entrypoints #{:start} :param-spec ::params}
  (workflow/workflow
   "Deliver and land automatically"
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
         quality gate and own landing.

         {failure-policy}
       " {:card card :failure-policy (autonomous/failure-policy card)})))
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
          :failure-policy (autonomous/failure-policy card)})))
   (shell-gate :quality "Pass repository quality checks for published HEAD" [:publish]
               (fn [{:keys [branch]}]
                 ["sh" "scripts/verify-published-candidate.sh" branch])
               5400)
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
          :failure-policy (autonomous/failure-policy card)})))
   (shell-gate :ci "Wait for the PR checks" [:prepare-pr]
               (fn [{:keys [branch]}]
                 ["sh" "scripts/verify-pr-checks.sh"
                  "allow-empty" branch "120" "5"])
               2100)
   (workflow/gate
    :review-card "Move the verified feature into review" :code
    :depends-on [:ci]
    :attributes {"code/fn" "millhouse.spools.land.card-actions/review-card!"
                 "code/params" (fn [{:keys [card]}] {:card card})}
    (fn [{:keys [card]}]
      (autonomous/failure-policy card)))
   (workflow/call :land #'autonomous/autonomous-land {}
                  :depends-on [:review-card]
                  :title "Review and hand off autonomous landing")))
