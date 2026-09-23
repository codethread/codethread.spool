(ns ct.spools.codethread.ralph.completion
  "Check Ralph's epic completion boundary without Kanban's finish cascade."
  (:require [millstrand.api.current.alpha :as current]
            [millstrand.api.graph.alpha :as graph]
            [millstrand.api.spool.alpha :refer [attr-get fail!]]
            [millstrand.api.weaver.alpha :as weaver]))

(defn finish-epic!
  "Close only the epic after every direct feature has a recorded done outcome.

  Persist the checked child snapshot with the epic's closed state. Other closed
  outcomes need coordinator reconciliation, not automatic acceptance by Ralph.
  Never cascade into children. Repeated executor delivery returns the existing
  receipt after an interrupted gate completion."
  [{:keys [epic]}]
  (let [rt (current/runtime)
        card (weaver/show rt epic)]
    (when-not (and (= "true" (attr-get card :kanban/card))
                   (= "epic" (attr-get card :kanban/type)))
      (fail! "Ralph completion requires a Kanban epic" {:epic epic}))
    (if (and (= "closed" (:state card))
             (= "done" (attr-get card :kanban/outcome))
             (attr-get card :ralph/completion))
      (attr-get card :ralph/completion)
      (let [children (->> (graph/outgoing-edges rt [epic] "parent-of")
                          (mapv :to_strand_id)
                          (graph/strands-by-ids rt)
                          (filter #(and (= "true" (attr-get % :kanban/card))
                                        (= "feature" (attr-get % :kanban/type))))
                          (sort-by :id))
            evidence (mapv (fn [child]
                             {:id (:id child)
                              :state (:state child)
                              :outcome (attr-get child :kanban/outcome)})
                           children)
            unfinished (filterv #(not (and (= "closed" (:state %))
                                           (= "done" (:outcome %))))
                                evidence)]
        (when-not (and (= "active" (:state card))
                       (contains? #{"pending" "refinement"}
                                  (attr-get card :kanban/lane)))
          (fail! "Ralph epic must be active in a queue lane" {:epic epic}))
        (when (seq unfinished)
          (fail! "Ralph epic still has unfinished or unaccepted features"
                 {:epic epic :features unfinished}))
        (let [receipt {:epic epic :features evidence :outcome "done"}]
          (weaver/update! rt epic
                          {:state "closed"
                           :attributes {:kanban/lane nil
                                        :kanban/outcome "done"
                                        :ralph/completion receipt}})
          receipt)))))
