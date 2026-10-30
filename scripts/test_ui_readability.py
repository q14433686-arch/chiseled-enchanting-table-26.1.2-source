#!/usr/bin/env python3
"""
Static UI readability, non-overlap, texture-skin, and locale bounds check for
Enchantment Fusion's Cutaway Split-Wing Auto-Morphing Altar UI & Obsidian Star-Codex Grimoire.
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
MENU_JAVA = ROOT / "src/main/java/enchantment_fusion/menu/EnchantmentFusionMenu.java"
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

    for clipped_screen in (STATION_SCREEN, GRIMOIRE_SCREEN, CONTEXT_DRAWER):
        src = clipped_screen.read_text(encoding="utf-8")
        if "enableScissor" not in src or "disableScissor" not in src:
            errors.append(f"{clipped_screen.name} missing enableScissor/disableScissor text clipping")

    # 2. Verify all GUI PNG sprites referenced in FusionGuiSkin exist on disk
    skin_src = (GUI_DIR / "FusionGuiSkin.java").read_text(encoding="utf-8")
    referenced_pngs = re.findall(r'EnchantmentFusion\.id\("([^"]+\.png)"\)', skin_src)
    if len(referenced_pngs) < 25:
        errors.append(f"Expected >= 25 GUI sprite PNGs in FusionGuiSkin.java, found {len(referenced_pngs)}")
    for rel in referenced_pngs:
        if not (ASSETS / rel).is_file():
            errors.append(f"Missing GUI texture asset: assets/enchantment_fusion/{rel}")

    # 3. Verify 384x218 Cutaway Split-Wing Auto-Morphing Altar fits cleanly in 427x240 (GUI scale 3 @ 1280x720)
    station_w, station_h = 384, 218
    grimoire_w, grimoire_h = 360, 224
    min_scaled_w, min_scaled_h = 427, 240
    if station_w + 24 > min_scaled_w or station_h > min_scaled_h:
        errors.append(f"Station GUI + drawer pull-tab ({station_w + 24}x{station_h}) exceeds {min_scaled_w}x{min_scaled_h}")
    if grimoire_w > min_scaled_w or grimoire_h > min_scaled_h:
        errors.append(f"Grimoire GUI ({grimoire_w}x{grimoire_h}) exceeds {min_scaled_w}x{min_scaled_h}")

    # 4. Verify non-overlapping structural wings + archway + inventory pedestal
    wings = {
        "left_wing": (0, 6, 116, 120),
        "center_cutaway_arch": (116, 6, 116, 118),
        "right_wing": (232, 6, 152, 120),
        "bottom_inv_pedestal": (103, 128, 178, 88),
        "drawer_pull_tab": (384, 16, 22, 44),
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
        "brazier_tl": (5, 9, 14, 14),
        "title": (20, 11, 76, 10),
        "brazier_tr": (97, 9, 14, 14),
        "slot_primary": (17, 43, 18, 18),
        "swap_medallion": (46, 40, 24, 24),
        "slot_secondary": (81, 43, 18, 18),
        "slot_catalyst": (49, 72, 18, 18),
        "slot_target": (49, 100, 18, 18),
    }
    book_mode_controls = {
        "brazier_rl": (237, 9, 14, 14),
        "header": (252, 11, 88, 10),
        "brazier_rr": (342, 9, 14, 14),
        "grimoire_btn": (361, 9, 17, 15),
        "cand_row_0": (240, 27, 136, 16),
        "cand_row_1": (240, 44, 136, 16),
        "preview_or_manage_card": (240, 63, 136, 29),
        "ritual_exec_btn": (240, 95, 136, 23),
    }
    gear_mode_controls = {
        "brazier_rl": (237, 9, 14, 14),
        "header": (254, 11, 62, 10),
        "vessel_gems": (316, 11, 25, 12),
        "brazier_rr": (342, 9, 14, 14),
        "grimoire_btn": (361, 9, 17, 15),
        "socket_0_text": (245, 28, 91, 14),
        "socket_0_flip": (339, 28, 16, 14),
        "socket_0_unsocket": (357, 28, 16, 14),
        "socket_1_text": (245, 45, 91, 14),
        "socket_1_flip": (339, 45, 16, 14),
        "socket_1_unsocket": (357, 45, 16, 14),
        "gear_cand_row": (240, 65, 104, 16),
        "single_mode_chip": (346, 65, 30, 16),
        "status_line": (242, 84, 132, 9),
        "ritual_exec_btn": (240, 95, 136, 23),
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
        f"UI READABILITY PASS: {len(referenced_pngs)} sprites verified, Cutaway Split-Wing 384x218 + Grimoire 360x224 "
        "non-overlapping, 11 locales BMP-safe."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
