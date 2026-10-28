#!/usr/bin/env python3
"""Architecture boundary verifier for Enchantment Fusion:
1. `src/main/java` must never import client-only packages (`net.minecraft.client.*`, `enchantment_fusion.gui.*`, `enchantment_fusion.render.*`).
2. Optional viewer imports (`mezz.jei.*`, `me.shedaniel.*`) must stay strictly inside `enchantment_fusion/compat/{jei,rei}/`.
3. `enchantment_fusion.model` and `enchantment_fusion.data` must stay free of menu/block-entity upward coupling.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([a-zA-Z0-9_.]+)\s*;", re.MULTILINE)


def scan_source_boundaries(main_root: Path | None = None, client_root: Path | None = None) -> list[str]:
    if main_root is None:
        main_root = ROOT / "src/main/java"
    if client_root is None:
        client_root = ROOT / "src/client/java"

    violations: list[str] = []

    for jf in main_root.rglob("*.java"):
        rel = jf.relative_to(main_root).as_posix()
        txt = jf.read_text(encoding="utf-8")
        for imp in IMPORT_RE.findall(txt):
            if imp.startswith(("net.minecraft.client.", "enchantment_fusion.gui.", "enchantment_fusion.render.")):
                violations.append(f"{rel}: server/common source imports client symbol {imp}")
            if imp.startswith(("mezz.jei.", "me.shedaniel.")):
                violations.append(f"{rel}: main source imports optional recipe viewer {imp}")
            if rel.startswith(("enchantment_fusion/model/", "enchantment_fusion/data/")):
                if imp.startswith(("enchantment_fusion.block.", "enchantment_fusion.menu.")):
                    violations.append(f"{rel}: pure model/data layer imports upward layer {imp}")

    for jf in client_root.rglob("*.java"):
        rel = jf.relative_to(client_root).as_posix()
        txt = jf.read_text(encoding="utf-8")
        for imp in IMPORT_RE.findall(txt):
            if imp.startswith("mezz.jei.") and not rel.startswith("enchantment_fusion/compat/jei/"):
                violations.append(f"{rel}: JEI import leaked outside compat/jei: {imp}")
            if imp.startswith("me.shedaniel.") and not rel.startswith("enchantment_fusion/compat/rei/"):
                violations.append(f"{rel}: REI import leaked outside compat/rei: {imp}")

    return violations


def main() -> int:
    errs = scan_source_boundaries()
    if errs:
        for e in errs:
            print(f"boundary violation: {e}", file=sys.stderr)
        return 1
    print("ok: architecture boundaries clean (server/client split + optional JEI/REI isolation)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
