package chiseled_enchanting_table.mechanics.resonance;

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
 * <p>All parameters are data-driven via {@code data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json}
 * and synced to clients in {@link chiseled_enchanting_table.menu.EnchantCostTablePayload}.</p>
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
        int maxTotalXpDiscountPercent,
        double masteryUnitExponent,
        double globalMasteryMultiplier,
        double masterTomeUpgradeMaterialMultiplier,
        double shelfUpgradeMaterialMultiplier,
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
        public static final Config DEFAULT = new Config(
            MIN_DISCOUNT_BOOKSHELVES,
            38,
            MAX_WOODEN_DISCOUNT_PERCENT,
            MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT,
            MAX_BASE_XP_DISCOUNT_PERCENT,
            MAX_SPECIFIC_ENCHANT_BONUS_PERCENT,
            MAX_TOTAL_XP_DISCOUNT_PERCENT,
            1.25,
            1.25,
            0.80,
            0.90,
            1.0,
            2.2,
            3.5,
            0.65,
            80.0,
            0.20,
            0.50,
            110.0,
            0.25,
            0.85
        );

        public static Config fromJson(JsonObject root) {
            if (root == null) {
                return DEFAULT;
            }
            int minShelves = clampInt(intOr(root, "min_discount_bookshelves", DEFAULT.minDiscountBookshelves), 0, 256);
            int maxShelves = clampInt(intOr(root, "max_discount_bookshelves", DEFAULT.maxDiscountBookshelves), Math.max(1, minShelves + 1), 256);
            int maxWood = clampInt(intOr(root, "max_wooden_discount_percent", DEFAULT.maxWoodenDiscountPercent), 0, 90);
            int maxGlobal = clampInt(intOr(root, "max_global_mastery_discount_percent", DEFAULT.maxGlobalMasteryDiscountPercent), 0, 90);
            int maxBase = clampInt(intOr(root, "max_base_xp_discount_percent", DEFAULT.maxBaseXpDiscountPercent), 0, 90);
            int maxSpec = clampInt(intOr(root, "max_specific_enchant_bonus_percent", DEFAULT.maxSpecificEnchantBonusPercent), 0, 90);
            int maxTotal = clampInt(intOr(root, "max_total_xp_discount_percent", DEFAULT.maxTotalXpDiscountPercent), maxBase, 90);
            return new Config(
                minShelves,
                maxShelves,
                maxWood,
                maxGlobal,
                maxBase,
                maxSpec,
                maxTotal,
                clampDouble(doubleOr(root, "mastery_unit_exponent", DEFAULT.masteryUnitExponent), 0.25, 4.0),
                clampDouble(doubleOr(root, "global_mastery_multiplier", DEFAULT.globalMasteryMultiplier), 0.0, 20.0),
                clampDouble(doubleOr(root, "master_tome_upgrade_material_multiplier", DEFAULT.masterTomeUpgradeMaterialMultiplier), 0.05, 1.0),
                clampDouble(doubleOr(root, "shelf_upgrade_material_multiplier", DEFAULT.shelfUpgradeMaterialMultiplier), 0.05, 1.0),
                clampDouble(doubleOr(root, "epiphany_shelf_score_factor", DEFAULT.epiphanyShelfScoreFactor), 0.0, 20.0),
                clampDouble(doubleOr(root, "epiphany_global_mastery_score_factor", DEFAULT.epiphanyGlobalMasteryScoreFactor), 0.0, 20.0),
                clampDouble(doubleOr(root, "epiphany_affinity_mastery_score_factor", DEFAULT.epiphanyAffinityMasteryScoreFactor), 0.0, 20.0),
                clampDouble(doubleOr(root, "epiphany_tier2_max_prob", DEFAULT.epiphanyTier2MaxProb), 0.0, 1.0),
                clampDouble(doubleOr(root, "epiphany_tier2_score_divisor", DEFAULT.epiphanyTier2ScoreDivisor), 1.0, 1000.0),
                clampDouble(doubleOr(root, "epiphany_tier2_mastery_bonus", DEFAULT.epiphanyTier2MasteryBonus), 0.0, 1.0),
                clampDouble(doubleOr(root, "epiphany_tier_plus_max_prob", DEFAULT.epiphanyTierPlusMaxProb), 0.0, 1.0),
                clampDouble(doubleOr(root, "epiphany_tier_plus_score_divisor", DEFAULT.epiphanyTierPlusScoreDivisor), 1.0, 1000.0),
                clampDouble(doubleOr(root, "epiphany_tier_plus_mastery_bonus", DEFAULT.epiphanyTierPlusMasteryBonus), 0.0, 1.0),
                clampDouble(doubleOr(root, "incremental_step_credit_factor", DEFAULT.incrementalStepCreditFactor), 0.0, 0.95)
            );
        }

        private static int intOr(JsonObject obj, String key, int fallback) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
        }

        private static double doubleOr(JsonObject obj, String key, double fallback) {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsDouble() : fallback;
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

    public static Config activeConfig() {
        Config cfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        return cfg != null ? cfg : Config.DEFAULT;
    }

    public static double relativeMastery(int storedLevel, int maxLevel) {
        if (storedLevel <= 0) {
            return 0.0;
        }
        int cappedMax = Math.max(1, maxLevel);
        return Math.min(1.0, (double) Math.min(storedLevel, cappedMax) / (double) cappedMax);
    }

    public static double masteryUnitsForEntry(int storedLevel, int maxLevel) {
        return masteryUnitsForEntry(activeConfig(), storedLevel, maxLevel);
    }

    public static double masteryUnitsForEntry(Config cfg, int storedLevel, int maxLevel) {
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0.0;
        }
        return Math.pow(r, cfg.masteryUnitExponent());
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
        return totalMasteryUnits(activeConfig(), level, storedMasterLevels);
    }

    public static double totalMasteryUnits(Config cfg, Level level, Map<Identifier, Integer> storedMasterLevels) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty()) {
            return 0.0;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                sum += masteryUnitsForEntry(cfg, lvl, holder.value().getMaxLevel());
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
        return affinityMasteryUnits(activeConfig(), level, storedMasterLevels, targetStack);
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
                    double units = masteryUnitsForEntry(cfg, lvl, holder.value().getMaxLevel());
                    sum += isBlankBook ? (units * 0.5) : units;
                }
            } catch (Exception ignored) {
            }
        }
        return sum;
    }

    public static int woodenBookshelfDiscountPercent(int regularBookshelves) {
        return woodenBookshelfDiscountPercent(activeConfig(), regularBookshelves);
    }

    public static int woodenBookshelfDiscountPercent(Config cfg, int regularBookshelves) {
        if (regularBookshelves < cfg.minDiscountBookshelves()) {
            return 0;
        }
        int clamped = Math.min(cfg.maxDiscountBookshelves(), regularBookshelves);
        int span = Math.max(1, cfg.maxDiscountBookshelves() - cfg.minDiscountBookshelves());
        double progress = (double) (clamped - cfg.minDiscountBookshelves()) / (double) span;
        return Math.min(cfg.maxWoodenDiscountPercent(), Math.max(1, (int) Math.round(1.0 + Math.max(0, cfg.maxWoodenDiscountPercent() - 1) * progress)));
    }

    public static int globalMasteryDiscountPercent(double totalMasteryUnits) {
        return globalMasteryDiscountPercent(activeConfig(), totalMasteryUnits);
    }

    public static int globalMasteryDiscountPercent(Config cfg, double totalMasteryUnits) {
        if (totalMasteryUnits <= 0.0) {
            return 0;
        }
        return Math.min(cfg.maxGlobalMasteryDiscountPercent(), (int) Math.floor(totalMasteryUnits * cfg.globalMasteryMultiplier()));
    }

    public static int specificEnchantmentDiscountBonus(int storedLevel, int maxLevel) {
        return specificEnchantmentDiscountBonus(activeConfig(), storedLevel, maxLevel);
    }

    public static int specificEnchantmentDiscountBonus(Config cfg, int storedLevel, int maxLevel) {
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0;
        }
        return Math.min(cfg.maxSpecificEnchantBonusPercent(), (int) Math.round(cfg.maxSpecificEnchantBonusPercent() * r));
    }

    public static int baseXpDiscountPercent(int regularBookshelves, double totalMasteryUnits) {
        return baseXpDiscountPercent(activeConfig(), regularBookshelves, totalMasteryUnits);
    }

    public static int baseXpDiscountPercent(Config cfg, int regularBookshelves, double totalMasteryUnits) {
        return Math.min(
            cfg.maxBaseXpDiscountPercent(),
            woodenBookshelfDiscountPercent(cfg, regularBookshelves) + globalMasteryDiscountPercent(cfg, totalMasteryUnits)
        );
    }

    public static int effectiveXpDiscountPercent(
        int regularBookshelves,
        double totalMasteryUnits,
        int specificStoredLevel,
        int specificMaxLevel
    ) {
        return effectiveXpDiscountPercent(
            activeConfig(),
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
        int base = baseXpDiscountPercent(cfg, regularBookshelves, totalMasteryUnits);
        int specific = specificEnchantmentDiscountBonus(cfg, specificStoredLevel, specificMaxLevel);
        return Math.min(cfg.maxTotalXpDiscountPercent(), base + specific);
    }

    public static double epiphanyResonanceScore(
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyResonanceScore(activeConfig(), regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
    }

    public static double epiphanyResonanceScore(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double shelfExcess = Math.max(0, regularBookshelves - minBookshelves) * cfg.epiphanyShelfScoreFactor();
        return shelfExcess
            + cfg.epiphanyGlobalMasteryScoreFactor() * Math.max(0.0, totalMasteryUnits)
            + cfg.epiphanyAffinityMasteryScoreFactor() * Math.max(0.0, affinityMasteryUnits);
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
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double effectiveMastery = 0.45 * Math.max(0.0, totalMasteryUnits) + 0.85 * Math.max(0.0, affinityMasteryUnits);
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
        if (regularBookshelves < minBookshelves) {
            return 0;
        }
        double score = epiphanyResonanceScore(regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
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
        return rollEpiphanyTier(activeConfig(), hashSeed, minTier, maxLevel, resonanceScore, ownStoredRelativeMastery);
    }

    public static int rollEpiphanyTier(
        Config cfg,
        long hashSeed,
        int minTier,
        int maxLevel,
        double resonanceScore,
        double ownStoredRelativeMastery
    ) {
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
                ? Math.min(cfg.epiphanyTier2MaxProb(), (resonanceScore / cfg.epiphanyTier2ScoreDivisor()) + cfg.epiphanyTier2MasteryBonus() * ownStoredRelativeMastery)
                : Math.min(cfg.epiphanyTierPlusMaxProb(), (resonanceScore / cfg.epiphanyTierPlusScoreDivisor()) + cfg.epiphanyTierPlusMasteryBonus() * ownStoredRelativeMastery);
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
