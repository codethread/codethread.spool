(ns ct.spools.codethread.shared-landing-consumer-smoke
  "Exercise local shared landing source through consumer workspace configuration.

  Every consumer runs in a disposable Weaver world. The fixture preserves its
  checked-in init and workspace files while replacing published coordinates
  with explicit local checkout roots supplied by the caller. This smoke covers
  consumer integration with the supplied source, not the checked-in pins."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [millstrand.api.runtime.alpha :as runtime]
            [millstrand.api.weaver.alpha :as weaver]
            [millstrand.test.alpha :as t]))

(defn- canonical-directory! [label path]
  (let [directory (.getCanonicalFile (io/file path))]
    (when-not (.isDirectory directory)
      (throw (ex-info (str label " must be a directory")
                      {:label label :path path})))
    directory))

(defn- local-root [root relative-path]
  {:local/root (.getCanonicalPath (io/file root relative-path))})

(defn- normalize-consumer-deps [deps consumer]
  (update deps :deps
          (fn [coordinates]
            (into {}
                  (map (fn [[library coordinate]]
                         [library
                          (if-let [path (:local/root coordinate)]
                            (local-root (io/file consumer ".millstrand") path)
                            coordinate)]))
                  coordinates))))

(defn- local-overrides [{:keys [codethread devflow harnesses millhouse]}]
  {'millhouse.spools/workflow (local-root millhouse "spools/workflow")
   'millhouse.spools/identity (local-root millhouse "spools/identity")
   'millhouse.spools/kanban (local-root millhouse "spools/kanban")
   'millhouse.spools/land (local-root millhouse "spools/land")
   'ct.spools/harnesses (local-root harnesses ".")
   'codethread/config (local-root codethread "spools/config")
   'codethread/ralph (local-root codethread "spools/ralph")
   'codethread/devflow (local-root devflow ".")
   'codethread/devflow-kanban-adapter
   (local-root devflow "kanban-adapter")})

(defn- fixture-deps [roots consumer]
  (let [deps-file (io/file consumer ".millstrand/deps.edn")]
    (when-not (.isFile deps-file)
      (throw (ex-info "Consumer has no .millstrand/deps.edn"
                      {:consumer (.getPath consumer)})))
    (-> (edn/read-string (slurp deps-file))
        (normalize-consumer-deps consumer)
        (update :deps merge (local-overrides roots))
        pr-str)))

(defn- fixture-files [consumer]
  (let [workspace (.getCanonicalFile (io/file consumer ".millstrand"))]
    (into {}
          (comp
           (mapcat (fn [directory]
                     (let [root (io/file workspace directory)]
                       (if (.isDirectory root) (file-seq root) []))))
           (filter #(.isFile ^java.io.File %))
           (map (fn [file]
                  [(str (.relativize (.toPath workspace) (.toPath file)))
                   (slurp file)])))
          ["config" "me" "workflows"])))

(defn- contains-name? [form expected]
  (let [nodes (set (tree-seq coll? seq form))]
    (or (contains? nodes expected)
        (contains? nodes (keyword expected)))))

(defn- require-smoke! [valid? message data]
  (when-not valid?
    (throw (ex-info message data))))

(defn- smoke-consumer! [roots consumer]
  (let [init-file (io/file consumer ".millstrand/init.clj")]
    (when-not (.isFile init-file)
      (throw (ex-info "Consumer has no .millstrand/init.clj"
                      {:consumer (.getPath consumer)})))
    (t/run-with-weaver-world
     {:storage :sqlite-memory
      :deps-edn (fixture-deps roots consumer)
      :init-clj (slurp init-file)
      :files (fixture-files consumer)}
     (fn [{:keys [runtime]}]
       (let [consumer-path (.getPath consumer)
             status (runtime/status runtime)
             op-names (set (map :name (weaver/ops runtime)))
             queue-status (weaver/op! runtime 'merge-queue ["status"])
             workflow-list (weaver/op! runtime 'workflow ["list"])
             workflow-names (set (map :name (:definitions workflow-list)))
             review (weaver/op! runtime 'workflow ["show" "review"])
             land (weaver/op! runtime 'workflow ["show" "land"])]
         (require-smoke!
          (= :applied
             (get-in status [:last-refresh :modules
                             :millhouse/spools-land :status]))
          "Shared landing module did not activate"
          {:consumer consumer-path :status status})
         (require-smoke! (contains? op-names "merge-queue")
                         "merge-queue operation is not visible"
                         {:consumer consumer-path :operations op-names})
         (require-smoke! (= {:lock nil :entries []}
                            (select-keys queue-status [:lock :entries]))
                         "merge-queue status is not initially empty"
                         {:consumer consumer-path :queue-status queue-status})
         (require-smoke! (every? workflow-names ["review" "land"])
                         "shared review and land workflows are not listed"
                         {:consumer consumer-path :workflows workflow-names})
         (require-smoke! (= "reviewer" (get-in land [:params :defaults :reviewer]))
                         "land does not expose the shared reviewer default"
                         {:consumer consumer-path :land land})
         (require-smoke! (contains-name? (get-in land [:declared :calls])
                                         "review")
                         "land does not call the mandatory shared review"
                         {:consumer consumer-path :land land})
         (require-smoke!
          (and (contains-name? (get-in review [:declared :gates])
                               "review-agent")
               (contains-name? (get-in review [:declared :checkpoints])
                               "resolve-review"))
          "review does not expose agent findings and coordinator resolution"
          {:consumer consumer-path :review review})
         (doseq [workflow-name ["land-merge" "land-abort"]]
           (require-smoke!
            (= workflow-name
               (:name (weaver/op! runtime 'workflow ["show" workflow-name])))
            "Continuation workflow is not visible"
            {:consumer consumer-path :workflow workflow-name}))
         (println "shared landing local-source smoke: clean"
                  consumer-path))))))

(defn -main
  "Exercise each consumer using disposable local dependency overrides.

  Arguments are MILLHOUSE CODETHREAD HARNESSES DEVFLOW followed by one or more
  consumer checkout roots. This command does not verify published pins."
  [& paths]
  (when (< (count paths) 5)
    (throw (ex-info
            (str "Usage: shared-landing-consumer-smoke "
                 "MILLHOUSE CODETHREAD HARNESSES DEVFLOW CONSUMER...")
            {:arguments paths})))
  (let [[millhouse codethread harnesses devflow & consumers] paths
        roots {:millhouse (canonical-directory! "MILLHOUSE" millhouse)
               :codethread (canonical-directory! "CODETHREAD" codethread)
               :harnesses (canonical-directory! "HARNESSES" harnesses)
               :devflow (canonical-directory! "DEVFLOW" devflow)}]
    (doseq [consumer consumers]
      (smoke-consumer! roots (canonical-directory! "CONSUMER" consumer)))))
