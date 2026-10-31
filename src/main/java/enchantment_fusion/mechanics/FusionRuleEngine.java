package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import java.util.Set;

/**
 * Pure, side-effect-free validation engine for Enchantment Fusion.
 * Pricing and instability quotes live exclusively in {@link FusionEconomyRules} (DEAD-02).
 */
public final class FusionRuleEngine {
    public enum RejectReason {
        NONE(""),
        MISSING_INPUT("gui.enchantment_fusion.reject.missing_input"),
        SAME_ENCHANTMENT("gui.enchantment_fusion.reject.same_enchantment"),
        REPULSIVE_REQUIRES_MAX_LEVEL("gui.enchantment_fusion.reject.repulsive_needs_max"),
        DUPLICATE_ON_HOST("gui.enchantment_fusion.reject.duplicate_on_host"),
        EXCLUSIVE_ON_HOST("gui.enchantment_fusion.reject.exclusive_on_host"),
        VESSEL_CAPACITY_FULL("gui.enchantment_fusion.reject.vessel_full"),
        ABERRANT_LOCKED("gui.enchantment_fusion.reject.aberrant_locked"),
        ALREADY_MAX_VESSEL("gui.enchantment_fusion.reject.already_max_vessel"),
        OUTPUT_OCCUPIED("gui.enchantment_fusion.reject.output_occupied"),
        NOT_CROSS_DOMAIN("gui.enchantment_fusion.reject.not_cross_domain"),
        STACK_MUST_BE_SINGLE("gui.enchantment_fusion.reject.stack_must_be_single"),
        NEED_PURIFY_STABILIZERS("gui.enchantment_fusion.reject.need_purify_stabilizers"),
        INSUFFICIENT_XP("gui.enchantment_fusion.reject.insufficient_xp"),
        INSUFFICIENT_REAGENTS("gui.enchantment_fusion.reject.insufficient_reagents"),
        TRANSMUTE_CAP_FULL("gui.enchantment_fusion.reject.transmute_cap_full"),
        UNSUPPORTED_DIRECTION_HOST("gui.enchantment_fusion.reject.unsupported_direction_host"),
        UNCLASSIFIED_ENCHANTMENT("gui.enchantment_fusion.reject.unclassified_enchantment");

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
        int maxLevel
    ) {
        /** Compatibility overload ignoring legacy {@code nativeToHost} flag (DEAD-1). */
        public EnchantInput(String id, int level, int maxLevel, boolean ignoredNativeToHost) {
            this(id, level, maxLevel);
        }

        public boolean isAtMaxLevel() {
            return level >= Math.max(1, maxLevel);
        }
    }

    public record PairEvaluation(
        boolean valid,
        RejectReason rejectReason,
        FusedPair resultingPair,
        boolean repulsiveExclusive
    ) {
        public static PairEvaluation rejected(RejectReason reason) {
            return new PairEvaluation(false, reason, null, false);
        }
    }

    private FusionRuleEngine() {}

    public static PairEvaluation evaluateFusion(
        EnchantInput primary,
        EnchantInput secondary,
        boolean targetingEquipment,
        FusedEnchantmentList existingHostFusions,
        Set<String> existingHostEnchantIds,
        int maxHostVesselSlots,
        boolean extraRuntimeIncompatiblePair,
        boolean incompatibleWithExistingHost
    ) {
        return evaluateFusion(
            FusionMatrixRegistry.serverSnapshot(),
            primary,
            secondary,
            targetingEquipment,
            existingHostFusions,
            existingHostEnchantIds,
            maxHostVesselSlots,
            extraRuntimeIncompatiblePair,
            incompatibleWithExistingHost
        );
    }

    /**
     * Evaluates creating or inscribing a fused pair {@code [A ▷ B]} onto a book or any equipment host.
     */
    public static PairEvaluation evaluateFusion(
        FusionMatrixRegistry.Snapshot snapshot,
        EnchantInput primary,
        EnchantInput secondary,
        boolean targetingEquipment,
        FusedEnchantmentList existingHostFusions,
        Set<String> existingHostEnchantIds,
        int maxHostVesselSlots,
        boolean extraRuntimeIncompatiblePair,
        boolean incompatibleWithExistingHost
    ) {
        if (primary == null || secondary == null || primary.id() == null || secondary.id() == null) {
            return PairEvaluation.rejected(RejectReason.MISSING_INPUT);
        }
        if (primary.id().equals(secondary.id())) {
            return PairEvaluation.rejected(RejectReason.SAME_ENCHANTMENT);
        }

        FusionMatrixRegistry.Snapshot matrix = snapshot != null ? snapshot : FusionMatrixRegistry.serverSnapshot();
        boolean repulsive = extraRuntimeIncompatiblePair || matrix.areRepulsiveExclusives(primary.id(), secondary.id());
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
            if (existingHostEnchantIds != null) {
                if (existingHostEnchantIds.contains(primary.id()) || existingHostEnchantIds.contains(secondary.id())) {
                    return PairEvaluation.rejected(RejectReason.DUPLICATE_ON_HOST);
                }
            }
            if (incompatibleWithExistingHost) {
                return PairEvaluation.rejected(RejectReason.EXCLUSIVE_ON_HOST);
            }
            // NUM-7: Check repulsive exclusivity against BOTH primaryId and secondaryId of existing host fusions
            for (FusedPair existingPair : hostList.pairs()) {
                if (matrix.areRepulsiveExclusives(primary.id(), existingPair.primaryId())
                    || matrix.areRepulsiveExclusives(primary.id(), existingPair.secondaryId())) {
                    return PairEvaluation.rejected(RejectReason.EXCLUSIVE_ON_HOST);
                }
            }
            if (existingHostEnchantIds != null) {
                for (String existingId : existingHostEnchantIds) {
                    if (matrix.areRepulsiveExclusives(primary.id(), existingId)) {
                        return PairEvaluation.rejected(RejectReason.EXCLUSIVE_ON_HOST);
                    }
                }
            }
        }

        int clampedPrimary = Math.clamp(primary.level(), 1, Math.max(1, primary.maxLevel()));
        int clampedSecondary = Math.clamp(secondary.level(), 1, Math.max(1, secondary.maxLevel()));
        FusedPair result = FusedPair.clean(primary.id(), clampedPrimary, secondary.id(), clampedSecondary);
        return new PairEvaluation(true, RejectReason.NONE, result, repulsive);
    }

    public static RejectReason canFlipPair(FusedPair existing) {
        return rejectIfAberrantLocked(existing);
    }

    public static RejectReason canUnsocketOrSplitPair(FusedPair existing) {
        return rejectIfAberrantLocked(existing);
    }

    private static RejectReason rejectIfAberrantLocked(FusedPair existing) {
        if (existing == null) {
            return RejectReason.MISSING_INPUT;
        }
        if (existing.isAberrant()) {
            return RejectReason.ABERRANT_LOCKED;
        }
        return RejectReason.NONE;
    }
}
