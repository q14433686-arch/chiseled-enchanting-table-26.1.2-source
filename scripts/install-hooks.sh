#!/usr/bin/env bash
# Install the warn-only pre-commit reminder hook — run once, applies to every branch.
# Ported from TaCZ_Refabricated_Unofficial/scripts/install-hooks.sh.
#
#   bash scripts/install-hooks.sh              # install / upgrade
#   bash scripts/install-hooks.sh --uninstall  # remove
#   bash scripts/install-hooks.sh --print      # print the hook without writing it
#   bash scripts/install-hooks.sh --force      # overwrite someone else's pre-commit (backs it up)
#
# Why not `git config core.hooksPath .githooks`?
#   .githooks/ is branch content: switching to a branch without that directory makes the
#   reminder silently disappear. Installing into the hooks dir is branch-independent.
#
# Why --git-common-dir instead of --git-dir?
#   In a linked worktree --git-dir is .git/worktrees/<name>/, so the hook would only apply
#   to that worktree. The common dir is the main .git/, so one install covers all worktrees.

set -euo pipefail

MARKER="Reminder: version declarations"

MODE="install"
FORCE=0
for arg in "$@"; do
  case "$arg" in
    --uninstall) MODE="uninstall" ;;
    --print)     MODE="print" ;;
    --force)     FORCE=1 ;;
    -h|--help)   sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) printf 'unknown argument: %s (see --help)\n' "$arg" >&2; exit 2 ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "$REPO_ROOT" ]]; then
  echo "error: not inside a git repository." >&2
  exit 1
fi

SOURCE_HOOK="$REPO_ROOT/.githooks/pre-commit"

if [[ "$MODE" == "print" ]]; then
  cat -- "$SOURCE_HOOK"
  exit 0
fi

COMMON_DIR="$(git rev-parse --git-common-dir 2>/dev/null || git rev-parse --git-dir)"
case "$COMMON_DIR" in
  /*) ;;
  *) COMMON_DIR="$(cd "$COMMON_DIR" && pwd)" ;;
esac
HOOK="$COMMON_DIR/hooks/pre-commit"

if [[ "$MODE" == "uninstall" ]]; then
  if [[ ! -e "$HOOK" ]]; then
    echo "not installed, nothing to do: $HOOK"
    exit 0
  fi
  if ! grep -q "$MARKER" "$HOOK" 2>/dev/null && [[ $FORCE -eq 0 ]]; then
    echo "refusing to delete: $HOOK was not installed by this script (use --force)." >&2
    exit 1
  fi
  rm -f -- "$HOOK"
  echo "removed: $HOOK"
  exit 0
fi

if [[ ! -f "$SOURCE_HOOK" ]]; then
  echo "error: missing $SOURCE_HOOK" >&2
  exit 1
fi

if [[ -e "$HOOK" ]] && ! grep -q "$MARKER" "$HOOK" 2>/dev/null && [[ $FORCE -eq 0 ]]; then
  echo "refusing to overwrite an existing pre-commit hook: $HOOK" >&2
  echo "re-run with --force to replace it (a .bak copy is kept)." >&2
  exit 1
fi

mkdir -p -- "$(dirname "$HOOK")"
if [[ -e "$HOOK" ]] && ! grep -q "$MARKER" "$HOOK" 2>/dev/null; then
  cp -- "$HOOK" "$HOOK.bak"
  echo "backed up existing hook to $HOOK.bak"
fi

cp -- "$SOURCE_HOOK" "$HOOK"
chmod +x -- "$HOOK"
echo "installed: $HOOK"
echo "the hook only warns; CI (build.yml / consistency.yml) is the real gate."
