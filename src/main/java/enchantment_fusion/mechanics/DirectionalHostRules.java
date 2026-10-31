package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostWakeDiveSkills;
import enchantment_fusion.mechanics.signatures.FrostFireSkills;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.PrecisionConstruction;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/**
 * Checks new or changed gear fusions, never rewriting or disabling old items. A third-party
 * enchantment needs an explicit trigger, payload and fusion-host declaration, not a guess
 * from vanilla enchantment slots. The explicit declaration is about use in fusions, not the
 * enchantment's native item set. Single-enchantment transmutations do not use this validator.
 *
 * Per-direction rules are deliberately limited to implemented abilities. Unimplemented vanilla
 * directions retain legacy behavior until their abilities and carrier rules are written; their
 * absence here must not be interpreted as complete coverage.
 */
public final class DirectionalHostRules {
    private DirectionalHostRules() {}

    public static RejectReason validateNewOrChanged(ItemStack host, FusedPair pair) {
        return validateNewOrChanged(host, pair, FusionMatrixRegistry.serverSnapshot());
    }

    public static RejectReason validateNewOrChanged(ItemStack host, FusedPair pair, FusionMatrixRegistry.Snapshot snapshot) {
        if (host == null || host.isEmpty() || pair == null || snapshot == null) return RejectReason.MISSING_INPUT;
        Set<FusionCarrier> actual = FusionCarrier.classify(host);
        RejectReason classification = validateClassifications(pair, actual, snapshot);
        if (classification != RejectReason.NONE) return classification;

        boolean glazedMapping = GlazedStrikeEffect.isPair(pair)
            && SignatureCatalog.resolve(pair).map(e -> GlazedStrikeEffect.SIGNATURE.equals(e.id())).orElse(false);
        if (glazedMapping && !GlazedStrikeEffect.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        String signature = SignatureCatalog.resolve(pair).map(e -> e.id()).orElse("");
        if (AmberSealSkill.isPair(pair) && AmberSealSkill.SIGNATURE.equals(signature)
            && !AmberSealSkill.applies(pair, host, signature)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        boolean underwaterRedesign = (UnderwaterMiningSkills.isSpendPair(pair) && signature.equals(UnderwaterMiningSkills.SPEND_SIGNATURE))
            || (UnderwaterMiningSkills.isBubblePair(pair) && signature.equals(UnderwaterMiningSkills.BUBBLE_SIGNATURE));
        if (underwaterRedesign && !UnderwaterMiningSkills.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        boolean frostFallRedesign = (FrostFallSkills.isWavePair(pair) && signature.equals(FrostFallSkills.WAVE_SIGNATURE))
            || (FrostFallSkills.isStepPair(pair) && signature.equals(FrostFallSkills.STEP_SIGNATURE));
        if (frostFallRedesign && !FrostFallSkills.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        boolean frostWakeDive = (FrostWakeDiveSkills.isWakePair(pair) && signature.equals(FrostWakeDiveSkills.WAKE))
            || (FrostWakeDiveSkills.isDivePair(pair) && signature.equals(FrostWakeDiveSkills.DIVE));
        if (frostWakeDive && !FrostWakeDiveSkills.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (FrostFireSkills.matches(pair, signature) && !FrostFireSkills.supportsCarrier(pair, actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (PhantomVolley.matches(pair, signature) && !PhantomVolley.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (PrecisionConstruction.matches(pair, signature) && !PrecisionConstruction.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (DurabilityEconomy.matches(pair, signature) && !DurabilityEconomy.supportsCarrier(host)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (BlastDefenseSkills.matches(pair, signature) && !BlastDefenseSkills.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (HeavyCrushSkill.matches(pair, signature) && !HeavyCrushSkill.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (SoulStepSkills.matches(pair, signature) && !SoulStepSkills.supportsCarrier(pair, actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (FishingTideSkills.matches(pair, signature) && !FishingTideSkills.supportsCarrier(actual)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (pair.primaryId().equals("minecraft:fire_aspect") && pair.secondaryId().equals("minecraft:aqua_affinity")
            && signature.equals("geyser_bore")
            && actual.stream().noneMatch(Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE)::contains)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        if (pair.primaryId().equals("minecraft:aqua_affinity") && pair.secondaryId().equals("minecraft:fire_aspect")
            && signature.equals("quench_temper_blade")
            && actual.stream().noneMatch(Set.of(FusionCarrier.SWORD, FusionCarrier.AXE)::contains)) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        return RejectReason.NONE;
    }

    /** Pure policy entrypoint shared by the station and codec/compatibility tests. */
    public static RejectReason validateClassifications(FusedPair pair, Set<FusionCarrier> actual,
                                                        FusionMatrixRegistry.Snapshot snapshot) {
        if (pair == null || actual == null || snapshot == null) return RejectReason.MISSING_INPUT;
        String[] ids = {pair.primaryId(), pair.secondaryId()};
        for (String id : ids) {
            if (FusionMatrixRegistry.isBuiltInVanillaEnchantment(id)) continue;
            FusionMatrixRegistry.MatrixProfile profile = snapshot.profiles().get(id);
            if (profile == null || !profile.classifiedForFusion()) return RejectReason.UNCLASSIFIED_ENCHANTMENT;
        }
        for (String id : ids) {
            if (FusionMatrixRegistry.isBuiltInVanillaEnchantment(id)) continue;
            if (snapshot.profiles().get(id).fusionHosts().stream().noneMatch(actual::contains)) {
                return RejectReason.UNSUPPORTED_DIRECTION_HOST;
            }
        }
        return RejectReason.NONE;
    }
}
