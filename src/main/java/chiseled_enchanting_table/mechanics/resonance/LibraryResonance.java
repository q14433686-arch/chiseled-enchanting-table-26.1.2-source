package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Relative-mastery math shared by Library XP Discounts, Master Tome specific bonuses,
 * and Treasure Epiphany proc rate / simultaneous slots / rolled tier.
 *
 * <p>Every parameter is parsed directly from the nested structure in
 * {@code data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json}
 * ({@code wooden_bookshelves}, {@code chiseled_mastery}, {@code epiphany_affinity}, with flat
 * root keys also accepted as fallback overrides) and synced to clients in
 * {@link chiseled_enchanting_table.menu.EnchantCostTablePayload}.</p>
 */
public final class LibraryResonance {
    public static final int MIN_DISCOUNT_BOOKSHELVES = 12;
    public static final int MAX_WOODEN_DISCOUNT_PERCENT = 15;
    public static final int MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT = 20;
    public static final int MAX_BASE_XP_DISCOUNT_PERCENT = 35;
    public static final int MAX_SPECIFIC_ENCHANT_BONUS_PERCENT = 15;
    public static final int MAX_TOTAL_XP_DISCOUNT_PERCENT = 40;

    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    public record Config(
        int minDiscountBookshelves,
        int maxDiscountBookshelves,
        int maxWoodenDiscountPercent,
        int maxGlobalMasteryDiscountPercent,
        int maxBaseXpDiscountPercent,
        int maxSpecificEnchantBonusPercent,
        double specificEnchantExponent,
        int maxTotalXpDiscountPercent,
        double masteryUnitExponent,
        double globalMasteryMultiplier,
        double masterTomeUpgradeMaterialMultiplier,
        double shelfUpgradeMaterialMultiplier,
        double epiphanyProbabilityGlobalWeight,
        double epiphanyProbabilityAffinityWeight,
        double blankBookAffinityWeight,
        double epiphanyShelfScoreFactor,
        double epiphanyGlobalMasteryScoreFactor,
        double epiphanyAffinityMasteryScoreFactor,
        double epiphanyTier2MaxProb,
        double epiphanyTier2ScoreDivisor,
        double epiphanyTier2MasteryBonus,
        double epiphanyTierPlusMaxProb,
        double epiphanyTierPlusScoreDivisor,
        double epiphanyTierPlusMasteryBonus,
        double incrementalStepCreditFactor,
        MasterTomeWear.ConsumptionMode tomeConsumptionMode,
        int maxTomeUses,
        int bookCopyWearCost,
        MasterTomeWear.DepleteBehavior tomeDepleteBehavior,
        boolean tomeRegenEnabled,
        int tomeRegenBaseSeconds,
        int tomeRegenTier1Shelves,
        int tomeRegenTier1Seconds,
        int tomeRegenTier2Shelves,
        int tomeRegenTier2Seconds
    ) {
        public static final Config DEFAULT = new Config(
            MIN_DISCOUNT_BOOKSHELVES,
            38,
            MAX_WOODEN_DISCOUNT_PERCENT,
            MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT,
            MAX_BASE_XP_DISCOUNT_PERCENT,
            MAX_SPECIFIC_ENCHANT_BONUS_PERCENT,
            1.0,
            MAX_TOTAL_XP_DISCOUNT_PERCENT,
            1.25,
            1.25,
            0.80,
            0.90,
            0.45,
            0.85,
            0.50,
            1.0,
            2.2,
            3.5,
            0.65,
            80.0,
            0.20,
            0.50,
            110.0,
            0.25,
            0.85,
            MasterTomeWear.ConsumptionMode.WEAR,
            MasterTomeWear.DEFAULT_MAX_TOME_USES,
            MasterTomeWear.DEFAULT_BOOK_COPY_WEAR_COST,
            MasterTomeWear.DepleteBehavior.STEP_DOWN,
            MasterTomeWear.DEFAULT_REGEN_ENABLED,
            MasterTomeWear.DEFAULT_REGEN_BASE_SECONDS,
            MasterTomeWear.DEFAULT_REGEN_TIER1_SHELVES,
            MasterTomeWear.DEFAULT_REGEN_TIER1_SECONDS,
            MasterTomeWear.DEFAULT_REGEN_TIER2_SHELVES,
            MasterTomeWear.DEFAULT_REGEN_TIER2_SECONDS
        );

        public Config {
            tomeConsumptionMode = tomeConsumptionMode != null ? tomeConsumptionMode : MasterTomeWear.ConsumptionMode.WEAR;
            maxTomeUses = clampInt(maxTomeUses, 1, 1024);
            bookCopyWearCost = clampInt(bookCopyWearCost, 1, 1024);
            tomeDepleteBehavior = tomeDepleteBehavior != null ? tomeDepleteBehavior : MasterTomeWear.DepleteBehavior.STEP_DOWN;
            tomeRegenBaseSeconds = clampInt(tomeRegenBaseSeconds, 1, 86_400);
            tomeRegenTier1Shelves = clampInt(tomeRegenTier1Shelves, 1, 256);
            tomeRegenTier1Seconds = clampInt(tomeRegenTier1Seconds, 1, tomeRegenBaseSeconds);
            tomeRegenTier2Shelves = clampInt(tomeRegenTier2Shelves, tomeRegenTier1Shelves, 256);
            tomeRegenTier2Seconds = clampInt(tomeRegenTier2Seconds, 1, tomeRegenTier1Seconds);
        }

        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor,
            MasterTomeWear.ConsumptionMode tomeConsumptionMode,
            int maxTomeUses,
            int bookCopyWearCost,
            MasterTomeWear.DepleteBehavior tomeDepleteBehavior
        ) {
            this(
                minDiscountBookshelves,
                maxDiscountBookshelves,
                maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent,
                maxBaseXpDiscountPercent,
                maxSpecificEnchantBonusPercent,
                specificEnchantExponent,
                maxTotalXpDiscountPercent,
                masteryUnitExponent,
                globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier,
                shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight,
                epiphanyProbabilityAffinityWeight,
                blankBookAffinityWeight,
                epiphanyShelfScoreFactor,
                epiphanyGlobalMasteryScoreFactor,
                epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb,
                epiphanyTier2ScoreDivisor,
                epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb,
                epiphanyTierPlusScoreDivisor,
                epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor,
                tomeConsumptionMode,
                maxTomeUses,
                bookCopyWearCost,
                tomeDepleteBehavior,
                MasterTomeWear.DEFAULT_REGEN_ENABLED,
                MasterTomeWear.DEFAULT_REGEN_BASE_SECONDS,
                MasterTomeWear.DEFAULT_REGEN_TIER1_SHELVES,
                MasterTomeWear.DEFAULT_REGEN_TIER1_SECONDS,
                MasterTomeWear.DEFAULT_REGEN_TIER2_SHELVES,
                MasterTomeWear.DEFAULT_REGEN_TIER2_SECONDS
            );
        }

        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor
        ) {
            this(
                minDiscountBookshelves,
                maxDiscountBookshelves,
                maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent,
                maxBaseXpDiscountPercent,
                maxSpecificEnchantBonusPercent,
                specificEnchantExponent,
                maxTotalXpDiscountPercent,
                masteryUnitExponent,
                globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier,
                shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight,
                epiphanyProbabilityAffinityWeight,
                blankBookAffinityWeight,
                epiphanyShelfScoreFactor,
                epiphanyGlobalMasteryScoreFactor,
                epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb,
                epiphanyTier2ScoreDivisor,
                epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb,
                epiphanyTierPlusScoreDivisor,
                epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor,
                MasterTomeWear.ConsumptionMode.WEAR,
                MasterTomeWear.DEFAULT_MAX_TOME_USES,
                MasterTomeWear.DEFAULT_BOOK_COPY_WEAR_COST,
                MasterTomeWear.DepleteBehavior.STEP_DOWN
            );
        }

        public static Config fromJson(JsonObject root) {
            if (root == null) {
                return DEFAULT;
            }
            JsonObject woodObj = subObject(root, "wooden_bookshelves");
            JsonObject masteryObj = subObject(root, "chiseled_mastery");
            JsonObject epiphanyObj = subObject(root, "epiphany_affinity");

            int minShelves = clampInt(
                intFrom(woodObj, root, "min_discount_bookshelves", null, DEFAULT.minDiscountBookshelves),
                0,
                256
            );
            int maxShelves = clampInt(
                intFrom(woodObj, root, "max_discount_bookshelves", null, DEFAULT.maxDiscountBookshelves),
                Math.max(1, minShelves + 1),
                256
            );
            int maxWood = clampInt(
                intFrom(woodObj, root, "max_wooden_discount_percent", "max_discount_percent", DEFAULT.maxWoodenDiscountPercent),
                0,
                90
            );
            int maxGlobal = clampInt(
                intFrom(masteryObj, root, "max_global_mastery_discount_percent", "max_global_discount_percent", DEFAULT.maxGlobalMasteryDiscountPercent),
                0,
                90
            );
            int maxBase = clampInt(
                intFrom(masteryObj, root, "max_base_xp_discount_percent", "max_base_discount_percent", DEFAULT.maxBaseXpDiscountPercent),
                0,
                90
            );
            int maxSpec = clampInt(
                intFrom(masteryObj, root, "max_specific_enchant_bonus_percent", "max_specific_discount_percent", DEFAULT.maxSpecificEnchantBonusPercent),
                0,
                90
            );
            double specExp = clampDouble(
                doubleFrom(masteryObj, root, "specific_enchant_exponent", "specific_exponent", DEFAULT.specificEnchantExponent),
                0.25,
                4.0
            );
            int maxTotal = clampInt(
                intFrom(masteryObj, root, "max_total_xp_discount_percent", null, DEFAULT.maxTotalXpDiscountPercent),
                maxBase,
                90
            );

            return new Config(
                minShelves,
                maxShelves,
                maxWood,
                maxGlobal,
                maxBase,
                maxSpec,
                specExp,
                maxTotal,
                clampDouble(
                    doubleFrom(masteryObj, root, "mastery_unit_exponent", null, DEFAULT.masteryUnitExponent),
                    0.25,
                    4.0
                ),
                clampDouble(
                    doubleFrom(masteryObj, root, "global_mastery_multiplier", "global_discount_per_unit", DEFAULT.globalMasteryMultiplier),
                    0.0,
                    20.0
                ),
                clampDouble(
                    doubleFrom(masteryObj, root, "master_tome_upgrade_material_multiplier", "upgrade_material_multiplier", DEFAULT.masterTomeUpgradeMaterialMultiplier),
                    0.05,
                    1.0
                ),
                clampDouble(
                    doubleFrom(woodObj, root, "shelf_upgrade_material_multiplier", "upgrade_material_multiplier", DEFAULT.shelfUpgradeMaterialMultiplier),
                    0.05,
                    1.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_probability_global_weight", null, DEFAULT.epiphanyProbabilityGlobalWeight),
                    0.0,
                    10.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_probability_affinity_weight", "affinity_chance_per_unit", DEFAULT.epiphanyProbabilityAffinityWeight),
                    0.0,
                    10.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "blank_book_affinity_weight", null, DEFAULT.blankBookAffinityWeight),
                    0.0,
                    2.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_shelf_score_factor", null, DEFAULT.epiphanyShelfScoreFactor),
                    0.0,
                    20.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_global_mastery_score_factor", "global_slot_weight", DEFAULT.epiphanyGlobalMasteryScoreFactor),
                    0.0,
                    20.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_affinity_mastery_score_factor", "affinity_slot_weight", DEFAULT.epiphanyAffinityMasteryScoreFactor),
                    0.0,
                    20.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier2_max_prob", null, DEFAULT.epiphanyTier2MaxProb),
                    0.0,
                    1.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier2_score_divisor", null, DEFAULT.epiphanyTier2ScoreDivisor),
                    1.0,
                    1000.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier2_mastery_bonus", null, DEFAULT.epiphanyTier2MasteryBonus),
                    0.0,
                    1.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier_plus_max_prob", null, DEFAULT.epiphanyTierPlusMaxProb),
                    0.0,
                    1.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier_plus_score_divisor", null, DEFAULT.epiphanyTierPlusScoreDivisor),
                    1.0,
                    1000.0
                ),
                clampDouble(
                    doubleFrom(epiphanyObj, root, "epiphany_tier_plus_mastery_bonus", null, DEFAULT.epiphanyTierPlusMasteryBonus),
                    0.0,
                    1.0
                ),
                clampDouble(
                    doubleFrom(masteryObj, root, "incremental_step_credit_factor", "incremental_threshold_credit_factor", DEFAULT.incrementalStepCreditFactor),
                    0.0,
                    0.95
                ),
                MasterTomeWear.ConsumptionMode.fromToken(
                    stringFrom(masteryObj, root, "tome_consumption_mode", null, DEFAULT.tomeConsumptionMode.token())
                ),
                clampInt(
                    intFrom(masteryObj, root, "max_tome_uses", null, DEFAULT.maxTomeUses),
                    1,
                    1024
                ),
                clampInt(
                    intFrom(masteryObj, root, "book_copy_wear_cost", null, DEFAULT.bookCopyWearCost),
                    1,
                    1024
                ),
                MasterTomeWear.DepleteBehavior.fromToken(
                    stringFrom(masteryObj, root, "tome_deplete_behavior", null, DEFAULT.tomeDepleteBehavior.token())
                ),
                boolFrom(masteryObj, root, "tome_regen_enabled", null, DEFAULT.tomeRegenEnabled),
                clampInt(
                    intFrom(masteryObj, root, "tome_regen_base_seconds", null, DEFAULT.tomeRegenBaseSeconds),
                    1,
                    86_400
                ),
                clampInt(
                    intFrom(masteryObj, root, "tome_regen_tier1_shelves", null, DEFAULT.tomeRegenTier1Shelves),
                    1,
                    256
                ),
                clampInt(
                    intFrom(masteryObj, root, "tome_regen_tier1_seconds", null, DEFAULT.tomeRegenTier1Seconds),
                    1,
                    86_400
                ),
                clampInt(
                    intFrom(masteryObj, root, "tome_regen_tier2_shelves", null, DEFAULT.tomeRegenTier2Shelves),
                    1,
                    256
                ),
                clampInt(
                    intFrom(masteryObj, root, "tome_regen_tier2_seconds", null, DEFAULT.tomeRegenTier2Seconds),
                    1,
                    86_400
                )
            );
        }

        private static boolean boolFrom(JsonObject section, JsonObject root, String primaryKey, String aliasKey, boolean fallback) {
            if (section != null) {
                if (section.has(primaryKey) && section.get(primaryKey).isJsonPrimitive()) {
                    return section.get(primaryKey).getAsBoolean();
                }
                if (aliasKey != null && section.has(aliasKey) && section.get(aliasKey).isJsonPrimitive()) {
                    return section.get(aliasKey).getAsBoolean();
                }
            }
            if (root.has(primaryKey) && root.get(primaryKey).isJsonPrimitive()) {
                return root.get(primaryKey).getAsBoolean();
            }
            if (aliasKey != null && root.has(aliasKey) && root.get(aliasKey).isJsonPrimitive()) {
                return root.get(aliasKey).getAsBoolean();
            }
            return fallback;
        }

        private static String stringFrom(JsonObject section, JsonObject root, String primaryKey, String aliasKey, String fallback) {
            if (section != null) {
                if (section.has(primaryKey) && section.get(primaryKey).isJsonPrimitive()) {
                    return section.get(primaryKey).getAsString();
                }
                if (aliasKey != null && section.has(aliasKey) && section.get(aliasKey).isJsonPrimitive()) {
                    return section.get(aliasKey).getAsString();
                }
            }
            if (root.has(primaryKey) && root.get(primaryKey).isJsonPrimitive()) {
                return root.get(primaryKey).getAsString();
            }
            if (aliasKey != null && root.has(aliasKey) && root.get(aliasKey).isJsonPrimitive()) {
                return root.get(aliasKey).getAsString();
            }
            return fallback;
        }

        private static JsonObject subObject(JsonObject root, String sectionKey) {
            return root.has(sectionKey) && root.get(sectionKey).isJsonObject()
                ? root.getAsJsonObject(sectionKey)
                : null;
        }

        private static int intFrom(JsonObject section, JsonObject root, String primaryKey, String aliasKey, int fallback) {
            if (section != null) {
                if (section.has(primaryKey) && section.get(primaryKey).isJsonPrimitive()) {
                    return section.get(primaryKey).getAsInt();
                }
                if (aliasKey != null && section.has(aliasKey) && section.get(aliasKey).isJsonPrimitive()) {
                    return section.get(aliasKey).getAsInt();
                }
            }
            if (root.has(primaryKey) && root.get(primaryKey).isJsonPrimitive()) {
                return root.get(primaryKey).getAsInt();
            }
            if (aliasKey != null && root.has(aliasKey) && root.get(aliasKey).isJsonPrimitive()) {
                return root.get(aliasKey).getAsInt();
            }
            return fallback;
        }

        private static double doubleFrom(JsonObject section, JsonObject root, String primaryKey, String aliasKey, double fallback) {
            if (section != null) {
                if (section.has(primaryKey) && section.get(primaryKey).isJsonPrimitive()) {
                    return section.get(primaryKey).getAsDouble();
                }
                if (aliasKey != null && section.has(aliasKey) && section.get(aliasKey).isJsonPrimitive()) {
                    return section.get(aliasKey).getAsDouble();
                }
            }
            if (root.has(primaryKey) && root.get(primaryKey).isJsonPrimitive()) {
                return root.get(primaryKey).getAsDouble();
            }
            if (aliasKey != null && root.has(aliasKey) && root.get(aliasKey).isJsonPrimitive()) {
                return root.get(aliasKey).getAsDouble();
            }
            return fallback;
        }

        private static int clampInt(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private static double clampDouble(double v, double lo, double hi) {
            if (!Double.isFinite(v)) return lo;
            return Math.max(lo, Math.min(hi, v));
        }
    }

    private LibraryResonance() {}

    public static Config activeServerConfig() {
        Config cfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        return cfg != null ? cfg : Config.DEFAULT;
    }

    public static Config configForSide(boolean isClientSide) {
        Config cfg = isClientSide
            ? SyncedEnchantmentCosts.libraryResonanceConfig()
            : ChiseledEnchantingTableData.libraryResonanceConfig();
        return cfg != null ? cfg : Config.DEFAULT;
    }

    public static Config activeConfig() {
        return activeServerConfig();
    }

    public static double relativeMastery(int storedLevel, int maxLevel) {
        if (storedLevel <= 0) {
            return 0.0;
        }
        int cappedMax = Math.max(1, maxLevel);
        return Math.min(1.0, (double) Math.min(storedLevel, cappedMax) / (double) cappedMax);
    }

    public static double masteryUnitsForEntry(int storedLevel, int maxLevel) {
        return masteryUnitsForEntry(activeServerConfig(), storedLevel, maxLevel);
    }

    public static double masteryUnitsForEntry(Config cfg, int storedLevel, int maxLevel) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0.0;
        }
        return Math.pow(r, c.masteryUnitExponent());
    }

    public static int resolveMaxLevel(Level level, Identifier enchantmentId) {
        try {
            Holder<Enchantment> holder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
            return Math.max(1, holder.value().getMaxLevel());
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static double totalMasteryUnits(Level level, Map<Identifier, Integer> storedMasterLevels) {
        return totalMasteryUnits(configForSide(level.isClientSide()), level, storedMasterLevels);
    }

    public static double totalMasteryUnits(Config cfg, Level level, Map<Identifier, Integer> storedMasterLevels) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty()) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                sum += masteryUnitsForEntry(c, lvl, holder.value().getMaxLevel());
            } catch (Exception ignored) {
            }
        }
        return sum;
    }

    public static double affinityMasteryUnits(
        Level level,
        Map<Identifier, Integer> storedMasterLevels,
        ItemStack targetStack
    ) {
        return affinityMasteryUnits(configForSide(level.isClientSide()), level, storedMasterLevels, targetStack);
    }

    public static double affinityMasteryUnits(
        Config cfg,
        Level level,
        Map<Identifier, Integer> storedMasterLevels,
        ItemStack targetStack
    ) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty() || targetStack.isEmpty()) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        boolean isBlankBook = targetStack.is(Items.BOOK);
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                if (isBlankBook || holder.value().canEnchant(targetStack)) {
                    double units = masteryUnitsForEntry(c, lvl, holder.value().getMaxLevel());
                    sum += isBlankBook ? (units * c.blankBookAffinityWeight()) : units;
                }
            } catch (Exception ignored) {
            }
        }
        return sum;
    }

    public static int woodenBookshelfDiscountPercent(int regularBookshelves) {
        return woodenBookshelfDiscountPercent(activeServerConfig(), regularBookshelves);
    }

    public static int woodenBookshelfDiscountPercent(Config cfg, int regularBookshelves) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < c.minDiscountBookshelves()) {
            return 0;
        }
        int clamped = Math.min(c.maxDiscountBookshelves(), regularBookshelves);
        int span = Math.max(1, c.maxDiscountBookshelves() - c.minDiscountBookshelves());
        double progress = (double) (clamped - c.minDiscountBookshelves()) / (double) span;
        return Math.min(c.maxWoodenDiscountPercent(), Math.max(1, (int) Math.round(1.0 + Math.max(0, c.maxWoodenDiscountPercent() - 1) * progress)));
    }

    public static int globalMasteryDiscountPercent(double totalMasteryUnits) {
        return globalMasteryDiscountPercent(activeServerConfig(), totalMasteryUnits);
    }

    public static int globalMasteryDiscountPercent(Config cfg, double totalMasteryUnits) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (totalMasteryUnits <= 0.0) {
            return 0;
        }
        return Math.min(c.maxGlobalMasteryDiscountPercent(), (int) Math.floor(totalMasteryUnits * c.globalMasteryMultiplier()));
    }

    public static int specificEnchantmentDiscountBonus(int storedLevel, int maxLevel) {
        return specificEnchantmentDiscountBonus(activeServerConfig(), storedLevel, maxLevel);
    }

    public static int specificEnchantmentDiscountBonus(Config cfg, int storedLevel, int maxLevel) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0;
        }
        double weighted = c.specificEnchantExponent() == 1.0 ? r : Math.pow(r, c.specificEnchantExponent());
        return Math.min(c.maxSpecificEnchantBonusPercent(), (int) Math.round(c.maxSpecificEnchantBonusPercent() * weighted));
    }

    public static int baseXpDiscountPercent(int regularBookshelves, double totalMasteryUnits) {
        return baseXpDiscountPercent(activeServerConfig(), regularBookshelves, totalMasteryUnits);
    }

    public static int baseXpDiscountPercent(Config cfg, int regularBookshelves, double totalMasteryUnits) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        return Math.min(
            c.maxBaseXpDiscountPercent(),
            woodenBookshelfDiscountPercent(c, regularBookshelves) + globalMasteryDiscountPercent(c, totalMasteryUnits)
        );
    }

    public static int effectiveXpDiscountPercent(
        int regularBookshelves,
        double totalMasteryUnits,
        int specificStoredLevel,
        int specificMaxLevel
    ) {
        return effectiveXpDiscountPercent(
            activeServerConfig(),
            regularBookshelves,
            totalMasteryUnits,
            specificStoredLevel,
            specificMaxLevel
        );
    }

    public static int effectiveXpDiscountPercent(
        Config cfg,
        int regularBookshelves,
        double totalMasteryUnits,
        int specificStoredLevel,
        int specificMaxLevel
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        int base = baseXpDiscountPercent(c, regularBookshelves, totalMasteryUnits);
        int specific = specificEnchantmentDiscountBonus(c, specificStoredLevel, specificMaxLevel);
        return Math.min(c.maxTotalXpDiscountPercent(), base + specific);
    }

    public static double epiphanyResonanceScore(
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyResonanceScore(activeServerConfig(), regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
    }

    public static double epiphanyResonanceScore(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double shelfExcess = Math.max(0, regularBookshelves - minBookshelves) * c.epiphanyShelfScoreFactor();
        return shelfExcess
            + c.epiphanyGlobalMasteryScoreFactor() * Math.max(0.0, totalMasteryUnits)
            + c.epiphanyAffinityMasteryScoreFactor() * Math.max(0.0, affinityMasteryUnits);
    }

    public static double epiphanyProbability(
        int regularBookshelves,
        int minBookshelves,
        double baseChance,
        double perShelfChance,
        double perTomeChance,
        double maxChance,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyProbability(
            activeServerConfig(),
            regularBookshelves,
            minBookshelves,
            baseChance,
            perShelfChance,
            perTomeChance,
            maxChance,
            totalMasteryUnits,
            affinityMasteryUnits
        );
    }

    public static double epiphanyProbability(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        double baseChance,
        double perShelfChance,
        double perTomeChance,
        double maxChance,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double effectiveMastery = c.epiphanyProbabilityGlobalWeight() * Math.max(0.0, totalMasteryUnits)
            + c.epiphanyProbabilityAffinityWeight() * Math.max(0.0, affinityMasteryUnits);
        double raw = baseChance
            + Math.max(0, regularBookshelves - minBookshelves) * perShelfChance
            + effectiveMastery * perTomeChance;
        return Math.max(0.0, Math.min(maxChance, raw));
    }

    public static int epiphanyMaxSlots(
        int regularBookshelves,
        int minBookshelves,
        int maxSlotsCap,
        int scoreStepPerExtraSlot,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyMaxSlots(
            activeServerConfig(),
            regularBookshelves,
            minBookshelves,
            maxSlotsCap,
            scoreStepPerExtraSlot,
            totalMasteryUnits,
            affinityMasteryUnits
        );
    }

    public static int epiphanyMaxSlots(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        int maxSlotsCap,
        int scoreStepPerExtraSlot,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0.0 >= 0.0 && regularBookshelves < minBookshelves ? 0 : 0;
        }
        double score = epiphanyResonanceScore(c, regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
        int step = Math.max(1, scoreStepPerExtraSlot);
        int unlocked = 1 + (int) Math.floor(score / (double) step);
        return Math.max(1, Math.min(Math.max(1, maxSlotsCap), unlocked));
    }

    public static int rollEpiphanyTier(
        long hashSeed,
        int minTier,
        int maxLevel,
        double resonanceScore,
        double ownStoredRelativeMastery
    ) {
        return rollEpiphanyTier(activeServerConfig(), hashSeed, minTier, maxLevel, resonanceScore, ownStoredRelativeMastery);
    }

    public static int rollEpiphanyTier(
        Config cfg,
        long hashSeed,
        int minTier,
        int maxLevel,
        double resonanceScore,
        double ownStoredRelativeMastery
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        int cappedMax = Math.max(1, maxLevel);
        int tier = Math.max(1, Math.min(cappedMax, minTier));
        if (tier >= cappedMax) {
            return cappedMax;
        }
        long s = hashSeed;
        while (tier < cappedMax) {
            s = splitMix64(s + 0x9E3779B97F4A7C15L);
            double u = (s >>> 11) * (1.0 / (1L << 53));
            double p = (tier == 1)
                ? Math.min(c.epiphanyTier2MaxProb(), (resonanceScore / c.epiphanyTier2ScoreDivisor()) + c.epiphanyTier2MasteryBonus() * ownStoredRelativeMastery)
                : Math.min(c.epiphanyTierPlusMaxProb(), (resonanceScore / c.epiphanyTierPlusScoreDivisor()) + c.epiphanyTierPlusMasteryBonus() * ownStoredRelativeMastery);
            if (u < p) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
