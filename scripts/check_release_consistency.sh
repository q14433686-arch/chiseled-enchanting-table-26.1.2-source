#!/usr/bin/env bash
# Version consistency gate — modelled on scripts/check_release_consistency.sh of the
# sister repository TaCZ_Refabricated_Unofficial.
#
# Why this exists: the 26.2 round shipped with .github/workflows/publish.yml still saying
# MINECRAFT_VERSION: 26.1.2 while gradle.properties said 26.2 (see
# docs/reports/PORTING_26_2_REPORT.md §2). Nothing failed, because nothing checked.
# This script makes "I bumped the version in one place and forgot the other four" a red PR.
#
# Usage:
#   bash scripts/check_release_consistency.sh              # report only (exit 0)
#   bash scripts/check_release_consistency.sh --strict     # CI / release gate (exit 1 on mismatch)
#   bash scripts/check_release_consistency.sh --staged     # read the git index (pre-commit hook)
#   bash scripts/check_release_consistency.sh --porcelain  # FAIL\t<message> lines only
#
# Checked sources of truth:
#   gradle.properties                -> mod_version / minecraft_version / loader_version / fabric_version
#   README.md                        -> title, "Current version", "Target" line, download blurb
#   src/main/resources/fabric.mod.json -> depends.minecraft, name, description, fabricloader floor
#   .github/workflows/publish.yml    -> MINECRAFT_VERSION, VERSION, RELEASE_NAME
#
# Exit codes: 0 = consistent (or report-only mode), 1 = mismatch with --strict, 2 = bad usage.

set -uo pipefail

MODE="worktree"
STRICT=0
PORCELAIN=0

usage() { sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --strict)    STRICT=1 ;;
    --staged)    MODE="index" ;;
    --porcelain) PORCELAIN=1 ;;
    --no-color)  NO_COLOR=1 ;;
    -h|--help)   usage; exit 0 ;;
    *) printf 'unknown argument: %s (see --help)\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

TOPLEVEL="$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

if [[ -n "${NO_COLOR:-}" || $PORCELAIN -eq 1 || ! -t 1 ]]; then
  C_RED=""; C_GRN=""; C_YLW=""; C_RST=""
else
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YLW=$'\033[33m'; C_RST=$'\033[0m'
fi

N_PASS=0; N_FAIL=0; N_WARN=0
pass() { N_PASS=$((N_PASS+1)); [[ $PORCELAIN -eq 1 ]] || printf '  %sok:%s   %s\n' "$C_GRN" "$C_RST" "$*"; }
fail() { N_FAIL=$((N_FAIL+1)); if [[ $PORCELAIN -eq 1 ]]; then printf 'FAIL\t%s\n' "$*"; else printf '  %sFAIL:%s %s\n' "$C_RED" "$C_RST" "$*"; fi; }
warn() { N_WARN=$((N_WARN+1)); if [[ $PORCELAIN -eq 1 ]]; then printf 'WARN\t%s\n' "$*"; else printf '  %swarn:%s %s\n' "$C_YLW" "$C_RST" "$*"; fi; }

read_source() {
  case "$MODE" in
    index) git show ":$1" 2>/dev/null ;;
    *)     cat -- "$TOPLEVEL/$1" 2>/dev/null ;;
  esac
}

GRADLE="$(read_source gradle.properties)"
README="$(read_source README.md)"
MODJSON="$(read_source src/main/resources/fabric.mod.json)"
PUBLISH="$(read_source .github/workflows/publish.yml)"
# .github/workflows/ is not writable with a GitHub App token that lacks the `workflows`
# permission, so the corrected publish.yml is staged in docs/ci/ instead (see docs/ci/README.md).
STAGED_PUBLISH="$(read_source docs/ci/publish.yml)"

[[ $PORCELAIN -eq 1 ]] || printf '=== version consistency (%s) ===\n' "$MODE"

if [[ -z "$GRADLE" ]]; then
  fail "cannot read gradle.properties"
  [[ $STRICT -eq 1 ]] && exit 1
  exit 0
fi

prop() { grep -E "^[[:space:]]*$1[[:space:]]*=" <<<"$GRADLE" | head -1 | cut -d= -f2- | tr -d '[:space:]'; }

MOD_VERSION="$(prop mod_version)"
MC_VERSION="$(prop minecraft_version)"
LOADER_VERSION="$(prop loader_version)"
FABRIC_VERSION="$(prop fabric_version)"

for pair in "mod_version:$MOD_VERSION" "minecraft_version:$MC_VERSION" \
            "loader_version:$LOADER_VERSION" "fabric_version:$FABRIC_VERSION"; do
  key="${pair%%:*}"; val="${pair#*:}"
  [[ -z "$val" ]] && fail "gradle.properties is missing '$key'"
done
[[ $N_FAIL -gt 0 ]] && { [[ $STRICT -eq 1 ]] && exit 1; exit 0; }

[[ $PORCELAIN -eq 1 ]] || printf '  gradle.properties: mod %s / mc %s / loader %s / fabric-api %s\n' \
  "$MOD_VERSION" "$MC_VERSION" "$LOADER_VERSION" "$FABRIC_VERSION"

# --- 1) Fabric API build must target the same Minecraft ------------------------
case "$FABRIC_VERSION" in
  *"+$MC_VERSION") pass "fabric_version $FABRIC_VERSION targets Minecraft $MC_VERSION" ;;
  *) fail "fabric_version '$FABRIC_VERSION' does not end in '+$MC_VERSION'" ;;
esac

# --- 2) README ----------------------------------------------------------------
if [[ -z "$README" ]]; then
  fail "cannot read README.md"
else
  readme_version="$(grep -oE '\*\*Current version:\*\* `[^`]+`' <<<"$README" | head -1 | sed -E 's/.*`([^`]+)`.*/\1/')"
  if [[ -z "$readme_version" ]]; then
    fail "README.md has no '**Current version:** \`x.y.z\`' line"
  elif [[ "$readme_version" != "$MOD_VERSION" ]]; then
    fail "README says version '$readme_version', gradle.properties says '$MOD_VERSION'"
  else
    pass "README version matches gradle.properties ($MOD_VERSION)"
  fi

  readme_mc="$(grep -oE '\*\*Target:\*\* Minecraft `[^`]+`' <<<"$README" | head -1 | sed -E 's/.*`([^`]+)`.*/\1/')"
  if [[ -z "$readme_mc" ]]; then
    fail "README.md has no '**Target:** Minecraft \`x.y\`' line"
  elif [[ "$readme_mc" != "$MC_VERSION" ]]; then
    fail "README targets Minecraft '$readme_mc', gradle.properties says '$MC_VERSION'"
  else
    pass "README target matches gradle.properties (Minecraft $MC_VERSION)"
  fi

  if grep -qF "$FABRIC_VERSION" <<<"$README"; then
    pass "README mentions Fabric API $FABRIC_VERSION"
  else
    fail "README does not mention Fabric API '$FABRIC_VERSION' (stale dependency blurb)"
  fi

  mc_flat="${MC_VERSION//./\\.}"
  if grep -qE "^# .*Unofficial ${mc_flat} Port" <<<"$README"; then
    pass "README title advertises the $MC_VERSION port"
  else
    fail "README title does not say 'Unofficial $MC_VERSION Port'"
  fi

  if grep -qE "chiseled-enchanting-table-${MOD_VERSION//./\\.}\.jar" <<<"$README"; then
    pass "README download blurb names the $MOD_VERSION jar"
  else
    fail "README download blurb does not name 'chiseled-enchanting-table-$MOD_VERSION.jar'"
  fi
fi

# --- 3) fabric.mod.json -------------------------------------------------------
if [[ -z "$MODJSON" ]]; then
  fail "cannot read src/main/resources/fabric.mod.json"
else
  dep_mc="$(python3 - "$MODJSON" <<'PY'
import json, sys
data = json.loads(sys.argv[1])
print((data.get("depends") or {}).get("minecraft", ""))
PY
)"
  if [[ -z "$dep_mc" ]]; then
    fail "fabric.mod.json declares no depends.minecraft"
  elif [[ "$dep_mc" != "$MC_VERSION" && "$dep_mc" != "~$MC_VERSION" && "$dep_mc" != ">=$MC_VERSION" ]]; then
    fail "fabric.mod.json depends.minecraft is '$dep_mc', gradle.properties says '$MC_VERSION'"
  else
    pass "fabric.mod.json depends.minecraft matches ($dep_mc)"
  fi

  loader_floor="$(python3 - "$MODJSON" <<'PY'
import json, re, sys
data = json.loads(sys.argv[1])
raw = (data.get("depends") or {}).get("fabricloader", "")
m = re.search(r"([0-9]+(?:\.[0-9]+)+)", raw)
print(m.group(1) if m else "")
PY
)"
  if [[ -n "$loader_floor" ]]; then
    if [[ "$(printf '%s\n%s\n' "$loader_floor" "$LOADER_VERSION" | sort -V | head -1)" == "$loader_floor" ]]; then
      pass "fabric.mod.json fabricloader floor ($loader_floor) is satisfiable by pinned loader $LOADER_VERSION"
    else
      fail "fabric.mod.json requires fabricloader >=$loader_floor but gradle pins $LOADER_VERSION"
    fi
  fi

  if grep -qF "$MC_VERSION" <<<"$MODJSON"; then
    pass "fabric.mod.json name/description mention $MC_VERSION"
  else
    fail "fabric.mod.json never mentions '$MC_VERSION' (stale port identity strings)"
  fi
fi

# --- 4) publish workflow ------------------------------------------------------
# A stale publish.yml whose corrected copy is already staged in docs/ci/publish.yml is reported as
# a warning (the fix exists, it is only blocked by the missing `workflows` token permission).
# An unstaged drift stays a hard failure.
publish_staged_ok() {
  [[ -n "$STAGED_PUBLISH" ]] \
    && grep -qE "^[[:space:]]*MINECRAFT_VERSION:[[:space:]]*${MC_VERSION}[[:space:]]*$" <<<"$STAGED_PUBLISH" \
    && grep -qE "^[[:space:]]*VERSION:[[:space:]]*${MOD_VERSION}[[:space:]]*$" <<<"$STAGED_PUBLISH"
}

publish_report() {
  if publish_staged_ok; then
    warn "publish.yml is stale, but docs/ci/publish.yml already carries $MC_VERSION / $MOD_VERSION — apply it with 'bash docs/ci/install.sh' (needs a token with the 'workflows' permission)"
  else
    fail "$1"
  fi
}

if [[ -z "$PUBLISH" ]]; then
  fail "cannot read .github/workflows/publish.yml"
else
  pub_mc="$(grep -E '^[[:space:]]*MINECRAFT_VERSION:' <<<"$PUBLISH" | head -1 | sed -E 's/.*MINECRAFT_VERSION:[[:space:]]*//' | tr -d '[:space:]')"
  pub_ver="$(grep -E '^[[:space:]]*VERSION:' <<<"$PUBLISH" | head -1 | sed -E 's/.*VERSION:[[:space:]]*//' | tr -d '[:space:]')"
  pub_name="$(grep -E '^[[:space:]]*RELEASE_NAME:' <<<"$PUBLISH" | head -1)"

  if [[ -z "$pub_mc" ]]; then
    publish_report "publish.yml has no MINECRAFT_VERSION env"
  elif [[ "$pub_mc" != "$MC_VERSION" ]]; then
    publish_report "publish.yml MINECRAFT_VERSION is '$pub_mc', gradle.properties says '$MC_VERSION'"
  else
    pass "publish.yml MINECRAFT_VERSION matches ($pub_mc)"
  fi

  if [[ -z "$pub_ver" ]]; then
    publish_report "publish.yml has no VERSION env"
  elif [[ "$pub_ver" != "$MOD_VERSION" ]]; then
    publish_report "publish.yml VERSION is '$pub_ver', gradle.properties mod_version is '$MOD_VERSION'"
  else
    pass "publish.yml VERSION matches ($pub_ver)"
  fi

  if [[ -n "$pub_name" ]] && ! grep -qF "$MC_VERSION" <<<"$pub_name"; then
    publish_report "publish.yml RELEASE_NAME does not mention '$MC_VERSION': $pub_name"
  elif [[ -n "$pub_name" ]]; then
    pass "publish.yml RELEASE_NAME mentions $MC_VERSION"
  fi
fi

[[ $PORCELAIN -eq 1 ]] || printf '\n%d passed, %d warned, %d failed\n' "$N_PASS" "$N_WARN" "$N_FAIL"


if [[ $N_FAIL -gt 0 ]]; then
  if [[ $STRICT -eq 1 ]]; then
    [[ $PORCELAIN -eq 1 ]] || printf '::error::version consistency gate failed (%d check(s))\n' "$N_FAIL"
    exit 1
  fi
  [[ $PORCELAIN -eq 1 ]] || printf 'report-only mode: not blocking. run with --strict to gate.\n'
fi
exit 0
