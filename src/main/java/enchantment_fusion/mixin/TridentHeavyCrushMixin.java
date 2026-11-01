package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.GrapnelChanneling;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Thrown tridents override the arrow collision method, so observe their own direct hit. */
@Mixin(ThrownTrident.class)
public abstract class TridentHeavyCrushMixin {
    @WrapMethod(method = "onHitEntity")
    private void ef$scopeTridentHit(EntityHitResult hit, Operation<Void> original) {
        ThrownTrident projectile = (ThrownTrident) (Object) this;
        GrapnelImpact.Attack frame = GrapnelImpact.beginTrident(projectile, hit.getEntity());
        GrapnelChanneling.Action channel = GrapnelChanneling.beginEntity(projectile, hit.getEntity());
        boolean completed = false;
        try { original.call(hit); completed = true; }
        finally {
            try { GrapnelChanneling.endAction(channel, completed); }
            finally { GrapnelImpact.endAttack(frame); }
        }
    }

    @WrapMethod(method = "hitBlockEnchantmentEffects")
    private void ef$scopeTridentBlock(ServerLevel level, BlockHitResult hit, ItemStack weapon, Operation<Void> original) {
        GrapnelChanneling.Action action = GrapnelChanneling.beginBlock((ThrownTrident) (Object) this, level, hit, weapon);
        boolean completed = false;
        try { original.call(level, hit, weapon); completed = true; }
        finally { GrapnelChanneling.endAction(action, completed); }
    }

    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeHeavyTrident(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        ThrownTrident projectile = (ThrownTrident) (Object) this;
        HeavyCrushSkill.Hit hit = HeavyCrushSkill.beginProjectile(projectile, target, source);
        MeleeLightningSkills.TridentHit lightning = MeleeLightningSkills.beginTrident(projectile, target, source);
        GrapnelImpact.ProjectileHit grapple = GrapnelImpact.beforeTrident(projectile, target, source);
        GrapnelChanneling.Damage channel = GrapnelChanneling.beforeDamage(projectile, target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            try { GrapnelChanneling.afterDamage(channel, succeeded); }
            finally {
                try { GrapnelImpact.afterTrident(grapple, succeeded); }
                finally {
                    try { MeleeLightningSkills.endTrident(lightning, succeeded); }
                    finally { HeavyCrushSkill.endHit(hit, succeeded); }
                }
            }
        }
    }
}
