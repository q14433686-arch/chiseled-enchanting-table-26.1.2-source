package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
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
        ThrownTrident projectile = (ThrownTrident) (Object) this;
        HeavyCrushSkill.Hit hit = HeavyCrushSkill.beginProjectile(projectile, target, source);
        MeleeLightningSkills.TridentHit lightning = MeleeLightningSkills.beginTrident(projectile, target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            try { MeleeLightningSkills.endTrident(lightning, succeeded); }
            finally { HeavyCrushSkill.endHit(hit, succeeded); }
        }
    }
}
