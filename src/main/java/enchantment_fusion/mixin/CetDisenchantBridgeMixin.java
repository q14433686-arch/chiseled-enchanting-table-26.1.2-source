package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseOperations;
import enchantment_fusion.compat.CetCleanseServerBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication", remap = false)
public abstract class CetDisenchantBridgeMixin {
    @Inject(
        method = "apply(Lchiseled_enchanting_table/menu/ChiseledEnchantingTableScreenHandler;Lchiseled_enchanting_table/menu/ChiseledEnchantingMenuPayloads$ApplyEnchantmentPayload;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onApplyCleanse(
        @Coerce Object menu,
        @Coerce Object payload,
        CallbackInfo ci
    ) {
        if (CetCleanseServerBridge.tryHandleApply(menu, payload)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "countNonCurseEnchantments(Lnet/minecraft/world/item/ItemStack;)I",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onCountNonCurses(
        ItemStack stack,
        CallbackInfoReturnable<Integer> cir
    ) {
        int delta = CetCleanseOperations.netNonCurseTraitDelta(stack);
        if (delta != 0) {
            cir.setReturnValue(Math.max(0, cir.getReturnValue() + delta));
        }
    }

    @Inject(
        method = "totalFullDisenchantRefund(Lchiseled_enchanting_table/mechanics/resonance/LibraryResonance$Config;Lnet/minecraft/world/item/ItemStack;DD)I",
        at = @At("RETURN"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onTotalFullDisenchantRefund(
        @Coerce Object resonanceCfg,
        ItemStack stack,
        double tableRefundScale,
        double externalRefundScale,
        CallbackInfoReturnable<Integer> cir
    ) {
        int extra = CetCleanseOperations.additionalFullDisenchantEssence(stack, tableRefundScale);
        if (extra > 0) {
            cir.setReturnValue(cir.getReturnValue() + extra);
        }
    }
}
