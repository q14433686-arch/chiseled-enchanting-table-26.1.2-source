package enchantment_fusion.mechanics;

import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
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
        RejectReason classification = validateClassifications(pair, FusionCarrier.classify(host), snapshot);
        if (classification != RejectReason.NONE) return classification;

        boolean glazedMapping = GlazedStrikeEffect.isPair(pair)
            && SignatureCatalog.resolve(pair).map(e -> GlazedStrikeEffect.SIGNATURE.equals(e.id())).orElse(false);
        if (glazedMapping && !GlazedStrikeEffect.isEligibleRole(TransmuteCatalog.classifyTarget(host))) {
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
