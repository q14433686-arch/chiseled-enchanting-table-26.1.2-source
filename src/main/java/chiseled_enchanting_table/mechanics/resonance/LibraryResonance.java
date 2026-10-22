package chiseled_enchanting_table.mechanics.resonance;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Unified Library Resonance math shared identically by Server and Client.
 *
 * <h3>Design Pillars</h3>
 * <ol>
 *   <li><b>Wooden Bookshelf Discount Floor ({@code W >= 12})</b>: fewer than 12 regular bookshelves
 *       grant {@code 0%} XP discount; starting at 12 shelves, discount begins at {@code 2%} and
 *       grows by {@code +1%} every 2 additional shelves up to {@code 12%} (at 32 shelves).</li>
 *   <li><b>Mod-Agnostic Relative Master-Tome Tier ({@code r = L / maxLevel})</b>: queries each
 *       enchantment's registered {@link Enchantment#getMaxLevel()} dynamically, automatically
 *       supporting vanilla and all third-party enchantment mods ({@code maxLevel = 1..255}).
 *       Each distinct stored enchantment contributes convex mastery weight
 *       {@code w(r) = 0.45 * r + 0.55 * r^2} in {@code [0, 1]}, preventing low-tier duplicate spam
 *       while rewarding upgrading Master Tomes to max tier ({@code r = 1.0 -> w = 1.0}).</li>
 *   <li><b>Three-Way Resonance Impact</b>:
 *     <ul>
 *       <li><b>XP Discount</b>: {@code min(40%, wooden(W) + globalMastery(E_all) + specificMastery(r_e))},
 *           where {@code specificMastery(r_e) = round(15 * r_e^1.5)} rewards upgrading that exact
 *           enchantment's Master Tome up to an extra {@code -15%} XP discount.</li>
 *       <li><b>Treasure Epiphany Probability</b>: combines {@code W}, global mastery {@code E_all},
 *           and target-equipment affinity mastery {@code E_affinity(target)} (all stored tomes
 *           matching {@link Enchantment#canEnchant(ItemStack)}).</li>
 *       <li><b>Per-Item Epiphany Cap & Tier</b>: scales max simultaneous Epiphany slots up to
 *           {@code maxSlotsCap} and elevates rolled tier on multi-level Treasure enchantments.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class LibraryResonance {
    public static final int MIN_DISCOUNT_BOOKSHELVES = 12;
    public static final int MAX_WOODEN_DISCOUNT_PERCENT = 12;
    public static final int MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT = 13;
    public static final int MAX_BASE_DISCOUNT_PERCENT = 25;
    public static final int MAX_SPECIFIC_MASTERY_DISCOUNT_PERCENT = 15;
    public static final int MAX_TOTAL_XP_DISCOUNT_PERCENT = 40;

    public static final double AFFINITY_CHANCE_PER_UNIT = 0.055;
    public static final double GLOBAL_SLOT_WEIGHT = 1.8;
    public static final double AFFINITY_SLOT_WEIGHT = 3.6;

    private LibraryResonance() {}

    public static int resolveMaxLevel(Level level, Identifier enchantmentId) {
        if (level == null || enchantmentId == null) return 1;
        try {
            Holder<Enchantment> holder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
            return Math.max(1, holder.value().getMaxLevel());
        } catch (Exception ignored) {
            return 1;
        }
    }

    /** Relative tier in {@code [0.0, 1.0]} for a stored Master Tome of {@code enchantmentId}. */
    public static double relativeMastery(int storedLevel, int maxLevel) {
        if (storedLevel <= 0) return 0.0;
        int safeMax = Math.max(1, maxLevel);
        int clamped = Math.min(safeMax, storedLevel);
        return (double) clamped / (double) safeMax;
    }

    /** Convex mastery tome unit in {@code [0.0, 1.0]}: low-tier 1/5 = 0.112, maxed N/N = 1.000. */
    public static double masteryUnit(int storedLevel, int maxLevel) {
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) return 0.0;
        return 0.45 * r + 0.55 * r * r;
    }

    /** Sum of convex mastery units across all distinct enchantments stored in Chiseled Bookshelves. */
    public static double totalMasteryUnits(Level level, Map<Identifier, Integer> storedMasterLevels) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (lvl <= 0) continue;
            int maxLvl = resolveMaxLevel(level, entry.getKey());
            sum += masteryUnit(lvl, maxLvl);
        }
        return sum;
    }

    /**
     * Sum of convex mastery units across stored Master Tomes compatible with {@code targetStack}
     * (via {@link Enchantment#canEnchant(ItemStack)} — works automatically for modded gear & enchants).
     */
    public static double affinityMasteryUnits(
        Level level,
        Map<Identifier, Integer> storedMasterLevels,
        ItemStack targetStack
    ) {
        if (level == null
            || storedMasterLevels == null
            || storedMasterLevels.isEmpty()
            || targetStack == null
            || targetStack.isEmpty()
            || targetStack.is(Items.BOOK)
            || targetStack.is(Items.ENCHANTED_BOOK)) {
            return 0.0;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (lvl <= 0) continue;
            var optHolder = registry.get(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
            if (optHolder.isEmpty()) continue;
            Enchantment ench = optHolder.get().value();
            if (ench.canEnchant(targetStack)) {
                sum += masteryUnit(lvl, Math.max(1, ench.getMaxLevel()));
            }
        }
        return sum;
    }

    /** Wooden bookshelf XP discount: strictly 0% below 12 shelves, then 2%..12% from 12..32 shelves. */
    public static int woodenBookshelfDiscountPercent(int regularBookshelves) {
        if (regularBookshelves < MIN_DISCOUNT_BOOKSHELVES) {
            return 0;
        }
        return Math.min(
            MAX_WOODEN_DISCOUNT_PERCENT,
            2 + (regularBookshelves - MIN_DISCOUNT_BOOKSHELVES) / 2
        );
    }

    /** Global Chiseled Bookshelf mastery discount across all stored Master Tomes (0%..13%). */
    public static int globalMasteryDiscountPercent(double totalMasteryUnits) {
        if (totalMasteryUnits <= 0.0) return 0;
        return Math.min(
            MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT,
            (int) Math.round(totalMasteryUnits * 1.5)
        );
    }

    /** Table-wide baseline XP discount (0%..25%) before per-enchantment Master Tome specialization. */
    public static int baseXpDiscountPercent(int regularBookshelves, double totalMasteryUnits) {
        return Math.min(
            MAX_BASE_DISCOUNT_PERCENT,
            woodenBookshelfDiscountPercent(regularBookshelves) + globalMasteryDiscountPercent(totalMasteryUnits)
        );
    }

    /** Per-enchantment relative-tier Master Tome bonus XP discount (0%..15%). */
    public static int specificEnchantmentDiscountBonus(int storedLevel, int maxLevel) {
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) return 0;
        return Math.min(
            MAX_SPECIFIC_MASTERY_DISCOUNT_PERCENT,
            (int) Math.round(MAX_SPECIFIC_MASTERY_DISCOUNT_PERCENT * Math.pow(r, 1.5))
        );
    }

    /** Total XP discount (0%..40%) for a specific enchantment id. */
    public static int effectiveXpDiscountPercent(
        int regularBookshelves,
        double totalMasteryUnits,
        int storedLevelForEnchant,
        int maxLevelForEnchant
    ) {
        int total = woodenBookshelfDiscountPercent(regularBookshelves)
            + globalMasteryDiscountPercent(totalMasteryUnits)
            + specificEnchantmentDiscountBonus(storedLevelForEnchant, maxLevelForEnchant);
        return Math.max(0, Math.min(MAX_TOTAL_XP_DISCOUNT_PERCENT, total));
    }

    /** Treasure Epiphany trigger probability incorporating W, global mastery, and item affinity mastery. */
    public static double epiphanyProbability(
        int regularBookshelves,
        int minBookshelves,
        double baseChance,
        double perShelfChance,
        double perMasterUnitChance,
        double maxChance,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double raw = baseChance
            + Math.max(0, regularBookshelves - minBookshelves) * perShelfChance
            + Math.max(0.0, totalMasteryUnits) * perMasterUnitChance
            + Math.max(0.0, affinityMasteryUnits) * AFFINITY_CHANCE_PER_UNIT;
        return Math.max(0.0, Math.min(maxChance, raw));
    }

    /** Combined resonance score used for simultaneous Epiphany slot cap and multi-tier rolls. */
    public static double epiphanyResonanceScore(
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        return Math.max(0, regularBookshelves - minBookshelves)
            + Math.max(0.0, totalMasteryUnits) * GLOBAL_SLOT_WEIGHT
            + Math.max(0.0, affinityMasteryUnits) * AFFINITY_SLOT_WEIGHT;
    }

    /** Maximum simultaneous Treasure Epiphany options on a single item (1..maxSlotsCap). */
    public static int epiphanyMaxSlots(
        int regularBookshelves,
        int minBookshelves,
        int maxSlotsCap,
        int scorePerExtraSlot,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        if (regularBookshelves < minBookshelves) {
            return 0;
        }
        double score = epiphanyResonanceScore(regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
        return Math.min(Math.max(1, maxSlotsCap), 1 + (int) Math.floor(score / Math.max(1, scorePerExtraSlot)));
    }

    /**
     * Deterministically rolls the Epiphany tier in {@code [minTier, maxTier]} for multi-level
     * Treasure enchantments (vanilla or modded), weighted upward by library & affinity mastery.
     */
    public static int rollEpiphanyTier(
        long hashBits,
        int minTier,
        int maxTier,
        double resonanceScore,
        double ownRelativeMastery
    ) {
        int lo = Math.max(1, minTier);
        int hi = Math.max(lo, maxTier);
        if (lo >= hi) {
            return hi;
        }
        double u = ((hashBits >>> 11) * (1.0 / (1L << 53)));
        double upgradeBias = Math.min(0.85, 0.22 + (resonanceScore / 42.0) * 0.48 + ownRelativeMastery * 0.25);
        int tier = lo;
        while (tier < hi && u < upgradeBias) {
            tier++;
            u = u / Math.max(1e-6, upgradeBias);
            upgradeBias *= 0.72;
        }
        return Math.min(hi, tier);
    }
}
