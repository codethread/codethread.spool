#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for spool in agents devflow-setup ralph; do
  echo "==> $spool"
  (cd "$root/spools/$spool" && clojure -M:test)
done

echo "==> ralph Go"
go_root="$root/spools/ralph"
# Millstrand's Go format standard is pinned so the gate is identical on every machine.
test -z "$(cd "$go_root" && go run mvdan.cc/gofumpt@v0.8.0 -l .)"
go -C "$go_root" vet ./...
go -C "$go_root" test ./...
go -C "$go_root" build -o /dev/null .
