(ns ct.spools.codethread.agents-next
  "Repo-local aliases for exercising the next harness vertical slice."
  (:require [ct.spools.harness-core :as harness]
            [millstrand.api.lifecycle.alpha :as lifecycle]))

(defn open-harness-next!
  "Register the workspace's aliases for the next harness implementation."
  [{:keys [runtime]}]
  (harness/register-alias! runtime :opus-high :claude
                           {:harness.claude/model "opus"
                            :harness.claude/effort "high"})
  (harness/register-alias! runtime :sonnet-low :claude
                           {:harness.claude/model "sonnet"
                            :harness.claude/effort "low"})
  (harness/register-alias! runtime :terra-med :codex
                           {:harness.codex/model "gpt-5.6-terra"
                            :harness.codex/effort "medium"})
  (harness/register-alias! runtime :pi-terra :pi
                           {:harness.pi/model "openai-codex/gpt-5.6-terra"
                            :harness.pi/effort "medium"})
  {:opened :harness-next :aliases ["opus-high" "sonnet-low" "terra-med" "pi-terra"]})

(defn close-harness-next!
  "Close the harness-next workspace resource."
  [_context]
  {:closed :harness-next})

(lifecycle/defresource harness-next-runtime
  "Own the workspace's next-harness aliases for the module lifetime."
  {:open 'ct.spools.codethread.agents-next/open-harness-next!
   :close 'ct.spools.codethread.agents-next/close-harness-next!})

