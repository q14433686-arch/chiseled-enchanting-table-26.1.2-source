#!/usr/bin/env python3
"""Scope indexing tests; these never assert that a direction is implemented or playable."""
import unittest

from acceptance_inventory import DIRECTION_FIXTURES
from discussed_scope import rows, summarize
from fusion_batch_index import build_index


class DiscussedScopeTest(unittest.TestCase):
    def test_scope_is_the_three_discussed_categories_without_duplicates(self):
        selected = rows()
        summary = summarize(selected)
        self.assertEqual(432, summary["directions"])
        self.assertEqual({"专属": 184, "副位通配": 124, "已选模板": 124},
                         {key: value["directions"] for key, value in summary["categories"].items()})
        keys = [(row["primary"], row["secondary"]) for row in selected]
        self.assertEqual(len(keys), len(set(keys)))
        all_keys = {(row["primary"], row["secondary"]) for row in build_index()}
        self.assertTrue(set(keys).issubset(all_keys))
        self.assertIn(("fire_aspect", "frost_walker"), keys)
        self.assertIn(("frost_walker", "fire_aspect"), keys)

    def test_mappings_are_conservative_evidence_not_gameplay_results(self):
        selected = rows()
        self.assertEqual(sum(bool(row["fixture"]) for row in selected), summarize(selected)["fixture_mappings"])
        for row in selected:
            mapping = DIRECTION_FIXTURES.get((row["primary"], row["secondary"]))
            self.assertEqual(mapping[0] if mapping else "", row["fixture"])
        self.assertIn("不是任何游戏 PASS", summarize(selected)["evidence_note"])
        self.assertNotIn("PASS", {key for row in selected for key in row})

    def test_wildcards_and_exact_exception_are_counted_once(self):
        summary = summarize(rows())
        self.assertEqual(40, summary["groups"]["40.1"]["directions"])
        self.assertEqual(42, summary["groups"]["40.2"]["directions"])
        self.assertEqual(42, summary["groups"]["40.3"]["directions"])
        self.assertEqual(4, summary["groups"]["44.3"]["directions"])
        self.assertEqual(2, summary["groups"]["33.2"]["directions"])


if __name__ == "__main__":
    unittest.main()
