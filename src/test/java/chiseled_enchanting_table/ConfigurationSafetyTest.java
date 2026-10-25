package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ConfigCatalog;
import chiseled_enchanting_table.config.ConfigLayoutSpec;
import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
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
            assertEquals(0, preset.countDeviations(draft), "Preset deviations must be 0 right after applyTo: " + preset);
            assertEquals(64, draft.enchantedBookStackSize,
                "All 4 presets (including 1.0.3 Classic) must set 64 Enchanted Book stack size: " + preset);
            assertTrue(draft.overrideDatapacks, "Presets must preserve overrideDatapacks governance toggle");
            var roundTripped = ModRuntimeConfig.fromJsonString(draft.build().toJsonString()).toDraft();
            assertTrue(preset.matches(roundTripped), "Preset must survive JSON round-trip: " + preset);
        }

        ModRuntimeConfig.Preset.CLASSIC_1_0_3.applyTo(draft);
        var classic = draft.build();
        assertTrue(classic.isAllClassicAtoms());
        assertTrue(classic.classic103Mode());
        assertEquals(ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT, classic.countClassic103Atoms());
        assertEquals(MasterTomeWear.ConsumptionMode.KEEP, classic.tomeConsumptionMode());
        assertFalse(classic.tomeRegenEnabled());
        assertFalse(classic.disenchantEnabled());
        assertFalse(classic.epiphanyEnabled());
        assertFalse(classic.libraryDiscountEnabled());
        assertEquals(2, classic.horizontalScanRadius());
        assertEquals(2, classic.verticalScanRadius());
        assertFalse(classic.requireBreathingRoom());
        assertFalse(classic.requireLineOfSight());

        var original = ChiseledEnchantingTableData.runtimeFrame();
        try {
            ChiseledEnchantingTableData.applyRuntimeConfig(
                classic, ModRuntimeConfig.PackPolicy.NONE, () -> {}
            );
            var classicFrame = ChiseledEnchantingTableData.runtimeFrame();
            assertEquals(2, classicFrame.itemTransformations().size(),
                "1.0.3 Classic transformation_set must expose only the 2 original item transformations");
        } finally {
            ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {}
            );
        }
    }

    @Test
    void legacyClassic103JsonAndPackPolicyMigrateCleanlyToGranularAtoms() {
        // Legacy config JSON with monolithic "classic_1_0_3_mode": true must migrate losslessly to all 19 classic atoms.
        ModRuntimeConfig migrated = ModRuntimeConfig.fromJsonString("""
            {
              "classic_1_0_3_mode": true,
              "enchanted_book_stack_size": 16
            }
            """);
        assertTrue(migrated.isAllClassicAtoms(), "Legacy classic_1_0_3_mode=true must populate all classic rule atoms");
        assertEquals(16, migrated.enchantedBookStackSize(), "Explicitly saved stack size must survive legacy migration");
        assertEquals(ModRuntimeConfig.CostTableDataset.CLASSIC_1_0_3, migrated.costTableDataset());
        assertEquals(ModRuntimeConfig.MaterialSelectionMode.PLAYER_SALTED_SINGLE, migrated.materialSelectionMode());
        assertEquals(ModRuntimeConfig.TransformationSetMode.CLASSIC_TWO, migrated.transformationSetMode());

        // Legacy PackPolicy locking "classic_1_0_3_mode" must lock the granular rule atoms.
        ModRuntimeConfig.PackPolicy legacyPolicy = ModRuntimeConfig.PackPolicy.fromJsonString("""
            {
              "lock_in_game_config": false,
              "enforce_datapack_priority": false,
              "locked_keys": ["classic_1_0_3_mode"]
            }
            """);
        for (String atomKey : ModRuntimeConfig.GRANULAR_RULE_ATOM_KEYS) {
            assertTrue(legacyPolicy.isKeyLocked(atomKey), "Legacy classic_1_0_3_mode lock must cover atom: " + atomKey);
        }
    }

    @Test
    void classicSaltedCountIsStablePerPlayerAndVariesAcrossPlayers() {
        var option = new ChiseledEnchantingTableData.ItemCostOption(
            Identifier.withDefaultNamespace("emerald"),
            0, 0, 2, 5, 0, 0, 1.0, List.of());
        var protection = Identifier.withDefaultNamespace("protection");
        Set<Integer> seen = new HashSet<>();
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
    void granularRuleAtomsOperateOrthogonallyWithoutMonolithicCrossLocking() {
        var original = ChiseledEnchantingTableData.runtimeFrame();
        try {
            // Case A: Enable ONLY cost_table = CLASSIC_1_0_3 + research_rounding = FLOOR_DOWN on top of 1.1.0 defaults!
            var hybridDraft = ModRuntimeConfig.DEFAULT.toDraft();
            hybridDraft.costTableDataset = ModRuntimeConfig.CostTableDataset.CLASSIC_1_0_3;
            hybridDraft.researchRoundingMode = ModRuntimeConfig.ResearchRoundingMode.FLOOR_DOWN;
            var hybridCfg = hybridDraft.build();
            assertFalse(hybridCfg.isAllClassicAtoms(), "Single-atom hybrid config must not force full classic mode");
            assertEquals(2, hybridCfg.countClassic103Atoms());

            ChiseledEnchantingTableData.applyRuntimeConfig(hybridCfg, ModRuntimeConfig.PackPolicy.NONE, () -> {});
            var mending1 = new EnchantmentWithLevel(Identifier.withDefaultNamespace("mending"), 1);
            var protection4 = new EnchantmentWithLevel(Identifier.withDefaultNamespace("protection"), 4);
            var sharpness3 = new EnchantmentWithLevel(Identifier.withDefaultNamespace("sharpness"), 3);
            var smite5 = new EnchantmentWithLevel(Identifier.withDefaultNamespace("smite"), 5);
            assertEquals(35, ChiseledEnchantingTableData.xpCost(mending1, 1), "1.0.3 Mending I XP cost applies independently");
            assertEquals(25, ChiseledEnchantingTableData.xpCost(protection4, 4), "1.0.3 Protection IV XP cost applies independently");
            assertEquals(15, ChiseledEnchantingTableData.xpCost(sharpness3, 5));
            assertEquals(7, ChiseledEnchantingTableData.bookResearchXpCost(sharpness3, 5), "FLOOR_DOWN rounds 15 * 0.5 -> 7");

            // Smite in 1.0.3 cost table carries specialty Bone / Rotten Flesh on base tiers
            // and multi-material Gem + Bone / Rotten Flesh bundles at max tier (Smite V)!
            var smiteId = Identifier.withDefaultNamespace("smite");
            var smite1Opts = ChiseledEnchantingTableData.rawItemCostOptions(smiteId, 1, 5);
            assertEquals(Identifier.withDefaultNamespace("bone"), smite1Opts.getFirst().itemId());
            var smite5Opts = ChiseledEnchantingTableData.rawItemCostOptions(smiteId, 5, 5);
            assertFalse(smite5Opts.isEmpty(), "1.0.3 cost table must expose Smite V options");
            assertFalse(smite5Opts.getFirst().extras().isEmpty(),
                "1.0.3 Smite V rule must preserve specialty additional material costs (Bones / Rotten Flesh)");
            assertNotNull(smite5);

            // Meanwhile, 1.1.0 features stay 100% active in this hybrid profile!
            assertEquals(6, ChiseledEnchantingTableData.runtimeFrame().itemTransformations().size(),
                "Full 6 transformations remain active when transformation_set == FULL_SIX");
            assertTrue(ChiseledEnchantingTableData.disenchantEnabled(), "Cleanse remains active when disenchant_enabled == true");
            assertTrue(ChiseledEnchantingTableData.treasureEpiphanyMaxChance() > 0.0, "Epiphany remains active when epiphany_enabled == true");
            assertTrue(ChiseledEnchantingTableData.libraryResonanceConfig().maxTotalXpDiscountPercent() > 0,
                "Resonance discount remains active when library_discount == true");

            // Case B: Toggle research_rounding back to ROUND_NEAREST -> 15 * 0.5 rounds to 8!
            hybridDraft.researchRoundingMode = ModRuntimeConfig.ResearchRoundingMode.ROUND_NEAREST;
            ChiseledEnchantingTableData.applyRuntimeConfig(hybridDraft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            assertEquals(8, ChiseledEnchantingTableData.bookResearchXpCost(sharpness3, 5), "ROUND_NEAREST rounds 15 * 0.5 -> 8");

            // Case C: Disable library_discount independently -> zeroes all resonance sub-caps while Epiphany stays live!
            var noDiscDraft = ModRuntimeConfig.DEFAULT.toDraft();
            noDiscDraft.libraryDiscountEnabled = false;
            ChiseledEnchantingTableData.applyRuntimeConfig(noDiscDraft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            var lr = ChiseledEnchantingTableData.runtimeFrame().libraryResonanceConfig();
            assertEquals(0, lr.maxWoodenDiscountPercent());
            assertEquals(0, lr.maxTotalXpDiscountPercent());
            assertEquals(0, lr.maxGlobalMasteryDiscountPercent());
            assertEquals(0, lr.maxBaseXpDiscountPercent());
            assertEquals(0, lr.maxSpecificEnchantBonusPercent());
            assertTrue(ChiseledEnchantingTableData.treasureEpiphanyMaxChance() > 0.0,
                "Disabling library_discount must NOT disable Treasure Epiphany");

            // Case D: Disable epiphany_enabled independently -> zeroes Epiphany chances while Resonance discount stays live!
            var noEpiphanyDraft = ModRuntimeConfig.DEFAULT.toDraft();
            noEpiphanyDraft.epiphanyEnabled = false;
            ChiseledEnchantingTableData.applyRuntimeConfig(noEpiphanyDraft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            assertEquals(0.0, ChiseledEnchantingTableData.treasureEpiphanyBaseChance(), 1e-9);
            assertEquals(0.0, ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance(), 1e-9);
            assertEquals(0.0, ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance(), 1e-9);
            assertEquals(0.0, ChiseledEnchantingTableData.treasureEpiphanyMaxChance(), 1e-9);
            assertTrue(ChiseledEnchantingTableData.libraryResonanceConfig().maxTotalXpDiscountPercent() > 0,
                "Disabling epiphany_enabled must NOT zero Library Resonance discount");
        } finally {
            ChiseledEnchantingTableData.applyRuntimeConfig(
                original.configuration(), original.policy(), () -> {}
            );
        }
    }

    @Test
    void classicBookUpgradeJsonCarries103ResearchMaterials() throws Exception {
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
    void parentRuleAtomsGateOnlyTheirNaturalSubordinateSliders() {
        var admin = ConfigCatalog.SessionMode.DEDICATED_ADMIN;
        var draft = ModRuntimeConfig.DEFAULT.toDraft();

        // 1. shelf_boost_model = CLASSIC_FLOOR_THIRD gates wooden curve sliders, NOT scan geometry!
        draft.shelfBoostModel = ModRuntimeConfig.ShelfBoostModel.CLASSIC_FLOOR_THIRD;
        assertFalse(ConfigCatalog.byKey("direct_gates_enabled").orElseThrow().evaluate(draft, admin).editable());
        assertEquals("config.chiseled_enchanting_table.reason.need_modern_shelf_curve",
            ConfigCatalog.byKey("direct_gates_enabled").orElseThrow().evaluate(draft, admin).inactiveReasonKey());
        assertTrue(ConfigCatalog.byKey("horizontal_scan_radius").orElseThrow().evaluate(draft, admin).editable(),
            "Scan geometry must remain editable regardless of shelf_boost_model");

        // 2. epiphany_enabled = false gates epiphany sliders, NOT library_discount!
        draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.epiphanyEnabled = false;
        assertFalse(ConfigCatalog.byKey("epiphany_base_chance").orElseThrow().evaluate(draft, admin).editable());
        assertEquals("config.chiseled_enchanting_table.reason.need_epiphany",
            ConfigCatalog.byKey("epiphany_base_chance").orElseThrow().evaluate(draft, admin).inactiveReasonKey());
        assertTrue(ConfigCatalog.byKey("max_total_xp_discount_percent").orElseThrow().evaluate(draft, admin).editable());

        // 3. tome_locks = false gates shelf-break & extraction lock guards, NOT tome-page early unlock!
        draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.tomeLocksEnabled = false;
        assertFalse(ConfigCatalog.byKey("locked_shelf_breakable").orElseThrow().evaluate(draft, admin).editable());
        assertFalse(ConfigCatalog.byKey("locked_book_extraction_blocked").orElseThrow().evaluate(draft, admin).editable());
        assertTrue(ConfigCatalog.byKey("early_unlock_xp_levels").orElseThrow().evaluate(draft, admin).editable());

        // 4. tome_page_enabled = false gates tome-page early unlock costs!
        draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.tomePageEnabled = false;
        assertFalse(ConfigCatalog.byKey("early_unlock_xp_levels").orElseThrow().evaluate(draft, admin).editable());
        assertFalse(ConfigCatalog.byKey("early_unlock_grit_count").orElseThrow().evaluate(draft, admin).editable());

        // 5. transformation_set = CLASSIC_TWO gates require_prior_discovery, while batch size remains editable!
        draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.transformationSetMode = ModRuntimeConfig.TransformationSetMode.CLASSIC_TWO;
        assertFalse(ConfigCatalog.byKey("require_prior_discovery").orElseThrow().evaluate(draft, admin).editable());
        assertTrue(ConfigCatalog.byKey("transformation_max_batch_size").orElseThrow().evaluate(draft, admin).editable());

        // 6. tier1_bookshelves stays active if ritual_queue uses it, and gates off once both drained_book_result=PLAIN_BOOK and ritual_queue=false!
        draft = ModRuntimeConfig.DEFAULT.toDraft();
        draft.drainedBookResult = ModRuntimeConfig.DrainedBookResult.PLAIN_BOOK;
        assertTrue(ConfigCatalog.byKey("tier1_bookshelves").orElseThrow().evaluate(draft, admin).editable(),
            "tier1_bookshelves remains active while ritual_queue still uses shelf tier thresholds");
        draft.ritualQueueEnabled = false;
        assertFalse(ConfigCatalog.byKey("tier1_bookshelves").orElseThrow().evaluate(draft, admin).editable());
        assertEquals("config.chiseled_enchanting_table.reason.need_depleted_tome",
            ConfigCatalog.byKey("tier1_bookshelves").orElseThrow().evaluate(draft, admin).inactiveReasonKey());
    }

    @Test
    void ruleTraceCatalogCoversAllTwentyEntriesAndTogglesEveryAtomCleanly() {
        assertEquals(20, ConfigCatalog.RULE_TRACE_ENTRIES.size(), "Expected 20 Rule Trace entries (19 bound + 1 derived)");
        var draft = ModRuntimeConfig.DEFAULT.toDraft();
        assertEquals(0, draft.build().countClassic103Atoms());

        // Pass 1: every single entry (all 19 bound atoms + derived classic_presentation) starts modern on 1.1.0 default
        for (var trace : ConfigCatalog.RULE_TRACE_ENTRIES) {
            assertFalse(trace.isUsingClassic103(draft), "Default 1.1.0 draft must start modern for " + trace.traceId());
            assertTrue(trace.isUsingModern110(draft), "Default 1.1.0 draft must match modern for " + trace.traceId());
        }

        // Pass 2: toggle all 19 bound entries to 1.0.3 and verify derived classic_presentation flips automatically
        int toggledCount = 0;
        for (var trace : ConfigCatalog.RULE_TRACE_ENTRIES) {
            if (!trace.derived()) {
                if ("tome_wear_system".equals(trace.traceId())) {
                    draft.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
                    draft.tomeRegenEnabled = false;
                } else {
                    trace.applyClassic103(draft);
                }
                toggledCount++;
                assertTrue(trace.isUsingClassic103(draft), "applyClassic103 must flip " + trace.traceId());
            }
        }
        assertEquals(19, toggledCount);
        assertEquals(ModRuntimeConfig.CLASSIC_RULE_ATOM_COUNT, draft.build().countClassic103Atoms());
        assertTrue(draft.build().isAllClassicAtoms(), "Flipping all 19 bound trace entries must derive classic_presentation");
        assertTrue(ConfigCatalog.RULE_TRACE_ENTRIES.getLast().isUsingClassic103(draft));
    }

    @Test
    void configModalLayoutAndLayeredAssetsGuaranteeZeroOverlapAcrossResolutions() throws Exception {
        // 1. Typography scale floors & safe border inset invariants
        assertTrue(ConfigLayoutSpec.MODAL_SAFE_INSET >= 14, "Modal safe inset must clear decorative 13px brass/rune border");
        assertTrue(ConfigLayoutSpec.PRIMARY_TEXT_MIN_SCALE >= 0.85f, "Primary text floor must be >= 0.85x");
        assertTrue(ConfigLayoutSpec.SECONDARY_TEXT_MIN_SCALE >= 0.75f, "Secondary text floor must be >= 0.75x");

        // 2. Verify zero box collision & zero border overflow across compact, standard, and widescreen viewports
        int[][] viewports = {
            {320, 240}, {427, 240}, {480, 270}, {540, 320},
            {640, 360}, {854, 480}, {960, 540}, {1280, 720}
        };
        int presetCount = ModRuntimeConfig.Preset.values().length;
        int railCount = ConfigCatalog.CategoryId.values().length + 1;
        for (int[] vp : viewports) {
            var layout = ConfigLayoutSpec.compute(vp[0], vp[1], presetCount, railCount);
            List<String> violations = ConfigLayoutSpec.verifyInvariants(layout);
            assertTrue(violations.isEmpty(),
                "Layout collision/overflow at " + vp[0] + "x" + vp[1] + ": " + violations);
        }

        // Roomy viewport (854x480) must achieve full 520x340 modal and fit >= 7 visible option rows simultaneously
        var roomy = ConfigLayoutSpec.compute(854, 480, presetCount, railCount);
        assertEquals(520, roomy.modal().w());
        assertEquals(340, roomy.modal().h());
        int visibleRows = (roomy.listViewport().h() + ConfigLayoutSpec.ROW_GAP)
            / (ConfigLayoutSpec.OPTION_ROW_H + ConfigLayoutSpec.ROW_GAP);
        assertTrue(visibleRows >= 7, "Roomy modal must fit at least 7 option rows simultaneously, got: " + visibleRows);

        // 3. Verify all 33 modular 2x HD pixel-art PNG layers exist with valid PNG signatures
        Path guiCfgDir = Path.of("src/client/resources/assets/chiseled_enchanting_table/gui/config");
        List<String> expectedAssets = List.of(
            "modal_frame.png", "frame_gem_pip.png", "backdrop_tile.png", "divider_ornament.png", "emblem_trace_large.png",
            "icon_domain_system.png", "icon_domain_unlock.png", "icon_domain_pricing.png", "icon_domain_tomes.png",
            "icon_domain_epiphany.png", "icon_domain_cleanse.png", "icon_domain_world.png", "icon_domain_trace.png",
            "rail_tab.png", "rail_tab_active.png", "section_plaque.png",
            "row_plaque.png", "row_plaque_hover.png", "row_plaque_atom.png", "row_plaque_disabled.png",
            "btn_plaque.png", "btn_plaque_hover.png", "btn_plaque_gold.png", "btn_plaque_disabled.png",
            "micro_btn.png", "micro_btn_hover.png", "micro_btn_disabled.png",
            "icon_reset.png", "icon_reset_disabled.png",
            "search_box.png", "search_box_focus.png",
            "toggle_on.png", "toggle_off.png", "toggle_locked.png",
            "slider_track.png", "slider_fill.png", "slider_knob.png", "slider_knob_hover.png"
        );
        for (String name : expectedAssets) {
            Path p = guiCfgDir.resolve(name);
            assertTrue(Files.isRegularFile(p), "Missing modular config UI layer: " + name);
            byte[] header = Files.readAllBytes(p);
            assertTrue(header.length > 24 && header[0] == (byte) 0x89 && header[1] == 0x50,
                "Invalid PNG header on " + name);
        }
    }

    @Test
    void booleanControlsScanRadiiDiscountCapsAndExpertPresetTakeEffectCleanly() {
        var original = ChiseledEnchantingTableData.runtimeFrame();
        try {
            // 1. Boolean options return actual Boolean from entry.get(draft) and toggle + track customizedKeys
            var boolDraft = ModRuntimeConfig.DEFAULT.toDraft();
            int booleanCount = 0;
            for (var entry : ConfigCatalog.ALL_OPTIONS) {
                if (entry.kind() == ConfigCatalog.ControlKind.BOOLEAN) {
                    booleanCount++;
                    Object initial = entry.get(boolDraft);
                    assertInstanceOf(Boolean.class, initial, "Boolean option must return Boolean: " + entry.key());
                    boolean start = Boolean.TRUE.equals(initial);
                    assertEquals(
                        start ? "@config.chiseled_enchanting_table.val.on" : "@config.chiseled_enchanting_table.val.off",
                        entry.rawOrKeyDisplay(boolDraft)
                    );
                    entry.toggleOrCycle(boolDraft, 1);
                    assertEquals(!start, Boolean.TRUE.equals(entry.get(boolDraft)), "Toggle must invert " + entry.key());
                    assertTrue(boolDraft.build().customizedKeys().contains(entry.key()));
                }
            }
            assertTrue(booleanCount >= 20);

            // 2. Full slider range of horizontal_scan_radius (1..8) and vertical_scan_radius (0..6) applies without silent clamping
            var scanDraft = ModRuntimeConfig.DEFAULT.toDraft();
            ConfigCatalog.byKey("horizontal_scan_radius").orElseThrow().setFromNormalizedSlider(scanDraft, 1.0);
            ConfigCatalog.byKey("vertical_scan_radius").orElseThrow().setFromNormalizedSlider(scanDraft, 1.0);
            ModRuntimeConfig maxScanCfg = scanDraft.build();
            assertEquals(8, maxScanCfg.horizontalScanRadius());
            assertEquals(6, maxScanCfg.verticalScanRadius());
            assertTrue(maxScanCfg.customizedKeys().contains("horizontal_scan_radius"));
            assertTrue(maxScanCfg.customizedKeys().contains("vertical_scan_radius"));
            ChiseledEnchantingTableData.applyRuntimeConfig(maxScanCfg, ModRuntimeConfig.PackPolicy.NONE, () -> {});
            assertEquals(8, ChiseledEnchantingTableData.shelfGatesConfig().horizontalScanRadius());
            assertEquals(6, ChiseledEnchantingTableData.shelfGatesConfig().verticalScanRadius());

            ConfigCatalog.byKey("horizontal_scan_radius").orElseThrow().setFromNormalizedSlider(scanDraft, 0.0);
            ConfigCatalog.byKey("vertical_scan_radius").orElseThrow().setFromNormalizedSlider(scanDraft, 0.0);
            ChiseledEnchantingTableData.applyRuntimeConfig(scanDraft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            assertEquals(1, ChiseledEnchantingTableData.shelfGatesConfig().horizontalScanRadius());
            assertEquals(0, ChiseledEnchantingTableData.shelfGatesConfig().verticalScanRadius());

            // 3. Wooden & total resonance XP discount caps take effect both ABOVE 35%/40% and BELOW 35%
            ChiseledEnchantingTableData.applyRuntimeConfig(ModRuntimeConfig.DEFAULT, ModRuntimeConfig.PackPolicy.NONE, () -> {});
            var defLr = ChiseledEnchantingTableData.libraryResonanceConfig();
            assertEquals(15, defLr.maxWoodenDiscountPercent());
            assertEquals(20, defLr.maxGlobalMasteryDiscountPercent());
            assertEquals(35, defLr.maxBaseXpDiscountPercent());
            assertEquals(15, defLr.maxSpecificEnchantBonusPercent());
            assertEquals(40, defLr.maxTotalXpDiscountPercent());

            var highDisc = ModRuntimeConfig.DEFAULT.toDraft();
            highDisc.libraryDiscountEnabled = true;
            highDisc.maxWoodenDiscountPercent = 55;
            highDisc.maxTotalXpDiscountPercent = 80;
            ChiseledEnchantingTableData.applyRuntimeConfig(highDisc.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            var highLr = ChiseledEnchantingTableData.libraryResonanceConfig();
            int maxWoodShelves = highLr.maxDiscountBookshelves();
            assertEquals(55, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.woodenBookshelfDiscountPercent(highLr, maxWoodShelves));
            assertEquals(55, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.baseXpDiscountPercent(highLr, maxWoodShelves, 0.0));
            assertEquals(80, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.effectiveXpDiscountPercent(highLr, maxWoodShelves, 30.0, 5, 5));

            var lowDisc = ModRuntimeConfig.DEFAULT.toDraft();
            lowDisc.libraryDiscountEnabled = true;
            lowDisc.maxWoodenDiscountPercent = 8;
            lowDisc.maxTotalXpDiscountPercent = 18;
            ChiseledEnchantingTableData.applyRuntimeConfig(lowDisc.build(), ModRuntimeConfig.PackPolicy.NONE, () -> {});
            var lowLr = ChiseledEnchantingTableData.libraryResonanceConfig();
            assertEquals(8, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.woodenBookshelfDiscountPercent(lowLr, maxWoodShelves));
            assertEquals(18, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.baseXpDiscountPercent(lowLr, maxWoodShelves, 30.0));
            assertEquals(18, chiseled_enchanting_table.mechanics.resonance.LibraryResonance.effectiveXpDiscountPercent(lowLr, maxWoodShelves, 30.0, 5, 5));

            // 4. Expert preset is strictly harder & matches cleanly
            ModRuntimeConfig std = ModRuntimeConfig.Preset.STANDARD.createConfig(false);
            ModRuntimeConfig exp = ModRuntimeConfig.Preset.EXPERT.createConfig(false);
            assertTrue(ModRuntimeConfig.Preset.EXPERT.matches(exp.toDraft()));
            assertFalse(exp.directTierUpgrade());
            assertFalse(exp.incrementalCredit());
            assertEquals(ModRuntimeConfig.MaterialSelectionMode.PLAYER_SALTED_SINGLE, exp.materialSelectionMode());
            assertEquals(chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode.ALL, exp.vanillaTableMode());
            assertFalse(exp.librarianTomeOffers());
            assertTrue(exp.maxTomeUses() < std.maxTomeUses());
            assertTrue(exp.maxWoodenDiscountPercent() < std.maxWoodenDiscountPercent());
            assertTrue(exp.maxTotalXpDiscountPercent() < std.maxTotalXpDiscountPercent());
            assertTrue(exp.minOpenAdjacentBlocks() > std.minOpenAdjacentBlocks());
            assertTrue(exp.xpRefundScale() < std.xpRefundScale());
            assertTrue(exp.bookResearchXpMultiplier() > std.bookResearchXpMultiplier());
        } finally {
            ChiseledEnchantingTableData.applyRuntimeConfig(original.configuration(), original.policy(), () -> {});
        }
    }
}
