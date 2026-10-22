package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Authoritative Minecraft XP level <-> XP point math shared by server deduction, client tooltips,
 * JEI/REI recipe viewers, and single-source disenchantment conservation verification.
 *
 * <p>All methods accept an explicit {@link LibraryResonance.Config} so dedicated-server datapack
 * overrides synced via {@code SyncedEnchantmentCosts} match server calculations 1:1 on clients.</p>
 */
public final class XpCostConversion {
    public record XpRequirement(
        int rawRequiredLevel,
        int requiredLevel,
        int deductedLevels,
        int discountPercent,
        boolean vanillaTierMode,
        int refundPoints,
        boolean refundMode
    ) {
        public XpRequirement(
            int rawRequiredLevel,
            int requiredLevel,
            int deductedLevels,
            int discountPercent,
            boolean vanillaTierMode,
            int refundPoints
        ) {
            this(rawRequiredLevel, requiredLevel, deductedLevels, discountPercent, vanillaTierMode, refundPoints, refundPoints > 0);
        }

        public XpRequirement(
            int rawRequiredLevel,
            int requiredLevel,
            int deductedLevels,
            int discountPercent,
            boolean vanillaTierMode
        ) {
            this(rawRequiredLevel, requiredLevel, deductedLevels, discountPercent, vanillaTierMode, 0, false);
        }

        public static final XpRequirement FREE = new XpRequirement(0, 0, 0, 0, false, 0, false);

        public static XpRequirement refund(int points) {
            return new XpRequirement(0, 0, 0, 0, false, Math.max(0, points), true);
        }

        public boolean isRefund() {
            return refundMode || refundPoints > 0;
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
     * Exact minimum XP points deducted when a player at {@code req.requiredLevel()} pays
     * {@code req.deductedLevels()}. Because {@link #xpPointsForLevel(int)} is monotonically
     * non-decreasing in level, both {@code player.giveExperienceLevels(-d)} (which preserves fractional
     * bar percentage {@code p} and therefore removes {@code sum W(j) + p*(W(L) - W(L-d))}) AND
     * {@link #pointsForLevelCost(Player, int)} ALWAYS deduct at least this many XP points for every
     * {@code L >= req.requiredLevel()} and every {@code p in [0, 1)}.
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
        if (req.isRefund()) {
            if (req.refundPoints() > 0) {
                player.giveExperiencePoints(req.refundPoints());
            }
            return;
        }
        if (req.deductedLevels() <= 0) {
            return;
        }
        if (req.vanillaTierMode()) {
            // Genuine vanilla mass-production behavior: subtracts exact whole levels while preserving
            // fractional experience bar percentage (e.g. Lv.30 @ 99% -> Lv.29 @ 99%).
            player.giveExperienceLevels(-req.deductedLevels());
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
        return forMasterTomeEnchant(LibraryResonance.activeServerConfig(), level, maxLevel, rawCost, discountPercent);
    }

    public static XpRequirement forMasterTomeEnchant(
        LibraryResonance.Config resonanceCfg,
        int level,
        int maxLevel,
        int rawCost,
        int discountPercent
    ) {
        int clampedMax = Math.max(1, maxLevel);
        int tierCost = vanillaTierDeduction(level, clampedMax);
        int minThreshold = Math.max(tierCost, (int) Math.round(8.0 + 22.0 * ((double) Math.min(clampedMax, level) / (double) clampedMax)));
        int rawThreshold = rawCost > 0 ? Math.max(minThreshold, rawCost) : minThreshold;
        int clampedDiscount = clampDiscount(resonanceCfg, discountPercent);
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
        return forIncrementalMasterTomeEnchant(
            LibraryResonance.activeServerConfig(),
            existingLevel,
            targetLevel,
            maxLevel,
            rawTargetCost,
            discountPercent
        );
    }

    public static XpRequirement forIncrementalMasterTomeEnchant(
        LibraryResonance.Config resonanceCfg,
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawTargetCost,
        int discountPercent
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        int clampedMax = Math.max(1, maxLevel);
        int deltaLevels = Math.max(1, targetLevel - Math.max(0, existingLevel));
        int tierCost = vanillaTierDeduction(deltaLevels, clampedMax);
        double credit = cfg.incrementalStepCreditFactor();
        double targetFraction = (double) Math.min(clampedMax, targetLevel) / (double) clampedMax;
        double prevFraction = (double) Math.min(clampedMax, Math.max(0, existingLevel)) / (double) clampedMax;
        double remainingFraction = Math.max(0.15, targetFraction - credit * prevFraction);
        int minStepThreshold = Math.max(tierCost, (int) Math.round(8.0 + 22.0 * remainingFraction));
        int scaledRaw = rawTargetCost > 0
            ? Math.max(tierCost, (int) Math.round(rawTargetCost * (targetFraction <= 0 ? 1.0 : remainingFraction / targetFraction)))
            : minStepThreshold;
        int rawThreshold = Math.max(minStepThreshold, scaledRaw);
        int clampedDiscount = clampDiscount(cfg, discountPercent);
        int requiredThreshold = Math.max(tierCost, (int) Math.round(rawThreshold * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(Math.max(1, rawThreshold), requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static XpRequirement forTargetedCleanse(int level, int maxLevel, int rawCost, int discountPercent) {
        return forTargetedCleanse(LibraryResonance.activeServerConfig(), level, maxLevel, rawCost, discountPercent);
    }

    public static XpRequirement forTargetedCleanse(
        LibraryResonance.Config resonanceCfg,
        int level,
        int maxLevel,
        int rawCost,
        int discountPercent
    ) {
        int clampedMax = Math.max(1, maxLevel);
        int tierCost = vanillaTierDeduction(level, clampedMax);
        int baseThreshold = Math.max(tierCost, Math.max(4, rawCost));
        int clampedDiscount = clampDiscount(resonanceCfg, discountPercent);
        int requiredThreshold = Math.max(tierCost, (int) Math.round(baseThreshold * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(baseThreshold, requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static XpRequirement forFullArcaneRitual(int rawCost, int discountPercent) {
        return forFullArcaneRitual(LibraryResonance.activeServerConfig(), rawCost, discountPercent);
    }

    public static XpRequirement forFullArcaneRitual(
        LibraryResonance.Config resonanceCfg,
        int rawCost,
        int discountPercent
    ) {
        int clampedRaw = Math.max(0, rawCost);
        if (clampedRaw == 0) {
            return XpRequirement.FREE;
        }
        int clampedDiscount = clampDiscount(resonanceCfg, discountPercent);
        int deducted = Math.max(1, (int) Math.round(clampedRaw * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(clampedRaw, deducted, deducted, clampedDiscount, false);
    }

    public static XpRequirement forFullArcaneEnchantOrUpgrade(
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawCost,
        int discountPercent
    ) {
        return forFullArcaneEnchantOrUpgrade(
            LibraryResonance.activeServerConfig(),
            existingLevel,
            targetLevel,
            maxLevel,
            rawCost,
            discountPercent
        );
    }

    public static XpRequirement forFullArcaneEnchantOrUpgrade(
        LibraryResonance.Config resonanceCfg,
        int existingLevel,
        int targetLevel,
        int maxLevel,
        int rawCost,
        int discountPercent
    ) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        XpRequirement base = forFullArcaneRitual(cfg, rawCost, discountPercent);
        if (rawCost <= 0) {
            return XpRequirement.FREE;
        }
        XpRequirement masterFloor = existingLevel > 0
            ? forIncrementalMasterTomeEnchant(cfg, existingLevel, targetLevel, maxLevel, 0, discountPercent)
            : forMasterTomeEnchant(cfg, targetLevel, maxLevel, 0, discountPercent);
        int deducted = Math.max(base.deductedLevels(), masterFloor.deductedLevels());
        int required = Math.max(deducted, Math.max(base.requiredLevel(), masterFloor.requiredLevel()));
        int rawShown = Math.max(base.rawRequiredLevel(), masterFloor.rawRequiredLevel());
        return new XpRequirement(rawShown, required, deducted, base.discountPercent(), false);
    }

    public static XpRequirement forTransformation(Identifier transformationId, int rawCost, int discountPercent) {
        return forTransformation(
            LibraryResonance.activeServerConfig(),
            transformationId,
            rawCost,
            discountPercent,
            ChiseledEnchantingTableData.transformationMinRequiredLevel(transformationId),
            ChiseledEnchantingTableData.transformationDeductedLevels(transformationId)
        );
    }

    public static XpRequirement forTransformation(
        Identifier transformationId,
        int rawCost,
        int discountPercent,
        int minRequiredLevel
    ) {
        return forTransformation(
            LibraryResonance.activeServerConfig(),
            transformationId,
            rawCost,
            discountPercent,
            minRequiredLevel,
            ChiseledEnchantingTableData.transformationDeductedLevels(transformationId)
        );
    }

    public static XpRequirement forTransformation(
        LibraryResonance.Config resonanceCfg,
        Identifier transformationId,
        int rawThresholdLevel,
        int discountPercent,
        int minRequiredLevel,
        int configuredDeductedLevels
    ) {
        int clampedRaw = Math.max(0, rawThresholdLevel);
        if (clampedRaw == 0) {
            return XpRequirement.FREE;
        }
        int tierCost = configuredDeductedLevels > 0
            ? configuredDeductedLevels
            : (clampedRaw <= 1 ? 1 : vanillaTierDeduction(clampedRaw, 30));
        int minGate = Math.max(tierCost, Math.max(1, minRequiredLevel));
        int clampedDiscount = clampDiscount(resonanceCfg, discountPercent);
        int requiredThreshold = Math.max(minGate, (int) Math.round(clampedRaw * (1.0 - clampedDiscount / 100.0)));
        return new XpRequirement(clampedRaw, requiredThreshold, tierCost, clampedDiscount, true);
    }

    public static int clampDiscount(int discountPercent) {
        return clampDiscount(LibraryResonance.activeServerConfig(), discountPercent);
    }

    public static int clampDiscount(LibraryResonance.Config resonanceCfg, int discountPercent) {
        LibraryResonance.Config cfg = resonanceCfg != null ? resonanceCfg : LibraryResonance.Config.DEFAULT;
        return Math.max(0, Math.min(cfg.maxTotalXpDiscountPercent(), discountPercent));
    }
}
