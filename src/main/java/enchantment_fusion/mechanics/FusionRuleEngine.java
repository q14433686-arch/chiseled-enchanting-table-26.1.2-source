package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import java.util.Set;

/**
 * Pure, side-effect-free validation and pricing engine for Enchantment Fusion.
 * Supports Universal Any-Gear Inscription ("万物可铭刻"): any fused pair [A ▷ B] or [B ▷ A]
 * may be socketed onto any valid equipment host regardless of vanilla slot restrictions.
 */
public final class FusionRuleEngine {
    public enum RejectReason {
        NONE(""),
        MISSING_INPUT("gui.enchantment_fusion.reject.missing_input"),
        SAME_ENCHANTMENT("gui.enchantment_fusion.reject.same_enchantment"),
        PRIMARY_INCOMPATIBLE_HOST("gui.enchantment_fusion.reject.primary_incompatible"),
        REPULSIVE_REQUIRES_MAX_LEVEL("gui.enchantment_fusion.reject.repulsive_needs_max"),
        DUPLICATE_ON_HOST("gui.enchantment_fusion.reject.duplicate_on_host"),
        VESSEL_CAPACITY_FULL("gui.enchantment_fusion.reject.vessel_full"),
        ABERRANT_LOCKED("gui.enchantment_fusion.reject.aberrant_locked"),
        NEEDS_CATALYST("gui.enchantment_fusion.reject.needs_catalyst"),
        ALREADY_MAX_VESSEL("gui.enchantment_fusion.reject.already_max_vessel"),
        OUTPUT_OCCUPIED("gui.enchantment_fusion.reject.output_occupied"),
        NOT_CROSS_DOMAIN("gui.enchantment_fusion.reject.not_cross_domain"),
        STACK_MUST_BE_SINGLE("gui.enchantment_fusion.reject.stack_must_be_single");

        private final String langKey;

        RejectReason(String langKey) {
            this.langKey = langKey;
        }

        public String langKey() {
            return langKey;
        }
    }

    public record EnchantInput(
        String id,
        int level,
        int maxLevel,
        boolean primaryFitsHost
    ) {
        public boolean isAtMaxLevel() {
            return level >= Math.max(1, maxLevel);
        }
    }

    public record PairEvaluation(
        boolean valid,
        RejectReason rejectReason,
        FusedPair resultingPair,
        boolean repulsiveExclusive,
        double instabilityChance,
        int xpLevelsCost
    ) {
        public static PairEvaluation rejected(RejectReason reason) {
            return new PairEvaluation(false, reason, null, false, 0.0, 0);
        }
    }

    private FusionRuleEngine() {}

    /**
     * Evaluates creating or inscribing a fused pair {@code [A ▷ B]} onto a book or any equipment host.
     */
    public static PairEvaluation evaluateFusion(
        EnchantInput primary,
        EnchantInput secondary,
        boolean targetingEquipment,
        FusedEnchantmentList existingHostFusions,
        Set<String> existingHostVanillaEnchantIds,
        int maxHostVesselSlots,
        boolean hasStabilizer,
        boolean zeroXpStabilizer
    ) {
        if (primary == null || secondary == null || primary.id() == null || secondary.id() == null) {
            return PairEvaluation.rejected(RejectReason.MISSING_INPUT);
        }
        if (primary.id().equals(secondary.id())) {
            return PairEvaluation.rejected(RejectReason.SAME_ENCHANTMENT);
        }

        FusionMatrixRegistry.Snapshot matrix = FusionMatrixRegistry.current();
        boolean repulsive = matrix.areRepulsiveExclusives(primary.id(), secondary.id());
        if (repulsive && (!primary.isAtMaxLevel() || !secondary.isAtMaxLevel())) {
            return PairEvaluation.rejected(RejectReason.REPULSIVE_REQUIRES_MAX_LEVEL);
        }

        if (targetingEquipment) {
            FusedEnchantmentList hostList = existingHostFusions == null ? FusedEnchantmentList.EMPTY : existingHostFusions;
            if (hostList.size() >= maxHostVesselSlots) {
                return PairEvaluation.rejected(RejectReason.VESSEL_CAPACITY_FULL);
            }
            if (hostList.containsEnchantmentId(primary.id()) || hostList.containsEnchantmentId(secondary.id())) {
                return PairEvaluation.rejected(RejectReason.DUPLICATE_ON_HOST);
            }
            if (existingHostVanillaEnchantIds != null) {
                if (existingHostVanillaEnchantIds.contains(primary.id()) || existingHostVanillaEnchantIds.contains(secondary.id())) {
                    return PairEvaluation.rejected(RejectReason.DUPLICATE_ON_HOST);
                }
            }
        }

        int clampedPrimary = Math.max(1, Math.min(primary.maxLevel(), primary.level()));
        int clampedSecondary = Math.max(1, Math.min(secondary.maxLevel(), secondary.level()));
        FusedPair result = FusedPair.clean(primary.id(), clampedPrimary, secondary.id(), clampedSecondary);

        double instability = (repulsive && !hasStabilizer) ? matrix.baseInstabilityChance() : 0.0;
        int baseXp = repulsive ? 8 : Math.max(2, clampedPrimary + clampedSecondary);
        if (targetingEquipment) {
            baseXp += 1;
        }
        int finalXp = zeroXpStabilizer ? 0 : baseXp;

        return new PairEvaluation(true, RejectReason.NONE, result, repulsive, instability, finalXp);
    }

    public static RejectReason canFlipPair(FusedPair existing) {
        if (existing == null) {
            return RejectReason.MISSING_INPUT;
        }
        if (existing.isAberrant()) {
            return RejectReason.ABERRANT_LOCKED;
        }
        return RejectReason.NONE;
    }

    public static RejectReason canUnsocketOrSplitPair(FusedPair existing) {
        if (existing == null) {
            return RejectReason.MISSING_INPUT;
        }
        if (existing.isAberrant()) {
            return RejectReason.ABERRANT_LOCKED;
        }
        return RejectReason.NONE;
    }
}
