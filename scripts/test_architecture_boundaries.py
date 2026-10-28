#!/usr/bin/env python3
"""Unit tests for check_architecture_boundaries.py."""
from pathlib import Path
import tempfile
import unittest
from check_architecture_boundaries import scan_source_boundaries


class ArchitectureBoundariesTest(unittest.TestCase):
    def test_real_codebase_passes(self) -> None:
        self.assertEqual([], scan_source_boundaries())

    def test_detects_client_import_in_main_and_viewer_leak_in_client(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            main_dir = Path(tmp) / "main/enchantment_fusion"
            client_dir = Path(tmp) / "client/enchantment_fusion/gui"
            main_dir.mkdir(parents=True)
            client_dir.mkdir(parents=True)
            (main_dir / "BadServer.java").write_text(
                "package enchantment_fusion;\nimport net.minecraft.client.Minecraft;\nclass BadServer {}\n",
                encoding="utf-8",
            )
            (client_dir / "BadGui.java").write_text(
                "package enchantment_fusion.gui;\nimport mezz.jei.api.IModPlugin;\nclass BadGui {}\n",
                encoding="utf-8",
            )
            errs = scan_source_boundaries(Path(tmp) / "main", Path(tmp) / "client")
            self.assertEqual(2, len(errs))


if __name__ == "__main__":
    unittest.main()
