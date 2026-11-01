#!/usr/bin/env python3
"""Verify report indexing logic only; no test here is a Minecraft gameplay result."""
import json
import re
import unittest

from acceptance_inventory import (
    DIRECTION_FIXTURES, VARIANT_FIXTURES, PRIORITY_CATEGORIES, collect_bindings, inventory,
    parse_report, scoped_status,
)
from verify_fusion_designs import ROOT, parse_coverage, read_doc


class AcceptanceInventoryTest(unittest.TestCase):
    def test_complete_default_catalog_is_listed_but_never_inferred_passed(self):
        text = inventory("")
        directions, _ = parse_coverage(read_doc("FUSION_MATRIX_COVERAGE.md"))
        data = json.loads((ROOT / "src/main/resources/data/enchantment_fusion/enchantment_fusion/transmutations.json")
                          .read_text(encoding="utf-8"))["variants"]
        self.assertEqual(1806, len(directions))
        self.assertEqual(116, len(data))
        self.assertEqual(431, len(collect_bindings(data)))
        self.assertEqual(1806 + 116 + 431, text.count(" | SKIP | "))
        self.assertNotIn(" | PASS | ", text)
        self.assertNotIn(" | FAIL | ", text)
        for (primary, secondary) in directions:
            self.assertIn(f"`{primary}` ▷ `{secondary}`", text)
        for variant in data:
            self.assertIn(f"| `{variant}` |", text)

    def test_discussed_items_are_listed_first_without_dropping_other_candidates(self):
        text = inventory("")
        priority, rest = text.split("## 其他方案：保留，后续复用机制", 1)
        self.assertIn("## 已讨论：专属、通配和已选模板", priority)
        self.assertIn("`sharpness` ▷ `smite`", priority)
        self.assertIn("`sharpness` ▷ `breach`", rest)
        self.assertNotIn("`sharpness` ▷ `breach`", priority)
        self.assertEqual({"专属", "副位通配", "已选模板"}, PRIORITY_CATEGORIES)

    def test_narrow_pass_does_not_become_full_pass(self):
        fixture = "39.4-luck-lure"
        results = parse_report(f"| {fixture} | scope | PASS | Prepared only. |\n")
        self.assertEqual("SKIP", scoped_status((fixture, True), results)[0])
        self.assertIn("有限探针", scoped_status((fixture, True), results)[1])
        text = inventory(f"| {fixture} | scope | PASS | Prepared only. |\n")
        row = next(line for line in text.splitlines() if "`luck_of_the_sea` ▷ `lure`" in line)
        self.assertIn(" | SKIP | ", row)
        self.assertIn(fixture, row)
        self.assertNotIn(" | PASS | ", text)

    def test_melee_template_families_have_individually_scoped_fixtures(self):
        for secondary in ("swift_sneak", "respiration", "unbreaking", "knockback", "protection", "efficiency", "aqua_affinity", "fortune", "fire_aspect", "frost_walker", "wind_burst"):
            pairs = {(attack, secondary) for attack in ("sharpness", "smite", "bane_of_arthropods")}
            pairs |= {(right, left) for left, right in pairs.copy()}
            fixtures = {DIRECTION_FIXTURES[pair][0] for pair in pairs}
            self.assertEqual(6, len(fixtures))
            self.assertTrue(all(DIRECTION_FIXTURES[pair][1] for pair in pairs))

    def test_ranged_wear_six_directions_have_separate_candidates(self):
        pairs = {(theme, "unbreaking") for theme in ("power", "punch", "flame")}
        pairs |= {(right, left) for left, right in pairs.copy()}
        self.assertEqual(6, len({DIRECTION_FIXTURES[pair][0] for pair in pairs}))
        self.assertTrue(all(DIRECTION_FIXTURES[pair][1] for pair in pairs))
        self.assertNotIn(("quick_charge", "unbreaking"), DIRECTION_FIXTURES)

    def test_lightning_family_keeps_both_sharpness_exceptions_out(self):
        pairs = {(attack, "channeling") for attack in ("smite", "bane_of_arthropods")}
        pairs |= {(right, left) for left, right in pairs.copy()}
        self.assertEqual(4, len({DIRECTION_FIXTURES[pair][0] for pair in pairs}))
        self.assertTrue(all(DIRECTION_FIXTURES[pair][1] for pair in pairs))
        self.assertNotIn(("sharpness", "channeling"), DIRECTION_FIXTURES)
        self.assertNotIn(("channeling", "sharpness"), DIRECTION_FIXTURES)

    def test_grapnel_has_thirty_five_action_specific_fixtures_not_all_40_directions(self):
        pairs = {(primary, "lure") for primary in
                 ("sharpness", "smite", "bane_of_arthropods", "efficiency", "silk_touch", "fortune", "power", "flame", "punch", "multishot", "infinity", "piercing", "protection", "fire_protection", "blast_protection", "projectile_protection", "feather_falling", "quick_charge", "mending", "unbreaking", "knockback", "breach", "density", "wind_burst", "thorns", "swift_sneak", "depth_strider", "soul_speed", "aqua_affinity", "respiration", "lunge", "impaling", "channeling", "looting", "sweeping_edge")}
        self.assertEqual(35, len({DIRECTION_FIXTURES[pair][0] for pair in pairs}))
        self.assertTrue(all(DIRECTION_FIXTURES[pair][1] for pair in pairs))
        self.assertNotIn(("frost_walker", "lure"), DIRECTION_FIXTURES)

    def test_mending_template_has_four_direction_specific_fixtures(self):
        pairs = {(attack, "mending") for attack in ("smite", "bane_of_arthropods")}
        pairs |= {(right, left) for left, right in pairs.copy()}
        self.assertEqual(4, len({DIRECTION_FIXTURES[pair][0] for pair in pairs}))
        self.assertTrue(all(DIRECTION_FIXTURES[pair][1] for pair in pairs))
        self.assertNotIn(("sharpness", "mending"), DIRECTION_FIXTURES)
        self.assertNotIn(("mending", "sharpness"), DIRECTION_FIXTURES)

    def test_unique_and_shared_failures_remain_distinct(self):
        results = parse_report("| unique | scope | FAIL | failure |\n| shared | scope | FAIL | failure |\n")
        self.assertEqual("FAIL", scoped_status(("unique", True), results)[0])
        self.assertEqual("SKIP", scoped_status(("shared", False), results)[0])
        self.assertEqual("SKIP", scoped_status(("absent", True), results)[0])
        self.assertEqual("SKIP", scoped_status(None, results)[0])

    def test_multi_direction_and_unrelated_checks_do_not_blame_one_item(self):
        for pair in [("density", "breach"), ("breach", "density"),
                     ("loyalty", "protection"), ("fire_aspect", "silk_touch")]:
            fixture, unique = DIRECTION_FIXTURES[pair]
            self.assertFalse(unique, f"{fixture} has shared assertions")
            self.assertEqual("SKIP", scoped_status((fixture, unique), {fixture: ("FAIL", "first assertion failed")})[0])
        self.assertFalse(VARIANT_FIXTURES["mending_replenish"][1], "fixture also checks XP bottle conversion")

    def test_markdown_escaped_pipes_and_duplicate_results(self):
        report = r"| example | a \| b | FAIL | reason \| detail |" + "\n"
        self.assertEqual("FAIL", parse_report(report)["example"][0])
        with self.assertRaises(ValueError):
            parse_report(report + report)

    def test_mapped_fixtures_are_planned_and_have_real_catalog_items(self):
        plan = (ROOT / "src/clientSmoke/java/enchantment_fusion/smoke/AcceptanceReport.java").read_text(encoding="utf-8")
        planned = set(re.findall(r'\bplan\("([^"]+)"', plan))
        self.assertFalse({fixture for fixture, _ in DIRECTION_FIXTURES.values()} - planned)
        self.assertFalse({fixture for fixture, _ in VARIANT_FIXTURES.values()} - planned)
        self.assertTrue((ROOT / "scripts/client_smoke.py").read_text(encoding="utf-8").find("acceptance-inventory.md") >= 0)
        self.assertIn("run-client-smoke/acceptance-inventory.md",
                      (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
