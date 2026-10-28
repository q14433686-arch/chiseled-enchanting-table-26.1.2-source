#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

prop() {
  grep -E "^$1=" gradle.properties | head -n1 | cut -d= -f2- | tr -d '\r'
}

MOD_VER="$(prop mod_version)"
MC_VER="$(prop minecraft_version)"
LOADER_VER="$(prop loader_version)"
FABRIC_VER="$(prop fabric_version)"
ARCHIVES_NAME="$(prop archives_base_name)"

fail=0
err() { echo "ERROR: $*" >&2; fail=1; }

[[ -n "$MOD_VER" && -n "$MC_VER" && -n "$LOADER_VER" && -n "$FABRIC_VER" ]] \
  || err "Missing core version coordinates in gradle.properties"

grep -Fq "**Current version:** \`${MOD_VER}\`" README.md \
  || err "README.md missing '**Current version:** \`${MOD_VER}\`'"
grep -Fq "Minecraft \`${MC_VER}\`" README.md \
  || err "README.md missing 'Minecraft \`${MC_VER}\`'"
grep -Fq "Fabric API \`${FABRIC_VER}\`" README.md \
  || err "README.md missing 'Fabric API \`${FABRIC_VER}\`'"
grep -Fq "${ARCHIVES_NAME}-${MOD_VER}.jar" README.md \
  || err "README.md missing jar filename '${ARCHIVES_NAME}-${MOD_VER}.jar'"

python3 - "$MC_VER" "$LOADER_VER" <<'PY' || fail=1
import json, sys
mc_ver, loader_ver = sys.argv[1], sys.argv[2]
fmj = json.loads(open("src/main/resources/fabric.mod.json", encoding="utf-8").read())
assert fmj["id"] == "enchantment_fusion", f"Unexpected mod id: {fmj['id']}"
assert mc_ver in fmj["depends"]["minecraft"], f"fabric.mod.json minecraft dep mismatch: {fmj['depends']['minecraft']}"
assert loader_ver in fmj["depends"]["fabricloader"], f"fabric.mod.json loader dep mismatch: {fmj['depends']['fabricloader']}"
assert mc_ver in fmj["description"], "fabric.mod.json description should mention target MC version"
PY

if [[ -f docs/ci/publish.yml ]]; then
  grep -Eq "MINECRAFT_VERSION:[[:space:]]*['\"]?${MC_VER}['\"]?" docs/ci/publish.yml \
    || err "docs/ci/publish.yml MINECRAFT_VERSION mismatch (expected ${MC_VER})"
  grep -Eq "VERSION:[[:space:]]*['\"]?${MOD_VER}['\"]?" docs/ci/publish.yml \
    || err "docs/ci/publish.yml VERSION mismatch (expected ${MOD_VER})"
fi

# Note: .github/workflows/publish.yml cannot be modified by tokens lacking `workflows: write`;
# report drift as a non-fatal warning just like consistency.yml's install.sh --check step.
if [[ -f .github/workflows/publish.yml ]] && ! cmp -s docs/ci/publish.yml .github/workflows/publish.yml; then
  echo "WARN: .github/workflows/publish.yml differs from docs/ci/publish.yml (workflows permission required to sync)"
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "ok: release consistency verified (${ARCHIVES_NAME} ${MOD_VER} on MC ${MC_VER} / Fabric ${FABRIC_VER})"
