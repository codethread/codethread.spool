#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

echo "==> git diff --check"
git diff --check

echo "==> make quality"
flock -w 180 /tmp/millstrand-test.lock make quality
