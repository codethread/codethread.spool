(ns me.auto-run
  "Activate bounded automatic delivery for this repository."
  (:require [clojure.java.io :as io]
            [millhouse.spools.auto-run :as auto-run]
            [millhouse.spools.auto-run-reporting :as reporting]
            [millhouse.spools.auto-run-worktree]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.millstrand.alpha :as millstrand]))

(millstrand/use-op! auto-run/auto-run)
(millstrand/use-hook! reporting/derive-labels)
(millstrand/use-pattern! reporting/auto-run-needs-decision
                         reporting/auto-run-unknown-failure
                         reporting/auto-run-unblock)

(defn desired-config
  "Return the checked-in automatic delivery configuration."
  [{:keys [runtime]}]
  {:repo (.getCanonicalPath
          (.getParentFile (io/file (get-in runtime [:metadata :config-dir]))))
   :seat "sol"
   :effort "high"
   :workflow "auto-full-land"
   :workflows #{"auto-full-land" "auto-human-review"}
   :prepare 'millhouse.spools.auto-run-worktree/prepare!
   :enabled? true
   :max-running 2
   :interval-ms 15000})

(defn actual-config
  "Return the dispatcher's active automatic delivery configuration."
  [{:keys [runtime]}]
  (when-let [config (:config (auto-run/status runtime))]
    (cond-> (-> config
                (update :prepare symbol)
                (update :workflows set))
      (:start-params config) (update :start-params symbol))))

(defn reconcile-config!
  "Apply the checked-in configuration when it differs from the dispatcher."
  [{:keys [runtime desired actual]}]
  (if (= desired actual)
    {:changed? false :config actual}
    {:changed? true :config (auto-run/configure! runtime desired)}))

(defn remove-config!
  "Stop new admissions when the configuration module is removed."
  [{:keys [runtime]}]
  (auto-run/stop! runtime))

(lifecycle/defreconcile! auto-run-dispatcher
  "Keep repository automatic delivery aligned with checked-in policy."
  {:read-desired 'me.auto-run/desired-config
   :read-actual 'me.auto-run/actual-config
   :apply 'me.auto-run/reconcile-config!
   :on-removed 'me.auto-run/remove-config!
   :trigger-kinds #{workflow/definition-kind}})
