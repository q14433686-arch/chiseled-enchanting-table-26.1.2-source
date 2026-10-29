package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseOperations;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.menu.MenuCleanseQuotes", remap = false)
public abstract class CetCleanseQuotesBridgeMixin {
    @Inject(
        method = "canExtractToBook(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onCanExtractToBook(
        ItemStack target,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) && CetCleanseOperations.canExtractAnyFusionTraitToBook(target)) {
            cir.setReturnValue(true);
        }
    }
}
