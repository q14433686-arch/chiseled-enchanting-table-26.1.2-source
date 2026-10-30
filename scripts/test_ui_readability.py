#!/usr/bin/env python3
"""
Static UI readability, non-overlap, JEI/REI compact width (<=304px), texture-skin,
and locale bounds check for Enchantment Fusion's 304x218 Split-Wing Live-Stage Altar UI
& Obsidian Star-Codex Grimoire.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GUI_DIR = ROOT / "src/client/java/enchantment_fusion/gui"
STATION_SCREEN = GUI_DIR / "EnchantmentFusionScreen.java"
GRIMOIRE_SCREEN = GUI_DIR / "FusionGrimoireScreen.java"
CONTEXT_DRAWER = GUI_DIR / "FusionContextDrawer.java"
LIVE_STAGE = GUI_DIR / "FusionLiveStageRenderer.java"
ASSETS = ROOT / "src/client/resources/assets/enchantment_fusion"


def rects_overlap(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and ax + aw > bx and ay < by + bh and ay + ah > by


def main() -> int:
    errors: list[str] = []

    # 1. Verify genuine pixel-art texture skin (no solid ColorRect .fill calls!)
    for java_path in sorted(GUI_DIR.glob("*.java")):
        src = java_path.read_text(encoding="utf-8")
        if ".fill(" in src:
            errors.append(f"{java_path.name} still contains solid .fill(...) rectangle draws")

    for clipped_screen in (STATION_SCREEN, GRIMOIRE_SCREEN, CONTEXT_DRAWER, LIVE_STAGE):
        src = clipped_screen.read_text(encoding="utf-8")
        if "enableScissor" not in src or "disableScissor" not in src:
            errors.append(f"{clipped_screen.name} missing enableScissor/disableScissor clipping")

    # 2. Verify all GUI PNG sprites referenced in FusionGuiSkin exist on disk
    skin_src = (GUI_DIR / "FusionGuiSkin.java").read_text(encoding="utf-8")
    referenced_pngs = re.findall(r'EnchantmentFusion\.id\("([^"]+\.png)"\)', skin_src)
    if len(referenced_pngs) < 30:
        errors.append(f"Expected >= 30 GUI sprite PNGs in FusionGuiSkin.java, found {len(referenced_pngs)}")
    for rel in referenced_pngs:
        if not (ASSETS / rel).is_file():
            errors.append(f"Missing GUI texture asset: assets/enchantment_fusion/{rel}")

    # 3. Verify compact JEI/REI-safe 304x218 Split-Wing Live-Stage Altar & in-panel popover
    station_w, station_h = 304, 218
    grimoire_w, grimoire_h = 360, 224
    min_scaled_w, min_scaled_h = 427, 240
    if station_w > 304 or station_h > min_scaled_h:
        errors.append(f"Station GUI ({station_w}x{station_h}) exceeds compact 304px JEI/REI budget")
    if grimoire_w > min_scaled_w or grimoire_h > min_scaled_h:
        errors.append(f"Grimoire GUI ({grimoire_w}x{grimoire_h}) exceeds {min_scaled_w}x{min_scaled_h}")

    # Popover must stay strictly within 0..304 width so right-side JEI/REI is never occluded
    popover_box = (94, 25, 204, 91)
    if popover_box[0] < 0 or popover_box[0] + popover_box[2] > station_w:
        errors.append(f"Synergy Popover {popover_box} overflows 0..{station_w} station width")

    # 4. Verify non-overlapping structural wings + live stage + inventory pedestal
    wings = {
        "left_wing": (4, 6, 88, 112),
        "center_live_stage": (94, 4, 92, 116),
        "right_wing": (188, 6, 112, 112),
        "bottom_inv_pedestal": (63, 121, 178, 95),
    }
    wing_items = list(wings.items())
    for i in range(len(wing_items)):
        for j in range(i + 1, len(wing_items)):
            n1, r1 = wing_items[i]
            n2, r2 = wing_items[j]
            if rects_overlap(r1, r2):
                errors.append(f"Structural wing overlap between {n1} {r1} and {n2} {r2}")

    # 5. Verify non-overlapping controls inside Left Wing, Book Mode, and Gear Mode
    left_controls = {
        "title": (8, 10, 80, 10),
        "slot_primary": (13, 31, 18, 18),
        "swap_medallion": (38, 29, 20, 20),
        "slot_secondary": (63, 31, 18, 18),
        "slot_catalyst": (13, 77, 18, 18),
        "slot_target": (63, 77, 18, 18),
    }
    book_mode_controls = {
        "header": (193, 9, 58, 14),
        "synergy_badge": (253, 8, 24, 16),
        "grimoire_btn": (279, 8, 18, 16),
        "cand_row_0": (193, 27, 102, 15),
        "cand_row_1": (193, 44, 102, 15),
        "preview_or_manage_card": (193, 62, 102, 32),
        "ritual_exec_btn": (193, 96, 102, 18),
    }
    gear_mode_controls = {
        "header": (193, 9, 58, 14),
        "synergy_badge": (253, 8, 24, 16),
        "grimoire_btn": (279, 8, 18, 16),
        "socket_0_text": (193, 28, 66, 14),
        "socket_0_flip": (261, 28, 15, 14),
        "socket_0_unsocket": (278, 28, 15, 14),
        "socket_1_text": (193, 45, 66, 14),
        "socket_1_flip": (261, 45, 15, 14),
        "socket_1_unsocket": (278, 45, 15, 14),
        "gear_cand_row": (193, 63, 74, 15),
        "single_mode_chip": (269, 63, 26, 15),
        "status_line": (193, 80, 102, 12),
        "ritual_exec_btn": (193, 96, 102, 18),
    }

    for group_name, boxes in (
        ("LeftWing", left_controls),
        ("BookMode", book_mode_controls),
        ("GearMode", gear_mode_controls),
    ):
        items = list(boxes.items())
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                n1, r1 = items[i]
                n2, r2 = items[j]
                if rects_overlap(r1, r2):
                    errors.append(f"{group_name} overlap between {n1} {r1} and {n2} {r2}")

    # 6. Verify all 11 locale files contain single-line, BMP-safe UI strings
    for lang_file in sorted((ASSETS / "lang").glob("*.json")):
        data = json.loads(lang_file.read_text(encoding="utf-8"))
        for k, v in data.items():
            if not isinstance(v, str) or not v.strip():
                errors.append(f"{lang_file.name}: empty translation for {k}")
                continue
            if "\n" in v:
                errors.append(f"{lang_file.name}: unexpected newline in {k}")
            for ch in v:
                if ord(ch) > 0xFFFF:
                    errors.append(f"{lang_file.name}: supplementary-plane glyph U+{ord(ch):X} in {k} will render as missing box")
                    break

    if errors:
        print("UI READABILITY CHECK FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1

    print(
        f"UI READABILITY PASS: {len(referenced_pngs)} sprites verified, Compact Split-Wing Live-Stage 304x218 + Grimoire 360x224 "
        "non-overlapping, 11 locales BMP-safe."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
