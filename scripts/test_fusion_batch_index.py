#!/usr/bin/env python3
"""Design grouping / default signature precedence only; never a gameplay test."""
import re
import unittest

from fusion_batch_index import build_index, default_resolution, default_signature_maps
from verify_fusion_designs import ROOT, parse_coverage, read_doc


class FusionBatchIndexTest(unittest.TestCase):
    def test_all_ordered_directions_are_indexed_once(self):
        rows = build_index()
        self.assertEqual(1806, len(rows))
        self.assertEqual(1806, len({(row['primary'], row['secondary']) for row in rows}))
        self.assertTrue(all(row['design_category'] and row['family'] for row in rows))

    def test_abstract_profiles_cover_every_default_enchantment_once(self):
        _, names = parse_coverage(read_doc('FUSION_MATRIX_COVERAGE.md'))
        document = (ROOT / 'docs/FUSION_BATCH_IMPLEMENTATION.md').read_text(encoding='utf-8')
        section = document.split('## 43 种原版附魔', 1)[1].split('## 潜行选中模板', 1)[0]
        found = re.findall(r'^\| `([^`]+)`', section, re.MULTILINE)
        self.assertEqual(43, len(found))
        self.assertEqual(set(names), set(found))

    def test_selected_melee_families_find_six_directions_each(self):
        rows = build_index()
        attacks = {'sharpness', 'smite', 'bane_of_arthropods'}
        for reference, other in [('42.2', 'respiration'), ('42.3', 'swift_sneak')]:
            selected = [row for row in rows if row['design_reference'] == reference]
            expected = {(attack, other) for attack in attacks} | {(other, attack) for attack in attacks}
            self.assertEqual(expected, {(row['primary'], row['secondary']) for row in selected})
            self.assertTrue(all(row['design_category'] == '已选模板' for row in selected))

    def test_exact_beats_secondary_wildcard_and_other_names_are_not_invented(self):
        direct, wildcard = default_signature_maps()
        self.assertEqual(('exact', 'beacon_bonfire'), default_resolution('fire_aspect', 'lure', direct, wildcard))
        self.assertEqual(('exact', 'tidal_double_catch'), default_resolution('luck_of_the_sea', 'lure', direct, wildcard))
        self.assertEqual(('secondary-wildcard', 'spirit_grapnel'), default_resolution('impaling', 'lure', direct, wildcard))
        self.assertEqual(('none', ''), default_resolution('sharpness', 'swift_sneak', direct, wildcard))

    def test_labels_are_design_not_claimed_implementation(self):
        rows = build_index()
        self.assertTrue(all('implemented' not in row and 'passed' not in row for row in rows))
        self.assertEqual(40, sum(row['design_reference'] == '40.1' for row in rows))


if __name__ == '__main__':
    unittest.main()
