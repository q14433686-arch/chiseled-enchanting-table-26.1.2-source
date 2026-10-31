package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.ProjectileLaunchWeaponAccess;
import enchantment_fusion.mechanics.ProjectilePathTracker;
import enchantment_fusion.mechanics.ProjectilePathTracker.CachedFlightPath;
import enchantment_fusion.mechanics.ProjectilePathTracker.PathTrailStyle;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.signatures.PhantomVolley;
import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.mechanics.signatures.PiercingChainSkills;
import enchantment_fusion.model.TriggerType;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@link Projectile#onHit(HitResult)}, snapshots the firing weapon at owner-bind / launch,
 * feeds Arrow &amp; Trident flight nodes into {@link ProjectilePathTracker} ({@code 路径暂缓存判定}),
 * and dispatches multi-slot fusion reactions on impact (EF-009 / EF-010 / MIX-02 / MIX-03).
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactFusionMixin implements ProjectileLaunchWeaponAccess {
    @Unique
    private ItemStack ef$launchWeapon = ItemStack.EMPTY;

    @Override
    public ItemStack ef$getLaunchWeapon() {
        return this.ef$launchWeapon == null ? ItemStack.EMPTY : this.ef$launchWeapon;
    }

    @Override
    public void ef$setLaunchWeapon(ItemStack weapon) {
        this.ef$launchWeapon = (weapon == null || weapon.isEmpty()) ? ItemStack.EMPTY : weapon.copyWithCount(1);
    }

    @Inject(method = "setOwner(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void ef$captureLaunchWeaponOnOwnerBind(@Nullable Entity owner, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(owner instanceof ServerPlayer shooter)) {
            return;
        }
        ItemStack firedFrom = ef$actualWeapon(self);
        if (firedFrom != null && !firedFrom.isEmpty()) {
            this.ef$launchWeapon = firedFrom.copyWithCount(1);
        } else if (self.getType() == EntityType.FISHING_BOBBER) {
            ItemStack main = shooter.getMainHandItem();
            if (main.getItem() instanceof FishingRodItem) {
                this.ef$launchWeapon = main.copyWithCount(1);
            } else {
                ItemStack off = shooter.getOffhandItem();
                if (off.getItem() instanceof FishingRodItem) {
                    this.ef$launchWeapon = off.copyWithCount(1);
                }
            }
            return;
        } else if (this.ef$launchWeapon.isEmpty()) {
            ItemStack using = shooter.getUseItem();
            if (!using.isEmpty() && isProjectileLauncher(using)) {
                this.ef$launchWeapon = using.copyWithCount(1);
            } else {
                ItemStack main = shooter.getMainHandItem();
                if (isProjectileLauncher(main)) {
                    this.ef$launchWeapon = main.copyWithCount(1);
                } else {
                    ItemStack off = shooter.getOffhandItem();
                    if (isProjectileLauncher(off)) {
                        this.ef$launchWeapon = off.copyWithCount(1);
                    }
                }
            }
        }

        if (!this.ef$launchWeapon.isEmpty() && self.getType() != EntityType.FISHING_BOBBER) {
            ProjectilePathTracker.recordProjectileStep(serverLevel, self, shooter, this.ef$launchWeapon);
        }
    }

    @Inject(method = "shoot(DDDFF)V", at = @At("TAIL"))
    private void ef$afterProjectileShot(
        double x,
        double y,
        double z,
        float velocity,
        float inaccuracy,
        CallbackInfo ci
    ) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }
        if (self.getType() == EntityType.FISHING_BOBBER) {
            return;
        }
        ItemStack weapon = this.ef$launchWeapon;
        if (weapon == null || weapon.isEmpty()) {
            ItemStack w = ef$actualWeapon(self);
            if (w != null && !w.isEmpty()) {
                weapon = w.copyWithCount(1);
                this.ef$launchWeapon = weapon;
            }
        }
        ItemStack exactLauncher = HeavyCrushProjectiles.exactLauncher(shooter);
        ItemStack heavyLauncher = exactLauncher != null ? exactLauncher : weapon;
        if (heavyLauncher != null && !heavyLauncher.isEmpty()) {
            HeavyCrushProjectiles.markLaunch(self, heavyLauncher);
        }
        if (weapon != null && !weapon.isEmpty()) {
            CrossDomainTransmutations.onProjectileLaunched(
                serverLevel, self, shooter, weapon, x, y, z, velocity, inaccuracy
            );
        }
    }

    @Unique
    private static @Nullable ItemStack ef$actualWeapon(Projectile projectile) {
        // Thrown tridents carry the actual trident in the pickup stack; their ordinary
        // getWeaponItem() can be null. This also survives chunk save/reload and switching hands.
        if (projectile instanceof ThrownTrident trident) {
            ItemStack thrown = trident.getPickupItemStackOrigin();
            if (thrown != null && !thrown.isEmpty()) return thrown;
        }
        return projectile.getWeaponItem();
    }

    @Unique
    private static boolean isProjectileLauncher(ItemStack stack) {
        return stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof TridentItem;
    }

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void ef$protectPhantomCollision(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
        Projectile self = (Projectile) (Object) this;
        if (self.level() instanceof ServerLevel && hit instanceof EntityHitResult entityHit
            && PhantomVolley.protectedCollision(self, entityHit.getEntity())) {
            self.discard();
            cir.setReturnValue(ProjectileDeflection.NONE);
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void ef$refreshWeaponFromVanillaBeforeHit(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.level() instanceof ServerLevel) {
            ItemStack w = ef$actualWeapon(self);
            if (w != null && !w.isEmpty()) {
                this.ef$launchWeapon = w.copyWithCount(1);
            }
        }
    }

    @Inject(method = "onHit", at = @At("TAIL"))
    private void ef$afterOnHit(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.level() instanceof ServerLevel && PhantomVolley.isGhost(self)) {
            // The arrow still ran vanilla block collision, but never runs secondary fusion
            // dispatch or leaves behind recoverable ammunition.
            if (hitResult instanceof BlockHitResult) self.discard();
            return;
        }
        ItemStack weapon = this.ef$launchWeapon;
        if (weapon == null || weapon.isEmpty() || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        if (!(self.level() instanceof ServerLevel serverLevel) || !(self.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        // MIX-03: Fishing bobber impact does NOT execute reel-in signatures (handled on actual reel-in)
        if (self.getType() == EntityType.FISHING_BOBBER) {
            return;
        }

        Vec3 hitPos = hitResult.getLocation();
        CachedFlightPath cachedPath = ProjectilePathTracker.recordStep(
            self.getUUID(),
            shooter.getUUID(),
            serverLevel.dimension(),
            ProjectilePathTracker.classifyProjectile(self),
            weapon,
            hitPos,
            self.getDeltaMovement(),
            serverLevel.getGameTime(),
            self.isInWater()
        );
        ProjectilePathTracker.checkAndTriggerCrossPathResonance(serverLevel, shooter, cachedPath, 4.5F);
        applyProjectilePathSignatures(serverLevel, shooter, weapon, cachedPath);

        LivingEntity target = (hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity liv)
            ? liv
            : null;

        Set<TriggerType> triggers = (self.getType() == EntityType.TRIDENT)
            ? EnumSet.of(TriggerType.ON_TRIDENT_CHANNEL, TriggerType.ON_PROJECTILE_IMPACT)
            : EnumSet.of(TriggerType.ON_PROJECTILE_IMPACT);

        if (target != null) {
            int prevInv = target.invulnerableTime;
            float hpBefore = target.getHealth();
            // MIX-02: Allow at most ONE bonus fusion damage proc to bypass the base projectile's invulnerableTime;
            // once any fusion proc lands damage, invulnerableTime is immediately restored so secondary slots cannot stack infinite burst!
            target.invulnerableTime = 0;
            try {
                MatrixEffectExecutor.dispatchAcrossEquippedSlots(
                    serverLevel, shooter, weapon, triggers, target, hitPos, true, 1.0F, true, prevInv
                );
                if (target.getHealth() < hpBefore) {
                    target.invulnerableTime = Math.max(target.invulnerableTime, prevInv);
                }
                CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, target, hitPos);
            } finally {
                target.invulnerableTime = Math.max(target.invulnerableTime, prevInv);
            }
            if (!self.isAlive()) {
                ProjectilePathTracker.markPathCompleted(self.getUUID(), hitPos, serverLevel.getGameTime());
            }
        } else if (hitResult instanceof BlockHitResult) {
            ProjectilePathTracker.markPathCompleted(self.getUUID(), hitPos, serverLevel.getGameTime());
            MatrixEffectExecutor.dispatchAcrossEquippedSlots(serverLevel, shooter, weapon, triggers, null, hitPos, true, 1.0F);
            CrossDomainTransmutations.onProjectileImpact(serverLevel, self, shooter, weapon, null, hitPos);
        }
    }

    @Unique
    private static void applyProjectilePathSignatures(
        ServerLevel level,
        ServerPlayer shooter,
        ItemStack weapon,
        CachedFlightPath path
    ) {
        if (EchoPathSkills.hasLegacySignature(weapon) || EchoPathSkills.playerHasLegacySignature(shooter)) {
            ProjectilePathTracker.schedulePathEcho(path, 12L, 3.6F, false, true);
        }
        if (SignaturePairs.stackHasSignature(weapon, "boomerang_lance")
            || SignaturePairs.playerHasEquippedSignature(shooter, "boomerang_lance")) {
            ProjectilePathTracker.schedulePathEcho(path, 9L, 4.0F, true, true);
        }
        if (PiercingChainSkills.hasLegacySignature(weapon, PiercingChainSkills.FORWARD)
            || PiercingChainSkills.playerHasLegacySignature(shooter, PiercingChainSkills.FORWARD)) {
            ProjectilePathTracker.applyPathTrailEffect(level, shooter, path, PathTrailStyle.SILK_TETHER, 1.15F);
        }
        if (SignaturePairs.stackHasSignature(weapon, "thunderbolt_cavalry")
            || SignaturePairs.playerHasEquippedSignature(shooter, "thunderbolt_cavalry")) {
            ProjectilePathTracker.applyPathTrailEffect(level, shooter, path, PathTrailStyle.VOLT_CHAIN, 1.2F);
        }
        if (SignaturePairs.stackHasSignature(weapon, "sunfire_barrage")
            || SignaturePairs.playerHasEquippedSignature(shooter, "sunfire_barrage")) {
            ProjectilePathTracker.applyPathTrailEffect(level, shooter, path, PathTrailStyle.PYRO_WAKE, 1.15F);
        }
    }
}
