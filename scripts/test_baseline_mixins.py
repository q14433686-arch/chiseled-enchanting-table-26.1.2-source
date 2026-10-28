#!/usr/bin/env python3
"""Unit tests for check_baseline_mixins.py."""
import unittest
from check_baseline_mixins import verify_baseline_mixins


class BaselineMixinsTest(unittest.TestCase):
    def test_real_repository_baseline_matches(self) -> None:
        self.assertEqual([], verify_baseline_mixins())

    def test_detects_undocumented_mixin(self) -> None:
        errs = verify_baseline_mixins(
            baseline_text="# Baseline\n- ProjectileImpactFusionMixin\n",
            mixin_cfg={"mixins": ["ProjectileImpactFusionMixin", "MissingMixin"]},
        )
        self.assertEqual(1, len(errs))
        self.assertIn("MissingMixin", errs[0])


if __name__ == "__main__":
    unittest.main()
