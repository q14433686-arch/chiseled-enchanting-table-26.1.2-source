#!/usr/bin/env bash
# Version-consistency gate.
#
# Borrowed from q14433686-arch/TaCZ_Refabricated_Unofficial's
# scripts/check_release_consistency.sh (their docs/ci/ convention), adapted to
# this repository's version surfaces. The failure class this prevents: bumping
# gradle.properties but forgetting README.md / fabric.mod.json / publish.yml
# (or vice versa) — exactly the drift that CURRENT_BASELINE.md recorded as
# "publish.yml pending manual apply" for several releases.
#
# Checked surfaces (all must agree):
#   gradle.properties   mod_version / minecraft_version / loader_version / fabric_version
#   README.md           H1 port label, "Current version:" line, "Target:" line
#   fabric.mod.json     name port label, depends.minecraft, depends.fabricloader
#   .github/workflows/publish.yml   VERSION / MINECRAFT_VERSION / RELEASE_NAME
#
# Usage: bash scripts/check_release_consistency.sh [--strict]
#   --strict  treat the "port label" checks (Unofficial <mc> Port wording in
#             README H1 / fabric.mod.json name / publish.yml RELEASE_NAME) as
#             errors too. Default: those are warnings only, because marketing
#             copy may briefly trail a version bump on purpose.
#
# Exit code 0 = consistent (warnings allowed), 1 = mismatch.

set -u

STRICT=0
for arg in "$@"; do
    case "$arg" in
        --strict) STRICT=1 ;;
        *) echo "unknown argument: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")/.."

python3 - "$STRICT" <<'PY'
import json, re, sys

strict = sys.argv[1] == "1"
errors, warnings = [], []

def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

# ---- gradle.properties (source of truth) -----------------------------------
gp = {}
for line in read("gradle.properties").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    gp[k.strip()] = v.strip()

mod_version = gp.get("mod_version")
mc_version = gp.get("minecraft_version")
loader_version = gp.get("loader_version")
fabric_version = gp.get("fabric_version")
for key in ("mod_version", "minecraft_version", "loader_version", "fabric_version"):
    if not gp.get(key):
        errors.append(f"gradle.properties: missing {key}")

# ---- README.md -------------------------------------------------------------
readme = read("README.md")

m = re.search(r"^#\s+Chiseled Enchanting Table\s+—\s+Unofficial\s+(\S+)\s+Port", readme, re.M)
readme_label = m.group(1) if m else None
if readme_label is None:
    errors.append("README.md: H1 does not match '# Chiseled Enchanting Table — Unofficial <mc> Port'")
elif mc_version and readme_label != mc_version:
    msg = f"README.md: H1 port label '{readme_label}' != gradle.properties minecraft_version '{mc_version}'"
    (errors if strict else warnings).append(msg)

m = re.search(r"\*\*Current version:\*\*\s*`([^`]+)`", readme)
readme_mod = m.group(1) if m else None
if readme_mod is None:
    errors.append("README.md: no '**Current version:** `X`' line")
elif mod_version and readme_mod != mod_version:
    errors.append(f"README.md: Current version '{readme_mod}' != gradle.properties mod_version '{mod_version}'")

m = re.search(
    r"\*\*Target:\*\*\s*Minecraft\s*`([^`]+)`,\s*Fabric Loader\s*`([^`]+)`,\s*Fabric API\s*`([^`]+)`",
    readme,
)
if not m:
    errors.append("README.md: no '**Target:** Minecraft `X`, Fabric Loader `Y`, Fabric API `Z`' line")
else:
    readme_mc, readme_loader, readme_fabric = m.groups()
    if mc_version and readme_mc != mc_version:
        errors.append(f"README.md: Target Minecraft '{readme_mc}' != gradle.properties minecraft_version '{mc_version}'")
    if loader_version and readme_loader != f">={loader_version}":
        errors.append(
            f"README.md: Target Fabric Loader '{readme_loader}' != '>={loader_version}' from gradle.properties loader_version"
        )
    if fabric_version and readme_fabric != fabric_version:
        errors.append(
            f"README.md: Target Fabric API '{readme_fabric}' != gradle.properties fabric_version '{fabric_version}'"
        )

# ---- fabric.mod.json -------------------------------------------------------
fmj = json.loads(read("src/main/resources/fabric.mod.json"))
name = fmj.get("name", "")
depends = fmj.get("depends", {})

if mc_version and f"Unofficial {mc_version} Port" not in name:
    msg = f"fabric.mod.json: name '{name}' does not contain 'Unofficial {mc_version} Port'"
    (errors if strict else warnings).append(msg)

fmj_mc = depends.get("minecraft")
if mc_version and fmj_mc != mc_version:
    errors.append(f"fabric.mod.json: depends.minecraft '{fmj_mc}' != gradle.properties minecraft_version '{mc_version}'")

fmj_loader = depends.get("fabricloader", "")
if loader_version and fmj_loader != f">={loader_version}":
    errors.append(
        f"fabric.mod.json: depends.fabricloader '{fmj_loader}' != '>={loader_version}' from gradle.properties loader_version"
    )

# ---- publish.yml -----------------------------------------------------------
# 两份都要看：docs/ci/publish.yml 是暂存区（沙箱 Agent 维护、维护者手动复制上线），
# .github/workflows/publish.yml 是正式件。正式件落后于暂存区且暂存区与
# gradle.properties 一致 => 只 WARN（记账「pending manual install」，见
# docs/ci/README.md）；暂存区自己对不上 => ERROR（Agent 忘了同步）。--strict
# 把正式件落后也升级为错误。
def check_pub(path, label, stale_is_error):
    try:
        pub = read(path)
    except FileNotFoundError:
        msg = f"{label}: 文件不存在"
        (errors if stale_is_error else warnings).append(msg)
        return

    def env_val(key):
        m = re.search(rf"^\s*{re.escape(key)}:\s*(.+?)\s*$", pub, re.M)
        return m.group(1) if m else None

    pub_version = env_val("VERSION")
    pub_mc = env_val("MINECRAFT_VERSION")
    pub_name = env_val("RELEASE_NAME")
    local_errors, local_msgs = [], []

    if not pub_version:
        local_errors.append(f"{label}: no VERSION: env")
    elif mod_version and pub_version != mod_version:
        local_errors.append(f"{label}: VERSION '{pub_version}' != gradle.properties mod_version '{mod_version}'")

    if not pub_mc:
        local_errors.append(f"{label}: no MINECRAFT_VERSION: env")
    elif mc_version and pub_mc != mc_version:
        local_errors.append(
            f"{label}: MINECRAFT_VERSION '{pub_mc}' != gradle.properties minecraft_version '{mc_version}'"
        )

    if not pub_name:
        local_errors.append(f"{label}: no RELEASE_NAME: env")
    else:
        if mod_version and mod_version not in pub_name:
            local_errors.append(f"{label}: RELEASE_NAME '{pub_name}' does not contain mod_version '{mod_version}'")
        if mc_version and f"Unofficial {mc_version} Port" not in pub_name:
            local_msgs.append(f"{label}: RELEASE_NAME '{pub_name}' does not contain 'Unofficial {mc_version} Port'")

    if local_errors:
        errors.extend(local_errors)
    elif local_msgs:
        for m in local_msgs:
            (errors if strict else warnings).append(m)
    elif stale_is_error is False:
        pass
    return

# 暂存区必须与 gradle.properties 一致 —— 这是 Agent 的同步责任
check_pub("docs/ci/publish.yml", "docs/ci/publish.yml", stale_is_error=True)
# 正式件：落后于暂存区是「pending manual install」，默认只警告
_pub_staged_ok = not any(e.startswith("docs/ci/publish.yml") for e in errors)
try:
    read(".github/workflows/publish.yml")
    installed_matches = True
    probe = read(".github/workflows/publish.yml")
    for key, want in (("VERSION", mod_version), ("MINECRAFT_VERSION", mc_version)):
        m = re.search(rf"^\s*{key}:\s*(.+?)\s*$", probe, re.M)
        if want and (not m or m.group(1) != want):
            installed_matches = False
    if not installed_matches and _pub_staged_ok:
        warnings.append(
            ".github/workflows/publish.yml: 落后于 docs/ci/publish.yml（pending manual install —— "
            "请把 docs/ci/publish.yml 复制到 .github/workflows/publish.yml，见 docs/ci/README.md）"
        )
        if strict:
            errors.append(".github/workflows/publish.yml: --strict 模式下正式件落后暂存区即为错误")
    else:
        check_pub(".github/workflows/publish.yml", ".github/workflows/publish.yml", stale_is_error=True)
except FileNotFoundError:
    warnings.append(".github/workflows/publish.yml: 不存在（pending manual install from docs/ci/）")

# ---- report ----------------------------------------------------------------
for w in warnings:
    print(f"WARN:  {w}")
for e in errors:
    print(f"ERROR: {e}")
print(
    f"check_release_consistency: {len(errors)} error(s), {len(warnings)} warning(s) "
    f"[mod_version={mod_version} minecraft={mc_version} loader={loader_version} fabric={fabric_version}]"
)
sys.exit(1 if errors else 0)
PY
