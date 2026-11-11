#!/usr/bin/env bash
#
# Clones every repository listed in repos.txt next to trust-getting-started.
# Those already present are left untouched. Safe to run again.
#
#   TRUST_GIT_BASE_URL  where to clone from (default: this repository's own
#                       origin, minus its name - e.g. git@github.com:my-org)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$(dirname "$ROOT_DIR")"

BASE_URL="${TRUST_GIT_BASE_URL:-}"
if [ -z "$BASE_URL" ]; then
  ORIGIN="$(git -C "$ROOT_DIR" remote get-url origin 2>/dev/null || true)"
  if [ -z "$ORIGIN" ]; then
    echo "ERROR: set TRUST_GIT_BASE_URL (this repository has no 'origin' to derive it from)." >&2
    exit 1
  fi
  BASE_URL="${ORIGIN%/*}"
fi
echo "Cloning from $BASE_URL into $WORKSPACE"

while read -r repo branch; do
  case "$repo" in ''|'#'*) continue ;; esac
  if [ -d "$WORKSPACE/$repo" ]; then
    echo "[skip] $repo (already present)"
    continue
  fi
  git clone --quiet ${branch:+--branch "$branch"} "$BASE_URL/$repo.git" "$WORKSPACE/$repo" \
    || { echo "ERROR: could not clone $BASE_URL/$repo.git" >&2; exit 1; }
  echo "[OK] $repo"
done < "$ROOT_DIR/repos.txt"
