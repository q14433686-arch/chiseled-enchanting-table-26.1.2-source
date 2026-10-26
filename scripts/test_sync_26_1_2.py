#!/usr/bin/env python3
"""Offline regression contracts for the 26.3 -> 26.1.2 fix sync & version boundary."""
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


class SyncContracts26_1_2(unittest.TestCase):
    def test_26_1_2_api_boundaries_preserved(self):
        """Ensure 26.3-only Mojang/Fabric API changes never leak onto 26.1.2."""
        block = (MAIN / 'block/ChiseledEnchantingTableBlock.java').read_text()
        self.assertIn('simpleCodec(ChiseledEnchantingTableBlock::new)', block)
        self.assertIn('protected MapCodec<? extends BaseEntityBlock> codec()', block)

        processor = (MAIN / 'mechanics/structure/BookshelfReplacerProcessor.java').read_text()
        self.assertIn('extends StructureProcessor', processor)
        self.assertIn('markPosForPostprocessing', processor)

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
            self.assertNotIn('.gui.setScreen(', source, str(java))
            self.assertNotIn('.gui.screen()', source, str(java))

    def test_no_stray_scratch_files(self):
        for stray in ['Dummy.java', 'Dummy.class', 'abstract_container_screen.txt',
                      'adapt_handler.py', 'adapted_handler.java', 'append.py', 'net']:
            self.assertFalse((ROOT / stray).exists(), f'Stray artifact found: {stray}')

    def test_26_1_2_loot_and_advancement_schemas_enforced(self):
        with patch.object(compat, 'err') as err:
            compat.check_loot_tables_use_26_1_2_schema()
            compat.check_advancements_use_26_1_2_schema()
            self.assertEqual(err.call_args_list, [])

        bad_26_3_adv = {
            'criteria': {
                'craft': {
                    'trigger': 'minecraft:recipe_crafted',
                    'conditions': {'recipes': ['test:recipe']},
                }
            }
        }
        with patch.object(compat, 'iter_data_json', return_value=[('test', 'test.json', bad_26_3_adv)]), \
             patch.object(compat, 'err') as err:
            compat.check_advancements_use_26_1_2_schema()
            self.assertTrue(err.called)

        loot = json.loads((DATA / 'loot_table/blocks/chiseled_enchanting_table.json').read_text())
        fn = loot['pools'][0]['entries'][0]['functions'][0]
        self.assertEqual(fn['source'], 'block_entity')
        self.assertTrue({'minecraft:custom_name', 'minecraft:dyed_color',
                         'minecraft:custom_data', 'minecraft:lore'} <= set(fn['include']))

    def test_runtime_rules_and_frame_architecture(self):
        self.assertTrue((MAIN / 'config/rules/RuleBook.java').exists())
        self.assertTrue((MAIN / 'config/rules/RuntimeRules.java').exists())
        data_src = (MAIN / 'data/ChiseledEnchantingTableData.java').read_text()
        key_extractor_src = (MAIN / 'data/DatapackKeyExtractor.java').read_text()
        self.assertIn('public record RuntimeFrame(', (MAIN / 'data/RuntimeFrame.java').read_text())
        self.assertIn('RuntimeRules.commit(prepared, persist)', data_src)
        self.assertIn('resource.sourcePackId()', data_src + key_extractor_src)

    def test_world_regeneration_and_ritual_escrow_safeguards(self):
        self.assertTrue((MAIN / 'mechanics/resonance/RegenerationScheduler.java').exists())
        self.assertTrue((MAIN / 'mechanics/resonance/WorldRegeneration.java').exists())
        wear = (MAIN / 'mechanics/resonance/MasterTomeWear.java').read_text()
        self.assertIn('WorldRegeneration.observe(', wear)
        self.assertIn('hasClearLineOfSight(level, tablePos, scanPos)', wear)

        be = (MAIN / 'block/ChiseledEnchantingTableBlockEntity.java').read_text()
        for token in ['MAX_RITUAL_STEPS = InscriptionRitualMechanics.MAX_QUEUE_STEPS',
                      'MAX_RITUAL_CHARACTERS = InscriptionRitualMechanics.MAX_QUEUE_CHARACTERS',
                      'ritualSession', 'ritualTarget', 'ritualBlocked',
                      'canPlaceItem', 'canTakeItem', 'menu.world() == level']:
            self.assertIn(token, be)
        ritual = (MAIN / 'mechanics/ritual/InscriptionRitualMechanics.java').read_text()
        self.assertIn('MAX_QUEUE_STEPS = 256', ritual)
        self.assertIn('MAX_QUEUE_CHARACTERS = 32_000', ritual)

    def test_design_audit_package_anchors(self):
        self.assertTrue((MAIN / 'mechanics/resonance/TomeWearData.java').exists())
        self.assertTrue((MAIN / 'BundledDefaults.java').exists())
        self.assertTrue((MAIN / 'data/DataIds.java').exists())
        self.assertTrue((MAIN / 'menu/EnchantingNetworking.java').exists())
        self.assertTrue((MAIN / 'mechanics/enchanting/BookConversion.java').exists())
        self.assertTrue((MAIN / 'mechanics/enchanting/InkStainLore.java').exists())
        self.assertTrue((MAIN / 'mechanics/loot/ApplyWildBookWearFunction.java').exists())
        wear = (MAIN / 'mechanics/resonance/TomeWearData.java').read_text()
        self.assertIn('cet_generation', wear)
        self.assertIn('computeOverflowNextWear', wear)
        catalog = (MAIN / 'config/ConfigCatalog.java').read_text()
        self.assertIn('grindstone_depleted_tome', catalog)
        self.assertIn('restrict_villager_infinity_books', catalog)
        tag = ROOT / 'src/main/resources/data/chiseled_enchanting_table/tags/enchantment/wear_immune.json'
        self.assertTrue(tag.exists())
        self.assertIn('minecraft:infinity', tag.read_text())
        gen = ROOT / 'scripts/gen_bundled_defaults.py'
        self.assertTrue(gen.exists())

    def test_audit_guards_authoritative_sync_and_input(self):
        protocol = (MAIN / 'menu/EnchantingProtocol.java').read_text()
        self.assertIn('CURRENT_REVISION_NUMBER = 18', protocol)
        self.assertIn('REVISION = "26_1_2_revision_" + CURRENT_REVISION_NUMBER', protocol)
        self.assertIn('IntStream.range(1, CURRENT_REVISION_NUMBER)', protocol)
        self.assertIn('IntStream.range(9, CURRENT_REVISION_NUMBER)', protocol)
        self.assertNotIn('Minecraft 26.3', protocol)

        sync = (MAIN / 'menu/EnchantCostSync.java').read_text()
        self.assertIn('player.closeContainer()', sync)
        self.assertIn('EnchantingProtocol.incompatible(', sync)

        handler = (MAIN / 'menu/ChiseledEnchantingTableScreenHandler.java').read_text()
        self.assertIn('!live.targets(this.containerId)', handler)
        self.assertNotIn('return ChiseledEnchantingTableData.transformationEnchantmentsFor(input)', handler)
        self.assertNotIn('display != null && display.deducted_levels() > 0', handler)

        for name in ['RemoveEnchantedBooksFunction', 'DepthBasedEfficiencyFunction']:
            loot = (MAIN / f'mechanics/loot/{name}.java').read_text()
            self.assertIn('commonFields(i).apply(i,', loot)
            self.assertNotIn('MapCodec.unit(', loot)

        jei = (CLIENT / 'compat/jei/ChiseledEnchantingJeiPlugin.java').read_text()
        self.assertIn('manager.hideRecipes(', jei)
        self.assertIn('manager.addRecipes(', jei)
        self.assertIn('Minecraft.getInstance().screen instanceof ArcaneGrimoireScreen', jei)

        rei = (CLIENT / 'compat/rei/ChiseledEnchantingReiPlugin.java').read_text()
        self.assertIn('registry.registerDisplayGenerator(', rei)
        self.assertIn('EntryStacks.equalsFuzzy(entry, focus)', rei)

    def test_backport_26_3_seven_path_fixes(self):
        """2026-10-26 backport of the 26.3 seven-path audit fixes, 26.1.2-adapted."""
        main_mod = (MAIN / 'ChiseledEnchantingTable.java').read_text()
        self.assertIn('FABRIC_MOD_ID = "chiseled-enchanting-table"', main_mod)

        extractor = (MAIN / 'data/DatapackKeyExtractor.java').read_text()
        self.assertIn('FABRIC_MOD_ID', extractor)
        self.assertIn('source.equals("fabric") || source.equals("vanilla")', extractor)
        for key in ['locked_shelf_breakable', 'locked_book_extraction_blocked',
                    'early_unlock_xp_levels', 'early_unlock_grit_count',
                    'sacrifice_required', 'tome_page_required_shelves']:
            self.assertIn(key, extractor)

        data = (MAIN / 'data/ChiseledEnchantingTableData.java').read_text()
        self.assertIn('record LootRegistryBuildRules(', data)
        self.assertIn('BUILT_IN_CLASSIC_COSTS', data)
        self.assertIn('d.tomePageRequiredShelves = rawLibraryResonanceConfig.tomePageRequiredShelves()', data)
        self.assertNotIn('preloadedLootInjections', data)
        self.assertNotIn('preloadedVanillaChestBooksMode', data)

        mixin = (MAIN / 'mixin/loot/ReloadableServerRegistriesMixin.java').read_text()
        self.assertIn('cancellable = true, require = 1', mixin)
        self.assertIn('CompletableFuture.failedFuture(error)', mixin)
        self.assertNotIn('falling back to bundled defaults', mixin)

        payload = (MAIN / 'menu/EnchantCostTablePayload.java').read_text()
        self.assertIn('configuration/header exceeds wire budget', payload)
        self.assertNotIn('actualBytes <= maxSafeBytes || candidate.costs().isEmpty()', payload)

        sync = (MAIN / 'menu/EnchantCostSync.java').read_text()
        self.assertIn('cost_sync_failed', sync)
        self.assertIn('LAST_DISPLAYS.remove(player.getUUID())', sync)

        particles = (MAIN / 'mechanics/resonance/TomeParticleEffects.java').read_text()
        self.assertIn('SERVER_STOPPED', particles)
        self.assertIn('server.getLevel(b.level.dimension()) != b.level', particles)

        creative = MAIN / 'mixin/bookshelf/BookshelfCreativeExtractionMixin.java'
        self.assertTrue(creative.exists())
        self.assertIn('method = "removeBook"', creative.read_text())
        mixins = json.loads((ROOT / 'src/main/resources/chiseled-enchanting-table-port.mixins.json').read_text())
        self.assertIn('bookshelf.BookshelfCreativeExtractionMixin', mixins['mixins'])
        self.assertEqual(14, len(mixins['mixins']))

        cfg = (MAIN / 'config/ModRuntimeConfig.java').read_text()
        self.assertNotIn('Files.copy(temporary, path', cfg)
        self.assertNotIn('Files.writeString(path, json', cfg)

        screen = (CLIENT / 'gui/config/ChiseledEnchantingConfigScreen.java').read_text()
        self.assertIn('ConfigSaveSession', screen)
        self.assertIn('saves.expire(', screen)
        self.assertIn('must not leave an editor permanently stuck in pending', screen)

    def test_config_authority_and_persistence_guards(self):
        cfg = (MAIN / 'config/ModRuntimeConfig.java').read_text()
        self.assertIn('StandardCopyOption.ATOMIC_MOVE', (MAIN / 'config/ConfigFileStore.java').read_text())
        self.assertIn('saveToDisk(normalized);', cfg)
        net = (MAIN / 'config/ModConfigNetworking.java').read_text()
        self.assertIn('server.isSingleplayerOwner(player.nameAndId())', net)
        self.assertIn('payload.requestId()', net)
        self.assertIn('cancel-ritual', net)
        screen = (CLIENT / 'gui/config/ChiseledEnchantingConfigScreen.java').read_text()
        self.assertIn('reconcileSaveAcknowledgement()', screen)
        self.assertIn('this.minecraft.setScreen(this.parentScreen)', screen)
        self.assertNotIn('event.button() == 0', screen)
        self.assertNotIn('event.button() == 1', screen)


if __name__ == '__main__':
    unittest.main()
