#!/usr/bin/env python3
"""Find similarly designed ordered fusions; this is not an implementation or gameplay report.

The index reads the checked-in design and default signature data. A running server's
merged datapacks may resolve differently; always consult SignatureCatalog at runtime.
Examples: python3 scripts/fusion_batch_index.py --group 42.3
          python3 scripts/fusion_batch_index.py --primary smite --json
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re

from verify_fusion_designs import FAMILY_OF, ROOT, category, parse_coverage, read_doc

SIGNATURES = ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/signatures.json"
REF = re.compile(r" · (G\d{4}|\d{2}(?:\.\d{1,2})?)")


def local_vanilla(identifier: str) -> str | None:
    if identifier.startswith("minecraft:"):
        return identifier.removeprefix("minecraft:")
    return identifier if ":" not in identifier and identifier != "*" else None


def default_signature_maps(path: Path = SIGNATURES) -> tuple[dict[tuple[str, str], str], dict[str, str]]:
    direct: dict[tuple[str, str], str] = {}
    wildcard: dict[str, str] = {}
    data = json.loads(path.read_text(encoding="utf-8"))["signatures"]
    for name, definition in data.items():
        primary, secondary = definition.get("primary", ""), local_vanilla(definition.get("secondary", ""))
        if secondary is None:
            continue
        if primary == "*":
            if secondary in wildcard:
                raise ValueError(f"duplicate wildcard for {secondary}")
            wildcard[secondary] = name
        else:
            local = local_vanilla(primary)
            if local is None:
                continue
            key = local, secondary
            if key in direct:
                raise ValueError(f"duplicate exact signature for {key}")
            direct[key] = name
    return direct, wildcard


def default_resolution(primary: str, secondary: str, direct: dict, wildcard: dict) -> tuple[str, str]:
    exact = direct.get((primary, secondary))
    if exact is not None:
        return "exact", exact
    wild = wildcard.get(secondary)
    return ("secondary-wildcard", wild) if wild is not None else ("none", "")


def build_index() -> list[dict[str, str]]:
    entries, names = parse_coverage(read_doc("FUSION_MATRIX_COVERAGE.md"))
    direct, wildcard = default_signature_maps()
    result = []
    for (primary, secondary), (state, host) in entries.items():
        reference = REF.search(state)
        priority, signature = default_resolution(primary, secondary, direct, wildcard)
        result.append({
            "primary": primary, "secondary": secondary,
            "primary_name": names[primary], "secondary_name": names[secondary],
            "family": FAMILY_OF[primary] + ">" + FAMILY_OF[secondary],
            "design_category": category(state), "design_reference": reference.group(1) if reference else "",
            "suggested_host": host, "default_signature_priority": priority, "default_signature": signature,
        })
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--group", help="Design reference, e.g. 42.3 or 40.1")
    parser.add_argument("--primary", help="Minecraft main enchantment id, without minecraft:")
    parser.add_argument("--secondary", help="Minecraft secondary enchantment id, without minecraft:")
    parser.add_argument("--json", action="store_true", help="Output matching rows as JSON")
    args = parser.parse_args()
    rows = build_index()
    if args.group:
        rows = [row for row in rows if row["design_reference"] == args.group]
    if args.primary:
        rows = [row for row in rows if row["primary"] == args.primary.removeprefix("minecraft:")]
    if args.secondary:
        rows = [row for row in rows if row["secondary"] == args.secondary.removeprefix("minecraft:")]
    if args.json:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return
    print("Design similarity index only; default signatures may be changed by datapacks. Not gameplay status.")
    print(f"Matching ordered directions: {len(rows)}")
    if args.group or args.primary or args.secondary:
        for row in rows:
            resolved = row["default_signature_priority"] + (":" + row["default_signature"] if row["default_signature"] else "")
            print(f"{row['primary']} ▷ {row['secondary']} | {row['design_category']} {row['design_reference']} | "
                  f"{row['family']} | {resolved} | {row['suggested_host']}")
    else:
        print("By design category:", dict(Counter(row["design_category"] for row in rows)))
        print("By checked-in default signature priority:", dict(Counter(row["default_signature_priority"] for row in rows)))
        print("Use --group, --primary, or --secondary to inspect candidates; counts are not implemented effects.")


if __name__ == "__main__":
    main()
