package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crossbow rockets have no vanilla weapon reference or gravity, so track only the actual struck target. */
@Mixin(FireworkRocketEntity.class)
public abstract class RocketHeavyCrushMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ef$heavyRocketArc(CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        double downward = HeavyCrushProjectiles.rocketDownwardAcceleration(rocket);
        if (downward > 0.0D) rocket.setDeltaMovement(rocket.getDeltaMovement().add(0.0D, -downward, 0.0D));
    }

    @WrapMethod(method = "onHitEntity")
    private void ef$directRocketHit(EntityHitResult result, Operation<Void> original) {
        HeavyCrushProjectiles.RocketHit frame = HeavyCrushProjectiles.beginRocketHit(
            (FireworkRocketEntity) (Object) this, result.getEntity());
        boolean completed = false;
        try {
            original.call(result);
            completed = true;
        } finally {
            HeavyCrushProjectiles.endRocketHit(frame, completed);
        }
    }
}
