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
    ("fire_aspect", "knockback"): ("01-fire-knockback", False),
    ("knockback", "fire_aspect"): ("01-fire-knockback", False),
    ("mending", "unbreaking"): ("05-mending-unbreaking", True),
    ("unbreaking", "mending"): ("05-unbreaking-mending", True),
    ("efficiency", "silk_touch"): ("09-efficiency-silk", True),
    ("silk_touch", "efficiency"): ("09-silk-efficiency", True),
    ("multishot", "piercing"): ("11-multishot-piercing", True),
    ("piercing", "multishot"): ("11-piercing-multishot", True),
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
        "变体与绑定是不同粒度，不能将两张表相加后称为不重复玩法总数。数据包新增的第三方内容不在默认清单中。", "",
        "## 有序融合方向", "", "| 主位 ▷ 副位 | ID | 设计分类 | 状态 | 原因 / 边界 |", "| --- | --- | --- | --- | --- |",
    ]
    for (primary, secondary), (state, _scope) in entries.items():
        status, reason = scoped_status(DIRECTION_FIXTURES.get((primary, secondary)), results)
        lines.append(f"| {esc(names[primary])} ▷ {esc(names[secondary])} | `{primary}` ▷ `{secondary}` | "
                     f"{esc(category(state))} | {status} | {esc(reason)} |")
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
