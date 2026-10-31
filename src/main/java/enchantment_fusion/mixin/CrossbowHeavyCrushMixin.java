package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.HeavyCrushMath;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies after Quick Charge/data-pack charging time, so both mechanics can coexist. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowHeavyCrushMixin {
    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void ef$heavyReload(ItemStack weapon, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        int duration = cir.getReturnValueI();
        if (HeavyCrushSkill.selected(weapon) != null) duration = HeavyCrushMath.slowedChargeDuration(duration);
        int resolved = PiercingChainSkills.chargeDuration(weapon, user, duration);
        if (resolved != cir.getReturnValueI()) cir.setReturnValue(resolved);
    }
}
