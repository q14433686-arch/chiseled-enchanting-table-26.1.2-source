#!/usr/bin/env python3
"""26.1.2 resource & API boundary regression checks for Enchantment Fusion."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Sync2612BoundaryTest(unittest.TestCase):
    def test_26_1_2_item_model_definitions_exist(self) -> None:
        items_dir = ROOT / "src/client/resources/assets/enchantment_fusion/items"
        for item_id in ("enchantment_fusion_table", "fused_enchanted_book", "resonance_stabilizer"):
            p = items_dir / f"{item_id}.json"
            self.assertTrue(p.is_file(), f"Missing 26.1.2 items definition: {p}")
            payload = json.loads(p.read_text(encoding="utf-8"))
            self.assertEqual("minecraft:model", payload["model"]["type"])

    def test_singular_datapack_directories_only(self) -> None:
        data_root = ROOT / "src/main/resources/data"
        forbidden_plural = ("recipes", "loot_tables", "advancements")
        for plural in forbidden_plural:
            matches = list(data_root.rglob(plural))
            self.assertEqual([], matches, f"Legacy plural datapack folder forbidden on 26.1.2: {matches}")

    def test_client_uses_gui_graphics_extractor_and_no_resource_location(self) -> None:
        for src_root in (ROOT / "src/main/java", ROOT / "src/client/java"):
            for jf in src_root.rglob("*.java"):
                txt = jf.read_text(encoding="utf-8")
                self.assertNotIn("ResourceLocation", txt, f"Legacy ResourceLocation forbidden on 26.1.2 in {jf}")


if __name__ == "__main__":
    unittest.main()
