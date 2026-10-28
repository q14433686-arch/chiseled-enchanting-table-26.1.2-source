package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.model.TriggerType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link Projectile#onHit(HitResult)} directly (because {@code AbstractArrow},
 * {@code ThrownTrident}, and {@code FishingHook} override {@code onHitEntity}/{@code onHitBlock}
 * without calling {@code super}) and snapshots the firing weapon before hit discard.
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactFusionMixin {
    @Unique
    private ItemStack ef$cachedHitWeapon = ItemStack.EMPTY;

    @Inject(method = "onHit", at = @At("HEAD"))
    private void ef$captureWeaponBeforeHit(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.level() instanceof ServerLevel && self.getOwner() instanceof ServerPlayer shooter) {
            ItemStack w = resolveFiringWeapon(self, shooter);
            this.ef$cachedHitWeapon = w.isEmpty() ? ItemStack.EMPTY : w.copy();
        } else {
            this.ef$cachedHitWeapon = ItemStack.EMPTY;
        }
    }

    @Inject(method = "onHit", at = @At("TAIL"))
    private void ef$afterOnHit(HitResult hitResult, CallbackInfo ci) {
        ItemStack weapon = this.ef$cachedHitWeapon;
        this.ef$cachedHitWeapon = ItemStack.EMPTY;
        if (weapon.isEmpty() || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        LivingEntity target = (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity liv)
            ? liv
            : null;

        if (self.getType() == EntityType.FISHING_BOBBER) {
            if (target != null) {
                int prevInv = target.invulnerableTime;
                target.invulnerableTime = 0;
                try {
                    SignaturePairs.onFishingReel(serverLevel, shooter, weapon, target, false);
                } finally {
                    target.invulnerableTime = prevInv;
                }
            }
            return;
        }

        TriggerType trigger = (self.getType() == EntityType.TRIDENT)
            ? TriggerType.ON_TRIDENT_CHANNEL
            : TriggerType.ON_PROJECTILE_IMPACT;

        if (target != null) {
            int prevInv = target.invulnerableTime;
            target.invulnerableTime = 0;
            try {
                MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, trigger, target, hitResult.getLocation());
                CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, target, hitResult.getLocation());
            } finally {
                target.invulnerableTime = prevInv;
            }
        } else if (hitResult instanceof BlockHitResult) {
            MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, trigger, null, hitResult.getLocation());
            CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, null, hitResult.getLocation());
        }
    }

    @Unique
    private static ItemStack resolveFiringWeapon(Projectile self, ServerPlayer shooter) {
        ItemStack firedFrom = self.getWeaponItem();
        if (firedFrom != null && !firedFrom.isEmpty()) {
            return firedFrom;
        }
        if (self.getType() == EntityType.FISHING_BOBBER) {
            ItemStack main = shooter.getMainHandItem();
            if (main.getItem() instanceof FishingRodItem) return main;
            ItemStack off = shooter.getOffhandItem();
            if (off.getItem() instanceof FishingRodItem) return off;
        }
        return shooter.getMainHandItem();
    }
}
