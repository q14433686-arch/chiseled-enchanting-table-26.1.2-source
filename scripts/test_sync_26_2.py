#!/usr/bin/env python3
"""Offline regression contracts for the 26.3 -> 26.2 full port & version boundary.

Adapted from the mother line's test_sync_26_1_2.py, but asserts the **26.2** Mojang/Fabric API
boundary (see SYNC_TO_26_2_AND_26_3_2026_10_26.md section 2 matrix + CURRENT_BASELINE.md 9.3/9.4).
"""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main/java/chiseled_enchanting_table'
CLIENT = ROOT / 'src/client/java/chiseled_enchanting_table'
DATA = ROOT / 'src/main/resources/data/chiseled_enchanting_table'


class SyncContracts26_2(unittest.TestCase):
    def test_26_2_api_boundaries_preserved(self):
        """No 26.1.2/26.3-only APIs may leak onto 26.2."""
        # Block keeps its per-block codec (26.3 removed BaseEntityBlock#codec()).
        block = (MAIN / 'block/ChiseledEnchantingTableBlock.java').read_text()
        self.assertIn('simpleCodec(ChiseledEnchantingTableBlock::new)', block)
        self.assertIn('codec()', block)

        # 26.2 StructureProcessor is an interface with codec() + markPosForPostProcessing.
        processor = (MAIN / 'mechanics/structure/BookshelfReplacerProcessor.java').read_text()
        self.assertIn('implements StructureProcessor', processor)
        self.assertIn('markPosForPostProcessing', processor)
        self.assertNotIn('extends StructureProcessor', processor)
        self.assertNotIn('StructureProcessorType', processor)
        registry = (MAIN / 'registry/StructureProcessorRegistry.java').read_text()
        self.assertIn('BuiltInRegistries.STRUCTURE_PROCESSOR', registry)

        renderer = (CLIENT / 'render/ChiseledEnchantingTableBlockEntityRenderer.java').read_text()
        self.assertIn('poseStack.mulPose(', renderer)
        self.assertNotIn('submitCrumblingOverlay(', renderer)

        for name in ['RemoveEnchantedBooksFunction', 'DepthBasedEfficiencyFunction']:
            text = (MAIN / f'mechanics/loot/{name}.java').read_text()
            self.assertIn('List<LootItemCondition>', text)
            self.assertNotIn('Optional<Holder<LootItemCondition>>', text)
        depth = (MAIN / 'mechanics/loot/DepthBasedEfficiencyFunction.java').read_text()
        self.assertIn('context.getOptionalParameter(LootContextParams.ORIGIN)', depth)
        for name in ['EnchantedBookLootInjector', 'DepletedTomeWorldMechanics']:
            loot_src = (MAIN / f'mechanics/loot/{name}.java').read_text()
            self.assertIn('ConstantValue.exactly(', loot_src)
            self.assertNotIn('ContextIntProviders', loot_src)

        for java in list(MAIN.rglob('*.java')) + list(CLIENT.rglob('*.java')):
            source = java.read_text()
            self.assertNotIn('Prediction.PREDICTED', source, str(java))
            # ChatFormatting removed in 26.2: only the CetFormatting shim may mention the name.
            if java.name != 'CetFormatting.java':
                self.assertNotIn('import net.minecraft.ChatFormatting;', source, str(java))
                self.assertNotIn('net.minecraft.ChatFormatting.', source, str(java))
        # Screen management lives on Gui in 26.2/26.3.
        client = (CLIENT / 'ChiseledEnchantingTableClient.java').read_text()
        self.assertIn('.gui.setScreen(', client)
        self.assertNotIn('Minecraft.getInstance().setScreen(', client)

    def test_chatformatting_shim_present(self):
        """ChatFormatting removal (omitted from the guide's section 2 matrix) is handled by a shim."""
        shim = MAIN / 'compat/CetFormatting.java'
        self.assertTrue(shim.exists())
        s = shim.read_text()
        for color in ['GOLD', 'GRAY', 'AQUA', 'GREEN', 'RED', 'LIGHT_PURPLE', 'DARK_GRAY',
                      'YELLOW', 'DARK_AQUA', 'DARK_PURPLE', 'DARK_RED']:
            self.assertIn(f'Style {color}', s)

    def test_protocol_26_2_revision_7(self):
        protocol = (MAIN / 'menu/EnchantingProtocol.java').read_text()
        self.assertIn('CURRENT_REVISION_NUMBER = 7', protocol)
        self.assertIn('"26_2_revision_" + CURRENT_REVISION_NUMBER', protocol)
        self.assertIn('IntStream.range(1, CURRENT_REVISION_NUMBER)', protocol)
        self.assertNotIn('IntStream.range(9, CURRENT_REVISION_NUMBER)', protocol)
        self.assertNotIn('26_1_2_revision_', protocol)

    def test_26_2_loot_and_advancement_schemas(self):
        """26.2 keeps the plural conditions[]/functions[] and recipe_id schema (not 26.3 singular/recipes[])."""
        loot = json.loads((DATA / 'loot_table/blocks/chiseled_enchanting_table.json').read_text())
        fn = loot['pools'][0]['entries'][0]['functions'][0]
        self.assertEqual(fn['source'], 'block_entity')
        for f in (DATA / 'advancement').glob('*.json'):
            d = json.loads(f.read_text())
            for crit in d.get('criteria', {}).values():
                if crit.get('trigger') == 'minecraft:recipe_crafted':
                    self.assertIn('recipe_id', crit.get('conditions', {}), f.name)
                    self.assertNotIn('recipes', crit.get('conditions', {}), f.name)

    def test_seven_path_backport_fixes_present(self):
        """RULE/RELOAD/NET/LIFE/MIXIN/DEAD fixes from the 26.3 seven-path audit landed with the mother tree."""
        main_mod = (MAIN / 'ChiseledEnchantingTable.java').read_text()
        self.assertIn('FABRIC_MOD_ID', main_mod)  # RULE-02 hyphenated mod id
        extractor = (MAIN / 'data/DatapackKeyExtractor.java').read_text()
        for key in ['locked_shelf_breakable', 'early_unlock_xp_levels', 'tome_page_required_shelves']:
            self.assertIn(key, extractor)  # RULE-01 six keys
        data = (MAIN / 'data/ChiseledEnchantingTableData.java').read_text()
        self.assertIn('LootRegistryBuildRules', data)  # RELOAD-02
        self.assertIn('BUILT_IN_CLASSIC_COSTS', data)  # RELOAD-03
        mixin = (MAIN / 'mixin/loot/ReloadableServerRegistriesMixin.java').read_text()
        self.assertIn('CompletableFuture.failedFuture(error)', mixin)  # RELOAD-01
        payload = (MAIN / 'menu/EnchantCostTablePayload.java').read_text()
        self.assertIn('costTableComplete', payload)  # NET-01
        sync = (MAIN / 'menu/EnchantCostSync.java').read_text()
        self.assertIn('cost_sync_failed', sync)  # NET-02
        self.assertIn('LAST_DISPLAYS.remove(player.getUUID())', sync)
        particles = (MAIN / 'mechanics/resonance/TomeParticleEffects.java').read_text()
        self.assertIn('SERVER_STOPPED', particles)  # LIFE-01
        self.assertTrue((MAIN / 'mixin/bookshelf/BookshelfCreativeExtractionMixin.java').exists())  # MIXIN-01
        mixins = json.loads((ROOT / 'src/main/resources/chiseled-enchanting-table-port.mixins.json').read_text())
        self.assertEqual(14, len(mixins['mixins']))  # DEAD-01 count
        # DEAD-01 retired legacy files must not exist
        for stray in ['config/ModBootstrapConfig.java', 'utils/BlockPosStream.java',
                      'utils/ChiseledBookshelfLootTable.java',
                      'modifyChestLootTable/ReworkEnchantedBookChestLoot.java']:
            self.assertFalse((MAIN / stray).exists(), stray)
        # The active JSON-driven loot function must still be present (DEAD-01 removed only the
        # legacy hard-coded chest-loot packages, not the registered function).
        self.assertTrue((MAIN / 'mechanics/loot/RemoveEnchantedBooksFunction.java').exists())

    def test_no_stray_scratch_files(self):
        for stray in ['Dummy.java', 'Dummy.class', 'abstract_container_screen.txt',
                      'adapt_handler.py', 'adapted_handler.java', 'append.py', 'net']:
            self.assertFalse((ROOT / stray).exists(), f'Stray artifact found: {stray}')


if __name__ == '__main__':
    unittest.main()
