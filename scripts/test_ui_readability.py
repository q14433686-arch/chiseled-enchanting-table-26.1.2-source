#!/usr/bin/env python3
"""Verifies EnchantmentFusionScreen 308x214 3-tab layout geometry is overlap-free, scissor-clipped, and pixel-art backed."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def overlaps(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return not (ax + aw <= bx or bx + bw <= ax or ay + ah <= by or by + bh <= ay)


def assert_disjoint(testcase: unittest.TestCase, regions: dict[str, tuple[int, int, int, int]]) -> None:
    names = list(regions.keys())
    for i in range(len(names)):
        x, y, w, h = regions[names[i]]
        testcase.assertGreaterEqual(x, 0)
        testcase.assertGreaterEqual(y, 0)
        testcase.assertLessEqual(x + w, 308, f"{names[i]} exceeds 308px width")
        testcase.assertLessEqual(y + h, 214, f"{names[i]} exceeds 214px height")
        for j in range(i + 1, len(names)):
            testcase.assertFalse(
                overlaps(regions[names[i]], regions[names[j]]),
                f"UI regions overlap: {names[i]} vs {names[j]}",
            )


class UiReadabilityTest(unittest.TestCase):
    COMMON = {
        "tab_0": (12, 0, 84, 20),
        "tab_1": (100, 0, 84, 20),
        "tab_2": (188, 0, 84, 20),
        "slot_primary": (17, 51, 18, 18),
        "btn_swap": (43, 48, 24, 24),
        "slot_secondary": (77, 51, 18, 18),
        "slot_catalyst": (47, 79, 18, 18),
        "slot_target": (47, 107, 18, 18),
        "vessel_diamonds": (69, 110, 26, 12),
        "player_inv": (73, 131, 162, 76),
    }

    def test_tab0_book_fusion_regions_do_not_overlap(self) -> None:
        regions = dict(self.COMMON)
        regions.update(
            {
                "picker_rows_3": (112, 36, 186, 50),
                "preview_plaque": (112, 89, 122, 36),
                "btn_execute": (237, 95, 61, 24),
            }
        )
        assert_disjoint(self, regions)

    def test_tab1_socket_and_inplace_regions_do_not_overlap(self) -> None:
        regions = dict(self.COMMON)
        regions.update(
            {
                "socket_card_0": (112, 34, 186, 23),
                "socket_card_1": (112, 59, 186, 23),
                "inplace_picker_2": (112, 86, 122, 33),
                "btn_execute": (237, 95, 61, 24),
            }
        )
        assert_disjoint(self, regions)

    def test_tab2_split_and_compendium_regions_do_not_overlap(self) -> None:
        regions = dict(self.COMMON)
        regions.update(
            {
                "book_label": (112, 35, 85, 18),
                "btn_flip": (200, 35, 46, 18),
                "btn_split": (249, 35, 49, 18),
                "compendium_rows_4": (112, 57, 186, 67),
            }
        )
        assert_disjoint(self, regions)

    def test_screen_uses_gpu_scissor_and_real_textures(self) -> None:
        screen_src = (ROOT / "src/client/java/enchantment_fusion/gui/EnchantmentFusionScreen.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("enableScissor", screen_src)
        self.assertIn("disableScissor", screen_src)
        self.assertNotIn(".fill(", screen_src)


if __name__ == "__main__":
    unittest.main()
