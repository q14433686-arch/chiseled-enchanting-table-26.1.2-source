#!/usr/bin/env python3
"""Regression checks for the design catalogue; not Minecraft/gameplay tests."""
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_fusion_designs as designs


class FusionDesignChecks(unittest.TestCase):
    def run_validator(self):
        with patch.object(sys, "argv", ["verify_fusion_designs.py"]), redirect_stdout(StringIO()):
            designs.main()

    def with_mutated_document(self, filename, before, after):
        read_original = designs.read_doc

        def read_mutated(name):
            contents = read_original(name)
            if name == filename:
                self.assertIn(before, contents)
                return contents.replace(before, after, 1)
            return contents

        return patch.object(designs, "read_doc", side_effect=read_mutated)

    def test_current_documents_validate(self):
        self.run_validator()

    def test_generated_catalog_is_reproducible(self):
        result = subprocess.run(
            [sys.executable, "scripts/build_fusion_direction_catalog.py", "--check"],
            cwd=designs.ROOT, text=True, capture_output=True, check=True,
        )
        self.assertIn("PASS generated catalog matches", result.stdout)

    def test_missing_catalog_field_is_rejected(self):
        with self.with_mutated_document(
            designs.DIRECTION_CATALOG, "- **取义与连接**：", "- **缺少取义**：",
        ), self.assertRaisesRegex(ValueError, "972 complete structured G rows"):
            self.run_validator()

    def test_noncombat_fishing_cannot_automatically_attack(self):
        with self.with_mutated_document(
            designs.DIRECTION_CATALOG,
            "本次真实钓鱼后出现一次提示；攻击性副效果须玩家主动选择释放，普通钓鱼不会误当攻击",
            "本次真实钓鱼后自动发出有害副效果",
        ), self.assertRaisesRegex(ValueError, "automatic harmful effect"):
            self.run_validator()

    def test_carrier_alternative_cannot_target_selected_direction(self):
        with self.with_mutated_document(
            designs.CARRIER_COMPATIBILITY, "47.01 锋利 ▷ 穿刺", "47.01 锋利 ▷ 火焰附加",
        ), self.assertRaisesRegex(ValueError, "targets a selected or wrong direction"):
            self.run_validator()


if __name__ == "__main__":
    unittest.main()
