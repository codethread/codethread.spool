(ns ct.spools.codethread.config-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.codethread.bootstrap :as codethread]
            [ct.spools.codethread.sub-coordinator :as sub-coordinator]
            [ct.spools.harnesses :as harnesses]
            [ct.spools.harnesses.reviewers :as reviewers]
            [millhouse.spools.workflow :as workflow]
            [millstrand.api.current.alpha :as current]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.spool.alpha :refer [attr-get]]
            [millstrand.api.weaver.alpha :as weaver]
            [millstrand.test.alpha :as t]))

(def ^:private project-root (.getCanonicalPath (io/file "../..")))
(def ^:private deps-edn
  (pr-str
   {:deps
    {'codethread/config {:local/root (str project-root "/spools/config")}
     'codethread/ralph {:local/root (str project-root "/spools/ralph")}}}))
(def ^:private workspace-init-clj
  (slurp (io/file project-root ".millstrand/init.clj")))

(deftest workspace-deps-compose-library-roots-and-current-harnesses
  (let [{:keys [deps]} (edn/read-string
                        (slurp (io/file project-root ".millstrand/deps.edn")))
        workspace-root (io/file project-root ".millstrand")
        config-root (io/file workspace-root (get-in deps ['codethread/config :local/root]))
        ralph-root (io/file workspace-root (get-in deps ['codethread/ralph :local/root]))
        config-deps (:deps (edn/read-string (slurp (io/file config-root "deps.edn"))))]
    (is (= {:local/root "../spools/config"}
           (get deps 'codethread/config)))
    (is (= {:local/root "../spools/ralph"}
           (get deps 'codethread/ralph)))
    (is (.isFile (io/file config-root "deps.edn")))
    (is (.isFile (io/file ralph-root "deps.edn")))
    (is (.isFile (io/file ralph-root "bin/ralph")))
    (is (= "310368dff9174bd889ad21d4ed8196952684eaf9"
           (get-in config-deps ['io.millstrand/batteries :git/sha])))
    (is (not (contains? config-deps 'millstrand.spools/batteries)))
    (doseq [[library sha] [['millhouse.spools/workflow
                            "3132c8f7f10455c893da28fef0e9ca0047560f82"]
                           ['millhouse.spools/identity
                            "b1955a96ad91bf2909a407859fca1565ec4b9fdb"]
                           ['millhouse.spools/kanban
                            "3132c8f7f10455c893da28fef0e9ca0047560f82"]
                           ['millhouse.spools/land
                            "89e5e32f8a948547c233d5dd183bb73f9c5abe4a"]]]
      (is (= sha (get-in config-deps [library :git/sha]))))
    (is (= "e8a26477852216bca2579b050a2356c86af132b7"
           (get-in config-deps ['ct.spools/harnesses :git/sha])))
    (is (not-any? #{'ct.spools/agent-run 'ct.spools/delegation}
                  (keys config-deps)))
    (is (= "99313b48f14ab0892cb90264d100dce4ff2a25e0"
           (get-in config-deps ['codethread/devflow :git/sha])))
    (is (= "99313b48f14ab0892cb90264d100dce4ff2a25e0"
           (get-in config-deps
                   ['codethread/devflow-kanban-adapter :git/sha])))))

(deftest bootstrap-registers-catalog-and-reviewers-without-an-executor
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)
          expected (mapv first codethread/module-definitions)
          result (codethread/register! rt)]
      (is (= expected (:registered result)))
      (is (= expected (:registered (codethread/register! rt))))
      (testing "the preferred Pi-backed role seats resolve"
        (is (= {:alias "luna" :harness "pi"}
               (select-keys (harnesses/resolve-harness rt :luna)
                            [:alias :harness])))
        (is (= "openai-codex/gpt-5.6-luna"
               (get-in (harnesses/resolve-harness rt :luna)
                       [:generated :harness/model])))
        (is (= "openai-codex/gpt-6-astra"
               (get-in (harnesses/resolve-harness rt :oracle)
                       [:generated :harness/model])))
        (is (= "low"
               (get-in (harnesses/resolve-harness rt :luna-low)
                       [:generated :harness/effort]))))
      (testing "the bounded sub-coordinator carries its Luna-first runbook"
        (let [coordinator-before (harnesses/resolve-harness rt :coordinator)
              luna (harnesses/resolve-harness rt :sub-coordinator)
              luna-guidance (get-in luna
                                    [:generated
                                     :harness/appended-system-prompts])
              luna-run (harnesses/create!
                        rt {:harness :sub-coordinator
                            :mode :interactive
                            :cwd "/tmp"
                            :title "Frozen Luna sub-coordinator run"})]
          (is (= "codex" (:harness luna)))
          (is (= "gpt-5.6-luna"
                 (get-in luna [:generated :harness/model])))
          (is (= "max" (get-in luna [:generated :harness/effort])))
          (is (= 1 (count luna-guidance)))
          (doseq [contract-fragment
                  ["# Bounded sub-coordinator runbook"
                   "canonical coordination workspace"
                   "Set a real goal for every assigned card"
                   "Never stop or restart the global Mill"
                   "Never restart or replace a running Weaver"
                   "explicit user sign-off"
                   "Never use the deprecated `agent-harness.spool`"
                   "only by an identified run or PID"
                   "Never use a broad process-name kill"
                   "Never edit or push `main`"
                   "Preserve unrelated owner and run state"
                   "disposable explicit workspaces"
                   "Never use the shared Millstrand world"
                   "only through tracked Strand runs"
                   "Assign every source change"
                   "implement directly without recursively delegating"
                   "only with explicit parent authorization"
                   "not another claimable feature"
                   "feature assignment only"
                   "every child launch and resume"
                   "verify delivery, target lifecycle"
                   "dependency readiness"
                   "request publication"
                   "invocation attempt"
                   "custody, and the current run pointer"
                   "reported as `ready` does not"
                   "stable request IDs and request lineage"
                   "Include them in dispatch, retry, and handoff evidence"
                   "one structured argument, payload, or raw"
                   "Never interpolate rich prose into shell commands"
                   "confuse JSON encoding with shell escaping"
                   "bounded `strand await`"
                   "agent-run-terminal"
                   "agent-run-settled"
                   "agent-run-active"
                   "agent-work-complete"
                   "agent-work-complete-or-intervention"
                   "A timeout means only"
                   "exact implementation SHA"
                   "required quality marker"
                   "same unfinished milestone with its sole"
                   "retain the predecessor request and run lineage"
                   "shared Land workflow"
                   "strict FIFO"
                   "identify its cleanup owner"
                   "verify active runs"
                   "clean and pushed state"
                   "canonical ancestry"
                   "retained artifacts"
                   "only with explicit runtime-owner"
                   "repeated documented mistakes"
                   "persist despite clear"
                   "Timeouts, latency, and provider or infrastructure failures"
                   "not evidence of poor coordination or grounds for fallback"
                   "Preserve and settle the old run"
                   "Retain the exact workspace"
                   "target, run, candidate, and evidence"
                   "fresh request for the new assignment"
                   "Do not change runtime flags"
                   "evidenced handoff"]]
            (is (str/includes? (first luna-guidance)
                               contract-fragment)))
          (doseq [provider-specific-fragment
                  ["Pi" "Codex" "/goal" "goal_wait" "goal_complete"
                   "goal_blocked" "native resume" "model" "trial"]]
            (is (not (str/includes? (first luna-guidance)
                                    provider-specific-fragment))))
          (is (= "gpt-5.6-luna"
                 (attr-get luna-run :harness/model)))
          (is (= luna-guidance
                 (attr-get luna-run :harness/appended-system-prompts)))
          (harnesses/set-flag! rt :seat/sub-coordinator-terra true)
          (let [terra (harnesses/resolve-harness rt :sub-coordinator)]
            (is (= "codex" (:harness terra)))
            (is (= "gpt-5.6-terra"
                   (get-in terra [:generated :harness/model])))
            (is (= "high" (get-in terra [:generated :harness/effort])))
            (is (= luna-guidance
                   (get-in terra
                           [:generated :harness/appended-system-prompts])))
            (is (= "gpt-5.6-luna"
                   (attr-get (harnesses/run rt (:id luna-run))
                             :harness/model))))
          (is (= coordinator-before
                 (harnesses/resolve-harness rt :coordinator)))))
      (testing "the Sol sub-coordinator resolves independently with sustained guidance"
        (let [coordinator-before (harnesses/resolve-harness rt :coordinator)
              sol-before (harnesses/resolve-harness rt :sol)
              bounded-before (harnesses/resolve-harness rt :sub-coordinator)
              sustained (harnesses/resolve-harness rt :sub-coordinator-sol)
              guidance (get-in sustained
                               [:generated :harness/appended-system-prompts])
              sustained-run (harnesses/create!
                             rt {:harness :sub-coordinator-sol
                                 :mode :interactive
                                 :cwd "/tmp"
                                 :title "Frozen Sol sub-coordinator run"})]
          (is (= "codex" (:harness sustained)))
          (is (= "gpt-5.6-sol"
                 (get-in sustained [:generated :harness/model])))
          (is (= "high" (get-in sustained [:generated :harness/effort])))
          (is (= 1 (count guidance)))
          (doseq [contract-fragment
                  ["# Sustained sub-coordinator runbook"
                   "canonical coordination workspace"
                   "Set a real goal for every assigned card"
                   "Never stop or restart the global Mill"
                   "Never restart or replace a running Weaver"
                   "explicit user sign-off"
                   "Never use the deprecated `agent-harness.spool`"
                   "only by an identified run or PID"
                   "Never use a broad process-name kill"
                   "Never edit or push `main`"
                   "Preserve unrelated owner and run state"
                   "disposable explicit workspaces"
                   "Never use the shared Millstrand world"
                   "only through tracked Strand runs"
                   "Assign every source change"
                   "implement directly without recursively delegating"
                   "only with explicit parent authorization"
                   "not another claimable feature"
                   "feature assignment only"
                   "every child launch and resume"
                   "verify delivery, target lifecycle"
                   "dependency readiness"
                   "request publication"
                   "invocation attempt"
                   "custody, and the current run pointer"
                   "reported as `ready` does not"
                   "stable request IDs and request lineage"
                   "Include them in dispatch, retry, and handoff evidence"
                   "one structured argument, payload, or raw"
                   "Never interpolate rich prose into shell commands"
                   "confuse JSON encoding with shell escaping"
                   "bounded `strand await`"
                   "agent-run-terminal"
                   "agent-run-settled"
                   "agent-run-active"
                   "agent-work-complete"
                   "agent-work-complete-or-intervention"
                   "A timeout means only"
                   "P1/P2"
                   "required quality marker"
                   "same unfinished milestone with its sole"
                   "retain the predecessor request and run lineage"
                   "shared Land workflow"
                   "strict FIFO"
                   "identify its cleanup owner"
                   "verify active runs"
                   "clean and pushed state"
                   "canonical ancestry"
                   "retained artifacts"
                   "only with explicit runtime-owner"
                   "repeated documented mistakes"
                   "persist despite clear"
                   "Timeouts, latency, and provider or infrastructure failures"
                   "not evidence of poor coordination or grounds for fallback"
                   "Preserve and settle the old run"
                   "Retain the exact workspace"
                   "target, run, candidate, and evidence"
                   "fresh request for the new assignment"
                   "Do not change runtime flags"
                   "acknowledged next owner"]]
            (is (str/includes? (first guidance) contract-fragment)))
          (doseq [provider-specific-fragment
                  ["Pi" "Codex" "/goal" "goal_wait" "goal_complete"
                   "goal_blocked" "native resume" "model" "trial"]]
            (is (not (str/includes? (first guidance)
                                    provider-specific-fragment))))
          (is (= "gpt-5.6-sol"
                 (attr-get sustained-run :harness/model)))
          (is (= "high" (attr-get sustained-run :harness/effort)))
          (is (= guidance
                 (attr-get sustained-run :harness/appended-system-prompts)))
          (is (= coordinator-before
                 (harnesses/resolve-harness rt :coordinator)))
          (is (= sol-before (harnesses/resolve-harness rt :sol)))
          (is (= bounded-before
                 (harnesses/resolve-harness rt :sub-coordinator)))))
      (testing "provider defaults preserve the authoritative workspace policy"
        (is (false? (harnesses/flag rt :harness/claude)))
        (is (false? (harnesses/flag rt :harness/cursor)))
        (is (false? (:available (harnesses/availability rt :opus)))))
      (testing "shared review lenses resolve through available aliases"
        (let [catalog (reviewers/reviewers rt)]
          (is (= ["docs-and-tests" "runtime-correctness" "source-form"]
                 (mapv :name catalog)))
          (is (every? :available catalog))
          (is (some #{"spools/*/src/**"}
                    (:glob (some #(when (= "source-form" (:name %)) %)
                                 catalog))))))
      (testing "landing is active while executor activation stays deferred"
        (current/with-runtime rt
          (is (some? (workflow/workflow-definition :review)))
          (is (some? (workflow/workflow-definition :land)))
          (is (not (contains? (set (keys (workflow/executors))) :agent))))))))

(deftest live-sub-coordinator-registration-is-additive
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (codethread/register! rt)
      (is (true? (harnesses/unregister-alias!
                  rt sub-coordinator/alias-name)))
      (let [registry-before (harnesses/harnesses rt)
            flags-before (harnesses/flags rt)
            modules-before (runtime/status rt)
            existing-run (harnesses/create!
                          rt {:harness :coordinator
                              :mode :interactive
                              :cwd "/tmp"
                              :title "Frozen existing coordinator run"})
            run-before (harnesses/run rt (:id existing-run))
            registration (sub-coordinator/register! rt)
            registry-after (harnesses/harnesses rt)
            run-after (harnesses/run rt (:id existing-run))
            added (some #(when (= "sub-coordinator" (:name %)) %)
                        registry-after)
            resolved (harnesses/resolve-harness rt :sub-coordinator)
            guidance (get-in resolved
                             [:generated :harness/appended-system-prompts])]
        (is (= "sub-coordinator" (:alias registration)))
        (is (= 2 (count (:candidates registration))))
        (is (= registry-before
               (filterv #(not= "sub-coordinator" (:name %))
                        registry-after)))
        (is (= flags-before (harnesses/flags rt)))
        (is (= modules-before (runtime/status rt)))
        (is (= run-before run-after))
        (is (= "coordinator" (attr-get run-after :harness/alias)))
        (is (= "openai-codex/gpt-5.6-sol"
               (attr-get run-after :harness/model)))
        (is (= "codex" (:harness resolved)))
        (is (= "gpt-5.6-luna"
               (get-in resolved [:generated :harness/model])))
        (is (= "max" (get-in resolved [:generated :harness/effort])))
        (is (= 1 (count guidance)))
        (is (str/includes? (first guidance)
                           "# Bounded sub-coordinator runbook"))
        (is (= "alias" (:kind added)))
        (is (true? (:available added)))))))

(deftest live-sol-sub-coordinator-registration-is-additive
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (codethread/register! rt)
      (is (true? (harnesses/unregister-alias!
                  rt sub-coordinator/sol-alias-name)))
      (let [registry-before (harnesses/harnesses rt)
            flags-before (harnesses/flags rt)
            modules-before (runtime/status rt)
            resolutions-before
            (into {}
                  (map (fn [alias]
                         [alias (harnesses/resolve-harness rt alias)]))
                  [:sol :coordinator :sub-coordinator])
            existing-runs
            (mapv #(harnesses/create!
                    rt {:harness %
                        :mode :interactive
                        :cwd "/tmp"
                        :title (str "Frozen existing " (name %) " run")})
                  [:coordinator :sub-coordinator])
            runs-before (mapv #(harnesses/run rt (:id %)) existing-runs)
            registration (sub-coordinator/register-sol! rt)
            registry-after (harnesses/harnesses rt)
            runs-after (mapv #(harnesses/run rt (:id %)) existing-runs)
            resolutions-after
            (into {}
                  (map (fn [alias]
                         [alias (harnesses/resolve-harness rt alias)]))
                  [:sol :coordinator :sub-coordinator])
            added (some #(when (= "sub-coordinator-sol" (:name %)) %)
                        registry-after)
            resolved (harnesses/resolve-harness rt :sub-coordinator-sol)]
        (is (= "sub-coordinator-sol" (:alias registration)))
        (is (= 1 (count (:candidates registration))))
        (is (= registry-before
               (filterv #(not= "sub-coordinator-sol" (:name %))
                        registry-after)))
        (is (= flags-before (harnesses/flags rt)))
        (is (= modules-before (runtime/status rt)))
        (is (= resolutions-before resolutions-after))
        (is (= runs-before runs-after))
        (is (= "openai-codex/gpt-5.6-sol"
               (attr-get (first runs-after) :harness/model)))
        (is (= "gpt-5.6-luna"
               (attr-get (second runs-after) :harness/model)))
        (is (= "codex" (:harness resolved)))
        (is (= "gpt-5.6-sol"
               (get-in resolved [:generated :harness/model])))
        (is (= "high" (get-in resolved [:generated :harness/effort])))
        (is (= "alias" (:kind added)))
        (is (true? (:available added)))))))

(deftest consumer-modules-reconcile-before-explicit-executor-activation
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (codethread/register! rt)
      (let [consumer-result
            (runtime/module! rt :consumer/aliases
                             {:ns 'ct.spools.codethread.consumer-fixture
                              :after [:codethread/config-agents]
                              :required? true})]
        (is (= :applied
               (get-in consumer-result
                       [:modules :consumer/aliases :status])))
        (is (= :applied
               (get-in consumer-result
                       [:modules :consumer/aliases :lifecycle/outcomes
                        :consumer-alias :status])))
        (is (= "openai-codex/gpt-5.6-luna"
               (get-in (harnesses/resolve-harness rt :consumer-luna)
                       [:generated :harness/model]))))
      (let [result (codethread/register-executor! rt [:consumer/aliases])
            status (runtime/status rt)]
        (is (= [codethread/executor-module-id] (:registered result)))
        (is (= :consumer/aliases (last (:after result))))
        (is (= (:after result)
               (get-in status
                       [:modules codethread/executor-module-id :after])))
        (is (= :applied
               (get-in status
                       [:last-refresh :modules codethread/executor-module-id
                        :lifecycle/outcomes :agent-engine :status])))
        (current/with-runtime rt
          (is (contains? (set (keys (workflow/executors))) :agent)))))))

(deftest workspace-init-stages-and-activates-the-complete-cli-surface
  (t/with-weaver-world [ctx {:storage :sqlite-memory
                             :deps-edn deps-edn
                             :init-clj workspace-init-clj}]
    (let [rt (:runtime ctx)
          status (runtime/status rt)
          aliases (weaver/op! rt 'agent ["list"])
          reviewer-result (weaver/op! rt 'agent ["reviewers"])
          workflow-result (weaver/op! rt 'workflow ["list"])
          land-result (weaver/op! rt 'workflow ["show" "land"])
          op-names (set (map :name (weaver/ops rt)))]
      (is (= {:status :applied :mode :full}
             (select-keys (:last-refresh status) [:status :mode])))
      (is (every? #{:applied}
                  (map :status (vals (get-in status
                                             [:last-refresh :modules])))))
      (is (= :applied
             (get-in status
                     [:last-refresh :modules codethread/executor-module-id
                      :lifecycle/outcomes :agent-engine :status])))
      (is (every? (set (map :name aliases))
                  ["coordinator" "grunt" "luna" "oracle" "reviewer"
                   "sub-coordinator" "sub-coordinator-sol"]))
      (is (= ["docs-and-tests" "runtime-correctness" "source-form"]
             (mapv :name (:reviewers reviewer-result))))
      (is (= #{"intake" "land" "publish-spool-kondo" "ralph-iterate" "review"}
             (set (map :name (:definitions workflow-result)))))
      (is (= "land" (:name land-result)))
      (is (= "reviewer" (get-in land-result [:params :defaults :reviewer])))
      (is (contains? op-names "merge-queue")))))

(deftest optional-workspace-config-keeps-the-devflow-kanban-election
  (t/with-weaver-world [ctx {:storage :sqlite-memory :deps-edn deps-edn}]
    (let [rt (:runtime ctx)]
      (runtime/module! rt :millstrand/spools-batteries
                       {:ns 'millstrand.spools.batteries})
      (codethread/register! rt)
      (runtime/module! rt :devflow {:ns 'ct.spools.devflow
                                    :after [:millhouse/spools-workflow]})
      (runtime/module! rt :devflow/kanban-adapter
                       {:ns 'ct.spools.devflow-kanban-adapter
                        :after [:devflow :millhouse/spools-kanban
                                :millhouse/spools-workflow]})
      (runtime/module! rt :codethread/config-help
                       {:ns 'ct.spools.codethread.help
                        :after [:millstrand/spools-batteries]})
      (runtime/module! rt :codethread/config-devflow
                       {:ns 'ct.spools.codethread.devflow})
      (let [result (runtime/module! rt :codethread/config
                                    {:ns 'ct.spools.codethread.config
                                     :after [:codethread/config-help
                                             :codethread/config-devflow
                                             :devflow/kanban-adapter]})]
        (is (contains? #{:applied :unchanged}
                       (get-in result [:modules :codethread/config :status])))
        (codethread/register-executor!
         rt [:devflow/kanban-adapter :codethread/config])
        (current/with-runtime rt
          (is (= 'ct.spools.devflow-kanban-adapter/decompose-kanban
                 (workflow/workflow-definition :decompose))))))))

(defn -main [& _]
  (let [summary (clojure.test/run-tests 'ct.spools.codethread.config-test)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
