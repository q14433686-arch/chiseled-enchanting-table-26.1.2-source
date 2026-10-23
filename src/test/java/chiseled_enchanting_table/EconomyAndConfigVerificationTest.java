package chiseled_enchanting_table;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ReopenTablePayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Authoritative compiled Java verification suite covering:
 * <ol>
 *   <li>Full nested schema consumption &amp; mutation test on {@code library_resonance.json} (R2-2 / CFG-01);</li>
 *   <li>Exhaustive {@code M = 1..255} full-wipe AND {@code 1 <= a < k <= M} step-upgrade conservation on
 *       the real compiled {@link DisenchantmentApplication#buildPotentialTable} &amp; {@link XpCostConversion}
 *       methods (~391,680 checks, R2-4 / TEST-01);</li>
 *   <li>Book Combine {@code <=} Book Research across all tiers and discounts (R2-1);</li>
 *   <li>Item Transformation mass-production thresholds &amp; Bottle o' Enchanting anti-arbitrage (XP-01 / R2-8);</li>
 *   <li>High-level modded shelf progression ({@code M = 1..255}) &amp; bounded scan volume (SHELF-01 / PERF-01).</li>
 * </ol>
 */
class EconomyAndConfigVerificationTest {

    private static JsonObject readResourceJson(String name) throws Exception {
        String path = "/data/chiseled_enchanting_table/chiseled_enchanting_table/" + name;
        try (var stream = EconomyAndConfigVerificationTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, "Missing bundled resource: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void shippedLibraryResonanceJsonMatchesDefaultAndEveryKeyMutatesConfig() throws Exception {
        JsonObject shipped = readResourceJson("library_resonance.json");
        LibraryResonance.Config parsedDefault = LibraryResonance.Config.fromJson(shipped);
        assertEquals(LibraryResonance.Config.DEFAULT, parsedDefault, "Shipped library_resonance.json must match Config.DEFAULT 1:1");

        JsonObject custom = JsonParser.parseString("""
            {
              "wooden_bookshelves": {
                "min_discount_bookshelves": 10,
                "max_discount_bookshelves": 32,
                "max_wooden_discount_percent": 12,
                "shelf_upgrade_material_multiplier": 0.85
              },
              "chiseled_mastery": {
                "mastery_unit_exponent": 1.40,
                "global_mastery_multiplier": 1.50,
                "max_global_mastery_discount_percent": 18,
                "max_base_xp_discount_percent": 30,
                "max_specific_enchant_bonus_percent": 14,
                "specific_enchant_exponent": 1.20,
                "max_total_xp_discount_percent": 44,
                "master_tome_upgrade_material_multiplier": 0.75,
                "incremental_step_credit_factor": 0.60,
                "tome_regen_enabled": false,
                "tome_regen_base_seconds": 120,
                "tome_regen_tier1_shelves": 34,
                "tome_regen_tier1_seconds": 25,
                "tome_regen_tier2_shelves": 44,
                "tome_regen_tier2_seconds": 8
              },
              "epiphany_affinity": {
                "epiphany_probability_global_weight": 0.50,
                "epiphany_probability_affinity_weight": 0.90,
                "blank_book_affinity_weight": 0.40,
                "epiphany_shelf_score_factor": 1.25,
                "epiphany_global_mastery_score_factor": 2.50,
                "epiphany_affinity_mastery_score_factor": 3.80,
                "epiphany_tier2_max_prob": 0.70,
                "epiphany_tier2_score_divisor": 75.0,
                "epiphany_tier2_mastery_bonus": 0.22,
                "epiphany_tier_plus_max_prob": 0.55,
                "epiphany_tier_plus_score_divisor": 105.0,
                "epiphany_tier_plus_mastery_bonus": 0.28
              }
            }
            """).getAsJsonObject();
        LibraryResonance.Config c = LibraryResonance.Config.fromJson(custom);
        assertEquals(10, c.minDiscountBookshelves());
        assertEquals(32, c.maxDiscountBookshelves());
        assertEquals(12, c.maxWoodenDiscountPercent());
        assertEquals(0.85, c.shelfUpgradeMaterialMultiplier(), 1e-9);
        assertEquals(1.40, c.masteryUnitExponent(), 1e-9);
        assertEquals(1.50, c.globalMasteryMultiplier(), 1e-9);
        assertEquals(18, c.maxGlobalMasteryDiscountPercent());
        assertEquals(30, c.maxBaseXpDiscountPercent());
        assertEquals(14, c.maxSpecificEnchantBonusPercent());
        assertEquals(1.20, c.specificEnchantExponent(), 1e-9);
        assertEquals(44, c.maxTotalXpDiscountPercent());
        assertEquals(0.75, c.masterTomeUpgradeMaterialMultiplier(), 1e-9);
        assertEquals(0.60, c.incrementalStepCreditFactor(), 1e-9);
        assertFalse(c.tomeRegenEnabled());
        assertEquals(120, c.tomeRegenBaseSeconds());
        assertEquals(34, c.tomeRegenTier1Shelves());
        assertEquals(25, c.tomeRegenTier1Seconds());
        assertEquals(44, c.tomeRegenTier2Shelves());
        assertEquals(8, c.tomeRegenTier2Seconds());
        assertEquals(0.50, c.epiphanyProbabilityGlobalWeight(), 1e-9);
        assertEquals(0.90, c.epiphanyProbabilityAffinityWeight(), 1e-9);
        assertEquals(0.40, c.blankBookAffinityWeight(), 1e-9);
        assertEquals(1.25, c.epiphanyShelfScoreFactor(), 1e-9);
        assertEquals(2.50, c.epiphanyGlobalMasteryScoreFactor(), 1e-9);
        assertEquals(3.80, c.epiphanyAffinityMasteryScoreFactor(), 1e-9);
        assertEquals(0.70, c.epiphanyTier2MaxProb(), 1e-9);
        assertEquals(75.0, c.epiphanyTier2ScoreDivisor(), 1e-9);
        assertEquals(0.22, c.epiphanyTier2MasteryBonus(), 1e-9);
        assertEquals(0.55, c.epiphanyTierPlusMaxProb(), 1e-9);
        assertEquals(105.0, c.epiphanyTierPlusScoreDivisor(), 1e-9);
        assertEquals(0.28, c.epiphanyTierPlusMasteryBonus(), 1e-9);

        // Runtime conservation must also hold strictly under custom server config overrides (e.g. 44% discount, 0.60 credit)!
        DisenchantmentApplication.assertRuntimeConservationStrict(c, 1.0);
    }

    @Test
    void runtimeConservationStrictAndReloadSafeHoldAcrossEdgeInputs() {
        DisenchantmentApplication.assertRuntimeConservationStrict(LibraryResonance.Config.DEFAULT, 1.0);
        DisenchantmentApplication.assertRuntimeConservationStrict(LibraryResonance.Config.DEFAULT, 50_000.0);
        assertDoesNotThrow(
            () -> DisenchantmentApplication.verifyRuntimeConservation(LibraryResonance.Config.DEFAULT, Double.NaN),
            "Reload-time conservation verifier must never throw even on malformed NaN scale (N3)"
        );
    }

    @Test
    void bundledDataSchemasAndReopenPayloadRoundTrip() throws Exception {
        JsonObject epiphany = readResourceJson("treasure_epiphany.json");
        assertEquals(
            32,
            epiphany.get("min_regular_bookshelves").getAsInt(),
            "Bundled treasure_epiphany.json must specify 32 wooden bookshelves (G2 / N1)"
        );
        assertEquals(
            4,
            epiphany.get("max_slots_cap").getAsInt(),
            "Bundled treasure_epiphany.json must specify max_slots_cap = 4 (G3 / N1)"
        );
        JsonObject upgrades = readResourceJson("book_upgrades.json");
        assertTrue(upgrades.getAsJsonObject("combine_route").get("enabled").getAsBoolean());
        assertTrue(upgrades.getAsJsonObject("research_route").get("enabled").getAsBoolean());
        JsonObject costs = readResourceJson("enchantment_costs.json");
        assertTrue(costs.has("xp") && costs.has("item_costs"));

        BlockPos pos = new BlockPos(-1024, 72, 4096);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        ReopenTablePayload.CODEC.encode(buf, new ReopenTablePayload(pos));
        ReopenTablePayload decoded = ReopenTablePayload.CODEC.decode(buf);
        assertEquals(pos, decoded.pos(), "ReopenTablePayload must round-trip exact BlockPos (G1)");
    }

    @Test
    void exhaustiveConservationAcrossAllMaxLevelsOneTo255() {
        LibraryResonance.Config cfg = LibraryResonance.Config.DEFAULT;
        int maxDiscount = cfg.maxTotalXpDiscountPercent();
        int[] anvilCosts = {1, 2, 4, 8, 25, 100};
        long checkedInequalities = 0;

        for (int m = 1; m <= 255; m++) {
            for (int anvil : anvilCosts) {
                for (boolean treasure : new boolean[] {false, true}) {
                    int[] v = DisenchantmentApplication.buildPotentialTable(cfg, m, anvil, treasure, 1.0);
                    int stepSum = 0;
                    for (int k = 1; k <= m; k++) {
                        assertTrue(v[k] >= v[k - 1], "Monotonicity failed at M=" + m + ", k=" + k);
                        stepSum += (v[k] - v[k - 1]);

                        int fullMasterPts = XpCostConversion.minPointsRequired(
                            XpCostConversion.forMasterTomeEnchant(cfg, k, m, 0, maxDiscount)
                        );
                        int fullArcanePts = XpCostConversion.minPointsRequired(
                            XpCostConversion.forFullArcaneEnchantOrUpgrade(cfg, 0, k, m, 1, maxDiscount)
                        );
                        assertTrue(
                            v[k] <= fullMasterPts - DisenchantmentApplication.CONSERVATION_SAFETY_MARGIN_POINTS,
                            "Full Master Tome conservation failed at M=" + m + ", k=" + k
                        );
                        assertTrue(v[k] < fullArcanePts, "Full Arcane conservation failed at M=" + m + ", k=" + k);
                        checkedInequalities++;

                        for (int a = 1; a < k; a++) {
                            int stepMasterPts = XpCostConversion.minPointsRequired(
                                XpCostConversion.forIncrementalMasterTomeEnchant(cfg, a, k, m, 0, maxDiscount)
                            );
                            int stepArcanePts = XpCostConversion.minPointsRequired(
                                XpCostConversion.forFullArcaneEnchantOrUpgrade(cfg, a, k, m, 1, maxDiscount)
                            );
                            int refundDelta = v[k] - v[a];
                            assertTrue(
                                refundDelta <= stepMasterPts - DisenchantmentApplication.CONSERVATION_SAFETY_MARGIN_POINTS,
                                "Step Master Tome conservation failed at M=" + m + ", " + a + "->" + k
                            );
                            assertTrue(
                                refundDelta < stepArcanePts,
                                "Step Arcane conservation failed at M=" + m + ", " + a + "->" + k
                            );
                            checkedInequalities++;
                        }
                    }
                    assertEquals(v[m], stepSum, "Exact telescoping sum failed at M=" + m);
                }
            }
        }
        assertTrue(checkedInequalities > 380_000L, "Expected >380k checked inequalities, got " + checkedInequalities);
    }

    @Test
    void bookCombineNeverExceedsBookResearchCost() {
        LibraryResonance.Config cfg = LibraryResonance.Config.DEFAULT;
        for (int m = 2; m <= 10; m++) {
            for (int target = 2; target <= m; target++) {
                int targetRaw = Math.max(2, Math.round(2 + 8.0f * (target - 1) / Math.max(1, m - 1)));
                int prevRaw = Math.max(2, Math.round(2 + 8.0f * (target - 2) / Math.max(1, m - 1)));
                int deltaRaw = Math.max(1, targetRaw - prevRaw);
                int combineRaw = Math.max(1, (int) Math.round(targetRaw * 0.60));
                int researchRaw = Math.max(1, (int) Math.round(targetRaw * 0.85));

                for (int discount = 0; discount <= 40; discount += 10) {
                    var combineReq = XpCostConversion.forFullArcaneEnchantOrUpgrade(
                        cfg, target - 1, target, m, Math.min(combineRaw, deltaRaw), discount
                    );
                    var researchReq = XpCostConversion.forFullArcaneEnchantOrUpgrade(
                        cfg, target - 1, target, m, Math.min(researchRaw, deltaRaw), discount
                    );
                    assertTrue(
                        combineReq.deductedLevels() <= researchReq.deductedLevels(),
                        "Combine deducted levels exceeded Research at M=" + m + ", target=" + target
                    );
                    assertTrue(
                        XpCostConversion.minPointsRequired(combineReq) <= XpCostConversion.minPointsRequired(researchReq),
                        "Combine XP points exceeded Research at M=" + m + ", target=" + target
                    );
                }
            }
        }
    }

    @Test
    void itemTransformationsMatchMassProductionTableAndPreserveConservation() throws Exception {
        JsonObject root = readResourceJson("item_transformations.json");
        LibraryResonance.Config cfg = LibraryResonance.Config.DEFAULT;
        for (JsonElement elem : root.getAsJsonArray("values")) {
            JsonObject obj = elem.getAsJsonObject();
            String id = obj.get("id").getAsString();
            int xpCost = obj.get("xp_cost").getAsInt();
            int minReq = obj.get("min_required_level").getAsInt();
            int ded = obj.get("deducted_levels").getAsInt();
            var reqZeroDisc = XpCostConversion.forTransformation(cfg, Identifier.parse(id), xpCost, 0, minReq, ded);
            assertTrue(reqZeroDisc.vanillaTierMode(), id + " must use vanilla tier mass-production mode");
            assertEquals(xpCost, reqZeroDisc.requiredLevel(), id + " zero-discount threshold mismatch");
            assertEquals(ded, reqZeroDisc.deductedLevels(), id + " deducted level count mismatch");

            if ("chiseled_enchanting_table:experience_bottle".equals(id)) {
                var reqMaxDisc = XpCostConversion.forTransformation(cfg, Identifier.parse(id), xpCost, 40, minReq, ded);
                assertTrue(
                    XpCostConversion.minPointsRequired(reqMaxDisc) >= 11,
                    "Experience Bottle at max resonance discount must cost >= max bottle XP drop (11)"
                );
            }
        }
    }

    @Test
    void highMaxLevelShelfCurveDistributesSmoothlyAndCapsScanVolume() {
        for (int m = 1; m <= 39; m++) {
            for (int lvl = 2; lvl <= m; lvl++) {
                int prev = BookshelfProgression.requiredBookshelvesForDirectTier(lvl - 1, m, 38, 1.25);
                int cur = BookshelfProgression.requiredBookshelvesForDirectTier(lvl, m, 38, 1.25);
                assertTrue(cur > prev, "Expected strict separation for M=" + m + " at L=" + lvl);
            }
        }
        Set<Integer> uniqueGatesAt255 = new HashSet<>();
        int countAtShelfOne = 0;
        int prev = 0;
        for (int lvl = 1; lvl <= 255; lvl++) {
            int cur = BookshelfProgression.requiredBookshelvesForDirectTier(lvl, 255, 38, 1.25);
            assertTrue(cur >= prev, "Monotonicity violated at M=255, L=" + lvl);
            uniqueGatesAt255.add(cur);
            if (cur == 1) countAtShelfOne++;
            prev = cur;
        }
        assertEquals(39, uniqueGatesAt255.size(), "M=255 must utilize all 39 shelf states 0..38");
        assertTrue(countAtShelfOne <= 25, "M=255 shelf=1 bucket must not collapse (" + countAtShelfOne + ")");

        var clampedScanCfg = new BookshelfProgression.Config(
            true, 38, 1.25, BookshelfProgression.BlankMaxTierMode.HALF_CEIL,
            16, 16, java.util.Map.of(), 12, 32, 0.5, 6, 8, 64
        );
        assertTrue(
            BookshelfProgression.Config.scanVolumeBlocks(clampedScanCfg.horizontalScanRadius(), clampedScanCfg.verticalScanRadius())
                <= BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS
        );
    }

    @Test
    void d1ToD7DesignInvariantsHoldAcrossWearProvenanceBreathingDiscoveryAndConfig() throws Exception {
        // D1: Master Tome wear state machine (wear / keep / consume, step_down vs depleted_tome)
        var wearCfg = LibraryResonance.Config.DEFAULT;
        assertEquals(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR, wearCfg.tomeConsumptionMode());
        assertEquals(16, wearCfg.maxTomeUses());
        assertEquals(4, wearCfg.bookCopyWearCost());
        assertEquals(chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.DepleteBehavior.STEP_DOWN, wearCfg.tomeDepleteBehavior());

        // Simulate 16 gear uses on a Lv.5 Master Tome -> steps down cleanly at use #16 to fresh Lv.4 (0 wear)!
        int curLevel = 5;
        int curWear = 0;
        for (int u = 1; u <= 15; u++) {
            var res = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.computeWearStep(
                wearCfg.tomeConsumptionMode(),
                wearCfg.tomeDepleteBehavior(),
                wearCfg.maxTomeUses(),
                wearCfg.bookCopyWearCost(),
                curLevel,
                curWear,
                false
            );
            assertEquals(5, res.nextEnchantLevel(), "Should stay Lv.5 through use #" + u);
            assertEquals(u, res.nextWearUsed());
            curLevel = res.nextEnchantLevel();
            curWear = res.nextWearUsed();
        }
        var sixteenthUse = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.computeWearStep(
            wearCfg.tomeConsumptionMode(),
            wearCfg.tomeDepleteBehavior(),
            wearCfg.maxTomeUses(),
            wearCfg.bookCopyWearCost(),
            curLevel,
            curWear,
            false
        );
        assertEquals(4, sixteenthUse.nextEnchantLevel(), "16th gear use must step Lv.5 Master Tome down to Lv.4");
        assertEquals(0, sixteenthUse.nextWearUsed(), "Stepped-down Master Tome starts fresh at 0 wear");
        assertTrue(sixteenthUse.steppedDown());

        // Copying onto a blank book costs 4 charges -> 4th book copy on Lv.1 tome exhausts into Depleted Tome
        var fourthCopy = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.computeWearStep(
            wearCfg.tomeConsumptionMode(),
            wearCfg.tomeDepleteBehavior(),
            wearCfg.maxTomeUses(),
            wearCfg.bookCopyWearCost(),
            1,
            12,
            true
        );
        assertEquals(0, fourthCopy.nextEnchantLevel(), "4th book copy on Lv.1 tome must exhaust into Depleted Tome (level 0)");
        assertTrue(fourthCopy.depletedCompletely());

        // D2 + D6: VanillaTableMode & unified 12 / 32 / 38 shelf milestones
        JsonObject sgJson = readResourceJson("shelf_gates.json");
        BookshelfProgression.Config sg = BookshelfProgression.Config.fromJson(sgJson);
        assertEquals(BookshelfProgression.VanillaTableMode.BOOKS, sg.vanillaTableMode());
        assertEquals(12, sg.tier1Bookshelves());
        assertEquals(32, sg.tier2Bookshelves());
        assertEquals(38, sg.maxDirectBookshelves());
        assertTrue(sg.requireBreathingRoom());
        assertEquals(6, sg.minOpenAdjacentBlocks());

        // D4: Breathing-room pure formula (ceilingBlocked = 0, buried = 0, half-choked 3/6 = 19, corner 7/17 = full 38)
        assertEquals(0, BookshelfProgression.effectiveShelvesAfterBreathingRoom(38, true, 17, sg));
        assertEquals(0, BookshelfProgression.effectiveShelvesAfterBreathingRoom(38, false, 0, sg));
        assertEquals(19, BookshelfProgression.effectiveShelvesAfterBreathingRoom(38, false, 3, sg));
        assertEquals(38, BookshelfProgression.effectiveShelvesAfterBreathingRoom(38, false, 7, sg));

        // D3: Provenance BitSet & anti-arbitrage across external loot, hybrid upgrade, book transfer & combine
        JsonObject disJson = readResourceJson("disenchanting.json");
        assertEquals(1.0, disJson.get("xp_refund_scale").getAsDouble(), 1e-9);
        assertEquals(0.25, disJson.get("external_xp_refund_scale").getAsDouble(), 1e-9);

        java.util.BitSet slicesNone = new java.util.BitSet(); // purely external Sharpness IV -> 25% refund
        java.util.BitSet slicesAll5 = chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance
            .markSliceRange(null, 0, 5);
        java.util.BitSet slicesUpgradedTo5 = chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance
            .markSliceRange(slicesNone, 4, 5); // upgraded 4 -> 5 on our table: slices 0..3 external, slice 4 table!
        assertEquals(false, slicesUpgradedTo5.get(3));
        assertEquals(true, slicesUpgradedTo5.get(4));

        int fullTablePot5 = DisenchantmentApplication.provenanceSlicePotential(
            wearCfg, 5, 5, 1, false, 1.0, 0.25, slicesAll5
        );
        int pureExternalPot4 = DisenchantmentApplication.provenanceSlicePotential(
            wearCfg, 5, 4, 1, false, 1.0, 0.25, slicesNone
        );
        int hybridWipePot5 = DisenchantmentApplication.provenanceSlicePotential(
            wearCfg, 5, 5, 1, false, 1.0, 0.25, slicesUpgradedTo5
        );
        int hybridRemainPot4 = DisenchantmentApplication.provenanceSlicePotential(
            wearCfg, 5, 4, 1, false, 1.0, 0.25,
            chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.truncateToLevel(slicesUpgradedTo5, 4)
        );
        int step5DownTo4 = hybridWipePot5 - hybridRemainPot4;
        int stepCost4To5 = XpCostConversion.minPointsRequired(
            XpCostConversion.forIncrementalMasterTomeEnchant(wearCfg, 4, 5, 5, 0, 40)
        );
        assertTrue(pureExternalPot4 < fullTablePot5 / 2, "External loot must receive steeply reduced refund");
        assertTrue(
            hybridWipePot5 - pureExternalPot4 < stepCost4To5,
            "Upgrading external Lv.4 -> Lv.5 then wiping ALL must NEVER yield net positive XP profit!"
        );
        assertTrue(step5DownTo4 < stepCost4To5, "Stepping hybrid Lv.5 -> Lv.4 must strictly conserve XP");

        // Direct Book Transfer + Combine: external Lv.4 + external Lv.4 -> Lv.5 marks ONLY slice 4 as table-paid
        java.util.BitSet mergedExternal = chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance
            .mergeTransferredSlices(slicesNone, slicesNone, 4);
        assertTrue(mergedExternal.isEmpty(), "0-XP transfer of an external book must never fabricate table provenance");
        java.util.BitSet combinedSlices = chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance
            .markSliceRange(mergedExternal, 4, 5);
        assertEquals(1, combinedSlices.cardinality());
        assertTrue(combinedSlices.get(4));

        // D5: Exploration-gated transformations vs always-open utility transformations
        JsonObject transRoot = readResourceJson("item_transformations.json");
        Set<String> gatedIds = new HashSet<>();
        Set<String> openIds = new HashSet<>();
        for (JsonElement el : transRoot.getAsJsonArray("values")) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            if (o.has("require_prior_discovery") && o.get("require_prior_discovery").getAsBoolean()) {
                gatedIds.add(id);
            } else {
                openIds.add(id);
            }
        }
        assertEquals(
            Set.of(
                "chiseled_enchanting_table:enchanted_golden_apple",
                "chiseled_enchanting_table:totem_of_undying",
                "chiseled_enchanting_table:trident",
                "chiseled_enchanting_table:echo_shard"
            ),
            gatedIds
        );
        assertEquals(
            Set.of(
                "chiseled_enchanting_table:blank_overwrite",
                "chiseled_enchanting_table:experience_bottle"
            ),
            openIds
        );

        // D7: Structure & chest datapack configurability schemas
        JsonObject poolJson = readResourceJson("bookshelf_book_pool.json");
        assertTrue(poolJson.get("enabled").getAsBoolean());
        JsonObject lootJson = readResourceJson("loot_injections.json");
        assertEquals("keep_both", lootJson.get("vanilla_chest_books").getAsString());
    }

    private static JsonObject readClasspathJson(String path) throws Exception {
        try (var stream = EconomyAndConfigVerificationTest.class.getResourceAsStream("/" + path)) {
            assertTrue(stream != null, "Missing bundled classpath resource: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Test
    void grimoireDualShapelessRecipesAndBookshelfTagAreValid() throws Exception {
        JsonObject shelfRecipe = readClasspathJson(
            "data/chiseled_enchanting_table/recipe/arcane_grimoire_from_shelf.json"
        );
        assertEquals("minecraft:crafting_shapeless", shelfRecipe.get("type").getAsString());
        assertEquals(
            "chiseled_enchanting_table:arcane_grimoire",
            shelfRecipe.getAsJsonObject("result").get("id").getAsString()
        );
        Set<String> shelfIngs = new HashSet<>();
        for (JsonElement el : shelfRecipe.getAsJsonArray("ingredients")) {
            shelfIngs.add(el.getAsString());
        }
        assertEquals(Set.of("minecraft:writable_book", "minecraft:chiseled_bookshelf"), shelfIngs);

        JsonObject tomeRecipe = readClasspathJson(
            "data/chiseled_enchanting_table/recipe/arcane_grimoire_from_tome.json"
        );
        assertEquals("minecraft:crafting_shapeless", tomeRecipe.get("type").getAsString());
        assertEquals(
            "chiseled_enchanting_table:arcane_grimoire",
            tomeRecipe.getAsJsonObject("result").get("id").getAsString()
        );
        Set<String> tomeIngs = new HashSet<>();
        for (JsonElement el : tomeRecipe.getAsJsonArray("ingredients")) {
            tomeIngs.add(el.getAsString());
        }
        assertEquals(Set.of("chiseled_enchanting_table:depleted_tome", "minecraft:ink_sac"), tomeIngs);

        JsonObject bookshelfTag = readClasspathJson("data/minecraft/tags/item/bookshelf_books.json");
        assertEquals(false, bookshelfTag.get("replace").getAsBoolean(), "bookshelf_books.json must not replace vanilla entries");
    }

    @Test
    void everyCodeAndDataTranslationKeyExistsAcrossAllElevenLocales() throws Exception {
        java.nio.file.Path repoRoot = java.nio.file.Paths.get("").toAbsolutePath();
        java.nio.file.Path langDir = repoRoot.resolve("src/client/resources/assets/chiseled_enchanting_table/lang");
        var dynPattern = java.util.regex.Pattern.compile(
            "\"(gui\\.chiseled_enchanting_table\\.[A-Za-z0-9_.]+)\"\\s*\\+\\s*[A-Za-z0-9_.()]+\\s*\\+\\s*\"(\\.[A-Za-z0-9_.]+)\""
        );
        var litPattern = java.util.regex.Pattern.compile(
            "\"([a-z0-9_]+\\.chiseled_enchanting_table(?:\\.[A-Za-z0-9_]+)+)\""
        );
        Set<String> referencedKeys = new HashSet<>();
        try (var walk = java.nio.file.Files.walk(repoRoot.resolve("src"))) {
            for (var p : walk.toList()) {
                String posix = p.toString().replace('\\', '/');
                if (!java.nio.file.Files.isRegularFile(p) || posix.contains("/lang/")) continue;
                if (!posix.endsWith(".java") && !posix.endsWith(".json")) continue;
                String txt = java.nio.file.Files.readString(p, StandardCharsets.UTF_8);
                var dm = dynPattern.matcher(txt);
                while (dm.find()) {
                    for (int ch = 1; ch <= 8; ch++) {
                        referencedKeys.add(dm.group(1) + ch + dm.group(2));
                    }
                }
                var lm = litPattern.matcher(txt);
                while (lm.find()) {
                    String k = lm.group(1);
                    if (!k.endsWith(".ch")) referencedKeys.add(k);
                }
            }
        }
        assertTrue(referencedKeys.size() >= 295, "Expected >=295 referenced mod keys, got " + referencedKeys.size());

        List<java.nio.file.Path> langFiles;
        try (var s = java.nio.file.Files.list(langDir)) {
            langFiles = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(11, langFiles.size(), "Expected all 11 locale files under lang/");
        for (var lf : langFiles) {
            JsonObject map = JsonParser.parseString(
                java.nio.file.Files.readString(lf, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            for (String key : referencedKeys) {
                assertTrue(
                    map.has(key),
                    lf.getFileName() + " is missing referenced translation key: " + key
                );
                String val = map.get(key).getAsString();
                assertTrue(!val.isBlank() && !val.equals(key), lf.getFileName() + " has raw/empty value for " + key);
            }

            // Verify single-line navigation pills & titles fit comfortably without extreme horizontal squash
            for (String pillKey : List.of(
                "gui.chiseled_enchanting_table.grimoire.nav.home",
                "gui.chiseled_enchanting_table.grimoire.nav.back_table",
                "gui.chiseled_enchanting_table.grimoire.nav.close",
                "gui.chiseled_enchanting_table.grimoire.nav.prev",
                "gui.chiseled_enchanting_table.grimoire.nav.next",
                "gui.chiseled_enchanting_table.grimoire.nav.return_home"
            )) {
                String txt = map.get(pillKey).getAsString();
                int estPx = estimateMinecraftFontPx(txt);
                assertTrue(
                    estPx * 0.62f <= 82.0f,
                    lf.getFileName() + " pill label too wide for button bounds: " + pillKey + " = '" + txt + "' (" + estPx + "px)"
                );
            }

            // Verify every parameterized Grimoire key formats cleanly with live config values
            assertEquals(
                String.format(map.get("gui.chiseled_enchanting_table.grimoire.ch4.res_b").getAsString(), 15, 20, 35, 15)
                    .contains("%s"),
                false
            );
            assertEquals(
                String.format(map.get("gui.chiseled_enchanting_table.grimoire.diag.ch4_res").getAsString(), 35, 40)
                    .contains("%s"),
                false
            );
        }

        // Verify all 7 crafting recipes displayed in Chapter 1 exist on classpath with valid schemas
        for (String recipeId : List.of(
            "arcane_grimoire_from_shelf",
            "arcane_grimoire_from_tome",
            "chiseled_enchanting_table",
            "chiseled_enchanting_table_upgrade",
            "crying_obsidian",
            "cleansing_grit",
            "arcane_catalyst"
        )) {
            JsonObject rec = readClasspathJson("data/chiseled_enchanting_table/recipe/" + recipeId + ".json");
            assertTrue(rec.has("type") && rec.has("result"), "Invalid recipe JSON schema for " + recipeId);
        }
    }

    @Test
    void disenchantingAndWordingAccuracyZeroDriftGate() throws Exception {
        JsonObject disenchant = readClasspathJson(
            "data/chiseled_enchanting_table/chiseled_enchanting_table/disenchanting.json"
        );
        JsonObject strip = disenchant.getAsJsonObject("targeted_strip");
        assertEquals(3, strip.get("xp_cost_min").getAsInt());
        assertEquals(10, strip.get("xp_cost_max").getAsInt());
        String stripJson = strip.toString();
        assertTrue(stripJson.contains("minecraft:glow_ink_sac"), "targeted_strip must accept glow_ink_sac");
        assertTrue(!stripJson.contains("\"minecraft:ink_sac\""), "targeted_strip must not accept regular ink_sac");

        JsonObject curse = disenchant.getAsJsonObject("curse_purge");
        assertEquals(12, curse.get("xp_cost").getAsInt());
        String curseJson = curse.toString();
        assertTrue(curseJson.contains("minecraft:ghast_tear"));
        assertTrue(curseJson.contains("minecraft:golden_apple"));
        assertTrue(curseJson.contains("chiseled_enchanting_table:arcane_catalyst"));
        assertTrue(!curseJson.contains("minecraft:echo_shard"), "curse_purge does not use echo_shard");
        assertTrue(!curseJson.contains("minecraft:lapis_lazuli"), "curse_purge does not use lapis_lazuli");

        java.nio.file.Path repoRoot = java.nio.file.Paths.get("").toAbsolutePath();
        java.nio.file.Path langDir = repoRoot.resolve("src/client/resources/assets/chiseled_enchanting_table/lang");
        JsonObject enMap = JsonParser.parseString(
            java.nio.file.Files.readString(langDir.resolve("en_us.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();

        List<java.nio.file.Path> langFiles;
        try (var s = java.nio.file.Files.list(langDir)) {
            langFiles = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (var lf : langFiles) {
            String fileName = lf.getFileName().toString();
            JsonObject map = JsonParser.parseString(
                java.nio.file.Files.readString(lf, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            assertEquals(enMap.keySet(), map.keySet(), "Key set mismatch in " + fileName);

            for (String key : enMap.keySet()) {
                String enVal = enMap.get(key).getAsString();
                String locVal = map.get(key).getAsString();
                int expArgs = countSubstring(enVal, "%s");
                int gotArgs = countSubstring(locVal, "%s");
                assertEquals(expArgs, gotArgs, "Placeholder %s count mismatch in " + fileName + ":" + key);
                if (expArgs > 0) {
                    String stripped = locVal.replace("%s", "").replace("%%", "");
                    assertTrue(!stripped.contains("%"), "Unescaped % in parameterized key " + fileName + ":" + key);
                } else {
                    assertTrue(!locVal.contains("%%"), "Double %% in non-parameterized key " + fileName + ":" + key);
                }
                if (key.contains(".grimoire.")) {
                    String low = locVal.toLowerCase();
                    assertTrue(!low.contains("ceil(") && !low.contains("min("),
                        "Raw math code left in prose " + fileName + ":" + key + " -> " + locVal);
                }
            }

            String c4 = (map.get("gui.chiseled_enchanting_table.grimoire.ch8.s4").getAsString() + " "
                + map.get("gui.chiseled_enchanting_table.grimoire.diag.ch8_c4_b").getAsString()).toLowerCase();
            for (String bad : List.of("echo", "回响", "迴響", "残響", "메아리", "écho", "эхо")) {
                assertTrue(!c4.contains(bad), "Stale Echo Shard curse claim in " + fileName + ": " + c4);
            }
        }

        JsonObject zhCn = JsonParser.parseString(
            java.nio.file.Files.readString(langDir.resolve("zh_cn.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        assertTrue(zhCn.get("gui.chiseled_enchanting_table.cleanse.panel_rule_strip").getAsString().contains("发光墨囊"));
        assertTrue(zhCn.get("gui.chiseled_enchanting_table.grimoire.rec.cleansing_grit").getAsString().startsWith("退魔砂"));
        assertTrue(zhCn.get("gui.chiseled_enchanting_table.grimoire.ch8.s3").getAsString().contains("Lv.3–10"));

        String wiki = java.nio.file.Files.readString(repoRoot.resolve("docs/WIKI.md"), StandardCharsets.UTF_8);
        assertTrue(wiki.contains("Lv.3~10"), "WIKI.md must state exact Lv.3~10 strip range");
        assertTrue(wiki.contains("1.4×"), "WIKI.md must state exact 1.4x book research multiplier");
        assertTrue(!wiki.contains("0.85×"), "WIKI.md must not retain stale 0.85x research multiplier");
        assertTrue(!wiki.contains("Lv.4~10"), "WIKI.md must not retain stale Lv.4~10 strip range");
        assertTrue(!wiki.contains("涤魔碎砂"), "WIKI.md must not contain removed grit-shard refund claim");
        assertTrue(wiki.contains("无书架门槛自动批量合成"), "WIKI.md must document zero-threshold universal batching");
    }

    @Test
    void universalTransformationBatchingHasZeroThresholdAndZeroArbitrage() throws Exception {
        assertEquals(64, BookshelfProgression.DEFAULT_BASE_BATCH_SIZE, "Batch crafting must have zero bookshelf threshold (64 at 0 shelves)");
        assertEquals(64, BookshelfProgression.DEFAULT_TIER3_BATCH_SIZE);

        JsonObject shelfGatesJson = readResourceJson("shelf_gates.json");
        BookshelfProgression.Config parsedCfg = BookshelfProgression.Config.fromJson(shelfGatesJson);
        assertEquals(64, parsedCfg.baseBatchSize(), "shelf_gates.json base_batch_size must default to 64 without any shelf gate");
        assertEquals(64, parsedCfg.tier3BatchSize());

        // Verify exact sequential equivalence & maxTransformationBatchForLevel bijection across ALL built-in transformations
        Identifier bottleId = Identifier.fromNamespaceAndPath("chiseled_enchanting_table", "experience_bottle");
        Identifier blankId = Identifier.fromNamespaceAndPath("chiseled_enchanting_table", "blank_overwrite");
        Identifier appleId = Identifier.fromNamespaceAndPath("chiseled_enchanting_table", "enchanted_golden_apple");
        Identifier totemId = Identifier.fromNamespaceAndPath("chiseled_enchanting_table", "totem_of_undying");

        // 1. Experience bottle strict anti-arbitrage for ALL batch sizes B = 1..64 across ALL discounts 0..40%
        for (int discount = 0; discount <= 40; discount++) {
            var unitBottle = XpCostConversion.forTransformation(
                LibraryResonance.Config.DEFAULT,
                bottleId,
                5,
                discount,
                5,
                1
            );
            for (int batch = 1; batch <= 64; batch++) {
                var batchXp = XpCostConversion.scaleTransformationXpForBatch(unitBottle, batch);
                int minPointsSpent = XpCostConversion.minPointsRequired(batchXp);
                assertTrue(
                    minPointsSpent >= 11 * batch,
                    "Batch " + batch + " at " + discount + "% discount spent " + minPointsSpent + " < " + (11 * batch)
                );
            }
        }

        // 2. Sequential equivalence on high-tier transformations (e.g., Totem of Undying: req=30, deduct=3):
        // At player level 45, sequential clicks craft (45 - 30) / 3 + 1 = 6 Totems (45 -> 42 -> 39 -> 36 -> 33 -> 30 -> 27).
        var unitTotem = XpCostConversion.forTransformation(
            LibraryResonance.Config.DEFAULT,
            totemId,
            30,
            0,
            30,
            3
        );
        assertEquals(6, XpCostConversion.maxTransformationBatchForLevel(unitTotem, 45));
        var batch6Totem = XpCostConversion.scaleTransformationXpForBatch(unitTotem, 6);
        assertEquals(45, batch6Totem.requiredLevel(), "6 Totems require exact sequential level 45 (NOT naive 180!)");
        assertEquals(18, batch6Totem.deductedLevels(), "6 Totems deduct exact 6 * 3 = 18 levels (45 -> 27)");
        assertEquals(5, XpCostConversion.maxTransformationBatchForLevel(unitTotem, 44));

        // 3. Exact bijection across all player levels 0..250 for all recipes
        for (var unit : List.of(
            XpCostConversion.forTransformation(LibraryResonance.Config.DEFAULT, blankId, 1, 0, 1, 1),
            XpCostConversion.forTransformation(LibraryResonance.Config.DEFAULT, appleId, 20, 40, 20, 2),
            unitTotem
        )) {
            for (int playerLv = 0; playerLv <= 250; playerLv++) {
                int maxB = XpCostConversion.maxTransformationBatchForLevel(unit, playerLv);
                if (maxB >= 1 && maxB < 10_000) {
                    var atMax = XpCostConversion.scaleTransformationXpForBatch(unit, maxB);
                    var overMax = XpCostConversion.scaleTransformationXpForBatch(unit, maxB + 1);
                    assertTrue(playerLv >= atMax.requiredLevel());
                    assertTrue(playerLv < overMax.requiredLevel());
                }
            }
        }
    }

    @Test
    void configCatalogCoversAllSixCategoriesAndRoundTripsJsonLosslessly() {
        var all = chiseled_enchanting_table.config.ConfigCatalog.ALL_OPTIONS;
        assertEquals(55, all.size(), "Expected 55 curated in-game configurable entries across 6 categories");
        for (var cat : chiseled_enchanting_table.config.ConfigCatalog.CategoryId.values()) {
            assertFalse(
                chiseled_enchanting_table.config.ConfigCatalog.optionsInCategory(cat).isEmpty(),
                "Category must not be empty: " + cat
            );
        }

        var working = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT.toDraft();
        assertEquals(0L, chiseled_enchanting_table.config.ConfigCatalog.countModified(working));

        for (var entry : all) {
            assertFalse(entry.isModifiedFromDefault(working), "Default must match on entry: " + entry.key());
        }

        for (var entry : all) {
            switch (entry.kind()) {
                case BOOLEAN, ENUM_CYCLE -> {
                    Object orig = entry.get(working);
                    entry.toggleOrCycle(working, 1);
                    assertNotEquals(orig, entry.get(working), "Toggle/enum should change value for " + entry.key());
                }
                case INT_SLIDER -> {
                    int orig = ((Number) entry.get(working)).intValue();
                    int candidate = orig == (int) entry.min() ? (int) entry.max() : (int) entry.min();
                    entry.set(working, candidate);
                }
                case DOUBLE_SLIDER -> {
                    double orig = ((Number) entry.get(working)).doubleValue();
                    double mid = Math.round(((entry.min() + entry.max()) * 0.5) / entry.step()) * entry.step();
                    if (Math.abs(mid - orig) < 1e-6) {
                        mid = entry.min();
                    }
                    entry.set(working, mid);
                }
            }
        }
        assertTrue(chiseled_enchanting_table.config.ConfigCatalog.countModified(working) > 30);

        var built = working.build();
        String json = built.toJsonString();
        var reloadedDraft = chiseled_enchanting_table.config.ModRuntimeConfig.fromJsonString(json).toDraft();
        for (var entry : all) {
            assertEquals(entry.get(working), entry.get(reloadedDraft), "JSON round-trip mismatch on " + entry.key());
        }

        chiseled_enchanting_table.config.ConfigCatalog.resetAll(reloadedDraft);
        assertEquals(0L, chiseled_enchanting_table.config.ConfigCatalog.countModified(reloadedDraft));
    }

    @Test
    void configCatalogApplicabilityAndCrossFieldCoherenceEnforceAllDependencies() {
        var cfg = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT.toDraft();
        var admin = chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_ADMIN;
        var readonly = chiseled_enchanting_table.config.ConfigCatalog.SessionMode.DEDICATED_READONLY;

        // Dedicated non-OP player sees every option read-only with explicit server reason
        for (var opt : chiseled_enchanting_table.config.ConfigCatalog.ALL_OPTIONS) {
            var avail = opt.evaluate(cfg, readonly);
            assertFalse(avail.editable(), "Read-only session must lock " + opt.key());
            assertEquals("config.chiseled_enchanting_table.reason.server_readonly", avail.inactiveReasonKey());
        }

        // All 49 options are active and editable under default config for admins/singleplayer
        for (var opt : chiseled_enchanting_table.config.ConfigCatalog.ALL_OPTIONS) {
            var avail = opt.evaluate(cfg, admin);
            assertTrue(avail.parentApplicable(), "Default config should leave " + opt.key() + " applicable");
            assertTrue(avail.editable(), "Admin session should leave " + opt.key() + " editable");
        }

        // 1. Direct wooden shelf gates chain
        cfg.directGatesEnabled = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("blank_max_tier_mode").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("max_direct_bookshelves").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // 2. Breathing room chain
        cfg.requireBreathingRoom = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("min_open_adjacent_blocks").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // 3. Master Tome wear chain (KEEP / CONSUME disables wear charges, copy wear & depletion behavior)
        cfg.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("max_tome_uses").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("book_copy_wear_cost").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("tome_deplete_behavior").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // 4 & 5. Book combine / research multipliers
        cfg.bookCombineEnabled = false;
        cfg.bookResearchEnabled = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("book_combine_xp_multiplier").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("book_research_xp_multiplier").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // 6. Two-level Disenchant -> Allow Curse Purge -> Curse Purge XP Cost chain
        cfg.allowCursePurge = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("curse_purge_xp_cost").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertTrue(chiseled_enchanting_table.config.ConfigCatalog.byKey("xp_refund_scale").orElseThrow().evaluate(cfg, admin).parentApplicable());
        cfg.allowCursePurge = true;
        cfg.disenchantEnabled = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("xp_refund_scale").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("allow_curse_purge").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("curse_purge_xp_cost").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // 7. Structure Chiseled Bookshelf pool chain
        cfg.bookshelfPoolEnabled = false;
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("bookshelf_min_books").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("bookshelf_max_books").orElseThrow().evaluate(cfg, admin).parentApplicable());
        assertFalse(chiseled_enchanting_table.config.ConfigCatalog.byKey("bookshelf_enchanted_prob").orElseThrow().evaluate(cfg, admin).parentApplicable());

        // Cross-field coherence checks
        cfg.tier1Bookshelves = 40;
        cfg.normalizeAfterEdit("tier1_bookshelves");
        assertTrue(cfg.tier1Bookshelves <= cfg.tier2Bookshelves);
        assertTrue(cfg.tier2Bookshelves <= cfg.maxDirectBookshelves);

        cfg.externalXpRefundScale = 0.95;
        cfg.xpRefundScale = 0.40;
        cfg.normalizeAfterEdit("xp_refund_scale");
        assertTrue(cfg.externalXpRefundScale <= cfg.xpRefundScale + 1e-9);

        cfg.bookshelfMinBooks = 5;
        cfg.bookshelfMaxBooks = 2;
        cfg.normalizeAfterEdit("bookshelf_max_books");
        assertEquals(2, cfg.bookshelfMinBooks);
        assertEquals(2, cfg.bookshelfMaxBooks);
    }

    @Test
    void allConfigTranslationKeysAndModMenuExclusionAreCompleteAcrossAllElevenLocales() throws Exception {
        Path langDir = Path.of("src/client/resources/assets/chiseled_enchanting_table/lang");
        List<Path> langFiles;
        try (var stream = Files.list(langDir)) {
            langFiles = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(11, langFiles.size(), "Expected all 11 locale files");

        Set<String> requiredKeys = new HashSet<>(List.of(
            "config.chiseled_enchanting_table.title",
            "config.chiseled_enchanting_table.open_tip.title",
            "config.chiseled_enchanting_table.open_tip.sub",
            "config.chiseled_enchanting_table.grimoire_btn",
            "config.chiseled_enchanting_table.filter.all",
            "config.chiseled_enchanting_table.filter.modified",
            "config.chiseled_enchanting_table.filter.modified_desc",
            "config.chiseled_enchanting_table.empty_modified",
            "config.chiseled_enchanting_table.btn.reset_page",
            "config.chiseled_enchanting_table.btn.reset_all",
            "config.chiseled_enchanting_table.btn.close",
            "config.chiseled_enchanting_table.btn.save",
            "config.chiseled_enchanting_table.val.on",
            "config.chiseled_enchanting_table.val.off",
            "config.chiseled_enchanting_table.tip.current_default",
            "config.chiseled_enchanting_table.msg.saved",
            "config.chiseled_enchanting_table.msg.unsaved",
            "config.chiseled_enchanting_table.msg.synced",
            "config.chiseled_enchanting_table.msg.restart_hint",
            "config.chiseled_enchanting_table.msg.reloaded",
            "config.chiseled_enchanting_table.msg.reset_all",
            "config.chiseled_enchanting_table.reason.server_readonly",
            "config.chiseled_enchanting_table.reason.need_direct_gates",
            "config.chiseled_enchanting_table.reason.need_breathing_room",
            "config.chiseled_enchanting_table.reason.need_wear_mode",
            "config.chiseled_enchanting_table.reason.need_tome_regen",
            "config.chiseled_enchanting_table.reason.need_book_combine",
            "config.chiseled_enchanting_table.reason.need_book_research",
            "config.chiseled_enchanting_table.reason.need_disenchant",
            "config.chiseled_enchanting_table.reason.need_curse_purge",
            "config.chiseled_enchanting_table.reason.need_bookshelf_pool"
        ));
        for (var mode : chiseled_enchanting_table.config.ConfigCatalog.SessionMode.values()) {
            requiredKeys.add(mode.bannerKey());
        }
        for (var scope : chiseled_enchanting_table.config.ConfigCatalog.EffectScope.values()) {
            requiredKeys.add(scope.badgeKey());
            requiredKeys.add(scope.badgeKey() + ".detail");
        }
        for (var cat : chiseled_enchanting_table.config.ConfigCatalog.CategoryId.values()) {
            requiredKeys.add(cat.titleKey());
            requiredKeys.add(cat.subtitleKey());
        }
        var probe = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT.toDraft();
        for (var opt : chiseled_enchanting_table.config.ConfigCatalog.ALL_OPTIONS) {
            requiredKeys.add(opt.nameKey());
            requiredKeys.add(opt.descKey());
            if (opt.kind() == chiseled_enchanting_table.config.ConfigCatalog.ControlKind.ENUM_CYCLE) {
                for (int i = 0; i < 8; i++) {
                    String formatted = opt.rawOrKeyDisplay(probe);
                    requiredKeys.add(formatted.startsWith("@") ? formatted.substring(1) : formatted);
                    opt.toggleOrCycle(probe, 1);
                }
            }
        }

        for (Path lf : langFiles) {
            JsonObject obj = JsonParser.parseString(Files.readString(lf)).getAsJsonObject();
            for (String key : requiredKeys) {
                assertTrue(obj.has(key), lf.getFileName() + " missing key: " + key);
                assertFalse(obj.get(key).getAsString().isBlank(), lf.getFileName() + " blank key: " + key);
            }
        }

        byte[] btnBytes = Files.readAllBytes(Path.of("src/client/resources/assets/chiseled_enchanting_table/gui/btn_config.png"));
        assertEquals((byte) 0x89, btnBytes[0]);
        assertEquals(64, ((btnBytes[16] & 0xFF) << 24) | ((btnBytes[17] & 0xFF) << 16) | ((btnBytes[18] & 0xFF) << 8) | (btnBytes[19] & 0xFF));
        assertEquals(64, ((btnBytes[20] & 0xFF) << 24) | ((btnBytes[21] & 0xFF) << 16) | ((btnBytes[22] & 0xFF) << 8) | (btnBytes[23] & 0xFF));

        String buildGradle = Files.readString(Path.of("build.gradle"));
        assertTrue(buildGradle.contains("exclude 'com/terraformersmc/**'"));

        String cfgScreenSrc = Files.readString(Path.of("src/client/java/chiseled_enchanting_table/gui/config/ChiseledEnchantingConfigScreen.java"));
        assertTrue(cfgScreenSrc.contains("public boolean isPauseScreen() {\n        return true;\n    }"));
        String tableScreenSrc = Files.readString(Path.of("src/client/java/chiseled_enchanting_table/gui/ChiseledEnchantingTableScreen.java"));
        assertTrue(tableScreenSrc.contains("return this.configOverlay != null && this.configOverlay.isPauseScreen();"));
    }

    @Test
    void infinityMasterTomeImmunityAnd38And42TwoTierRegenerationContract() throws Exception {
        // 1. Infinity-blessed Master Tomes suffer strictly zero wear across all modes, levels, and targets.
        for (var mode : MasterTomeWear.ConsumptionMode.values()) {
            for (var dep : MasterTomeWear.DepleteBehavior.values()) {
                int level = 5;
                int wear = 0;
                for (int i = 0; i < 200; i++) {
                    var step = MasterTomeWear.computeWearStep(mode, dep, 16, 4, level, wear, (i % 2 == 0), true);
                    assertEquals(5, step.nextEnchantLevel());
                    assertEquals(0, step.nextWearUsed());
                    assertFalse(step.steppedDown());
                    assertFalse(step.depletedCompletely());
                }
            }
        }

        // 2. Two-tier bookshelf passive regeneration curve (0 -> 90s, Tier 1 @ 38 -> 30s, Tier 2 @ 42 -> 10s).
        LibraryResonance.Config def = LibraryResonance.Config.DEFAULT;
        assertTrue(def.tomeRegenEnabled());
        assertEquals(90, def.tomeRegenBaseSeconds());
        assertEquals(38, def.tomeRegenTier1Shelves());
        assertEquals(30, def.tomeRegenTier1Seconds());
        assertEquals(42, def.tomeRegenTier2Shelves());
        assertEquals(10, def.tomeRegenTier2Seconds());

        assertEquals(0, MasterTomeWear.regenTierIndex(def, 0));
        assertEquals(0, MasterTomeWear.regenTierIndex(def, 37));
        assertEquals(1, MasterTomeWear.regenTierIndex(def, 38));
        assertEquals(1, MasterTomeWear.regenTierIndex(def, 41));
        assertEquals(2, MasterTomeWear.regenTierIndex(def, 42));
        assertEquals(2, MasterTomeWear.regenTierIndex(def, 64));

        assertEquals(90.0, MasterTomeWear.computeRegenSeconds(def, 0), 1e-6);
        assertEquals(1800, MasterTomeWear.computeRegenIntervalTicks(def, 0));
        assertEquals(30.0, MasterTomeWear.computeRegenSeconds(def, 38), 1e-6);
        assertEquals(600, MasterTomeWear.computeRegenIntervalTicks(def, 38));
        assertEquals(10.0, MasterTomeWear.computeRegenSeconds(def, 42), 1e-6);
        assertEquals(200, MasterTomeWear.computeRegenIntervalTicks(def, 42));

        // Dramatic two-tier breakthroughs at 37 -> 38 and 41 -> 42 AND strict non-increasing monotonicity for S in [0, 100].
        assertTrue(
            MasterTomeWear.computeRegenSeconds(def, 37) - MasterTomeWear.computeRegenSeconds(def, 38) >= 15.0,
            "Reaching Tier 1 (38 bookshelves) must deliver a substantial regeneration breakthrough"
        );
        assertTrue(
            MasterTomeWear.computeRegenSeconds(def, 41) - MasterTomeWear.computeRegenSeconds(def, 42) >= 7.5,
            "Reaching Tier 2 (42 bookshelves) must deliver a dramatic Grand Sanctum regeneration breakthrough"
        );
        for (int s = 0; s < 100; s++) {
            int curTicks = MasterTomeWear.computeRegenIntervalTicks(def, s);
            int nextTicks = MasterTomeWear.computeRegenIntervalTicks(def, s + 1);
            assertTrue(
                nextTicks <= curTicks,
                "Regen interval ticks must never slow down when adding bookshelves at S=" + s
            );
            assertTrue(curTicks >= 100, "Asymptotic floor must stay >= 5.0s (100 ticks)");
        }

        // Disabled flag cleanly stops passive regeneration.
        JsonObject disabledJson = JsonParser.parseString("""
            { "chiseled_mastery": { "tome_regen_enabled": false } }
            """).getAsJsonObject();
        LibraryResonance.Config disabledCfg = LibraryResonance.Config.fromJson(disabledJson);
        assertFalse(disabledCfg.tomeRegenEnabled());
        assertEquals(Integer.MAX_VALUE, MasterTomeWear.computeRegenIntervalTicks(disabledCfg, 42));

        // 3. ModRuntimeConfig + ConfigCatalog bidirectional reconciliation & two-level applicability.
        var draft = new chiseled_enchanting_table.config.ModRuntimeConfig.Draft();
        draft.tomeRegenTier1Shelves = 45;
        draft.normalizeAfterEdit("tome_regen_tier1_shelves");
        assertEquals(45, draft.tomeRegenTier2Shelves, "Tier 2 shelves must push up when Tier 1 exceeds it");
        draft.tomeRegenTier2Shelves = 36;
        draft.normalizeAfterEdit("tome_regen_tier2_shelves");
        assertEquals(36, draft.tomeRegenTier1Shelves, "Tier 1 shelves must pull down when Tier 2 drops below it");

        draft.tomeRegenTier1Seconds = 8;
        draft.normalizeAfterEdit("tome_regen_tier1_seconds");
        assertEquals(8, draft.tomeRegenTier2Seconds, "Tier 2 interval must stay <= Tier 1 interval");

        var tier2Opt = chiseled_enchanting_table.config.ConfigCatalog.byKey("tome_regen_tier2_shelves").orElseThrow();
        var liveMode = chiseled_enchanting_table.config.ConfigCatalog.SessionMode.SINGLEPLAYER_LIVE;
        assertTrue(tier2Opt.evaluate(draft, liveMode).editable());
        draft.tomeRegenEnabled = false;
        var offAvail = tier2Opt.evaluate(draft, liveMode);
        assertFalse(offAvail.editable());
        assertEquals("config.chiseled_enchanting_table.reason.need_tome_regen", offAvail.inactiveReasonKey());
        draft.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
        var keepAvail = tier2Opt.evaluate(draft, liveMode);
        assertFalse(keepAvail.editable());
        assertEquals("config.chiseled_enchanting_table.reason.need_wear_mode", keepAvail.inactiveReasonKey());

        // 4. Anti-regression static guards on Creative/Survival wear parity & live shelf sync.
        String enchantAppSrc = Files.readString(
            Path.of("src/main/java/chiseled_enchanting_table/mechanics/enchanting/EnchantmentApplication.java")
        );
        assertFalse(
            enchantAppSrc.contains("masterTomeUsed && !handler.player.getAbilities().instabuild"),
            "Master Tome shelf wear must apply identically in Creative and Survival modes"
        );
        assertTrue(
            enchantAppSrc.contains("handler.refreshLiveShelfState();"),
            "EnchantmentApplication.finish must push live Master Tome charges immediately after enchanting"
        );
    }

    private static int countSubstring(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static int estimateMinecraftFontPx(String s) {
        int px = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                px += 4;
            } else if (c >= 0x2E80 && c <= 0xFFEF) {
                px += 9;
            } else {
                px += 6;
            }
        }
        return px;
    }
}
