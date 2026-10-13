#!/usr/bin/env bash
# Copy the staged CI triad into .github/workflows/.
#
#   bash docs/ci/install.sh            # copy + show what changed
#   bash docs/ci/install.sh --check    # report drift without writing (CI-safe)
#
# Needs a git identity whose token has the `workflows` permission; a GitHub App
# installation token without it will be rejected at push time (see docs/ci/README.md).

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
STAGE="$REPO_ROOT/docs/ci"
TARGET="$REPO_ROOT/.github/workflows"

MODE="install"
[[ "${1:-}" == "--check" ]] && MODE="check"

FILES=(compile-check.yml build.yml consistency.yml publish.yml)

drift=0
for f in "${FILES[@]}"; do
  src="$STAGE/$f"
  dst="$TARGET/$f"
  if [[ ! -f "$src" ]]; then
    echo "missing staged file: docs/ci/$f" >&2
    exit 1
  fi
  if [[ -f "$dst" ]] && cmp -s "$src" "$dst"; then
    echo "ok       .github/workflows/$f (already up to date)"
    continue
  fi
  drift=1
  if [[ "$MODE" == "check" ]]; then
    if [[ -f "$dst" ]]; then
      echo "DRIFT    .github/workflows/$f differs from docs/ci/$f"
    else
      echo "MISSING  .github/workflows/$f (staged at docs/ci/$f)"
    fi
    continue
  fi
  mkdir -p "$TARGET"
  cp -- "$src" "$dst"
  echo "installed .github/workflows/$f"
done

if [[ "$MODE" == "check" ]]; then
  if [[ $drift -eq 1 ]]; then
    echo ""
    echo "docs/ci/ is ahead of .github/workflows/ — run: bash docs/ci/install.sh"
    exit 1
  fi
  exit 0
fi

echo ""
echo "Next:"
echo "  git add .github/workflows"
echo "  git commit -m \"ci: install the 26.3 CI triad\""
echo "  git push        # requires a token with the 'workflows' permission"
echo ""
git --no-pager diff --stat -- .github/workflows || true
