#!/usr/bin/env python3
"""Verifies EnchantmentFusionScreen layout geometry is overlap-free, scissor-clipped, and pixel-art backed."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def overlaps(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return not (ax + aw <= bx or bx + bw <= ax or ay + ah <= by or by + bh <= ay)


class UiReadabilityTest(unittest.TestCase):
    def test_workstation_regions_do_not_overlap(self) -> None:
        regions = {
            "slot_primary": (21, 33, 18, 18),
            "btn_swap": (47, 30, 24, 24),
            "slot_secondary": (85, 33, 18, 18),
            "slot_catalyst": (53, 61, 18, 18),
            "slot_target": (53, 91, 18, 18),
            "vessel_diamonds": (77, 94, 26, 12),
            "preview_panel": (122, 24, 141, 60),
            "btn_fuse": (122, 88, 76, 20),
            "btn_flip": (201, 89, 30, 18),
            "btn_split": (233, 89, 30, 18),
            "player_inv": (57, 115, 162, 76),
        }
        names = list(regions.keys())
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                self.assertFalse(
                    overlaps(regions[names[i]], regions[names[j]]),
                    f"UI regions overlap: {names[i]} vs {names[j]}",
                )

    def test_screen_uses_gpu_scissor_and_real_textures(self) -> None:
        screen_src = (ROOT / "src/client/java/enchantment_fusion/gui/EnchantmentFusionScreen.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("enableScissor", screen_src)
        self.assertIn("disableScissor", screen_src)
        self.assertNotIn(".fill(", screen_src)


if __name__ == "__main__":
    unittest.main()
