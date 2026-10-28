package enchantment_fusion.mixin;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces GDD §6.1: items bearing an Aberrant fusion ({@code hasAberration() == true})
 * cannot be scrubbed in a vanilla Grindstone.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneAberrationGuardMixin {
    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void ef$blockAberrantGrindstoneWipe(
        ItemStack input,
        ItemStack additional,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        FusedEnchantmentList listA = input.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        FusedEnchantmentList listB = additional.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (listA.hasAberration() || listB.hasAberration()) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
