#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for spool in agents spool-bump devflow ralph; do
  echo "==> $spool"
  (cd "$root/spools/$spool" && clojure -M:test)
done
