package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Carrier policy for the explicitly retained Efficiency/Multishot abilities; never changes their runtime. */
public final class RetainedVolleyHosts {
    public static final String RANGED = "rapid_bowstring", MINING = "triple_excavate";
    private static final String EFFICIENCY = "minecraft:efficiency", MULTISHOT = "minecraft:multishot";
    private static final Set<FusionCarrier> RANGED_ITEMS = Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW);
    private static final Set<FusionCarrier> MINING_ITEMS = Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE,
        FusionCarrier.SHOVEL, FusionCarrier.HOE, FusionCarrier.SHEARS);
    private RetainedVolleyHosts() {}
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && (EFFICIENCY.equals(pair.primaryId()) && MULTISHOT.equals(pair.secondaryId()) && RANGED.equals(signature)
            || MULTISHOT.equals(pair.primaryId()) && EFFICIENCY.equals(pair.secondaryId()) && MINING.equals(signature));
    }
    public static boolean supportsRoles(Set<FusionCarrier> roles, String signature) {
        if (roles == null) return false;
        Set<FusionCarrier> allowed = RANGED.equals(signature) ? RANGED_ITEMS : MINING.equals(signature) ? MINING_ITEMS : Set.of();
        return roles.stream().anyMatch(allowed::contains);
    }
    public static boolean supportsHost(ItemStack stack, String signature) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        Set<FusionCarrier> roles = FusionCarrier.classify(stack);
        return supportsRoles(roles, signature) || MINING.equals(signature)
            && roles.contains(FusionCarrier.OTHER_EQUIPMENT) && stack.has(DataComponents.TOOL);
    }
}
