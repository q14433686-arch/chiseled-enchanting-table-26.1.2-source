#!/usr/bin/env python3
"""Create a conservative item-by-item index of one integrated-game report.

This only indexes the supplied report; it never starts Minecraft and does not
turn a narrow fixture PASS into a full direction PASS.  The eventual acceptance
suite must add complete, separately scoped gameplay cases before items can be
marked fully passed.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

from verify_fusion_designs import ROOT, category, parse_coverage, read_doc

# A unique fixture failure can establish failure of the direction it exercises.
# Shared probes run multiple directions and cannot attribute a failure to either;
# neither kind of probe presently establishes a *complete* direction PASS.
DIRECTION_FIXTURES = {
    ("fire_aspect", "sweeping_edge"): ("02-fire-sweep", True),
    ("sweeping_edge", "fire_aspect"): ("02-sweep-fire", True),
    ("knockback", "sweeping_edge"): ("03-knockback-sweep", True),
    ("sweeping_edge", "knockback"): ("03-sweep-knockback", True),
    ("sharpness", "smite"): ("04-sharpness-smite", True),
    ("smite", "sharpness"): ("04-smite-sharpness", True),
    ("silk_touch", "fortune"): ("06-silk-fortune", True),
    ("fortune", "silk_touch"): ("06-fortune-silk", True),
    ("sharpness", "bane_of_arthropods"): ("10-sharpness-bane", True),
    ("bane_of_arthropods", "sharpness"): ("10-bane-sharpness", True),
    ("sharpness", "unbreaking"): ("42.1-sharp-unbreaking", True),
    ("unbreaking", "sharpness"): ("42.1-unbreaking-sharp", True),
    ("smite", "unbreaking"): ("42.1-smite-unbreaking", True),
    ("unbreaking", "smite"): ("42.1-unbreaking-smite", True),
    ("bane_of_arthropods", "unbreaking"): ("42.1-bane-unbreaking", True),
    ("unbreaking", "bane_of_arthropods"): ("42.1-unbreaking-bane", True),
    ("sharpness", "respiration"): ("42.2-sharp-breath", True),
    ("respiration", "sharpness"): ("42.2-breath-sharp", True),
    ("smite", "respiration"): ("42.2-smite-breath", True),
    ("respiration", "smite"): ("42.2-breath-smite", True),
    ("bane_of_arthropods", "respiration"): ("42.2-bane-breath", True),
    ("respiration", "bane_of_arthropods"): ("42.2-breath-bane", True),
    ("sharpness", "protection"): ("42.4-sharp-protection", True),
    ("protection", "sharpness"): ("42.4-protection-sharp", True),
    ("smite", "protection"): ("42.4-smite-protection", True),
    ("protection", "smite"): ("42.4-protection-smite", True),
    ("bane_of_arthropods", "protection"): ("42.4-bane-protection", True),
    ("protection", "bane_of_arthropods"): ("42.4-protection-bane", True),
    ("sharpness", "knockback"): ("42.5-sharp-knockback", True),
    ("knockback", "sharpness"): ("42.5-knockback-sharp", True),
    ("smite", "knockback"): ("42.5-smite-knockback", True),
    ("knockback", "smite"): ("42.5-knockback-smite", True),
    ("bane_of_arthropods", "knockback"): ("42.5-bane-knockback", True),
    ("knockback", "bane_of_arthropods"): ("42.5-knockback-bane", True),
    ("sharpness", "efficiency"): ("42.6-sharp-efficiency", True),
    ("efficiency", "sharpness"): ("42.6-efficiency-sharp", True),
    ("smite", "efficiency"): ("42.6-smite-efficiency", True),
    ("efficiency", "smite"): ("42.6-efficiency-smite", True),
    ("bane_of_arthropods", "efficiency"): ("42.6-bane-efficiency", True),
    ("efficiency", "bane_of_arthropods"): ("42.6-efficiency-bane", True),
    ("sharpness", "aqua_affinity"): ("44.4-sharp-aqua", True),
    ("aqua_affinity", "sharpness"): ("44.4-aqua-sharp", True),
    ("smite", "aqua_affinity"): ("44.4-smite-aqua", True),
    ("aqua_affinity", "smite"): ("44.4-aqua-smite", True),
    ("bane_of_arthropods", "aqua_affinity"): ("44.4-bane-aqua", True),
    ("aqua_affinity", "bane_of_arthropods"): ("44.4-aqua-bane", True),
    ("smite", "mending"): ("44.2-smite-mending", True),
    ("mending", "smite"): ("44.2-mending-smite", True),
    ("bane_of_arthropods", "mending"): ("44.2-bane-mending", True),
    ("mending", "bane_of_arthropods"): ("44.2-mending-bane", True),
    ("sharpness", "lure"): ("40.1-sharp-lure", True),
    ("smite", "lure"): ("40.1-smite-lure", True),
    ("bane_of_arthropods", "lure"): ("40.1-bane-lure", True),
    ("efficiency", "lure"): ("40.1-efficiency-lure", True),
    ("silk_touch", "lure"): ("40.1-silk-lure", True),
    ("fortune", "lure"): ("40.1-fortune-lure", True),
    ("power", "lure"): ("40.1-power-lure", True),
    ("flame", "lure"): ("40.1-flame-lure", True),
    ("punch", "lure"): ("40.1-punch-lure", True),
    ("multishot", "lure"): ("40.1-multishot-lure", True),
    ("infinity", "lure"): ("40.1-infinity-lure", True),
    ("piercing", "lure"): ("40.1-piercing-lure", True),
    ("protection", "lure"): ("40.1-protection-lure", True),
    ("fire_protection", "lure"): ("40.1-fire-protection-lure", True),
    ("blast_protection", "lure"): ("40.1-blast-protection-lure", True),
    ("projectile_protection", "lure"): ("40.1-projectile-protection-lure", True),
    ("feather_falling", "lure"): ("40.1-feather-lure", True),
    ("quick_charge", "lure"): ("40.1-quick-lure", True),
    ("mending", "lure"): ("40.1-mending-lure", True),
    ("unbreaking", "lure"): ("40.1-unbreaking-lure", True),
    ("knockback", "lure"): ("40.1-knockback-lure", True),
    ("breach", "lure"): ("40.1-breach-lure", True),
    ("density", "lure"): ("40.1-density-lure", True),
    ("sharpness", "fire_aspect"): ("43.1-sharp-fire", True),
    ("fire_aspect", "sharpness"): ("43.1-fire-sharp", True),
    ("smite", "fire_aspect"): ("43.1-smite-fire", True),
    ("fire_aspect", "smite"): ("43.1-fire-smite", True),
    ("bane_of_arthropods", "fire_aspect"): ("43.1-bane-fire", True),
    ("fire_aspect", "bane_of_arthropods"): ("43.1-fire-bane", True),
    ("sharpness", "fortune"): ("33.2-sharp-fortune", True),
    ("fortune", "sharpness"): ("33.2-fortune-sharp", True),
    ("smite", "fortune"): ("44.3-smite-fortune", True),
    ("fortune", "smite"): ("44.3-fortune-smite", True),
    ("bane_of_arthropods", "fortune"): ("44.3-bane-fortune", True),
    ("fortune", "bane_of_arthropods"): ("44.3-fortune-bane", True),
    ("sharpness", "swift_sneak"): ("42.3-sharp-swift", True),
    ("swift_sneak", "sharpness"): ("42.3-swift-sharp", True),
    ("smite", "swift_sneak"): ("42.3-smite-swift", True),
    ("swift_sneak", "smite"): ("42.3-swift-smite", True),
    ("bane_of_arthropods", "swift_sneak"): ("42.3-bane-swift", True),
    ("swift_sneak", "bane_of_arthropods"): ("42.3-swift-bane", True),
    ("fire_aspect", "knockback"): ("01-fire-knockback", False),
    ("knockback", "fire_aspect"): ("01-fire-knockback", False),
    ("mending", "unbreaking"): ("05-mending-unbreaking", True),
    ("unbreaking", "mending"): ("05-unbreaking-mending", True),
    ("efficiency", "silk_touch"): ("09-efficiency-silk", True),
    ("silk_touch", "efficiency"): ("09-silk-efficiency", True),
    ("multishot", "piercing"): ("11-multishot-piercing", True),
    ("piercing", "multishot"): ("11-piercing-multishot", True),
    ("quick_charge", "piercing"): ("12-quick-piercing", True),
    ("piercing", "quick_charge"): ("12-piercing-quick", True),
    ("flame", "punch"): ("14-flame-punch", True),
    ("punch", "flame"): ("14-punch-flame", True),
    ("protection", "fire_protection"): ("17-protection-fire", True),
    ("fire_protection", "protection"): ("17-fire-protection", True),
    ("smite", "bane_of_arthropods"): ("18-smite-bane", True),
    ("bane_of_arthropods", "smite"): ("18-bane-smite", True),
    ("thorns", "fire_protection"): ("19-thorns-fire", True),
    ("fire_protection", "thorns"): ("19-fire-thorns", True),
    ("efficiency", "multishot"): ("20-efficiency-multishot", True),
    ("multishot", "efficiency"): ("20-multishot-efficiency", True),
    ("loyalty", "riptide"): ("15-loyalty-riptide", False),
    ("riptide", "loyalty"): ("15-loyalty-riptide", False),
    ("blast_protection", "protection"): ("16-blast-protection", True),
    ("protection", "blast_protection"): ("16-protection-blast", True),
    ("respiration", "aqua_affinity"): ("21-underwater", False),
    ("aqua_affinity", "respiration"): ("21-underwater", False),
    ("frost_walker", "depth_strider"): ("22-frost-wake", False),
    ("depth_strider", "frost_walker"): ("22-frost-wake", False),
    ("feather_falling", "frost_walker"): ("23-frost-fall", False),
    ("frost_walker", "feather_falling"): ("23-frost-fall", False),
    ("frost_walker", "fire_protection"): ("24.1-frost-fire", False),
    ("fire_protection", "frost_walker"): ("24.1-frost-fire", False),
    # These fixtures also test unrelated preservation/flip operations, so a
    # first failure cannot be attributed to this direction alone.
    ("loyalty", "protection"): ("25.3-loyalty", False),
    ("fire_aspect", "silk_touch"): ("31.3-glaze", False),
    ("silk_touch", "fire_aspect"): ("31.3-amber", True),
    ("fire_aspect", "aqua_affinity"): ("31.4-carrier", False),
    ("aqua_affinity", "fire_aspect"): ("31.4-carrier", False),
    # Each heavy-crush fixture explicitly compares both orders before its own actions.
    ("density", "breach"): ("39.1-density-breach", False),
    ("breach", "density"): ("39.1-breach-density", False),
    ("infinity", "unbreaking"): ("39.2-infinity-unbreaking", True),
    ("unbreaking", "infinity"): ("39.2-unbreaking-infinity", True),
    ("knockback", "silk_touch"): ("39.3-knockback-silk", True),
    ("silk_touch", "knockback"): ("39.3-silk-knockback", True),
    ("luck_of_the_sea", "lure"): ("39.4-luck-lure", True),
    ("lure", "luck_of_the_sea"): ("39.4-lure-luck", True),
    ("power", "quick_charge"): ("39.5-power-quick", True),
    ("quick_charge", "power"): ("39.5-quick-power", True),
    ("soul_speed", "swift_sneak"): ("39.6-soul-swift", True),
    ("swift_sneak", "soul_speed"): ("39.6-swift-soul", True),
}
# This fixture also checks a distinct Mending-to-experience-bottle conversion.
VARIANT_FIXTURES = {"mending_replenish": ("transmute-replenish", False)}

# Read only rows with the exact reporter format, respecting escaped Markdown pipes.
# These are the designs explicitly discussed with the user, not every catalog proposal.
# The rest remains in the inventory, but does not gate the first integrated acceptance.
PRIORITY_CATEGORIES = frozenset(("专属", "副位通配", "已选模板"))

REPORT_ROW = re.compile(
    r"^\| ((?:\\.|[^|])+?) \| (?:\\.|[^|])*? \| (PASS|FAIL|SKIP) \| ((?:\\.|[^|])*?) \|$",
    re.MULTILINE,
)


def parse_report(text: str) -> dict[str, tuple[str, str]]:
    result: dict[str, tuple[str, str]] = {}
    for match in REPORT_ROW.finditer(text):
        fixture, status, reason = match.groups()
        if fixture in result:
            raise ValueError(f"duplicate fixture result: {fixture}")
        result[fixture] = status, reason
    return result


def scoped_status(mapping: tuple[str, bool] | None, results: dict[str, tuple[str, str]]) -> tuple[str, str]:
    if mapping is None:
        return "SKIP", "尚无该项完整的单进程游戏结果；可能尚未实现或缺少独立探针。"
    fixture, unique = mapping
    observed = results.get(fixture)
    if observed is None:
        return "SKIP", f"未找到准备探针 {fixture} 的结果；不能推断游戏表现。"
    status, _detail = observed
    if status == "FAIL" and unique:
        return "FAIL", f"该项的有限探针 {fixture} 失败；原因见独立探针报告，其他场景仍未覆盖。"
    if status == "FAIL":
        return "SKIP", f"共享探针 {fixture} 失败，无法仅据报告定位到本方向；需分别复测。"
    if status == "PASS":
        return "SKIP", f"有限探针 {fixture} 通过，但未覆盖完整方向/载体和兼容场景；不是全项通过。"
    return "SKIP", f"准备探针 {fixture} 跳过或未完成；原因见独立探针报告。"


def esc(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def collect_bindings(variants: dict) -> list[tuple[str, str, str]]:
    bindings = []
    for variant, data in variants.items():
        bindings.extend((variant, data["source"], role) for role in data["roles"])
        for source in data.get("extra_sources", []):
            roles = data.get("extra_source_roles", {}).get(source, data["roles"])
            bindings.extend((variant, source, role) for role in roles)
    if len(bindings) != len(set(bindings)):
        raise ValueError("duplicate transmutation binding")
    return bindings


def inventory(report_text: str) -> str:
    results = parse_report(report_text)
    entries, names = parse_coverage(read_doc("FUSION_MATRIX_COVERAGE.md"))
    variants_path = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/transmutations.json"
    variants = json.loads(variants_path.read_text(encoding="utf-8"))["variants"]
    bindings = collect_bindings(variants)
    unknown_pairs = set(DIRECTION_FIXTURES) - set(entries)
    unknown_variants = set(VARIANT_FIXTURES) - set(variants)
    if unknown_pairs or unknown_variants:
        raise ValueError(f"unknown fixture mappings: {unknown_pairs}, {unknown_variants}")

    lines = [
        "# 单进程验收：逐项索引（准备版）", "",
        "只索引同一份集成客户端探针报告，不启动游戏。目录设计、单元测试和静态检查不是游戏结果。",
        "现有探针均为局部范围：有限 PASS 不等于整项 PASS；共享探针失败也不猜测是哪一个方向出错。",
        "逐项完整游戏探针及多人/兼容场景仍须补齐；详情见 [探针报告](acceptance-report.md)。", "",
        f"清单：{len(entries)} 个不同附魔有序方向、{len(variants)} 个跨种变体、{len(bindings)} 个来源—角色绑定。",
        "第一轮验收优先已讨论的专属、通配、已选模板；其他方向仍保留在清单中，不作为这次进入游戏的前置条件。",
        "变体与绑定是不同粒度，不能将两张表相加后称为不重复玩法总数。数据包新增的第三方内容不在默认清单中。", "",
    ]
    for priority, heading in ((True, "已讨论：专属、通配和已选模板"), (False, "其他方案：保留，后续复用机制")):
        lines += [f"## {heading}", "", "| 主位 ▷ 副位 | ID | 设计分类 | 状态 | 原因 / 边界 |", "| --- | --- | --- | --- | --- |"]
        for (primary, secondary), (state, _scope) in entries.items():
            if (category(state) in PRIORITY_CATEGORIES) != priority:
                continue
            status, reason = scoped_status(DIRECTION_FIXTURES.get((primary, secondary)), results)
            lines.append(f"| {esc(names[primary])} ▷ {esc(names[secondary])} | `{primary}` ▷ `{secondary}` | "
                         f"{esc(category(state))} | {status} | {esc(reason)} |")
        lines.append("")
    lines += ["", "## 单附魔跨种变体", "", "| 变体 | 主来源 | 状态 | 原因 / 边界 |", "| --- | --- | --- | --- |"]
    for variant, data in variants.items():
        status, reason = scoped_status(VARIANT_FIXTURES.get(variant), results)
        lines.append(f"| `{variant}` | `{esc(data['source'])}` | {status} | {esc(reason)} |")
    lines += ["", "## 单附魔跨种：来源—角色绑定", "", "| 变体 | 来源 | 角色 | 状态 | 原因 / 边界 |", "| --- | --- | --- | --- | --- |"]
    for variant, source, role in bindings:
        # Fixture mapping is conservative at the variant and individual-binding levels.
        mapping = VARIANT_FIXTURES.get(variant)
        status, reason = scoped_status(mapping, results)
        lines.append(f"| `{variant}` | `{esc(source)}` | `{esc(role)}` | {status} | {esc(reason)} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: acceptance_inventory.py acceptance-report.md acceptance-inventory.md")
    report, output = map(Path, sys.argv[1:])
    output.write_text(inventory(report.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    main()
