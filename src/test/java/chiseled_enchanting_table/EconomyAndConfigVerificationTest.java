package chiseled_enchanting_table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
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
                "incremental_step_credit_factor": 0.60
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

        // Runtime conservation must also hold under custom server config overrides (e.g. 44% discount, 0.60 credit)!
        DisenchantmentApplication.verifyRuntimeConservation(c, 1.0);
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
            16, 16, java.util.Map.of(), 8, 18, 0.5, 6, 8, 64
        );
        assertTrue(
            BookshelfProgression.Config.scanVolumeBlocks(clampedScanCfg.horizontalScanRadius(), clampedScanCfg.verticalScanRadius())
                <= BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS
        );
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
}
