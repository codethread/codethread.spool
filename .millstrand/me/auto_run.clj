(ns me.auto-run
  "Activate bounded automatic delivery for this repository."
  (:require [clojure.java.io :as io]
            [ct.spools.codethread.auto-run :as auto-run]
            [ct.spools.codethread.auto-run-worktree]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.millstrand.alpha :as millstrand]))

(millstrand/use-op! auto-run/auto-run)

(defn open!
  "Configure two Sol/high worker slots for autonomous delivery."
  [{:keys [runtime]}]
  (auto-run/configure!
   runtime
   {:repo (.getCanonicalPath
           (.getParentFile (io/file (get-in runtime [:metadata :config-dir]))))
    :seat "sol"
    :effort "high"
    :workflow "auto-full-land"
    :workflows #{"auto-full-land" "auto-human-review"}
    :prepare 'ct.spools.codethread.auto-run-worktree/prepare!
    :enabled? true
    :max-running 2
    :interval-ms 15000}))

(defn close!
  "Stop new admissions without stopping accepted workers."
  [{:keys [runtime]}]
  (auto-run/stop! runtime))

(lifecycle/defresource! auto-run-dispatcher
  "Own repository automatic delivery for the module lifetime."
  {:open 'me.auto-run/open!
   :close 'me.auto-run/close!})
