package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe real orb repairs; use only leftover XP for non-native mending carriers. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbDurabilityMixin {
    @Inject(method = "repairPlayerItems", at = @At("RETURN"), cancellable = true)
    private void ef$repairNonNative(ServerPlayer player, int amount, CallbackInfoReturnable<Integer> result) {
        int remaining = result.getReturnValueI();
        if (remaining > 0) result.setReturnValue(DurabilityEconomy.repairNonNative(player, remaining));
    }

    @WrapOperation(method = "repairPlayerItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void ef$observeActualRepair(ItemStack stack, int newDamage, Operation<Void> original,
                                        ServerPlayer player, int amount) {
        int before = stack.getDamageValue();
        original.call(stack, newDamage);
        int repaired = Math.max(0, before - stack.getDamageValue());
        if (repaired > 0) DurabilityEconomy.afterOrbRepair(player, stack, repaired);
    }
}
