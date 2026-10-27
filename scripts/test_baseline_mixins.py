"""Offline regression tests for the generated baseline contract."""
import json
from pathlib import Path
import tempfile
import unittest
from check_baseline_mixins import START, END, inventory, render, synchronize


class BaselineMixinTests(unittest.TestCase):
    def test_counts_include_common_client_and_server(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resources = root / 'src/main/resources'
            resources.mkdir(parents=True)
            (resources / 'fabric.mod.json').write_text(json.dumps({'mixins': ['a.json', {'config': 'b.json'}]}))
            (resources / 'a.json').write_text(json.dumps({'package': 'p', 'mixins': ['A'], 'client': ['B']}))
            (resources / 'b.json').write_text(json.dumps({'package': 'q', 'server': ['C']}))
            entries = inventory(root)
            self.assertEqual(len(entries), 3)
            self.assertIn(('b.json', 'server', 'q.C'), entries)

    def test_changed_class_is_detected_even_when_count_unchanged(self):
        old = render([('a.json', 'mixins', 'p.A')])
        new = render([('a.json', 'mixins', 'p.B')])
        self.assertNotEqual(old, new)
        self.assertNotEqual(old, synchronize(old, new))

    def test_update_preserves_surrounding_document_and_is_idempotent(self):
        block = render([])
        doc = 'before\n' + START + '\nstale\n' + END + '\nafter\n'
        updated = synchronize(doc, block)
        self.assertTrue(updated.startswith('before\n'))
        self.assertTrue(updated.endswith('\nafter\n'))
        self.assertEqual(updated, synchronize(updated, block))

    def test_missing_block_and_broken_markers(self):
        self.assertNotEqual('document\n', synchronize('document\n', render([])))
        with self.assertRaises(ValueError):
            synchronize(START, render([]))
        with self.assertRaises(ValueError):
            synchronize(END + START, render([]))


if __name__ == '__main__':
    unittest.main()
