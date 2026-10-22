package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Authoritative Minecraft XP level <-> XP point math shared by server deduction, client tooltips,
 * JEI/REI recipe viewers, and single-source disenchantment conservation verification.
 */
public final class XpCostConversion {
    public record XpRequirement(
        int rawRequiredLevel,
        int requiredLevel,
        int deductedLevels,
        int discountPercent,
        boolean vanillaTierMode,
        int refundPoints
    ) {
        public XpRequirement(
            int rawRequiredLevel,
            int requiredLevel,
            int deductedLevels,
            int discountPercent,
            boolean vanillaTierMode
        ) {
            this(rawRequiredLevel, requiredLevel, deductedLevels, discountPercent, vanillaTierMode, 0);
        }

        public static final XpRequirement FREE = new XpRequirement(0, 0, 0, 0, false, 0);

        public static XpRequirement refund(int points) {
            return new XpRequirement(0, 0, 0, 0, false, Math.max(0, points));
        }

        public boolean isRefund() {
            return refundPoints > 0;
        }

        public boolean isMetBy(Player player) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            if (isRefund() || (requiredLevel <= 0 && deductedLevels <= 0)) {
                return true;
            }
            return player.experienceLevel >= requiredLevel;
        }
    }

    private XpCostConversion() {}

    public static int xpPointsForLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + Math.max(0, level) * 2;
    }

    public static int totalXpForLevel(int level) {
        int clamped = Math.max(0, level);
        int sum = 0;
        for (int l = 0; l < clamped; l++) {
            sum += xpPointsForLevel(l);
        }
        return sum;
    }

    public static int totalPlayerXpPoints(Player player) {
        int base = totalXpForLevel(player.experienceLevel);
        int barPoints = Math.round(player.experienceProgress * (float) xpPointsForLevel(player.experienceLevel));
        return Math.max(0, base + barPoints);
    }

    /**
     * Exact minimum XP points deducted when a player with zero fractional bar at {@code req.requiredLevel()}
     * pays {@code req.deductedLevels()}. Because {@link #xpPointsForLevel(int)} is strictly increasing in
     * level, higher player levels always deduct at least this many XP points.
     */
    public static int minPointsRequired(XpRequirement req) {
        if (req == null || req.isRefund() || req.deductedLevels() <= 0) {
            return 0;
        }
        int startLevel = Math.max(req.deductedLevels(), req.requiredLevel());
        int total = 0;
        for (int i = 0; i < req.deductedLevels(); i++) {
            total += xpPointsForLevel(startLevel - 1 - i);
        }
        return total;
    }

    public static int pointsForLevelCost(Player player, int levelCost) {
        if (levelCost <= 0) {
            return 0;
        }
        int curLvl = Math.max(0, player.experienceLevel);
        int targetLvl = Math.max(0, curLvl - levelCost);
        int total = 0;
        for (int l = targetLvl; l < curLvl; l++) {
            total += xpPointsForLevel(l);
        }
        return total;
    }

    public static void deductXp(Player player, XpRequirement req) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (req.refundPoints() > 0) {
            player.giveExperiencePoints(req.refundPoints());
            return;
        }
        if (req.deductedLevels() <= 0) {
            return;
        }
        int pts = pointsForLevelCost(player, req.deductedLevels());
        if (pts > 0) {
            player.giveExperiencePoints(-pts);
        }
    }

    public static int vanillaTierDeduction(int level, int maxLevel) {
        int clampedMax = Math.max(1, maxLevel);
        if (clampedMax <= 1) {
            return 3;
        }
        double r = (double) Math.max(1, Math.min(level, clampedMax)) / (double) clampedMax;
        if (r <= 0.40) {
            return 1;
        }
        if (r <= 0.75) {
            return 2;
        }
        return 3;
    }

    public static XpRequirement forMasterTomeEnchant(int level, int maxLevel, int rawCost, int discountPercent) {
        int clampedMax = Math.max(1, maxLevel);
        int tierCost = vanillaTierDeduction(level, clampedMax);
        int minThreshold = Math.max(tierCost, (int) Math.round(8.0 + 22.0 * ((double) Math.min(clampedMax, level) / (double) clampedMax)));
        int rawThreshold = rawCost > 0 ? Math.max(minThreshold, rawCost) : minThreshold;
        int clampedDiscount = clampDiscount(discountPercent);
        int requiredThreshold = Math.max(tierCost, (int) Math.round(rawThreshold * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(rawThreshold, requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static XpRequirement forIncrementalMasterTomeEnchant(
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawTargetCost,
        int discountPercent
    ) {
        int clampedMax = Math.max(1, maxLevel);
        int deltaLevels = Math.max(1, targetLevel - Math.max(0, existingLevel));
        int tierCost = vanillaTierDeduction(deltaLevels, clampedMax);
        double credit = LibraryResonance.activeConfig().incrementalStepCreditFactor();
        double targetFraction = (double) Math.min(clampedMax, targetLevel) / (double) clampedMax;
        double prevFraction = (double) Math.min(clampedMax, Math.max(0, existingLevel)) / (double) clampedMax;
        double remainingFraction = Math.max(0.15, targetFraction - credit * prevFraction);
        int minStepThreshold = Math.max(tierCost, (int) Math.round(8.0 + 22.0 * remainingFraction));
        int scaledRaw = rawTargetCost > 0
            ? Math.max(tierCost, (int) Math.round(rawTargetCost * (targetFraction <= 0 ? 1.0 : remainingFraction / targetFraction)))
            : minStepThreshold;
        int rawThreshold = Math.max(minStepThreshold, scaledRaw);
        int clampedDiscount = clampDiscount(discountPercent);
        int requiredThreshold = Math.max(tierCost, (int) Math.round(rawThreshold * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(Math.max(1, rawThreshold), requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static XpRequirement forTargetedCleanse(int level, int maxLevel, int rawCost, int discountPercent) {
        int clampedMax = Math.max(1, maxLevel);
        int tierCost = vanillaTierDeduction(level, clampedMax);
        int baseThreshold = Math.max(tierCost, Math.max(4, rawCost));
        int clampedDiscount = clampDiscount(discountPercent);
        int requiredThreshold = Math.max(tierCost, (int) Math.round(baseThreshold * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(baseThreshold, requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static XpRequirement forFullArcaneRitual(int rawCost, int discountPercent) {
        int clampedRaw = Math.max(0, rawCost);
        if (clampedRaw == 0) {
            return XpRequirement.FREE;
        }
        int clampedDiscount = clampDiscount(discountPercent);
        int deducted = Math.max(1, (int) Math.round(clampedRaw * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(clampedRaw, deducted, deducted, clampedDiscount, false);
    }

    /**
     * Full arcane ritual for a known target enchantment tier {@code (existingLevel -> targetLevel / maxLevel)}
     * without a Master Tome: guarantees non-Master-Tome rituals always require at least as much player XP
     * threshold & level deduction as the Master Tome path, closing low-level upgrade-wipe loops.
     */
    public static XpRequirement forFullArcaneEnchantOrUpgrade(
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawCost,
        int discountPercent
    ) {
        XpRequirement base = forFullArcaneRitual(rawCost, discountPercent);
        if (rawCost <= 0) {
            return XpRequirement.FREE;
        }
        XpRequirement masterFloor = existingLevel > 0
            ? forIncrementalMasterTomeEnchant(existingLevel, targetLevel, maxLevel, 0, discountPercent)
            : forMasterTomeEnchant(targetLevel, maxLevel, 0, discountPercent);
        int deducted = Math.max(base.deductedLevels(), masterFloor.deductedLevels());
        int required = Math.max(deducted, Math.max(base.requiredLevel(), masterFloor.requiredLevel()));
        int rawShown = Math.max(base.rawRequiredLevel(), masterFloor.rawRequiredLevel());
        return new XpRequirement(rawShown, required, deducted, base.discountPercent(), false);
    }

    public static XpRequirement forTransformation(Identifier transformationId, int rawCost, int discountPercent) {
        return forTransformation(
            transformationId,
            rawCost,
            discountPercent,
            ChiseledEnchantingTableData.transformationMinPlayerLevel(transformationId)
        );
    }

    public static XpRequirement forTransformation(
        Identifier transformationId,
        int rawCost,
        int discountPercent,
        int configuredMinPlayerLevel
    ) {
        int clampedRaw = Math.max(0, rawCost);
        if (clampedRaw == 0) {
            return XpRequirement.FREE;
        }
        int clampedDiscount = clampDiscount(discountPercent);
        int minDeducted = Math.max(1, configuredMinPlayerLevel);
        int deducted = Math.max(minDeducted, (int) Math.round(clampedRaw * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(clampedRaw, deducted, deducted, clampedDiscount, false);
    }

    public static int clampDiscount(int discountPercent) {
        int cap = LibraryResonance.activeConfig().maxTotalXpDiscountPercent();
        return Math.max(0, Math.min(cap, discountPercent));
    }
}
