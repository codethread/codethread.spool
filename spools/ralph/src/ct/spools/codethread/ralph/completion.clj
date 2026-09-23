(ns ct.spools.codethread.ralph.completion
  "Check Ralph's epic completion boundary without Kanban's finish cascade."
  (:require [millstrand.api.current.alpha :as current]
            [millstrand.api.graph.alpha :as graph]
            [millstrand.api.hooks.alpha :as hooks]
            [millstrand.api.spool.alpha :refer [attr-get fail!]]
            [millstrand.api.weaver.alpha :as weaver]))

(defn- completion-receipt [rt card]
  (let [epic (:id card)]
    (when-not (and (= "true" (attr-get card :kanban/card))
                   (= "epic" (attr-get card :kanban/type))
                   (= "active" (:state card))
                   (contains? #{"pending" "refinement"}
                              (attr-get card :kanban/lane)))
      (fail! "Ralph completion requires an active Kanban epic in a queue lane"
             {:epic epic}))
    (let [evidence (->> (graph/outgoing-edges rt [epic] "parent-of")
                        (mapv :to_strand_id)
                        (graph/strands-by-ids rt)
                        (filter #(and (= "true" (attr-get % :kanban/card))
                                      (= "feature" (attr-get % :kanban/type))))
                        (sort-by :id)
                        (mapv (fn [child]
                                {:id (:id child)
                                 :state (:state child)
                                 :outcome (attr-get child :kanban/outcome)})))
          unfinished (filterv #(not (and (= "closed" (:state %))
                                         (= "done" (:outcome %))))
                              evidence)]
      (when (seq unfinished)
        (fail! "Ralph epic still has unfinished or unaccepted features"
               {:epic epic :features unfinished}))
      {:epic epic :features evidence :outcome "done"})))

(defn validate-completion!
  "Reject a stale Ralph receipt inside the epic update's pre-commit boundary.

  update! has already written the epic, so SQLite excludes competing writers
  until this hook returns and the transaction commits. The graph reads use the
  public API; only the epic was changed by this update, not its children.
  Throwing rolls back both closure and receipt. Ordinary Kanban updates and
  finish cascades carry no Ralph receipt patch and are unaffected."
  [{:strand/keys [before after patch]}]
  (when-let [receipt (attr-get patch :ralph/completion)]
    (when-not (= receipt (completion-receipt (current/runtime) before))
      (fail! "Ralph completion snapshot changed before epic closure"
             {:epic (:id before) :receipt receipt}))
    (when-not (and (= "closed" (:state after))
                   (= "done" (attr-get after :kanban/outcome)))
      (fail! "Ralph completion receipt requires a done epic" {:epic (:id before)}))))

(defn open-completion-guard!
  "Install Ralph's receipt validation on the existing update transaction."
  [{:keys [runtime]}]
  (hooks/register-hook! runtime ::completion #{:strand/update-before-commit}
                        'ct.spools.codethread.ralph.completion/validate-completion!))

(defn close-completion-guard!
  "Remove Ralph's receipt validation when its module closes."
  [{:keys [runtime]}]
  (hooks/unregister-hook! runtime ::completion))

(defn finish-epic!
  "Close only the epic after every direct feature has a recorded done outcome.

  Persist the checked child snapshot with the epic's closed state. The module's
  pre-commit hook revalidates that snapshot while the update owns SQLite's write
  transaction; a changed snapshot rejects without automatic retry. Other closed
  outcomes need coordinator reconciliation, not automatic acceptance by Ralph.
  Never cascade into children. Repeated executor delivery returns the existing
  receipt after an interrupted gate completion."
  [{:keys [epic]}]
  (let [rt (current/runtime)
        card (weaver/show rt epic)]
    (if (and (= "closed" (:state card))
             (= "done" (attr-get card :kanban/outcome))
             (attr-get card :ralph/completion))
      (attr-get card :ralph/completion)
      (let [receipt (completion-receipt rt card)]
        (weaver/update! rt epic
                        {:state "closed"
                         :attributes {:kanban/lane nil
                                      :kanban/outcome "done"
                                      :ralph/completion receipt}})
        receipt))))
