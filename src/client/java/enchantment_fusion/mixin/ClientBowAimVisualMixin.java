package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.RangedSneakSkills;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Render the target-only aim credit; the server alone decides whether a shot benefits. */
@Mixin(UseDuration.class)
public abstract class ClientBowAimVisualMixin {
    @Inject(method = "useDuration", at = @At("RETURN"), cancellable = true)
    private static void ef$aimedBowPull(ItemStack item, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (!(item.getItem() instanceof BowItem) || user.getUseItem() != item) return;
        int displayed = RangedSneakSkills.visualBowTicks(user, item, cir.getReturnValueI());
        if (displayed != cir.getReturnValueI()) cir.setReturnValue(displayed);
    }
}
