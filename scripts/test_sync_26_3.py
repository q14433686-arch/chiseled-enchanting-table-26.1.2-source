#!/usr/bin/env python3
"""Offline regression contracts for the 26.1.2 → 26.3 sync (not a Java compile test)."""
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main/java/chiseled_enchanting_table'
CLIENT = ROOT / 'src/client/java/chiseled_enchanting_table'
DATA = ROOT / 'src/main/resources/data/chiseled_enchanting_table'

spec = importlib.util.spec_from_file_location('compat', ROOT / 'scripts/compat_check.py')
compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compat)


class SyncContracts(unittest.TestCase):
    def test_target_api_contracts(self):
        block = (MAIN / 'block/ChiseledEnchantingTableBlock.java').read_text()
        self.assertNotIn('simpleCodec(', block)
        self.assertNotIn('protected MapCodec', block)
        processor = (MAIN / 'mechanics/structure/BookshelfReplacerProcessor.java').read_text()
        self.assertIn('implements StructureProcessor', processor)
        self.assertIn('BlockPos templatePos', processor)
        self.assertIn('markPosForPostProcessing', processor)
        renderer = (CLIENT / 'render/ChiseledEnchantingTableBlockEntityRenderer.java').read_text()
        self.assertIn('poseStack.rotate(', renderer)
        self.assertIn('submitCrumblingOverlay(', renderer)
        for name in ['RemoveEnchantedBooksFunction', 'DepthBasedEfficiencyFunction']:
            self.assertIn('Optional<Holder<LootItemCondition>>',
                          (MAIN / f'mechanics/loot/{name}.java').read_text())
        depth = (MAIN / 'mechanics/loot/DepthBasedEfficiencyFunction.java').read_text()
        self.assertIn('context.getOptional(LootContextParams.ORIGIN)', depth)
        injector = (MAIN / 'mechanics/loot/EnchantedBookLootInjector.java').read_text()
        for token in ['ContextIntProviders.exactly', 'UniformContainerBase.Builder',
                      'DepthBasedEfficiencyFunction::new']:
            self.assertIn(token, injector)

    def test_removed_network_helpers_not_reintroduced(self):
        for java in MAIN.rglob('*.java'):
            source = java.read_text()
            for call in ['.writeCollection(', '.readCollection(', '.readList(',
                         '.getOptionalParameter(']:
                self.assertNotIn(call, source, str(java))
        payload = (MAIN / 'menu/ChiseledEnchantingMenuPayloads.java').read_text()
        for token in ['stored_master_levels', 'epiphany_seed', 'RerollEpiphanyPayload',
                      'FreezeEpiphanyPayload', 'buf.writeVarInt(unlocked_enchantements.size())']:
            self.assertIn(token, payload)
        handler = (MAIN / 'menu/ChiseledEnchantingTableScreenHandler.java').read_text()
        self.assertIn('drop(overflow, false, net.minecraft.util.Prediction.PREDICTED)', handler)

    def test_features_and_mixins_present(self):
        for path in ['mechanics/resonance/LibraryResonance.java',
                     'compat/TransformationQueryIndex.java']:
            self.assertTrue((MAIN / path).exists())
        for path in ['compat/jei/TransformationRecipeCategory.java',
                     'compat/rei/ReiTransformationCategory.java']:
            self.assertTrue((CLIENT / path).exists())
        config = json.loads((ROOT / 'src/main/resources/chiseled-enchanting-table-port.mixins.json').read_text())
        self.assertIn('anvil.RemoveXpCostScaling', config['mixins'])
        self.assertNotIn("exclude 'chiseled_enchanting_table/mixin/anvil/RemoveXpCostScaling.java'",
                         (ROOT / 'build.gradle').read_text())
        for name in ['treasure_epiphany', 'book_upgrades', 'item_transformations']:
            self.assertTrue((DATA / f'chiseled_enchanting_table/{name}.json').exists())

    def test_legacy_loot_schema_is_rejected(self):
        legacy = {'pools': [{'conditions': [{'condition': 'minecraft:survives_explosion'}],
                             'functions': [{'function': 'minecraft:copy_components'}]}]}
        modern = {'pools': [{'condition': {'type': 'minecraft:survives_explosion'},
                             'modifier': {'type': 'minecraft:copy_components'}}]}
        for data, should_fail in [(legacy, True), (modern, False)]:
            with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', data)]), \
                 patch.object(compat, 'err') as err:
                compat.check_loot_tables_use_26_3_schema()
                self.assertEqual(err.called, should_fail)

    def test_legacy_advancement_schema_is_rejected(self):
        for conditions, should_fail in [({'recipe_id': 'test:recipe'}, True),
                                         ({'recipes': ['test:recipe']}, False)]:
            data = {'criteria': {'craft': {'trigger': 'minecraft:recipe_crafted',
                                           'conditions': conditions}}}
            with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', data)]), \
                 patch.object(compat, 'err') as err:
                compat.check_advancements_use_26_3_schema()
                self.assertEqual(err.called, should_fail)

    def test_runtime_log_condition_array_regression(self):
        for condition, should_fail in [
            ([{'type': 'minecraft:survives_explosion'}], True),
            ({'type': 'minecraft:survives_explosion'}, False),
            ({'type': 'minecraft:all_of', 'terms': [
                {'type': 'minecraft:survives_explosion'}]}, False),
        ]:
            with self.subTest(condition=condition):
                data = {'pools': [{'condition': condition}]}
                with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', data)]), \
                     patch.object(compat, 'err') as err:
                    compat.check_loot_tables_use_26_3_schema()
                    self.assertEqual(err.called, should_fail)

    def test_recipe_unlocked_requires_recipes(self):
        for conditions, should_fail in [
            ({'recipe': 'test:recipe'}, True),
            ({}, True),
            ({'recipes': 'test:recipe'}, True),
            ({'recipes': ['test:recipe']}, False),
        ]:
            with self.subTest(conditions=conditions):
                data = {'criteria': {'has_the_recipe': {
                    'trigger': 'minecraft:recipe_unlocked', 'conditions': conditions}}}
                with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', data)]), \
                     patch.object(compat, 'err') as err:
                    compat.check_advancements_use_26_3_schema()
                    self.assertEqual(err.called, should_fail)

    def test_bundled_datapack_passes_schema_checks(self):
        with patch.object(compat, 'err') as err:
            compat.check_loot_tables_use_26_3_schema()
            compat.check_advancements_use_26_3_schema()
            self.assertEqual(err.call_args_list, [])
        loot = json.loads((DATA / 'loot_table/blocks/chiseled_enchanting_table.json').read_text())
        modifier = loot['pools'][0]['entries'][0]['modifier']
        self.assertEqual(modifier['source'], 'block_entity')
        self.assertTrue({'minecraft:custom_name', 'minecraft:dyed_color',
                         'minecraft:custom_data', 'minecraft:lore'} <= set(modifier['include']))

    def test_modifier_sequence_still_allowed(self):
        data = {'pools': [{'modifier': [{'type': 'minecraft:copy_components'}]}]}
        with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', data)]), \
             patch.object(compat, 'err') as err:
            compat.check_loot_tables_use_26_3_schema()
            self.assertFalse(err.called)

    def test_incremental_client_screen_api(self):
        for java in CLIENT.rglob('*.java'):
            source = java.read_text()
            self.assertNotIn('getInstance().setScreen(', source, str(java))
            self.assertNotIn('getInstance().screen ', source, str(java))
        plugin = (CLIENT / 'compat/jei/ChiseledEnchantingJeiPlugin.java').read_text()
        self.assertIn('getInstance().gui.screen() instanceof ArcaneGrimoireScreen', plugin)

    def test_incremental_features_and_live_payload(self):
        for path in ['mechanics/enchanting/DisenchantmentApplication.java',
                     'mechanics/resonance/BookshelfProgression.java',
                     'config/ModBootstrapConfig.java', 'item/ArcaneGrimoireItem.java']:
            self.assertTrue((MAIN / path).exists())
        payload = (MAIN / 'menu/ChiseledEnchantingMenuPayloads.java').read_text()
        for token in ['LiveShelfStatePayload', 'high_tier_chiseled_books', 'ReopenTablePayload',
                      'DISENCHANT_ALL', 'PURGE_CURSE']:
            self.assertIn(token, payload)
        self.assertTrue((CLIENT / 'gui/grimoire/ArcaneGrimoireScreen.java').exists())

    def test_grimoire_clicks_use_platform_button_constants(self):
        source = (CLIENT / 'gui/grimoire/ArcaneGrimoireScreen.java').read_text()
        click = source.split('public boolean mouseClicked(', 1)[1].split(
            'public boolean mouseScrolled(', 1)[0]
        # SDL left-click must reach navigation AND recipe lookup, not the right-click uses path.
        self.assertIn('event.button() == InputConstants.MOUSE_BUTTON_RIGHT', click)
        self.assertEqual(click.count('event.button() == InputConstants.MOUSE_BUTTON_LEFT'), 2)
        self.assertNotRegex(click, r'event\.button\(\)\s*==\s*\d+')
        self.assertIn('case NAV_SPREAD -> jumpToSpread', click)
        self.assertIn('case NAV_CLOSE -> onClose()', click)
        self.assertIn('case GrimoireDiagramRenderer.ACTION_SHELF_STEP', click)
        # The same handler serves the table overlay, which must keep forwarding clicks.
        table = (CLIENT / 'gui/ChiseledEnchantingTableScreen.java').read_text()
        self.assertIn('this.grimoireOverlay.mouseClicked(event, isDoubleClick)', table)

    def test_audit_guards_authoritative_sync_and_input(self):
        sync = (MAIN / 'menu/EnchantCostSync.java').read_text()
        self.assertIn('player.closeContainer()', sync)
        handler = (MAIN / 'menu/ChiseledEnchantingTableScreenHandler.java').read_text()
        self.assertIn('!live.targets(this.containerId)', handler)
        self.assertNotIn('return ChiseledEnchantingTableData.transformationEnchantmentsFor(input)', handler)
        self.assertIn('EnchantingProtocol.incompatible(', sync)
        table = (CLIENT / 'gui/ChiseledEnchantingTableScreen.java').read_text()
        self.assertIn('event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT', table)
        for name in ['RemoveEnchantedBooksFunction', 'DepthBasedEfficiencyFunction']:
            loot = (MAIN / f'mechanics/loot/{name}.java').read_text()
            self.assertIn('commonFields(i).apply(i,', loot)
            self.assertNotIn('MapCodec.unit(', loot)
        index = (MAIN / 'compat/TransformationQueryIndex.java').read_text()
        self.assertIn('SyncedEnchantmentCosts.transformationDisplays()', index)
        self.assertIn('display.input_is_tag()', index)
        self.assertIn('stack.is(tag)', index)
        self.assertIn('XpCostConversion.forTransformation(', index)
        self.assertNotIn('display != null && display.deducted_levels() > 0', handler)

        jei = (CLIENT / 'compat/jei/ChiseledEnchantingJeiPlugin.java').read_text()
        self.assertIn('manager.hideRecipes(', jei)
        self.assertIn('manager.addRecipes(', jei)
        rei = (CLIENT / 'compat/rei/ChiseledEnchantingReiPlugin.java').read_text()
        self.assertIn('registry.registerDisplayGenerator(', rei)
        self.assertIn('EntryStacks.equalsFuzzy(entry, focus)', rei)
        self.assertNotIn('registry.add(new ReiTransformationDisplay(', rei)



if __name__ == '__main__':
    unittest.main()
