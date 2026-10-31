package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Synchronized gravity for real Density/Breach projectiles, not a cosmetic trail. */
@Mixin(AbstractArrow.class)
public abstract class ArrowHeavyCrushMixin {
    @Inject(method = "getDefaultGravity", at = @At("RETURN"), cancellable = true)
    private void ef$heavyGravity(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(HeavyCrushProjectiles.gravity((AbstractArrow) (Object) this, cir.getReturnValueD()));
    }
}
