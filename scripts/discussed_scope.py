#!/usr/bin/env python3
"""Index the first delivery scope; fixture mappings are NOT implementation or game-pass counts.

All numbers are derived from the checked-in design catalog and acceptance mapping. A running
server can override default signature priorities via datapacks. This script does not run Minecraft.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json

from acceptance_inventory import DIRECTION_FIXTURES, PRIORITY_CATEGORIES
from fusion_batch_index import build_index


def rows() -> list[dict[str, str | bool]]:
    selected = []
    for record in build_index():
        if record["design_category"] not in PRIORITY_CATEGORIES:
            continue
        row: dict[str, str | bool] = dict(record)
        row["fixture"] = DIRECTION_FIXTURES.get((record["primary"], record["secondary"]), ("", False))[0]
        # Existing fire/frost behavior was explicitly retained and predates the numbered groups.
        if not row["design_reference"]:
            row["design_reference"] = "保留-火焰冰霜"
        selected.append(row)
    return selected


def summarize(selected: list[dict[str, str | bool]]) -> dict:
    category_counts = Counter(str(row["design_category"]) for row in selected)
    mapped_counts = Counter(str(row["design_category"]) for row in selected if row["fixture"])
    groups = defaultdict(lambda: {"directions": 0, "fixtures": 0})
    for row in selected:
        group = groups[str(row["design_reference"])]
        group["directions"] += 1
        group["fixtures"] += int(bool(row["fixture"]))
    return {
        "scope_note": "已讨论的专属、通配与已选模板；单附魔既有范围保留，另行核查。",
        "evidence_note": "候选探针映射既不是实施完成的证明，也不是任何游戏 PASS；未映射不等于未实现。",
        "directions": len(selected),
        "fixture_mappings": sum(bool(row["fixture"]) for row in selected),
        "categories": {name: {"directions": category_counts[name], "fixture_mappings": mapped_counts[name]}
                       for name in ("专属", "副位通配", "已选模板")},
        "groups": dict(sorted(groups.items())),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Machine-readable summary and rows")
    parser.add_argument("--group", help="Restrict to a discussed group, e.g. 40.1 or 44.3")
    parser.add_argument("--unmapped", action="store_true", help="Only directions without a candidate fixture mapping")
    args = parser.parse_args()
    data = rows()
    if args.group:
        data = [row for row in data if row["design_reference"] == args.group]
    if args.unmapped:
        data = [row for row in data if not row["fixture"]]
    summary = summarize(data)
    if args.json:
        print(json.dumps({"summary": summary, "directions": data}, ensure_ascii=False, indent=2))
        return
    print(summary["scope_note"])
    print(summary["evidence_note"])
    print(f"方向 {summary['directions']}；候选探针映射 {summary['fixture_mappings']}（不是 PASS）")
    for name, counts in summary["categories"].items():
        print(f"  {name}: {counts['directions']} 方向 / {counts['fixture_mappings']} 个候选映射")
    if args.group or args.unmapped:
        for row in data:
            print(f"  {row['design_reference']} | {row['primary']} ▷ {row['secondary']} | {row['fixture'] or '无映射'}")
    else:
        print("按机制组统计（候选映射 / 有序方向）：")
        for group, counts in summary["groups"].items():
            print(f"  {group}: {counts['fixtures']}/{counts['directions']}")


if __name__ == "__main__":
    main()
