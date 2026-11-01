package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.MeleeMendingSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe real orb repairs; use only leftover XP for non-native mending carriers. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbDurabilityMixin {
    // playerTouch calls the recursive vanilla method exactly once. Wrapping that outer
    // call avoids replaying our non-native pass at every recursive return frame.
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/server/level/ServerPlayer;I)I"))
    private int ef$repairNonNative(ExperienceOrb orb, ServerPlayer player, int amount, Operation<Integer> original) {
        int remaining = original.call(orb, player, amount);
        return remaining > 0 ? DurabilityEconomy.repairNonNative(player, remaining) : remaining;
    }

    @WrapOperation(method = "repairPlayerItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void ef$observeActualRepair(ItemStack stack, int newDamage, Operation<Void> original,
                                        ServerPlayer player, int amount) {
        int before = stack.getDamageValue();
        original.call(stack, newDamage);
        int repaired = Math.max(0, before - stack.getDamageValue());
        if (repaired > 0) {
            DurabilityEconomy.afterOrbRepair(player, stack, repaired);
            MeleeMendingSkills.afterOrbRepair(player, stack, repaired);
        }
    }
}
