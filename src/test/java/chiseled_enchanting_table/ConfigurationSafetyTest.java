package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSafetyTest {
    @TempDir Path directory;

    @Test void lanGuestsAreNotImplicitAdministrators() {
        assertFalse(ModConfigNetworking.mayManage(false, false, false));
        assertTrue(ModConfigNetworking.mayManage(false, true, false));
        assertTrue(ModConfigNetworking.mayManage(false, false, true));
        assertFalse(ModConfigNetworking.mayManage(true, true, false));
        assertTrue(ModConfigNetworking.mayManage(true, false, true));
    }

    @Test void atomicSaveReplacesValidConfigurationAndCleansTemporaryFiles() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "old content");
        ModRuntimeConfig.writeAtomically(file, ModRuntimeConfig.DEFAULT);
        assertEquals(ModRuntimeConfig.DEFAULT, ModRuntimeConfig.fromJsonString(Files.readString(file)));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void failedReplacementDoesNotDestroyExistingTarget() throws Exception {
        Path target = directory.resolve("config.json");
        Files.createDirectory(target);
        Files.writeString(target.resolve("sentinel"), "preserve");
        assertThrows(java.io.IOException.class, () -> ModRuntimeConfig.writeAtomically(target, ModRuntimeConfig.DEFAULT));
        assertEquals("preserve", Files.readString(target.resolve("sentinel")));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void malformedUpdatesAreRejectedAndSaveIdsDoNotRepeat() {
        assertThrows(RuntimeException.class, () -> ModRuntimeConfig.fromJsonString("{broken"));
        assertThrows(RuntimeException.class, () -> ModRuntimeConfig.fromJsonString("[]"));
        long first = ModRuntimeConfig.nextSaveRequestId();
        long second = ModRuntimeConfig.nextSaveRequestId();
        assertTrue(first > 0 && second > first);
        ModRuntimeConfig.acknowledgeSave(first, false);
        assertEquals(first, ModRuntimeConfig.acknowledgedSaveId());
        assertFalse(ModRuntimeConfig.acknowledgedSaveAccepted());
    }

    @Test void allFourPresetsRoundTripRestore64BookStackAnd103Semantics() {
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.enchantedBookStackSize = 1;
        draft.overrideDatapacks = true;

        for (ModRuntimeConfig.Preset preset : ModRuntimeConfig.Preset.values()) {
            draft.enchantedBookStackSize = 1;
            preset.applyTo(draft);
            assertTrue(preset.matches(draft), "Preset must match draft right after applyTo: " + preset);
            assertEquals(64, draft.enchantedBookStackSize,
                "All 4 presets (including 1.0.3 Classic) must set 64 Enchanted Book stack size: " + preset);
            assertTrue(draft.overrideDatapacks, "Presets must preserve overrideDatapacks governance toggle");
            var roundTripped = ModRuntimeConfig.fromJsonString(draft.build().toJsonString()).toDraft();
            assertTrue(preset.matches(roundTripped), "Preset must survive JSON round-trip: " + preset);
        }

        ModRuntimeConfig.Preset.CLASSIC_1_0_3.applyTo(draft);
        var classic = draft.build();
        assertTrue(classic.classic103Mode());
        assertEquals(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP, classic.tomeConsumptionMode());
        assertFalse(classic.tomeRegenEnabled());
        assertFalse(classic.disenchantEnabled());
        assertEquals(0.0, classic.epiphanyMaxChance(), 1e-9);
        assertEquals(2, classic.horizontalScanRadius());
        assertEquals(2, classic.verticalScanRadius());
        assertFalse(classic.requireBreathingRoom());
        assertFalse(classic.requireLineOfSight());
        assertEquals(0, classic.maxWoodenDiscountPercent());
        assertEquals(0, classic.maxTotalXpDiscountPercent());

        var original = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
        try {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                classic, ModRuntimeConfig.PackPolicy.NONE, () -> {}
            );
            var classicFrame = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
            assertEquals(2, classicFrame.itemTransformations().size(),
                "1.0.3 Classic mode must expose only the 2 original item transformations (Golden Apple & Experience Bottle)");
        } finally {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {}
            );
        }
    }

    @Test
    void classic103ModeFlagAloneForcesFullPresetLinkage() {
        // The standalone classic_1_0_3_mode toggle must be identical to the preset:
        // forcing happens in the ModRuntimeConfig compact constructor, so flipping
        // ONLY the flag on an otherwise-Standard draft still yields 1.0.3 semantics.
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        assertFalse(draft.classic103Mode);
        draft.classic103Mode = true;
        var classic = draft.build();
        assertTrue(classic.classic103Mode());
        assertEquals(64, classic.enchantedBookStackSize());
        assertEquals(0.0, classic.epiphanyBaseChance(), 1e-9);
        assertEquals(0.0, classic.epiphanyMaxChance(), 1e-9);
        assertFalse(classic.disenchantEnabled());
        assertFalse(classic.allowCursePurge());
        assertEquals(
            chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP,
            classic.tomeConsumptionMode());
        assertFalse(classic.tomeRegenEnabled());
        assertEquals(0, classic.maxWoodenDiscountPercent());
        assertEquals(0, classic.maxTotalXpDiscountPercent());
        assertEquals(0.03, classic.bookshelfEnchantedProb(), 1e-9);
        assertFalse(classic.directGatesEnabled());
        assertTrue(classic.anvilBlockSameBookMerge());
        assertEquals(2, classic.horizontalScanRadius());
        assertEquals(2, classic.verticalScanRadius());
        assertEquals(1, classic.transformationMaxBatchSize());
    }

    @Test
    void classicFrameUses103CostAndBookUpgradeTables() {
        // Audit findings 06/07/08 (external report): "full price" in classic must
        // mean the 1.0.3 prices — XP table, research materials and the golden
        // apple's ranged emerald cost all come from tag 26.1.2_1.0.3 data.
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.classic103Mode = true;
        var classic = draft.build();
        var original = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
        try {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                classic, ModRuntimeConfig.PackPolicy.NONE, () -> {
                }
            );
            var mending1 = new chiseled_enchanting_table.utils.EnchantmentWithLevel(
                net.minecraft.resources.Identifier.withDefaultNamespace("mending"), 1);
            assertEquals(35, chiseled_enchanting_table.data.ChiseledEnchantingTableData.xpCost(mending1, 1),
                "1.0.3 Mending I override is 35 XP levels");
            var protection4 = new chiseled_enchanting_table.utils.EnchantmentWithLevel(
                net.minecraft.resources.Identifier.withDefaultNamespace("protection"), 4);
            assertEquals(25, chiseled_enchanting_table.data.ChiseledEnchantingTableData.xpCost(protection4, 4),
                "1.0.3 common max-level cost is 25 XP levels");
            var sharpness3 = new chiseled_enchanting_table.utils.EnchantmentWithLevel(
                net.minecraft.resources.Identifier.withDefaultNamespace("sharpness"), 3);
            var research = chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookResearchCostOptions(sharpness3, 5);
            assertTrue(research.stream().flatMap(java.util.List::stream)
                    .anyMatch(st -> st.is(net.minecraft.world.item.Items.FLINT)),
                "1.0.3 Sharpness research uses its themed Flint material");
            var apple = chiseled_enchanting_table.data.ChiseledEnchantingTableData.itemTransformation(
                chiseled_enchanting_table.ChiseledEnchantingTable.identifier("enchanted_golden_apple"));
            assertTrue(apple.isPresent());
            assertEquals(2, apple.get().additionalCosts().getFirst().minCount());
            assertEquals(5, apple.get().additionalCosts().getFirst().maxCount());
            for (String salt : java.util.List.of("00000000-0000-0000-0000-000000000001",
                    "00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000003",
                    "00000000-0000-0000-0000-000000000004", "00000000-0000-0000-0000-000000000005")) {
                var bundle = chiseled_enchanting_table.data.ChiseledEnchantingTableData.classicTransformationCosts(
                    apple.get().id(), salt);
                int emeralds = bundle.stream().filter(st -> st.is(net.minecraft.world.item.Items.EMERALD))
                    .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
                assertTrue(emeralds >= 2 && emeralds <= 5, "Classic apple emerald cost must stay in 2..5, got " + emeralds);
            }
        } finally {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {
                }
            );
        }
    }

    @Test
    void classicModeLocksSubordinateConfigOptionsWithReason() {
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.classic103Mode = true;
        var epiphany = chiseled_enchanting_table.config.ConfigCatalog.byKey("epiphany_base_chance");
        assertTrue(epiphany.isPresent());
        var availability = epiphany.get().evaluate(draft, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN);
        assertFalse(availability.editable());
        assertEquals("config.chiseled_enchanting_table.reason.classic_1_0_3", availability.inactiveReasonKey());
        var standard = ModRuntimeConfig.DEFAULT.toDraft();
        assertTrue(epiphany.get().evaluate(standard, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN).editable());
    }

    @Test
    void classicFrameZeroesResonanceSubCapsAndEpiphanyPerShelfChances() {
        // Co-resonance discount and Epiphany must be DISABLED (all components 0),
        // not merely hidden: their JSON sub-caps/per-chances previously survived
        // applyRuntimeConfig and leaked into floating texts.
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.classic103Mode = true;
        var classic = draft.build();
        var original = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
        try {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                classic, ModRuntimeConfig.PackPolicy.NONE, () -> {
                }
            );
            var frame = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame();
            var lr = frame.libraryResonanceConfig();
            assertEquals(0, lr.maxWoodenDiscountPercent());
            assertEquals(0, lr.maxTotalXpDiscountPercent());
            assertEquals(0, lr.maxGlobalMasteryDiscountPercent());
            assertEquals(0, lr.maxBaseXpDiscountPercent());
            assertEquals(0, lr.maxSpecificEnchantBonusPercent());
            assertEquals(0.0, chiseled_enchanting_table.data.ChiseledEnchantingTableData.treasureEpiphanyBaseChance(), 1e-9);
            assertEquals(0.0, chiseled_enchanting_table.data.ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance(), 1e-9);
            assertEquals(0.0, chiseled_enchanting_table.data.ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance(), 1e-9);
            assertEquals(0.0, chiseled_enchanting_table.data.ChiseledEnchantingTableData.treasureEpiphanyMaxChance(), 1e-9);
        } finally {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {
                }
            );
        }
    }
}
