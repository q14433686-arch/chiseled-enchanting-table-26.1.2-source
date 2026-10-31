package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Thrown tridents override the arrow collision method, so observe their own direct hit. */
@Mixin(ThrownTrident.class)
public abstract class TridentHeavyCrushMixin {
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeHeavyTrident(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        HeavyCrushSkill.Hit hit = HeavyCrushSkill.beginProjectile((ThrownTrident) (Object) this, target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            HeavyCrushSkill.endHit(hit, succeeded);
        }
    }
}
