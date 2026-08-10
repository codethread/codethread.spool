#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for spool in agents spool-bump devflow-setup ralph; do
  echo "==> $spool"
  (cd "$root/spools/$spool" && clojure -M:test)
done

echo "==> ralph Go"
go_root="$root/spools/ralph"
if command -v gofumpt >/dev/null 2>&1; then
  test -z "$(cd "$go_root" && gofumpt -l .)"
else
  test -z "$(cd "$go_root" && gofmt -l .)"
fi
go -C "$go_root" vet ./...
go -C "$go_root" test ./...
go -C "$go_root" build -o /dev/null .
