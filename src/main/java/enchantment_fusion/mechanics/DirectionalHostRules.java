package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import enchantment_fusion.model.FusedPair;
import net.minecraft.world.item.ItemStack;

/**
 * New or changed gear fusions are checked against an ordered ability's actual carrier.
 * This intentionally includes only implemented rules: unknown directions retain the old
 * behavior until their own abilities and carrier rules are implemented, rather than
 * being silently advertised as covered. It does not run on existing items during play.
 */
public final class DirectionalHostRules {
    private DirectionalHostRules() {}

    public static RejectReason validateNewOrChanged(ItemStack host, FusedPair pair) {
        if (host == null || host.isEmpty() || pair == null) return RejectReason.MISSING_INPUT;
        boolean glazedMapping = GlazedStrikeEffect.isPair(pair)
            && SignatureCatalog.resolve(pair).map(e -> GlazedStrikeEffect.SIGNATURE.equals(e.id())).orElse(false);
        if (glazedMapping && !GlazedStrikeEffect.isEligibleRole(TransmuteCatalog.classifyTarget(host))) {
            return RejectReason.UNSUPPORTED_DIRECTION_HOST;
        }
        return RejectReason.NONE;
    }
}
