package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Phantom steering, protected collisions and an exact main-arrow damage reservation. */
@Mixin(AbstractArrow.class)
public abstract class PhantomArrowMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ef$tickPhantom(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        EchoPathSkills.sample(arrow);
        if (!PhantomVolley.tick(arrow)) ci.cancel();
    }

    // Vanilla normally excludes players when PvP is disabled. Ghosts must still physically
    // see such protected players and vanish instead of passing through them to another target.
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void ef$seeProtectedEntity(Entity target, CallbackInfoReturnable<Boolean> cir) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level() instanceof ServerLevel && PhantomVolley.isGhost(arrow) && target != arrow.getOwner()
            && target.isAlive() && target.canBeHitByProjectile() && PhantomVolley.protectedCollision(arrow, target)) {
            cir.setReturnValue(true);
        }
    }

    // The entity list and block hit were computed for the incoming ray.  A redirected
    // arrow must not hit any remaining entities on that stale ray in the same tick.
    @WrapOperation(method = "hitTargetsOrDeflectSelf", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private ProjectileDeflection ef$endOldRayAfterRedirect(AbstractArrow arrow, HitResult hit,
                                                           Operation<ProjectileDeflection> original) {
        ProjectileDeflection result = original.call(arrow, hit);
        boolean stop = PiercingChainSkills.consumeRedirectStop(arrow);
        // This return only ends AbstractArrow's current collision batch; it does not
        // invoke Projectile.deflect or alter owner/velocity.
        return stop && result == ProjectileDeflection.NONE ? ProjectileDeflection.REVERSE : result;
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$splitAfterActualHit(Entity target, DamageSource source, float amount, Operation<Boolean> original,
                                            EntityHitResult hit) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        boolean reserved = arrow.level() instanceof ServerLevel && PhantomVolley.reserveImpact(arrow);
        HeavyCrushSkill.Hit heavyHit = HeavyCrushSkill.beginProjectile(arrow, target, source);
        PiercingChainSkills.Impact chainHit = PiercingChainSkills.beginImpact(arrow, target, hit.getLocation());
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, amount);
            return succeeded;
        } finally {
            try {
                HeavyCrushSkill.endHit(heavyHit, succeeded);
                RangedCadenceSkills.finishBowHit(arrow, target, succeeded);
                EchoPathSkills.hit(arrow, target, hit.getLocation(), amount, succeeded);
            } finally {
                try {
                    PiercingChainSkills.finishImpact(chainHit, succeeded);
                } finally {
                    if (reserved) PhantomVolley.finishImpact(arrow, target, hit.getLocation(), succeeded);
                }
            }
        }
    }
}
