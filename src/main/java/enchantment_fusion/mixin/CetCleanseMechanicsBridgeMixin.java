package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseOperations;
import enchantment_fusion.compat.CetCleanseServerBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.mechanics.enchanting.CleanseMechanics", remap = false)
public abstract class CetCleanseMechanicsBridgeMixin {
    @Inject(
        method = "countCurses(Lnet/minecraft/world/item/ItemStack;)I",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onCountCurses(
        ItemStack stack,
        CallbackInfoReturnable<Integer> cir
    ) {
        int aberrants = CetCleanseOperations.countAberrantFusions(stack);
        if (aberrants > 0) {
            cir.setReturnValue(cir.getReturnValue() + aberrants);
        }
    }

    @Inject(
        method = "canPurgeAnyCurseNow(Lchiseled_enchanting_table/menu/ChiseledEnchantingTableScreenHandler;)Z",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onCanPurgeAnyCurseNow(
        @Coerce Object menu,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) && CetCleanseServerBridge.canPurgeAnyAberrantNow(menu)) {
            cir.setReturnValue(true);
        }
    }
}
