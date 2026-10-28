package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.TriggerType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks server-side projectile impacts so bows, crossbows, and thrown tridents carrying
 * binary Fused Pairs or Cross-Domain Single Transmutations trigger their impact effects.
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactFusionMixin {
    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void ef$afterHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        ItemStack weapon = resolveFiringWeapon(self, shooter);
        if (weapon.isEmpty()) {
            return;
        }
        LivingEntity target = hitResult.getEntity() instanceof LivingEntity liv ? liv : null;
        TriggerType trigger = (self.getType() == EntityType.TRIDENT)
            ? TriggerType.ON_TRIDENT_CHANNEL
            : TriggerType.ON_PROJECTILE_IMPACT;
        MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, trigger, target, hitResult.getLocation());
        CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, target, hitResult.getLocation());
    }

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void ef$afterHitBlock(BlockHitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        ItemStack weapon = resolveFiringWeapon(self, shooter);
        if (weapon.isEmpty()) {
            return;
        }
        TriggerType trigger = (self.getType() == EntityType.TRIDENT)
            ? TriggerType.ON_TRIDENT_CHANNEL
            : TriggerType.ON_PROJECTILE_IMPACT;
        MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, trigger, null, hitResult.getLocation());
        CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, null, hitResult.getLocation());
    }

    private static ItemStack resolveFiringWeapon(Projectile self, ServerPlayer shooter) {
        ItemStack firedFrom = self.getWeaponItem();
        if (firedFrom != null && !firedFrom.isEmpty()) {
            return firedFrom;
        }
        return shooter.getMainHandItem();
    }
}
