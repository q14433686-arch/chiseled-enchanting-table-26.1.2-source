package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Unified server/client progression rules driven by surrounding bookshelves within the 7x5x7 scan volume:
 *
 * <ol>
 *   <li><b>Direct Category Tier Unlock ({@code 0 .. 38} Regular Bookshelves)</b>:
 *       Unlocks non-Master-Tome category upgrades progressively from {@code Level I} ({@code 0} shelves)
 *       across strictly increasing integer bookshelf thresholds up to {@code MAX} at {@code 38} regular
 *       bookshelves (configurable in {@code shelf_gates.json}), while keeping the Chiseled Bookshelf
 *       Master Tome &amp; two-book Combine routes completely independent and untouched.</li>
 *   <li><b>Three-Gradient Depleted Tome ({@code 褪魔残卷 -> 书}) Progression</b>:
 *       <ul>
 *         <li>{@code < 8} regular bookshelves: baseline XP + full materials.</li>
 *         <li><b>Gradient I</b> ({@code 8 .. 17} regular bookshelves): waives all XP loss ({@code 0 XP}), full materials.</li>
 *         <li><b>Gradient II</b> ({@code >= 18} regular bookshelves, {@code < 6} qualifying {@code MAX / MAX-1}
 *             stored enchantments in Chiseled Bookshelves): waives XP loss + discounts required material counts
 *             ({@code floor(count * 0.50)}, minimum {@code 1}).</li>
 *         <li><b>Gradient III</b> ({@code >= 18} regular bookshelves <b>AND</b> {@code >= 6} qualifying
 *             {@code MAX / MAX-1} non-curse enchantments across all books stored in nearby Chiseled Bookshelves):
 *             <b>completely free</b> ({@code 0 XP} + {@code 0 materials})!</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class BookshelfProgression {
    public static final int MAX_DIRECT_UPGRADE_BOOKSHELVES = 38;
    public static final double DIRECT_TIER_CURVE_EXPONENT = 1.11;

    public static final int DEPLETED_TOME_TIER1_BOOKSHELVES = 8;
    public static final int DEPLETED_TOME_TIER2_BOOKSHELVES = 18;
    public static final int DEPLETED_TOME_TIER3_QUALIFYING_ENCHANTS = 6;
    /** Kept as alias constant for compatibility with callers. */
    public static final int DEPLETED_TOME_TIER3_HIGH_BOOKS = DEPLETED_TOME_TIER3_QUALIFYING_ENCHANTS;
    public static final double DEPLETED_TOME_TIER2_MATERIAL_MULTIPLIER = 0.50;

    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    public enum DepletedTomeTier {
        BASELINE,
        TIER_1_NO_XP,
        TIER_2_DISCOUNT_MAT,
        TIER_3_FREE
    }

    /**
     * Immutable data-driven shelf progression config loaded from {@code shelf_gates.json} and synced S2C.
     */
    public record Config(
        boolean directGatesEnabled,
        int maxDirectBookshelves,
        double gamma,
        Map<Identifier, List<Integer>> overrides,
        int tier1Bookshelves,
        int tier2Bookshelves,
        double tier2MaterialMultiplier,
        int tier3QualifyingEnchantments
    ) {
        public static final Config DEFAULT = new Config(
            true,
            MAX_DIRECT_UPGRADE_BOOKSHELVES,
            DIRECT_TIER_CURVE_EXPONENT,
            Map.of(),
            DEPLETED_TOME_TIER1_BOOKSHELVES,
            DEPLETED_TOME_TIER2_BOOKSHELVES,
            DEPLETED_TOME_TIER2_MATERIAL_MULTIPLIER,
            DEPLETED_TOME_TIER3_QUALIFYING_ENCHANTS
        );

        public Config {
            maxDirectBookshelves = Math.clamp(maxDirectBookshelves, 1, 256);
            gamma = Math.clamp(gamma, 0.25, 3.0);
            overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
            tier1Bookshelves = Math.max(1, tier1Bookshelves);
            tier2Bookshelves = Math.max(tier1Bookshelves, tier2Bookshelves);
            tier2MaterialMultiplier = Math.clamp(tier2MaterialMultiplier, 0.05, 1.0);
            tier3QualifyingEnchantments = Math.max(1, tier3QualifyingEnchantments);
        }
    }

    private BookshelfProgression() {}

    public static int requiredBookshelvesForDirectTier(int targetLevel, int maxLevel) {
        return requiredBookshelvesForDirectTier(Config.DEFAULT, null, targetLevel, maxLevel);
    }

    /**
     * Exact integer regular-bookshelf count required to unlock {@code targetLevel} directly for an
     * enchantment whose maximum level is {@code maxLevel}:
     * <ul>
     *   <li>{@code maxLevel == 1}: {@code I -> 0}</li>
     *   <li>{@code maxLevel == 2}: {@code I -> 0, II -> 38}</li>
     *   <li>{@code maxLevel == 3}: {@code I -> 0, II -> 18, III -> 38}</li>
     *   <li>{@code maxLevel == 4}: {@code I -> 0, II -> 11, III -> 24, IV -> 38}</li>
     *   <li>{@code maxLevel == 5}: {@code I -> 0, II -> 8, III -> 18, IV -> 28, V -> 38}</li>
     * </ul>
     */
    public static int requiredBookshelvesForDirectTier(
        Config cfg,
        @Nullable Identifier enchantmentId,
        int targetLevel,
        int maxLevel
    ) {
        Config safeCfg = cfg == null ? Config.DEFAULT : cfg;
        if (!safeCfg.directGatesEnabled()) {
            return 0;
        }
        int safeMax = Math.max(1, maxLevel);
        int safeTarget = Math.clamp(targetLevel, 1, safeMax);
        if (enchantmentId != null) {
            List<Integer> custom = safeCfg.overrides().get(enchantmentId);
            if (custom != null && !custom.isEmpty()) {
                int idx = Math.min(custom.size() - 1, safeTarget - 1);
                return Math.max(0, custom.get(idx));
            }
        }
        int maxAnchor = safeCfg.maxDirectBookshelves();
        if (safeMax <= 1 || safeTarget <= 1) {
            return 0;
        }
        if (safeTarget >= safeMax) {
            return maxAnchor;
        }
        double progress = (double) (safeTarget - 1) / (double) (safeMax - 1);
        int raw = (int) Math.round(maxAnchor * Math.pow(progress, safeCfg.gamma()));
        int minAllowed = Math.min(maxAnchor - 1, safeTarget - 1);
        int maxAllowed = Math.max(1, maxAnchor - (safeMax - safeTarget));
        return Math.clamp(raw, Math.min(minAllowed, maxAllowed), maxAllowed);
    }

    public static int maxUnlockedDirectTier(int maxLevel, int regularBookshelves) {
        return maxUnlockedDirectTier(Config.DEFAULT, null, maxLevel, regularBookshelves);
    }

    /**
     * Highest level in {@code [1 .. maxLevel]} unlocked directly by {@code regularBookshelves}
     * regular wooden bookshelves (without requiring a Master Tome in a Chiseled Bookshelf).
     */
    public static int maxUnlockedDirectTier(
        Config cfg,
        @Nullable Identifier enchantmentId,
        int maxLevel,
        int regularBookshelves
    ) {
        int safeMax = Math.max(1, maxLevel);
        int safeShelves = Math.max(0, regularBookshelves);
        for (int lvl = safeMax; lvl >= 1; lvl--) {
            if (safeShelves >= requiredBookshelvesForDirectTier(cfg, enchantmentId, lvl, safeMax)) {
                return lvl;
            }
        }
        return 1;
    }

    public static int effectiveUnlockedTierCap(int maxLevel, int regularBookshelves, int storedMasterLevel) {
        return effectiveUnlockedTierCap(Config.DEFAULT, null, maxLevel, regularBookshelves, storedMasterLevel);
    }

    /**
     * Effective maximum unlocked tier combining both independent routes:
     * {@code max(storedMasterLevel, maxUnlockedDirectTier(cfg, enchantmentId, maxLevel, regularBookshelves))}.
     */
    public static int effectiveUnlockedTierCap(
        Config cfg,
        @Nullable Identifier enchantmentId,
        int maxLevel,
        int regularBookshelves,
        int storedMasterLevel
    ) {
        int safeMax = Math.max(1, maxLevel);
        int directCap = maxUnlockedDirectTier(cfg, enchantmentId, safeMax, regularBookshelves);
        return Math.min(safeMax, Math.max(Math.max(0, storedMasterLevel), directCap));
    }

    public static DepletedTomeTier resolveDepletedTomeTier(int regularBookshelves, int qualifyingHighTierEnchantments) {
        return resolveDepletedTomeTier(Config.DEFAULT, regularBookshelves, qualifyingHighTierEnchantments);
    }

    /**
     * Resolves which of the three Depleted Tome upgrade gradients (or baseline) is active based on
     * regular bookshelf count and the total number of qualifying {@code MAX / MAX-1} stored enchantments
     * across nearby Chiseled Bookshelves.
     */
    public static DepletedTomeTier resolveDepletedTomeTier(
        Config cfg,
        int regularBookshelves,
        int qualifyingHighTierEnchantments
    ) {
        Config safeCfg = cfg == null ? Config.DEFAULT : cfg;
        int shelves = Math.max(0, regularBookshelves);
        int qualifyingEnchants = Math.max(0, qualifyingHighTierEnchantments);
        if (shelves >= safeCfg.tier2Bookshelves()) {
            return qualifyingEnchants >= safeCfg.tier3QualifyingEnchantments()
                ? DepletedTomeTier.TIER_3_FREE
                : DepletedTomeTier.TIER_2_DISCOUNT_MAT;
        }
        if (shelves >= safeCfg.tier1Bookshelves()) {
            return DepletedTomeTier.TIER_1_NO_XP;
        }
        return DepletedTomeTier.BASELINE;
    }

    /**
     * Counts the total number of qualifying non-curse stored enchantments on {@code stack} whose
     * level satisfies {@code level >= max(1, maxLevel - 1)} ({@code MAX} or {@code MAX - 1}).
     *
     * <p>Crucially, this evaluates <b>every stored enchantment entry</b> on the book rather than
     * judging by item rarity or collapsing a multi-enchant Master Tome down to {@code 1} book: a
     * book storing 4 {@code MAX / MAX-1} enchantments contributes all {@code 4} qualifying entries,
     * while low-tier enchantments ({@code < MAX - 1}) and curses contribute {@code 0}.</p>
     */
    public static int countQualifyingHighTierEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        int qualifyingCount = 0;
        for (var entry : stored.entrySet()) {
            if (entry.getKey().is(CURSE_TAG)) {
                continue;
            }
            int maxLvl = Math.max(1, entry.getKey().value().getMaxLevel());
            int minQualifying = Math.max(1, maxLvl - 1);
            if (entry.getIntValue() >= minQualifying) {
                qualifyingCount++;
            }
        }
        return qualifyingCount;
    }

    /**
     * Returns {@code true} if {@code stack} carries at least one qualifying {@code MAX / MAX - 1}
     * non-curse stored enchantment.
     */
    public static boolean isQualifyingHighTierBook(ItemStack stack) {
        return countQualifyingHighTierEnchantments(stack) > 0;
    }

    /**
     * Sums the total number of qualifying {@code MAX} / {@code MAX - 1} non-curse stored enchantments
     * across all books placed inside {@code cbsbe}.
     */
    public static int countQualifyingHighTierEnchantmentsInShelf(ChiseledBookShelfBlockEntity cbsbe) {
        int count = 0;
        for (int slot = 0; slot < cbsbe.getContainerSize(); slot++) {
            count += countQualifyingHighTierEnchantments(cbsbe.getItem(slot));
        }
        return count;
    }

    /** Alias forwarding to {@link #countQualifyingHighTierEnchantmentsInShelf(ChiseledBookShelfBlockEntity)}. */
    public static int countHighTierBooksInShelf(ChiseledBookShelfBlockEntity cbsbe) {
        return countQualifyingHighTierEnchantmentsInShelf(cbsbe);
    }

    /**
     * Applies the active three-gradient XP rule to {@code chiseled_enchanting_table:blank_overwrite}.
     */
    public static XpRequirement applyDepletedTomeXpTier(XpRequirement baseRequirement, DepletedTomeTier tier) {
        return switch (tier) {
            case TIER_1_NO_XP, TIER_2_DISCOUNT_MAT, TIER_3_FREE -> XpRequirement.FREE;
            case BASELINE -> baseRequirement;
        };
    }

    public static List<List<ItemStack>> applyDepletedTomeMaterialTier(
        List<List<ItemStack>> baseBundles,
        DepletedTomeTier tier
    ) {
        return applyDepletedTomeMaterialTier(Config.DEFAULT, baseBundles, tier);
    }

    /**
     * Applies the active three-gradient material rule to {@code chiseled_enchanting_table:blank_overwrite}.
     */
    public static List<List<ItemStack>> applyDepletedTomeMaterialTier(
        Config cfg,
        List<List<ItemStack>> baseBundles,
        DepletedTomeTier tier
    ) {
        Config safeCfg = cfg == null ? Config.DEFAULT : cfg;
        if (tier == DepletedTomeTier.TIER_3_FREE) {
            return List.of();
        }
        if (tier != DepletedTomeTier.TIER_2_DISCOUNT_MAT || baseBundles == null || baseBundles.isEmpty()) {
            return baseBundles == null ? List.of() : baseBundles;
        }
        List<List<ItemStack>> out = new ArrayList<>(baseBundles.size());
        for (List<ItemStack> bundle : baseBundles) {
            List<ItemStack> discounted = new ArrayList<>(bundle.size());
            for (ItemStack st : bundle) {
                ItemStack copy = st.copy();
                int reducedCount = Math.max(1, (int) Math.floor(copy.getCount() * safeCfg.tier2MaterialMultiplier()));
                copy.setCount(reducedCount);
                discounted.add(copy);
            }
            if (!discounted.isEmpty()) {
                out.add(List.copyOf(discounted));
            }
        }
        return List.copyOf(out);
    }
}
