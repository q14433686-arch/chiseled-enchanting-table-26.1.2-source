package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.TriggerType;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link Projectile#onHit(HitResult)} and snapshots the firing weapon at owner-bind / launch
 * without falling back to {@code shooter.getMainHandItem()} at impact time (EF-009 / EF-010):
 * <ul>
 *   <li>Retains {@code ef$launchWeapon} across piercing multi-hits.</li>
 *   <li>Tridents dispatch both {@code ON_TRIDENT_CHANNEL} and {@code ON_PROJECTILE_IMPACT} (MIX-02).</li>
 *   <li>Fishing bobber hits do NOT fire reel-in signatures early (MIX-03).</li>
 * </ul>
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactFusionMixin {
    @Unique
    private ItemStack ef$launchWeapon = ItemStack.EMPTY;

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void ef$captureLaunchWeaponOnOwnerBind(@Nullable Entity owner, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel) || !(owner instanceof ServerPlayer shooter)) {
            return;
        }
        ItemStack firedFrom = self.getWeaponItem();
        if (firedFrom != null && !firedFrom.isEmpty()) {
            this.ef$launchWeapon = firedFrom.copy();
        } else if (self.getType() == EntityType.FISHING_BOBBER) {
            ItemStack main = shooter.getMainHandItem();
            if (main.getItem() instanceof FishingRodItem) {
                this.ef$launchWeapon = main.copy();
            } else {
                ItemStack off = shooter.getOffhandItem();
                if (off.getItem() instanceof FishingRodItem) {
                    this.ef$launchWeapon = off.copy();
                }
            }
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void ef$refreshWeaponFromVanillaBeforeHit(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.level() instanceof ServerLevel) {
            ItemStack w = self.getWeaponItem();
            if (w != null && !w.isEmpty()) {
                this.ef$launchWeapon = w.copy();
            }
        }
    }

    @Inject(method = "onHit", at = @At("TAIL"))
    private void ef$afterOnHit(HitResult hitResult, CallbackInfo ci) {
        ItemStack weapon = this.ef$launchWeapon;
        if (weapon == null || weapon.isEmpty() || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        // MIX-03: Fishing bobber impact does NOT execute reel-in signatures (handled on actual reel-in)
        if (self.getType() == EntityType.FISHING_BOBBER) {
            return;
        }

        LivingEntity target = (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity liv)
            ? liv
            : null;

        // MIX-02: Thrown Tridents accept both ON_TRIDENT_CHANNEL and ON_PROJECTILE_IMPACT fusions!
        Set<TriggerType> triggers = (self.getType() == EntityType.TRIDENT)
            ? EnumSet.of(TriggerType.ON_TRIDENT_CHANNEL, TriggerType.ON_PROJECTILE_IMPACT)
            : EnumSet.of(TriggerType.ON_PROJECTILE_IMPACT);

        if (target != null) {
            int prevInv = target.invulnerableTime;
            target.invulnerableTime = 0;
            try {
                MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, triggers, target, hitResult.getLocation(), true, 1.0F);
                CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, target, hitResult.getLocation());
            } finally {
                target.invulnerableTime = prevInv;
            }
        } else if (hitResult instanceof BlockHitResult) {
            MatrixEffectExecutor.dispatch(serverLevel, shooter, weapon, triggers, null, hitResult.getLocation(), true, 1.0F);
            CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, null, hitResult.getLocation());
        }
    }
}
