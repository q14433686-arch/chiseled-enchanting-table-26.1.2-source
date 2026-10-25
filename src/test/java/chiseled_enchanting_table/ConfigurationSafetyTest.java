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
    void classic103FlagAloneKeepsValuesAndToggleAppliesProfileOnce() {
        // Round-3 profile semantics (R2-01): the classic flag alone rewrites
        // nothing — every value is a live, editable preference and the hard
        // mechanism gates key on the flag itself. In particular the 64-book
        // stack is only *suggested* by the profile, so a runtime save that
        // keeps stack size 1 under classic must build successfully.
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        assertFalse(draft.classic103Mode);
        draft.classic103Mode = true;
        draft.enchantedBookStackSize = 7;
        var flagOnly = draft.build();
        assertTrue(flagOnly.classic103Mode());
        assertEquals(7, flagOnly.enchantedBookStackSize(),
            "build() must not force the startup-only stack size (R2-01)");
        assertTrue(flagOnly.disenchantEnabled() == ModRuntimeConfig.DEFAULT.disenchantEnabled());
        assertEquals(ModRuntimeConfig.DEFAULT.epiphanyBaseChance(), flagOnly.epiphanyBaseChance(), 1e-9);

        // Flipping the switch on (screen toggle -> normalizeAfterEdit with the
        // classic key) applies the 1.0.3 profile exactly once.
        var toggled = ModRuntimeConfig.DEFAULT.toDraft();
        toggled.classic103Mode = true;
        toggled.normalizeAfterEdit("classic_1_0_3_mode");
        var classic = toggled.build();
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

        // ...and values edited afterwards (Classic merged page) stay as made.
        toggled.enchantedBookStackSize = 1;
        assertEquals(1, toggled.build().enchantedBookStackSize());
    }

    @Test
    void classicSaltedCountIsStablePerPlayerAndVariesAcrossPlayers() {
        // R2-03: 1.0.3 ranged counts resolve by stable per-player hash, not by
        // the standard engine's deterministic midpoint (which would give 4).
        var option = new chiseled_enchanting_table.data.ChiseledEnchantingTableData.ItemCostOption(
            net.minecraft.resources.Identifier.withDefaultNamespace("emerald"),
            0, 0, 2, 5, 0, 0, 1.0, java.util.List.of());
        var protection = net.minecraft.resources.Identifier.withDefaultNamespace("protection");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 64; i++) {
            String salt = "00000000-0000-0000-0000-%012d".formatted(i);
            int count = option.saltedCount(4, 4, salt, protection);
            assertTrue(count >= 2 && count <= 5, "salted count must stay in the 1.0.3 range: " + count);
            assertEquals(count, option.saltedCount(4, 4, salt, protection),
                "same player must always see the same count");
            seen.add(count);
        }
        assertTrue(seen.size() > 1, "different players must see different salted counts: " + seen);
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
            // 1.0.3: Sharpness III direct cost = 5 + 0.5 * (25 - 5) = 15 XP;
            // research charges floor(15 x 0.5) = 7 XP (1.0.3 floors, R2-04;
            // the themed Flint material itself is asserted from the bundled
            // JSON in classicBookUpgradeJsonCarries103ResearchMaterials).
            assertEquals(15, chiseled_enchanting_table.data.ChiseledEnchantingTableData.xpCost(sharpness3, 5));
            assertEquals(7, chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookResearchXpCost(sharpness3, 5));
            var transformations = chiseled_enchanting_table.data.ChiseledEnchantingTableData.runtimeFrame().itemTransformations();
            assertEquals(2, transformations.size(), "Classic keeps exactly the 2 transformations of 1.0.3");
            var apple = transformations.get(
                chiseled_enchanting_table.ChiseledEnchantingTable.identifier("enchanted_golden_apple"));
            assertNotNull(apple);
            assertEquals(2, apple.additionalCosts().getFirst().minCount());
            assertEquals(5, apple.additionalCosts().getFirst().maxCount());
        } finally {
            chiseled_enchanting_table.data.ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {
                }
            );
        }
    }

    @Test
    void classicBookUpgradeJsonCarries103ResearchMaterials() throws Exception {
        // The bundled classic table is a byte copy of tag 26.1.2_1.0.3; guard the
        // two properties the frame wiring relies on (x0.5 materials, themed costs).
        try (var stream = getClass().getResourceAsStream(
            "/data/chiseled_enchanting_table/chiseled_enchanting_table/book_upgrades_classic_1_0_3.json")) {
            assertNotNull(stream);
            var root = com.google.gson.JsonParser.parseReader(
                new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var research = root.getAsJsonObject("research_route");
            assertEquals(0.5, research.get("material_cost_multiplier_of_direct_enchant").getAsDouble(), 1e-9);
            assertEquals("minecraft:flint", research.getAsJsonObject("enchantment_overrides")
                .getAsJsonObject("minecraft:sharpness").getAsJsonObject("material_cost")
                .get("item").getAsString());
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
        // Epiphany belongs to the rule-owned group: the classic frame zeroes
        // its chances outright, even on the Classic merged page.
        assertEquals(
            "config.chiseled_enchanting_table.reason.classic_1_0_3_overridden",
            availability.inactiveReasonKey()
        );
        // A live classic preference stays in the ordinary classic-locked
        // group; the config screen unlocks only this group on its Classic page.
        var scanRadius = chiseled_enchanting_table.config.ConfigCatalog.byKey("horizontal_scan_radius");
        assertTrue(scanRadius.isPresent());
        var scanAvailability = scanRadius.get().evaluate(draft, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN);
        assertFalse(scanAvailability.editable());
        assertEquals(
            "config.chiseled_enchanting_table.reason.classic_1_0_3",
            scanAvailability.inactiveReasonKey()
        );
        // Round 5 (#13): tier/early-unlock/discovery keys have no consumer in
        // the classic branch either, so they share the rule-owned reason.
        for (String key : java.util.List.of(
            "tier1_bookshelves", "tier2_bookshelves", "tier3_qualifying_enchantments",
            "early_unlock_xp_levels", "early_unlock_grit_count", "require_prior_discovery")) {
            var entry = chiseled_enchanting_table.config.ConfigCatalog.byKey(key);
            assertTrue(entry.isPresent(), key);
            var a = entry.get().evaluate(draft, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN);
            assertFalse(a.editable(), key);
            assertEquals(
                "config.chiseled_enchanting_table.reason.classic_1_0_3_overridden",
                a.inactiveReasonKey(), key);
            assertTrue(chiseled_enchanting_table.config.ConfigCatalog.CLASSIC_OVERRIDDEN_KEYS.contains(key), key);
        }
        // Classification invariant: rule-owned keys are a subset of the
        // classic-locked set, and every classic-page row outside that group
        // is an explicitly reviewed live preference with a consumer under
        // classic (shelf-gates config, frame fields, mixins, worldgen).
        var locked = chiseled_enchanting_table.config.ConfigCatalog.CLASSIC_LOCKED_KEYS;
        var owned = chiseled_enchanting_table.config.ConfigCatalog.CLASSIC_OVERRIDDEN_KEYS;
        assertTrue(locked.containsAll(owned), "rule-owned keys must be classic-locked");
        java.util.Set<String> reviewedLivePreferences = java.util.Set.of(
            "enchanted_book_stack_size", "vanilla_table_mode", "anvil_flat_repair_cost",
            "anvil_block_same_book_merge", "restrict_villager_infinity_books",
            "grindstone_depleted_tome", "direct_gates_enabled", "blank_max_tier_mode",
            "horizontal_scan_radius", "vertical_scan_radius", "require_breathing_room",
            "require_line_of_sight", "tome_consumption_mode", "tome_regen_enabled",
            "locked_shelf_breakable", "locked_book_extraction_blocked",
            "book_combine_enabled", "book_research_enabled", "transformation_max_batch_size",
            "bookshelf_enchanted_prob", "max_tome_uses", "book_copy_wear_cost",
            "tome_deplete_behavior", "tome_regen_base_seconds", "tome_regen_tier1_shelves",
            "tome_regen_tier1_seconds", "tome_regen_tier2_shelves", "tome_regen_tier2_seconds",
            "vanilla_chest_books_mode", "bookshelf_pool_enabled");
        java.util.Set<String> editableUnderClassic = new java.util.TreeSet<>(locked);
        editableUnderClassic.removeAll(owned);
        assertEquals(reviewedLivePreferences, editableUnderClassic,
            "classic-page editable set changed: classify the new key as rule-owned or reviewed-live");
        var standard = ModRuntimeConfig.DEFAULT.toDraft();
        assertTrue(epiphany.get().evaluate(standard, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN).editable());
        assertTrue(scanRadius.get().evaluate(standard, chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN).editable());
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
