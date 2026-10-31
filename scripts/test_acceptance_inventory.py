#!/usr/bin/env python3
"""Verify report indexing logic only; no test here is a Minecraft gameplay result."""
import json
import re
import unittest

from acceptance_inventory import (
    DIRECTION_FIXTURES, VARIANT_FIXTURES, collect_bindings, inventory,
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

    def test_unique_and_shared_failures_remain_distinct(self):
        results = parse_report("| unique | scope | FAIL | failure |\n| shared | scope | FAIL | failure |\n")
        self.assertEqual("FAIL", scoped_status(("unique", True), results)[0])
        self.assertEqual("SKIP", scoped_status(("shared", False), results)[0])
        self.assertEqual("SKIP", scoped_status(("absent", True), results)[0])
        self.assertEqual("SKIP", scoped_status(None, results)[0])

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
