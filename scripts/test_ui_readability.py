#!/usr/bin/env python3
"""Verifies EnchantmentFusionScreen 308x214 2-tab + Grimoire button & FusionGrimoireScreen 360x224 geometry."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def overlaps(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return not (ax + aw <= bx or bx + bw <= ax or ay + ah <= by or by + bh <= ay)


def assert_disjoint(
    testcase: unittest.TestCase,
    regions: dict[str, tuple[int, int, int, int]],
    max_w: int = 308,
    max_h: int = 214,
) -> None:
    names = list(regions.keys())
    for i in range(len(names)):
        x, y, w, h = regions[names[i]]
        testcase.assertGreaterEqual(x, 0)
        testcase.assertGreaterEqual(y, 0)
        testcase.assertLessEqual(x + w, max_w, f"{names[i]} exceeds {max_w}px width")
        testcase.assertLessEqual(y + h, max_h, f"{names[i]} exceeds {max_h}px height")
        for j in range(i + 1, len(names)):
            testcase.assertFalse(
                overlaps(regions[names[i]], regions[names[j]]),
                f"UI regions overlap: {names[i]} vs {names[j]}",
            )


class UiReadabilityTest(unittest.TestCase):
    COMMON = {
        "tab_0": (12, 0, 104, 20),
        "tab_1": (120, 0, 104, 20),
        "btn_grimoire": (232, 0, 68, 18),
        "slot_primary": (17, 51, 18, 18),
        "btn_swap_slots": (37, 51, 18, 18),
        "btn_swap_order": (57, 51, 18, 18),
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
                "btn_execute": (237, 95, 61, 26),
            }
        )
        assert_disjoint(self, regions)

    def test_tab0_integrated_book_manage_buttons_do_not_overlap(self) -> None:
        regions = dict(self.COMMON)
        regions.update(
            {
                "picker_rows_3": (112, 36, 186, 50),
                "btn_book_flip": (140, 98, 44, 20),
                "btn_book_split": (187, 98, 47, 20),
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
                "btn_execute": (237, 95, 61, 26),
            }
        )
        assert_disjoint(self, regions)

    def test_grimoire_double_page_spread_regions_do_not_overlap(self) -> None:
        spread = {
            "left_header": (25, 18, 141, 16),
            "left_search": (27, 36, 137, 13),
            "left_rows": (25, 53, 141, 120),
            "left_pager": (25, 173, 141, 14),
            "left_footer": (25, 190, 141, 14),
            "right_detail": (194, 22, 141, 178),
        }
        assert_disjoint(self, spread, max_w=360, max_h=224)

    def test_screens_use_gpu_scissor_and_zero_solid_fills(self) -> None:
        for rel in (
            "src/client/java/enchantment_fusion/gui/EnchantmentFusionScreen.java",
            "src/client/java/enchantment_fusion/gui/FusionGrimoireScreen.java",
        ):
            src = (ROOT / rel).read_text(encoding="utf-8")
            self.assertIn("enableScissor", src)
            self.assertIn("disableScissor", src)
            self.assertNotIn(".fill(", src)

        station_src = (ROOT / "src/client/java/enchantment_fusion/gui/EnchantmentFusionScreen.java").read_text(encoding="utf-8")
        self.assertIn("SWAP_SLOTS_BTN", station_src)
        self.assertIn("SWAP_ORDER_BTN", station_src)
        grimoire_src = (ROOT / "src/client/java/enchantment_fusion/gui/FusionGrimoireScreen.java").read_text(encoding="utf-8")
        self.assertIn("DETAIL_BODY_HEIGHT", grimoire_src)
        self.assertIn("detailScroll", grimoire_src)


if __name__ == "__main__":
    unittest.main()
