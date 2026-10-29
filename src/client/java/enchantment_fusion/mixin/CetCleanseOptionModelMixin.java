package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseClientBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.gui.CleanseOptionModel", remap = false)
public abstract class CetCleanseOptionModelMixin {
    @Inject(
        method = "build(Lnet/minecraft/world/item/ItemStack;Z)Lchiseled_enchanting_table/gui/CleanseOptionModel$Result;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onBuildCleanseOptions(
        ItemStack target,
        boolean allowCursePurge,
        CallbackInfoReturnable<Object> cir
    ) {
        Object augmented = CetCleanseClientBridge.augmentCleanseOptionModel(
            target, allowCursePurge, cir.getReturnValue()
        );
        if (augmented != null) {
            cir.setReturnValue(augmented);
        }
    }
}
