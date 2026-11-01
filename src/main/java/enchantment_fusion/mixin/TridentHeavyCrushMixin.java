package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Thrown tridents override the arrow collision method, so observe their own direct hit. */
@Mixin(ThrownTrident.class)
public abstract class TridentHeavyCrushMixin {
    @WrapMethod(method = "onHitEntity")
    private void ef$scopeTridentHit(EntityHitResult hit, Operation<Void> original) {
        GrapnelImpact.Attack frame = GrapnelImpact.beginTrident((ThrownTrident) (Object) this, hit.getEntity());
        try { original.call(hit); } finally { GrapnelImpact.endAttack(frame); }
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeHeavyTrident(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        ThrownTrident projectile = (ThrownTrident) (Object) this;
        HeavyCrushSkill.Hit hit = HeavyCrushSkill.beginProjectile(projectile, target, source);
        MeleeLightningSkills.TridentHit lightning = MeleeLightningSkills.beginTrident(projectile, target, source);
        GrapnelImpact.ProjectileHit grapple = GrapnelImpact.beforeTrident(projectile, target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            try { GrapnelImpact.afterTrident(grapple, succeeded); }
            finally {
                try { MeleeLightningSkills.endTrident(lightning, succeeded); }
                finally { HeavyCrushSkill.endHit(hit, succeeded); }
            }
        }
    }
}
