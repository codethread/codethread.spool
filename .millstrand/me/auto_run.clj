(ns me.auto-run
  "Activate bounded automatic delivery for this repository."
  (:require [clojure.java.io :as io]
            [ct.spools.codethread.auto-run :as auto-run]
            [ct.spools.codethread.auto-run-worktree]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.millstrand.alpha :as millstrand]))

(millstrand/use-op! auto-run/auto-run)

(defn desired-config
  "Return the checked-in automatic delivery configuration."
  [{:keys [runtime]}]
  {:repo (.getCanonicalPath
          (.getParentFile (io/file (get-in runtime [:metadata :config-dir]))))
   :seat "sol"
   :effort "high"
   :workflow "auto-full-land"
   :workflows #{"auto-full-land" "auto-human-review"}
   :prepare 'ct.spools.codethread.auto-run-worktree/prepare!
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
