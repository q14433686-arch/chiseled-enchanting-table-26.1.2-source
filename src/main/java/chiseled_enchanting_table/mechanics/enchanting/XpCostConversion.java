package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Dual-mode coexisting XP formula engine shared identically across Server, Client GUI, JEI, and REI:
 *
 * <ul>
 *   <li><b>Vanilla Table Mass-Production Mode ({@code vanillaTierMode = true})</b>:
 *       Used for repeatable production — <b>Item Transformations</b> and <b>Master Tome Inscribing</b>
 *       (when a nearby Chiseled Bookshelf stores {@code storedMasterLevel >= optionLevel}).
 *       Mirrors vanilla's Enchanting Table formula: checks a player level threshold
 *       ({@code requiredLevel}, up to {@code 30}, discounted by Library/Tome Resonance) while
 *       deducting ONLY {@code 1 / 2 / 3} experience levels ({@code player.giveExperienceLevels(-tierCost)}).</li>
 *   <li><b>Arcane Full-Cost Ritual Mode ({@code vanillaTierMode = false})</b>:
 *       Used for non-mass-production breakthroughs — un-enshrined base enchanting, Book Combine/Research
 *       Upgrades, and Treasure Epiphanies. Deducts full experience points along the capped level-30 curve.</li>
 * </ul>
 */
public final class XpCostConversion {
    private static final int MARGINAL_CAP_LEVEL = 30;
    private static final Identifier EXP_BOTTLE_TRANSFORM_ID = ChiseledEnchantingTable.identifier("experience_bottle");

    private XpCostConversion() {}

    /**
     * Complete resolved XP requirement for any row in the Chiseled Enchanting Table.
     *
     * @param vanillaTierMode  true = Vanilla Enchanting Table formula (level threshold + 1..3 levels consumed);
     *                         false = Full-Level Arcane Ritual formula (full level points deducted)
     * @param requiredLevel    minimum {@code player.experienceLevel} needed to execute this row
     * @param deductedLevels   actual XP levels consumed ({@code 1..3} in Vanilla mode; full cost in Arcane mode)
     * @param rawRequiredLevel pre-discount threshold/cost before Library & Master Tome Resonance
     * @param discountPercent  active total XP discount percentage applied ({@code 0..40})
     */
    public record XpRequirement(
        boolean vanillaTierMode,
        int requiredLevel,
        int deductedLevels,
        int rawRequiredLevel,
        int discountPercent,
        int refundPoints
    ) {
        public static final XpRequirement FREE = new XpRequirement(false, 0, 0, 0, 0, 0);

        public XpRequirement(
            boolean vanillaTierMode,
            int requiredLevel,
            int deductedLevels,
            int rawRequiredLevel,
            int discountPercent
        ) {
            this(vanillaTierMode, requiredLevel, deductedLevels, rawRequiredLevel, discountPercent, 0);
        }

        public static XpRequirement refund(int xpPoints) {
            return new XpRequirement(false, 0, 0, 0, 0, Math.max(0, xpPoints));
        }

        public boolean isRefund() {
            return refundPoints > 0;
        }

        public boolean isMetBy(Player player) {
            if (refundPoints > 0 || (requiredLevel <= 0 && deductedLevels <= 0)) {
                return true;
            }
            return player.getAbilities().instabuild || player.experienceLevel >= requiredLevel;
        }
    }

    /**
     * Resolves Vanilla Enchanting Table tier cost ({@code 1..3} levels) and player level threshold
     * ({@code 1..30} before discount) for Master Tome inscribing of ANY vanilla or modded enchantment
     * with arbitrary {@code maxLevel >= 1}.
     */
    public static XpRequirement forMasterTomeEnchant(
        int level,
        int maxLevel,
        int rawBaseXpCost,
        int discountPercent
    ) {
        int safeMax = Math.max(1, maxLevel);
        int safeLvl = Math.clamp(level, 1, safeMax);
        int tierCost;
        int rawThreshold;
        if (safeMax == 1) {
            tierCost = rawBaseXpCost >= 10 ? 3 : 2;
            rawThreshold = tierCost == 3 ? 30 : 20;
        } else {
            double r = (double) safeLvl / (double) safeMax;
            tierCost = r <= 0.4001 ? 1 : (r < 0.7999 ? 2 : 3);
            rawThreshold = Math.clamp(Math.round(30.0f * (float) r), tierCost, 30);
        }
        int clampedDisc = Math.clamp(discountPercent, 0, 40);
        int effectiveThreshold = clampedDisc > 0
            ? Math.max(tierCost, Math.round(rawThreshold * (100 - clampedDisc) / 100.0f))
            : rawThreshold;
        return new XpRequirement(true, effectiveThreshold, tierCost, rawThreshold, clampedDisc);
    }

    /**
     * Resolves incremental Master Tome XP requirement when upgrading an item or book that already
     * carries {@code existingLevel >= 1} up to {@code targetLevel > existingLevel}: reduces both
     * deducted levels ({@code 1..2} levels by relative delta) and threshold proportionally.
     */
    public static XpRequirement forIncrementalMasterTomeEnchant(
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawBaseXpCost,
        int discountPercent
    ) {
        XpRequirement full = forMasterTomeEnchant(targetLevel, maxLevel, rawBaseXpCost, 0);
        int safeMax = Math.max(1, maxLevel);
        int delta = Math.max(1, targetLevel - existingLevel);
        double deltaRatio = (double) delta / (double) safeMax;
        int tierCost = deltaRatio <= 0.5001 ? 1 : 2;
        float progressCredit = Math.clamp((float) existingLevel / (float) Math.max(1, targetLevel), 0.0f, 0.85f);
        int rawThreshold = Math.max(tierCost, Math.round(full.rawRequiredLevel() * (1.0f - 0.35f * progressCredit)));
        int clampedDisc = Math.clamp(discountPercent, 0, 40);
        int effectiveThreshold = clampedDisc > 0
            ? Math.max(tierCost, Math.round(rawThreshold * (100 - clampedDisc) / 100.0f))
            : rawThreshold;
        return new XpRequirement(true, effectiveThreshold, tierCost, rawThreshold, clampedDisc);
    }

    /**
     * Resolves Vanilla-style light surgical XP cost for stripping a single non-curse enchantment
     * while preserving all other enchantments on the item ({@code 1..2} levels deducted).
     */
    public static XpRequirement forTargetedCleanse(
        int level,
        int maxLevel,
        int rawThreshold,
        int discountPercent
    ) {
        if (rawThreshold <= 0) {
            return XpRequirement.FREE;
        }
        double r = Math.clamp((double) level / (double) Math.max(1, maxLevel), 0.0, 1.0);
        int tierCost = r <= 0.55 ? 1 : 2;
        int baseThreshold = Math.clamp(rawThreshold, tierCost, 30);
        int clampedDisc = Math.clamp(discountPercent, 0, 40);
        int effectiveThreshold = clampedDisc > 0
            ? Math.max(tierCost, Math.round(baseThreshold * (100 - clampedDisc) / 100.0f))
            : baseThreshold;
        return new XpRequirement(true, effectiveThreshold, tierCost, baseThreshold, clampedDisc);
    }

    /**
     * Resolves Vanilla Enchanting Table tier cost ({@code 1..3} levels) and player level threshold
     * for any built-in or datapack Item Transformation.
     */
    public static XpRequirement forTransformation(
        Identifier transformationId,
        int rawXpCost,
        int discountPercent
    ) {
        if (rawXpCost <= 0) {
            return XpRequirement.FREE;
        }
        int tierCost;
        int rawThreshold;
        int minDiscountedFloor;
        if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(transformationId)) {
            tierCost = 1;
            rawThreshold = 1;
            minDiscountedFloor = 1;
        } else {
            tierCost = rawXpCost <= 2 ? 1 : (rawXpCost <= 8 ? 2 : 3);
            rawThreshold = Math.clamp(Math.round(rawXpCost * 2.5f), tierCost, 30);
            // Guarantee Bottle o' Enchanting never drops below Lv.3 (11 XP >= max 11 XP drop).
            minDiscountedFloor = EXP_BOTTLE_TRANSFORM_ID.equals(transformationId) ? Math.max(tierCost, 3) : tierCost;
        }
        int clampedDisc = Math.clamp(discountPercent, 0, 40);
        int effectiveThreshold = clampedDisc > 0
            ? Math.max(minDiscountedFloor, Math.round(rawThreshold * (100 - clampedDisc) / 100.0f))
            : rawThreshold;
        return new XpRequirement(true, effectiveThreshold, tierCost, rawThreshold, clampedDisc);
    }

    /**
     * Resolves Full-Level Arcane Ritual cost for un-enshrined base enchants, book upgrades, and epiphanies.
     */
    public static XpRequirement forFullArcaneRitual(int rawXpCost, int discountPercent) {
        if (rawXpCost <= 0) {
            return XpRequirement.FREE;
        }
        int clampedDisc = Math.clamp(discountPercent, 0, 40);
        int effective = clampedDisc > 0
            ? Math.max(1, Math.round(rawXpCost * (100 - clampedDisc) / 100.0f))
            : rawXpCost;
        return new XpRequirement(false, effective, effective, rawXpCost, clampedDisc);
    }

    /**
     * Deducts the resolved XP requirement from {@code player} on the server:
     * <ul>
     *   <li>{@code vanillaTierMode == true}: subtracts {@code 1..3} levels cleanly via
     *       {@code player.giveExperienceLevels(-deductedLevels)}, preserving bar % progress.</li>
     *   <li>{@code vanillaTierMode == false}: subtracts full XP points along the capped curve.</li>
     * </ul>
     */
    public static void deductXp(Player player, XpRequirement requirement) {
        if (player.getAbilities().instabuild || requirement.deductedLevels() <= 0) {
            return;
        }
        if (requirement.vanillaTierMode()) {
            player.giveExperienceLevels(-requirement.deductedLevels());
        } else {
            int xpPointsToDeduct = pointsForLevelCost(player, requirement.deductedLevels());
            player.giveExperiencePoints(-xpPointsToDeduct);
        }
    }

    public static int pointsForLevelCost(Player player, int levelCost) {
        if (levelCost <= 0) return 0;
        int actualStart = player.experienceLevel;
        if (actualStart <= 0) return 0;

        int effectiveStart = Math.min(actualStart, MARGINAL_CAP_LEVEL);
        int effectiveCost = Math.min(levelCost, actualStart);
        int effectiveEnd = Math.max(0, effectiveStart - effectiveCost);

        float progress = player.experienceProgress;
        long startPartial = Math.round(progress * xpNeededToReachNextLevelFrom(effectiveStart));
        long endPartial = Math.round(progress * xpNeededToReachNextLevelFrom(effectiveEnd));
        long points = startPartial - endPartial;

        // If cost exceeds 30 while capped (e.g., datapack custom cost > 30), charge remaining levels at lv0.. cap floor.
        for (int i = 0; i < effectiveCost; i++) {
            int barLevel = Math.max(0, effectiveStart - 1 - i);
            points += xpNeededToReachNextLevelFrom(barLevel);
        }

        if (points <= 0L) return 1;
        if (points > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) points;
    }

    private static int xpNeededToReachNextLevelFrom(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
}
