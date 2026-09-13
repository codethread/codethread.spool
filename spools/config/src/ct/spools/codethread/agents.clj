(ns ct.spools.codethread.agents
  "Register Codethread's shared Harnesses seats and routing policy.

  The base aliases follow the authoritative Harnesses workspace catalog.
  Compatibility variants retain useful model/effort handles from the previous
  config without retaining the legacy agent-harness API."
  (:require [ct.spools.harnesses :as harnesses]
            [millstrand.api.format.alpha :as format-alpha]
            [millstrand.api.lifecycle.alpha :as lifecycle]))

(def ^:private alias-definitions
  {:deepseek-flash
   {:doc (format-alpha/prose
          "
            Scores: complexity X; code-taste X; resilience X; ui-design X;
            coordination -; cost 9.

            Enumeration-shaped recon and quota fallback at very low cost. Good
            for wide fan-out exploration, but verify citations and keep it away
            from load-bearing deep dives.
            "
          {})
    :parent :pi
    :model "deepseek/deepseek-v4-flash"
    :effort :high
    :allow #{:reviewer :oracle}
    :attributes {:harness/extra-argv ["--agent" "main"]}}

   :luna
   {:doc (format-alpha/prose
          "
            Scores: complexity 3; code-taste 4; resilience 1; ui-design 2;
            coordination -; cost 9.

            gpt-5.6-luna for implementation details, scouting, and tightly
            scoped delegated tasks. Be explicit about success criteria.
            "
          {})
    :parent :pi
    :model "openai-codex/gpt-5.6-luna"
    :effort :high
    :allow #{:reviewer :oracle}
    :attributes {}}

   :opus
   {:doc (format-alpha/prose
          "
            Scores: complexity 8; code-taste 9; resilience X; ui-design 9;
            coordination 6; docs-prose 7; cost 2.

            Claude Opus for greenfield features, API design, and critical
            seams. Keep independent review for authored changes.
            "
          {})
    :parent :claude
    :model "opus"
    :effort :low
    :attributes {}}

   :fable
   {:doc (format-alpha/prose
          "
            Scores: complexity 9; code-taste 9; resilience X; ui-design 8;
            coordination 9; docs-prose 9; cost 1.

            Claude Fable for extreme diagnosis, top-level coordination, and
            user-facing prose where writing is the product.
            "
          {})
    :parent :claude
    :model "claude-fable-5"
    :effort :high
    :attributes {}}

   :astra
   {:doc (format-alpha/prose
          "
            gpt-6-astra for extreme diagnosis, top-level coordination,
            architectural guidance, and high-stakes user-facing prose.
            "
          {})
    :parent :pi
    :model "openai-codex/gpt-6-astra"
    :effort :high
    :attributes {}}

   :sol
   {:doc (format-alpha/prose
          "
            Scores: complexity 6; code-taste 6; resilience 9; ui-design 5;
            coordination 8; cost 5.

            gpt-5.6-sol for complex implementation, hostile-environment
            debugging, and delegated coordination.
            "
          {})
    :parent :pi
    :model "openai-codex/gpt-5.6-sol"
    :effort :low
    :attributes {}}

   :terra
   {:doc (format-alpha/prose
          "
            Scores: complexity 5; code-taste 6; resilience 2; ui-design 4;
            coordination 5; cost 7.

            gpt-5.6-terra medium for well-defined single-concern review and
            validation on clean checkouts.
            "
          {})
    :parent :pi
    :model "openai-codex/gpt-5.6-terra"
    :effort :medium
    :attributes {}}

   :grok
   {:doc "Grok for greenfield implementation, API design, and critical code review."
    :parent :cursor
    :model "cursor-grok-4.6"
    :effort :high
    :attributes {:harness.cursor/fast true}}

   :oracle
   {:doc "Default seat for high-stakes guidance, diagnosis, and architecture."
    :parent :astra
    :effort :high
    :attributes {}}

   :reviewer
   {:doc "Default seat for targeted reviews."
    :parent :terra
    :allow #{:grunt :oracle}
    :attributes {}}

   :grunt
   {:doc "Default seat for mechanical, tightly scoped implementation tasks."
    :parent :luna
    :allow #{:reviewer :oracle}
    :attributes {}}

   :coordinator
   {:doc "Default seat for delegated coordination and work decomposition."
    :parent :sol
    :attributes {}}

   :tui
   {:doc "Primary interactive user seat."
    :parent :sol
    :effort :low
    :attributes {}}

   :luna-low
   {:doc "Low-effort Luna compatibility seat for cheap bounded work."
    :parent :luna
    :effort :low
    :allow #{:reviewer :oracle}
    :attributes {}}

   :luna-high
   {:doc "High-effort Luna compatibility seat for bounded feature work."
    :parent :luna
    :effort :high
    :allow #{:reviewer :oracle}
    :attributes {}}

   :terra-low
   {:doc "Low-effort Terra compatibility seat for cheap review sweeps."
    :parent :terra
    :effort :low
    :attributes {}}

   :terra-med
   {:doc "Medium-effort Terra compatibility seat for focused validation."
    :parent :terra
    :effort :medium
    :attributes {}}

   :sol-low
   {:doc "Low-effort Sol compatibility seat for general implementation."
    :parent :sol
    :effort :low
    :attributes {}}

   :sol-med
   {:doc "Medium-effort Sol compatibility seat for cross-cutting work."
    :parent :sol
    :effort :medium
    :attributes {}}

   :sol-high
   {:doc "High-effort Sol compatibility seat for complex implementation."
    :parent :sol
    :effort :high
    :attributes {}}

   :gpt-mini
   {:doc "Low-cost Codex seat for low-stakes recon and validation."
    :parent :codex
    :model "gpt-5.4-mini"
    :effort :medium
    :attributes {}}

   :flash
   {:doc "Compatibility name for the DeepSeek Flash recon seat."
    :parent :deepseek-flash
    :allow #{:reviewer :oracle}
    :attributes {}}

   :deepseek
   {:doc "DeepSeek Pro quota fallback for bounded reviews."
    :parent :pi
    :model "deepseek/deepseek-v4-pro:high"
    :effort :high
    :attributes {:harness/extra-argv ["--agent" "main"]}}})

(defn open-shared-catalog!
  "Register shared aliases and apply the default provider policy.

  Claude and Cursor remain registered but disabled. Consumers may explicitly
  enable either process-local flag after startup."
  [{:keys [runtime]}]
  (doseq [provider-flag [:harness/claude :harness/cursor]]
    (harnesses/set-flag! runtime provider-flag false))
  (let [registrations
        (mapv (fn [[alias descriptor]]
                (harnesses/register-alias! runtime alias descriptor))
              alias-definitions)]
    {:opened :codethread/shared-catalog
     :aliases (mapv :alias registrations)}))

(defn close-shared-catalog!
  "Remove aliases owned by the shared catalog resource."
  [{:keys [runtime resource]}]
  (doseq [alias (:aliases resource)]
    (harnesses/unregister-alias! runtime alias))
  {:closed :codethread/shared-catalog})

(lifecycle/defresource! shared-harness-catalog
  "Own Codethread's shared Harnesses aliases and provider defaults."
  {:open 'ct.spools.codethread.agents/open-shared-catalog!
   :close 'ct.spools.codethread.agents/close-shared-catalog!})
