(ns ct.spools.codethread.agents
  "Codethread-shared harness seats and routing policy, ported from millstrand.

  Aliases are pure model handles (`:sol-low`, `:terra-med`, `:opus`) rather
  than role names. A `-ro` suffix marks an enforced read-only sandbox.

  Seat scorecards use these axes:

    {:complexity N :code-taste N :resilience N :ui-design N
     :coordination N :docs-prose N :cost N}

  Nine is best, X is untested, and `-` means do not route that work to the
  seat. Scores and routing policy retain their millstrand bench provenance.

  GPT seats work best from goals, constraints, and Done-when; reserve long
  prohibition lists for Claude seats. Bench judges run on :oracle or
  :sol-high. Use :oracle's docs-prose premium only where user-facing prose is
  the product; agent-facing docs route to :sol-high or :opus.

  Harness aliases are weaver-lifetime state. `strand agent harnesses` lists
  the live registry."
  (:require [millstrand.api.format.alpha :as format-alpha]
            ))

;; gpt-5.6 rate cards, USD per 1M tokens, hand-pinned 2026-07-13 from
;; https://developers.openai.com/api/docs/pricing.
(def ^:private sol-rates {:input 5.0 :cache-read 0.5 :output 30.0})
(def ^:private terra-rates {:input 2.5 :cache-read 0.25 :output 15.0})
(def ^:private luna-rates {:input 1.0 :cache-read 0.1 :output 6.0})

(def harness-options
  "Codethread-shared harness tools. Seats layered over them live in `alias-defs`."
  {:codex
   {:argv ["codex" "exec" "--json" "--skip-git-repo-check" "--color" "never"
           "--dangerously-bypass-approvals-and-sandbox"
           "-c" "shell_environment_policy.inherit=all"]
    :parse :codex-json
    :resume ["resume" :agent-run/session-id]
    :cost-rates {:input 1.25 :cache-read 0.125 :output 10.0}
    :doc (format-alpha/reflow
          "|Codex CLI headless, read/write. Seats pin models with -m and
           |reasoning effort with -c model_reasoning_effort. :codex-json
           |captures the final result, session id, and cumulative token usage;
           |the rate card derives cost because Codex reports no dollar cost.
           |The sandbox bypass and explicit environment inheritance let workers
           |reach the weaver and the strand/mill CLIs. :resume continues the
           |captured session.")}
   :codex-ro
   {:argv ["codex" "exec" "--json" "--skip-git-repo-check" "--color" "never"
           "--sandbox" "read-only"
           "-c" "shell_environment_policy.inherit=all"]
    :parse :codex-json
    :resume ["resume" :agent-run/session-id]
    :cost-rates {:input 1.25 :cache-read 0.125 :output 10.0}
    :doc (format-alpha/reflow
          "|Codex CLI headless with an enforced read-only sandbox. The sandbox
           |also blocks the weaver socket, so use this only when findings ride
           |the run result and the contract needs neither writes nor strand
           |CLI access.")}
   :hitl-fable
   {:argv ["claude" "--model" "claude-fable-5" "--dangerously-skip-permissions"]
    :parse :raw
    :doc (format-alpha/reflow
          "|Claude Fable interactive TUI: the top-of-graph HITL seat; the prompt
           |rides as the initial argv message.")}
   :hitl-opus
   {:argv ["claude" "--model" "opus" "--dangerously-skip-permissions"]
    :parse :raw
    :doc (format-alpha/reflow
          "|Claude Opus interactive TUI for HITL sessions when Fable-level depth
           |is not warranted.")}})

(def alias-options
  "Codethread-shared seats layered over `harness-defs` and agent-run's :pi tool."
  {:luna-low
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-luna" "-c" "model_reasoning_effort=low"]
    :cost-rates luna-rates
    :doc (format-alpha/reflow
          "|{:complexity 3 :code-taste 4 :resilience 1 :ui-design 2
           | :coordination - :cost 9}
           |gpt-5.6-luna low via Codex. Concrete, well-scoped recon, fan-out
           |search, and single-concern review sweeps at the lowest Codex cost.
           |Won millstrand's deep-trace and needle explore arms and matched
           |:terra-med's change-review recall at about 28% of the price. Quits
           |at environment friction and scored lowest on authored-code quality.
           |Source: millstrand cards vk5re, vw8pf, and nihrl; UI is a prior.")}
   :luna-low-ro
   {:alias-of :codex-ro
    :extra-args ["-m" "gpt-5.6-luna" "-c" "model_reasoning_effort=low"]
    :cost-rates luna-rates
    :doc "Read-only-sandbox variant of :luna-low; no writes or strand CLI access."}
   :terra-low
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-terra" "-c" "model_reasoning_effort=low"]
    :cost-rates terra-rates
    :doc (format-alpha/reflow
          "|{:complexity 4 :code-taste 6 :resilience 2 :ui-design 3
           | :coordination - :cost 8}
           |gpt-5.6-terra low via Codex. The cheap diff-review seat: single-pass
           |cross-vendor review of a branch or patch diff where the findings are
           |the whole product. Scores are :terra-med's discounted for effort and
           |are unbenched; escalate to :terra-med or :sol-med when a review must
           |reason across packages.")}
   :terra-low-ro
   {:alias-of :codex-ro
    :extra-args ["-m" "gpt-5.6-terra" "-c" "model_reasoning_effort=low"]
    :cost-rates terra-rates
    :doc "Read-only-sandbox variant of :terra-low; no writes or strand CLI access."}
   :terra-med
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-terra" "-c" "model_reasoning_effort=medium"]
    :cost-rates terra-rates
    :doc (format-alpha/reflow
          "|{:complexity 5 :code-taste 7 :resilience 2 :ui-design 4
           | :coordination 7 :cost 7}
           |gpt-5.6-terra medium via Codex. Well-defined single-concern review
           |and validation on clean checkouts; benched the cleanest test-writing
           |of the Codex tiers at about 40% of Sol's price. It missed
           |cross-package fallout when tests could not run and gives up on
           |broken toolchains. Source: millstrand card nihrl; UI is a prior.")}
   :terra-med-ro
   {:alias-of :codex-ro
    :extra-args ["-m" "gpt-5.6-terra" "-c" "model_reasoning_effort=medium"]
    :cost-rates terra-rates
    :doc "Read-only-sandbox variant of :terra-med; no writes or strand CLI access."}
   :sol-low
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-sol" "-c" "model_reasoning_effort=low"]
    :cost-rates sol-rates
    :doc (format-alpha/reflow
          "|{:complexity 6 :code-taste 6 :resilience 9 :ui-design 5
           | :coordination X :cost 5}
           |gpt-5.6-sol low via Codex. General build and default delegation
           |seat, including diff-shaped refactors. It was the only model to
           |ship a passing gate under every benched environment condition,
           |recovering hostile toolchains when needed. Source: millstrand card
           |nihrl; UI is a prior.")}
   :sol-med
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-sol" "-c" "model_reasoning_effort=medium"]
    :cost-rates sol-rates
    :doc (format-alpha/reflow
          "|{:complexity 7 :code-taste 8 :resilience 9 :ui-design 5
           | :coordination 8 :cost 4}
           |gpt-5.6-sol medium via Codex. Best benched Codex quality with Sol's
           |environment resilience: use when quality outweighs roughly twice
           |:sol-low's cost, for cross-vendor review of Claude-authored work,
           |and for delegated sub-supervision. Source: millstrand card nihrl and
           |the 2026-07-13 coordination test; UI is a prior.")}
   :sol-med-ro
   {:alias-of :codex-ro
    :extra-args ["-m" "gpt-5.6-sol" "-c" "model_reasoning_effort=medium"]
    :cost-rates sol-rates
    :doc "Read-only-sandbox variant of :sol-med; no writes or strand CLI access."}
   :sol-high
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-sol" "-c" "model_reasoning_effort=high"]
    :cost-rates sol-rates
    :doc (format-alpha/reflow
          "|{:complexity X :code-taste X :resilience X :ui-design X
           | :coordination X :docs-prose 8 :cost 3}
           |gpt-5.6-sol high via Codex. Use for the most complex implementation
           |before :oracle escalation, agent-facing docs, and bench judging.
           |The millstrand docs bake-off found it strong on structured long-form
           |work, with drier prose than Claude. Code axes at high effort remain
           |untested; treat early code runs as trials. Source: card x6gam.")}
   :luna-high
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.6-luna" "-c" "model_reasoning_effort=high"]
    :cost-rates luna-rates
    :doc (format-alpha/reflow
          "|{:complexity 7 :code-taste 7 :resilience X :ui-design 5
           | :coordination 8 :docs-prose X :cost 6}
           |gpt-5.6-luna high via Codex. The shared implementation seat for
           |deep, bounded feature work when the coordinator asks for
           |luna-high. Escalate cross-repository design or review to :sol-high.")}
   :opus
   {:alias-of :claude
    :extra-args ["--model" "opus"]
    :doc (format-alpha/reflow
          "|{:complexity 8 :code-taste 9 :resilience X :ui-design 9
           | :coordination 6 :docs-prose 7 :cost 2}
           |Claude Opus. Greenfield features, API design, and critical seams;
           |archaeology-first and strongest on known-work code quality. Keep
           |cross-vendor GPT sign-off for Opus-authored changes. Suitable for
           |agent-facing docs, though the docs bake-off found its restructuring
           |paste-up-prone. Source: millstrand cards nihrl and x6gam; UI is a
           |prior.")}
   :oracle
   {:alias-of :claude
    :extra-args ["--model" "claude-fable-5"]
    :doc (format-alpha/reflow
          "|{:complexity 9 :code-taste 9 :resilience X :ui-design 8
           | :coordination 9 :docs-prose 9 :cost 1}
           |Claude Fable. Reserve for extreme diagnosis, top-of-graph
           |coordination, user-facing prose where writing is the product, and
           |bench judging. Brief one case per run and require incremental
           |notes: a context-overflowed run that writes nothing is unusually
           |expensive. Source: millstrand card x6gam and coordinator priors.")}
   :gpt-mini
   {:alias-of :codex
    :extra-args ["-m" "gpt-5.4-mini" "-c" "model_reasoning_effort=medium"]
    :cost-rates {:input 0.25 :cache-read 0.025 :output 2.0}
    :doc (format-alpha/reflow
          "|{:complexity 2 :code-taste 3 :resilience X :ui-design X
           | :coordination - :cost 9}
           |gpt-5.4-mini via Codex. Low-stakes single-concern review, recon,
           |and validation. Scores are estimated from a bench-spool smoke run;
           |do not route broader work until a larger run proves it.")}
   :flash
   {:alias-of :pi
    :extra-args ["--agent" "main" "--model" "deepseek/deepseek-v4-flash"
                 "--thinking" "high"]
    :doc (format-alpha/reflow
          "|{:complexity X :code-taste X :resilience X :ui-design X
           | :coordination - :cost 9}
           |DeepSeek v4 Flash via Pi. Enumeration-shaped recon and quota
           |fallback at very low cost. It won millstrand's wide fan-out explore
           |arm but loses precision on deep traces and exact citations; verify
           |citations and do not route load-bearing deep dives here. Source:
           |millstrand card vk5re.")}
   :deepseek
   {:alias-of :pi
    :extra-args ["--agent" "main" "--model" "deepseek/deepseek-v4-pro:high"]
    :doc (format-alpha/reflow
          "|UNSCORED (unbenched; :coordination -). DeepSeek v4 Pro via Pi:
           |provider-quota fallback for reviews of primarily Claude-authored
           |code, not frontier design or broad implementation.")}})

;; The current agent-run default-contract API is not explicit-runtime, so this
;; release selects only the harness and alias declarations. Consumers may bind
;; review/task defaults in their own runtime-aware policy layer.
