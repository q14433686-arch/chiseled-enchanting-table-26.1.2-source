#!/usr/bin/env python3
"""Verifies CURRENT_BASELINE.md documents every active mixin in enchantment_fusion.mixins.json."""
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def verify_baseline_mixins(baseline_text: str | None = None, mixin_cfg: dict | None = None) -> list[str]:
    if baseline_text is None:
        baseline_text = (ROOT / "CURRENT_BASELINE.md").read_text(encoding="utf-8")
    if mixin_cfg is None:
        mixin_cfg = json.loads(
            (ROOT / "src/main/resources/enchantment_fusion.mixins.json").read_text(encoding="utf-8")
        )
    errors: list[str] = []
    all_mixins = (
        list(mixin_cfg.get("mixins", []))
        + list(mixin_cfg.get("client", []))
        + list(mixin_cfg.get("server", []))
    )
    for name in all_mixins:
        simple = name.split(".")[-1]
        if simple not in baseline_text:
            errors.append(f"CURRENT_BASELINE.md does not document registered mixin {simple}")
    return errors


def main() -> int:
    errs = verify_baseline_mixins()
    if errs:
        for e in errs:
            print(f"error: {e}", file=sys.stderr)
        return 1
    print("ok: CURRENT_BASELINE.md matches enchantment_fusion.mixins.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
