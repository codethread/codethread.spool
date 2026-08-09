(ns ct.spools.codethread.spool-bump.internal.support
  "Shared script helpers for the repo's independently loaded workflow definitions."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn non-blank-string?
  "Return true when v is a non-blank string."
  [v]
  (and (string? v) (not (str/blank? v))))

(defn script
  "Return the frozen source of named workspace script."
  [name]
  (if-let [resource (io/resource
                     (str "ct/spools/codethread/spool_bump/scripts/" name))]
    (slurp resource)
    (throw (ex-info "Missing spool-bump support script" {:script name}))))

(defn sh-gate
  "Return shell argv that runs script with name as `$0` and args as positionals."
  [script name & args]
  (into ["sh" "-c" script name] args))

(def feature-ci-watch-script
  "POSIX script for the feature CI shell gate."
  (script "feature-ci-watch.sh"))

(def land-pull-main-script
  "Fast-forward the canonical main checkout to origin/main.

  This stays inline as the small-script exemplar: eight lines of shell and no
  data-shaping logic do not earn a separate file."
  (str "set -eu\n"
       "root=$(dirname \"$(git rev-parse --path-format=absolute --git-common-dir)\")\n"
       "branch=$(git -C \"$root\" branch --show-current)\n"
       "if [ \"$branch\" != main ]; then\n"
       "  echo \"refusing to update canonical checkout: expected main, found $branch\" >&2\n"
       "  exit 1\n"
       "fi\n"
       "git -C \"$root\" pull --ff-only origin main\n"))
