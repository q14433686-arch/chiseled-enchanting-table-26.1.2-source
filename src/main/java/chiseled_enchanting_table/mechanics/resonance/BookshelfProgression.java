package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/**
 * Unified server/client progression rules driven by surrounding bookshelves within the 7x5x7 scan volume:
 *
 * <ol>
 *   <li><b>Direct Category Tier Unlock ({@code 0 .. 38} Regular Bookshelves)</b>:
 *       Unlocks non-Master-Tome category upgrades progressively from {@code Level I} ({@code 0} shelves)
 *       across strictly increasing integer bookshelf thresholds up to {@code MAX} at {@code 38} regular
 *       bookshelves, while keeping the Chiseled Bookshelf Master Tome &amp; two-book Combine routes
 *       completely independent and untouched.</li>
 *   <li><b>Three-Gradient Depleted Tome ({@code 褪魔残卷 -> 书}) Progression</b>:
 *       <ul>
 *         <li>{@code < 8} regular bookshelves: baseline XP + full materials.</li>
 *         <li><b>Gradient I</b> ({@code 8 .. 17} regular bookshelves): waives all XP loss ({@code 0 XP}), full materials.</li>
 *         <li><b>Gradient II</b> ({@code >= 18} regular bookshelves, {@code < 6} {@code MAX / MAX-1} books in Chiseled Bookshelves):
 *             waives XP loss + discounts required material counts ({@code floor(count * 0.50)}, minimum {@code 1}).</li>
 *         <li><b>Gradient III</b> ({@code >= 18} regular bookshelves <b>AND</b> {@code >= 6} {@code MAX / MAX-1} Enchanted Books
 *             stored in nearby Chiseled Bookshelves): <b>completely free</b> ({@code 0 XP} + {@code 0 materials})!</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class BookshelfProgression {
    public static final int MAX_DIRECT_UPGRADE_BOOKSHELVES = 38;
    public static final double DIRECT_TIER_CURVE_EXPONENT = 1.11;

    public static final int DEPLETED_TOME_TIER1_BOOKSHELVES = 8;
    public static final int DEPLETED_TOME_TIER2_BOOKSHELVES = 18;
    public static final int DEPLETED_TOME_TIER3_HIGH_BOOKS = 6;
    public static final double DEPLETED_TOME_TIER2_MATERIAL_MULTIPLIER = 0.50;

    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    public enum DepletedTomeTier {
        BASELINE,
        TIER_1_NO_XP,
        TIER_2_DISCOUNT_MAT,
        TIER_3_FREE
    }

    private BookshelfProgression() {}

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
    public static int requiredBookshelvesForDirectTier(int targetLevel, int maxLevel) {
        int safeMax = Math.max(1, maxLevel);
        int safeTarget = Math.clamp(targetLevel, 1, safeMax);
        if (safeMax <= 1 || safeTarget <= 1) {
            return 0;
        }
        if (safeTarget >= safeMax) {
            return MAX_DIRECT_UPGRADE_BOOKSHELVES;
        }
        double progress = (double) (safeTarget - 1) / (double) (safeMax - 1);
        int raw = (int) Math.round(MAX_DIRECT_UPGRADE_BOOKSHELVES * Math.pow(progress, DIRECT_TIER_CURVE_EXPONENT));
        int minAllowed = Math.min(MAX_DIRECT_UPGRADE_BOOKSHELVES - 1, safeTarget - 1);
        int maxAllowed = Math.max(1, MAX_DIRECT_UPGRADE_BOOKSHELVES - (safeMax - safeTarget));
        return Math.clamp(raw, Math.min(minAllowed, maxAllowed), maxAllowed);
    }

    /**
     * Highest level in {@code [1 .. maxLevel]} unlocked directly by {@code regularBookshelves}
     * regular wooden bookshelves (without requiring a Master Tome in a Chiseled Bookshelf).
     */
    public static int maxUnlockedDirectTier(int maxLevel, int regularBookshelves) {
        int safeMax = Math.max(1, maxLevel);
        int safeShelves = Math.max(0, regularBookshelves);
        for (int lvl = safeMax; lvl >= 1; lvl--) {
            if (safeShelves >= requiredBookshelvesForDirectTier(lvl, safeMax)) {
                return lvl;
            }
        }
        return 1;
    }

    /**
     * Effective maximum unlocked tier combining both independent routes:
     * {@code max(storedMasterLevel, maxUnlockedDirectTier(maxLevel, regularBookshelves))}.
     */
    public static int effectiveUnlockedTierCap(int maxLevel, int regularBookshelves, int storedMasterLevel) {
        int safeMax = Math.max(1, maxLevel);
        int directCap = maxUnlockedDirectTier(safeMax, regularBookshelves);
        return Math.min(safeMax, Math.max(Math.max(0, storedMasterLevel), directCap));
    }

    /**
     * Resolves which of the three Depleted Tome upgrade gradients (or baseline) is active.
     */
    public static DepletedTomeTier resolveDepletedTomeTier(int regularBookshelves, int highTierChiseledBooks) {
        int shelves = Math.max(0, regularBookshelves);
        int highBooks = Math.max(0, highTierChiseledBooks);
        if (shelves >= DEPLETED_TOME_TIER2_BOOKSHELVES) {
            return highBooks >= DEPLETED_TOME_TIER3_HIGH_BOOKS
                ? DepletedTomeTier.TIER_3_FREE
                : DepletedTomeTier.TIER_2_DISCOUNT_MAT;
        }
        if (shelves >= DEPLETED_TOME_TIER1_BOOKSHELVES) {
            return DepletedTomeTier.TIER_1_NO_XP;
        }
        return DepletedTomeTier.BASELINE;
    }

    /**
     * Returns {@code true} if {@code stack} is an Enchanted Book carrying at least one non-curse stored
     * enchantment at {@code MAX} or {@code MAX - 1} level ({@code level >= max(1, maxLevel - 1)}).
     */
    public static boolean isQualifyingHighTierBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        for (var entry : stored.entrySet()) {
            if (entry.getKey().is(CURSE_TAG)) {
                continue;
            }
            int maxLvl = Math.max(1, entry.getKey().value().getMaxLevel());
            int minQualifying = Math.max(1, maxLvl - 1);
            if (entry.getIntValue() >= minQualifying) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts how many individual book slots inside {@code cbsbe} contain a {@code MAX} or {@code MAX - 1}
     * Enchanted Book.
     */
    public static int countHighTierBooksInShelf(ChiseledBookShelfBlockEntity cbsbe) {
        int count = 0;
        for (int slot = 0; slot < cbsbe.getContainerSize(); slot++) {
            if (isQualifyingHighTierBook(cbsbe.getItem(slot))) {
                count++;
            }
        }
        return count;
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

    /**
     * Applies the active three-gradient material rule to {@code chiseled_enchanting_table:blank_overwrite}.
     */
    public static List<List<ItemStack>> applyDepletedTomeMaterialTier(
        List<List<ItemStack>> baseBundles,
        DepletedTomeTier tier
    ) {
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
                int reducedCount = Math.max(1, (int) Math.floor(copy.getCount() * DEPLETED_TOME_TIER2_MATERIAL_MULTIPLIER));
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
