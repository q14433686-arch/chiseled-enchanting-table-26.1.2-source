package enchantment_fusion.mechanics.transmute;

import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getTransmutedLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.FlyingBladeManager;
import enchantment_fusion.mechanics.ProjectileLaunchWeaponAccess;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport;
import enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.ConvergingArrowState;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ranged Launch, Flight Physics, and Impact Mechanics for Bow / Crossbow / Trident.
 * Fixes non-native Multishot ({@code fan_volley} &amp; {@code [minecraft:multishot ▷ X]}) and
 * non-native Piercing ({@code sun_piercer} &amp; {@code [minecraft:piercing ▷ X]}) at {@code Projectile#shoot} time!
 */
public final class RangedProjectileTransmutes {
    private RangedProjectileTransmutes() {}

    /**
     * Invoked at the tail of {@code Projectile#shoot(DDDFF)V} when velocity &amp; owner are finalized.
     */
    public static void onProjectileLaunched(
        ServerLevel level,
        Projectile proj,
        ServerPlayer shooter,
        ItemStack weapon,
        double dirX,
        double dirY,
        double dirZ,
        float velocity,
        float inaccuracy
    ) {
        if (weapon == null || weapon.isEmpty() || Boolean.TRUE.equals(IN_FAN_LAUNCH.get())) {
            return;
        }
        // Crossbows already invoke vanilla Multishot/Piercing if native, only apply non-native Bow/Trident hooks!
        boolean isCrossbow = weapon.getItem() instanceof CrossbowItem;

        // 1. Apply real AbstractArrow pierce level for Bow sun_piercer & binary [minecraft:piercing ▷ X] pairs!
        if (!isCrossbow && proj instanceof AbstractArrow arrow && !(proj instanceof ThrownTrident)) {
            int pierceAdd = getTransmutedLevel(weapon, "sun_piercer");
            FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (FusedPair p : pairs.pairs()) {
                if ("minecraft:piercing".equals(p.primaryId())) {
                    pierceAdd = Math.max(pierceAdd, p.primaryLevel());
                } else if ("minecraft:piercing".equals(p.secondaryId())) {
                    pierceAdd = Math.max(pierceAdd, Math.max(1, p.secondaryLevel() - 1));
                }
            }
            if (pierceAdd > 0) {
                int nextPierce = Math.min(12, arrow.getPierceLevel() + pierceAdd);
                ((AbstractArrowAccessor) arrow).ef$setPierceLevel((byte) nextPierce);
            }
        }

        // 2. Spawn real ±10° flanking projectiles for Bow fan_volley / [minecraft:multishot ▷ X] & Trident fan_volley!
        boolean hasFanVolley = getTransmutedLevel(weapon, "fan_volley") > 0;
        boolean hasMultishotPair = false;
        FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair p : pairs.pairs()) {
            if ("minecraft:multishot".equals(p.primaryId()) || "minecraft:multishot".equals(p.secondaryId())) {
                hasMultishotPair = true;
                break;
            }
        }
        if (!hasFanVolley && !hasMultishotPair) {
            return;
        }
        if (isCrossbow) {
            return;
        }

        IN_FAN_LAUNCH.set(Boolean.TRUE);
        try {
            Vec3 baseAim = new Vec3(dirX, dirY, dirZ);
            if (baseAim.lengthSqr() < 1.0E-5D) {
                baseAim = shooter.getLookAngle();
            }
            baseAim = baseAim.normalize();

            if (proj instanceof ThrownTrident) {
                FlyingBladeManager.launchAngledPhantomWeapon(
                    level, shooter, InteractionHand.MAIN_HAND, weapon, 2,
                    FlyingBladeManager.FlightStyle.PHANTOM_LANCE, -10.0F
                );
                FlyingBladeManager.launchAngledPhantomWeapon(
                    level, shooter, InteractionHand.MAIN_HAND, weapon, 2,
                    FlyingBladeManager.FlightStyle.PHANTOM_LANCE, 10.0F
                );
                return;
            }

            if (weapon.getItem() instanceof BowItem && proj instanceof AbstractArrow mainArrow) {
                boolean converging = SignaturePairs.stackHasSignature(weapon, "converging_volley");
                for (float yawDeg : new float[]{-10.0F, 10.0F}) {
                    Vec3 rotDir = baseAim.yRot((float) Math.toRadians(-yawDeg)).normalize();
                    AbstractArrow sideArrow = (mainArrow instanceof SpectralArrow)
                        ? new SpectralArrow(level, shooter, new ItemStack(Items.SPECTRAL_ARROW), weapon.copyWithCount(1))
                        : new Arrow(level, shooter, new ItemStack(Items.ARROW), weapon.copyWithCount(1));
                    sideArrow.setPos(mainArrow.getX(), mainArrow.getY(), mainArrow.getZ());
                    sideArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    sideArrow.setCritArrow(mainArrow.isCritArrow());
                    sideArrow.setBaseDamage(1.8D);
                    if (mainArrow.getPierceLevel() > 0) {
                        ((AbstractArrowAccessor) sideArrow).ef$setPierceLevel(mainArrow.getPierceLevel());
                    }
                    if (mainArrow.isOnFire()) {
                        sideArrow.igniteForSeconds(100.0F);
                    }
                    if (sideArrow instanceof ProjectileLaunchWeaponAccess acc) {
                        acc.ef$setLaunchWeapon(weapon);
                    }
                    sideArrow.shoot(rotDir.x, rotDir.y, rotDir.z, velocity, inaccuracy);
                    level.addFreshEntity(sideArrow);
                    if (converging) {
                        SignatureRuntimeSupport.CONVERGING_ARROWS.put(
                            sideArrow.getUUID(),
                            new ConvergingArrowState(shooter.getUUID(), shooter.getEyePosition(), baseAim, level.getGameTime())
                        );
                    }
                }
            }
        } finally {
            IN_FAN_LAUNCH.set(Boolean.FALSE);
        }
    }

    public static void onProjectileTick(ServerLevel level, Projectile proj, ServerPlayer shooter, ItemStack weapon) {
        if (!proj.isAlive()) return;
        SignaturePairs.tickProjectile(level, proj);
        if (weapon.isEmpty()) return;

        if (LAUNCH_PROCESSED_PROJ.size() > 4096) LAUNCH_PROCESSED_PROJ.clear();
        if (LAUNCH_PROCESSED_PROJ.add(proj.getUUID())) {
            double speedBoost = 1.0D;
            if (getTransmutedLevel(weapon, "yishui") > 0 && shooter.isInWaterOrRain()) speedBoost *= 1.30D;
            int rapidDraw = Math.max(getTransmutedLevel(weapon, "swift_draw"), getTransmutedLevel(weapon, "slipstream_wings"));
            if (rapidDraw > 0) speedBoost *= (1.0D + 0.12D * rapidDraw);
            int hydroLv = getTransmutedLevel(weapon, "hydro_propulsion");
            if (hydroLv > 0 && shooter.isInWaterOrRain()) {
                speedBoost *= (1.0D + 0.15D * hydroLv);
                Vec3 forward = shooter.getLookAngle().normalize().scale(0.55D + 0.18D * hydroLv);
                shooter.push(forward.x, 0.18D, forward.z);
                shooter.hurtMarked = true;
            }
            if (speedBoost != 1.0D) {
                proj.setDeltaMovement(proj.getDeltaMovement().scale(speedBoost));
            }
            int counterLv = getTransmutedLevel(weapon, "counter_volley");
            if (counterLv > 0) deflectNearbyProjectiles(level, shooter, 3.2D + 0.6D * counterLv);

            int vaultLv = getTransmutedLevel(weapon, "lunge_vault_shot");
            if (vaultLv > 0) {
                Vec3 horiz = new Vec3(shooter.getLookAngle().x, 0.0D, shooter.getLookAngle().z);
                if (horiz.lengthSqr() > 0.001D) {
                    Vec3 step = horiz.normalize().scale(0.38D + 0.12D * vaultLv);
                    shooter.push(step.x, 0.16D, step.z);
                    shooter.hurtMarked = true;
                    level.sendParticles(ParticleTypes.CLOUD, shooter.getX(), shooter.getY() + 0.2D, shooter.getZ(), 6, 0.2D, 0.1D, 0.2D, 0.04D);
                }
            }
            int recoilLv = getTransmutedLevel(weapon, "recoil_jump");
            if (recoilLv > 0 && shooter.getXRot() > 55.0F) {
                Vec3 recoil = shooter.getLookAngle().normalize().scale(-0.75D - 0.22D * recoilLv);
                shooter.setDeltaMovement(shooter.getDeltaMovement().add(recoil.x, Math.max(0.55D, recoil.y), recoil.z));
                shooter.hurtMarked = true;
                shooter.resetFallDistance();
            }
            if (getTransmutedLevel(weapon, "endless_crossbow") > 0 && proj.getType() == EntityType.ARROW && !shooter.getAbilities().instabuild) {
                shooter.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
            }
        }

        if (proj.onGround()) return;

        if (getTransmutedLevel(weapon, "wave_parting") > 0 && proj.isInWater()) {
            Vec3 v = proj.getDeltaMovement();
            if (v.lengthSqr() < 4.5D) proj.setDeltaMovement(v.scale(1.45D));
        }
        if (getTransmutedLevel(weapon, "feather_flight") > 0) {
            Vec3 v = proj.getDeltaMovement();
            proj.setDeltaMovement(v.x, v.y + 0.036D, v.z);
        }
        if (getTransmutedLevel(weapon, "heavy_slug_arrow") > 0) {
            Vec3 v = proj.getDeltaMovement();
            proj.setDeltaMovement(v.x, v.y - 0.03D, v.z);
        }
        if (getTransmutedLevel(weapon, "soul_seeker_arrow") > 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, proj.getX(), proj.getY(), proj.getZ(), 1, 0.02D, 0.02D, 0.02D, 0.0D);
            for (LivingEntity mob : nearbyHostiles(level, shooter, proj.position(), 5.0D, null)) {
                if (mob.getHealth() < mob.getMaxHealth() * 0.75F) {
                    Vec3 cur = proj.getDeltaMovement();
                    double spd = cur.length();
                    if (spd > 0.2D) {
                        Vec3 toMob = mob.getEyePosition().subtract(proj.position()).normalize();
                        proj.setDeltaMovement(cur.normalize().scale(0.84D).add(toMob.scale(0.16D)).normalize().scale(spd));
                    }
                    break;
                }
            }
        }
    }

    public static void onProjectileImpact(
        ServerLevel level,
        @Nullable Projectile proj,
        ServerPlayer shooter,
        ItemStack weapon,
        @Nullable LivingEntity target,
        Vec3 hitPos
    ) {
        if (weapon.isEmpty()) return;
        RandomSource rng = level.getRandom();
        Vec3 flightDir = (proj != null && proj.getDeltaMovement().lengthSqr() > 0.001D)
            ? proj.getDeltaMovement().normalize()
            : shooter.getLookAngle().normalize();

        if (target != null) {
            if (getTransmutedLevel(weapon, "yishui") > 0 && shooter.isInWaterOrRain()) {
                target.hurtServer(level, level.damageSources().magic(), 2.8F);
            }
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 70 * frostLv));
                grantEffect(target, "slowness", 55, frostLv - 1);
                if (target.getTicksFrozen() >= 140 || rng.nextFloat() < 0.28F) {
                    AberrantRegistry.placeSingleFrostedIce(level, target.blockPosition());
                }
            }
            int breechLv = getTransmutedLevel(weapon, "blast_breech");
            if (breechLv > 0) {
                for (LivingEntity mob : nearbyHostiles(level, shooter, hitPos, 2.6D, null)) {
                    mob.hurtServer(level, level.damageSources().explosion(shooter, proj != null ? proj : shooter), 1.6F + 0.5F * breechLv);
                    Vec3 out = mob.position().subtract(hitPos).normalize().scale(0.45D);
                    mob.push(out.x, 0.25D, out.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y + 0.4D, hitPos.z, 2, 0.15D, 0.15D, 0.15D, 0.0D);
            }
            int barbLv = getTransmutedLevel(weapon, "barbed_arrow");
            if (barbLv > 0) {
                grantEffect(target, "slowness", 65, barbLv - 1);
                Vec3 yank = shooter.position().subtract(target.position()).normalize().scale(0.38D + 0.12D * barbLv);
                target.push(yank.x, 0.16D, yank.z);
                target.hurtServer(level, level.damageSources().magic(), 1.2F * barbLv);
            }
            int holyBow = getTransmutedLevel(weapon, "holy_bolt");
            if (holyBow > 0) {
                for (int h = 0; h <= 3; h++) {
                    level.sendParticles(ParticleTypes.END_ROD, hitPos.x, hitPos.y + h * 0.7D, hitPos.z, 3, 0.1D, 0.1D, 0.1D, 0.02D);
                }
                if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)) {
                    target.igniteForSeconds(4.0F);
                    target.hurtServer(level, level.damageSources().magic(), 2.8F * holyBow);
                }
            }
            int bugBow = getTransmutedLevel(weapon, "arthropod_bolt");
            if (bugBow > 0 && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.ARTHROPOD)) {
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                target.hurtServer(level, level.damageSources().magic(), 2.6F * bugBow);
                grantEffect(target, "slowness", 75, 3);
            }
            int slugLv = getTransmutedLevel(weapon, "heavy_slug_arrow");
            if (slugLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.6F * slugLv);
                Vec3 heavyPush = flightDir.scale(0.55D * slugLv);
                target.push(heavyPush.x, 0.22D, heavyPush.z);
            }
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 2.0F * impaleLv);
            }
            int breachLv = getTransmutedLevel(weapon, "hard_breach");
            if (breachLv > 0) {
                if (target.isBlocking()) target.stopUsingItem();
                if (target.getArmorValue() > 0) {
                    target.hurtServer(level, level.damageSources().magic(), 1.3F * breachLv);
                }
            }
            int windCut = getTransmutedLevel(weapon, "wind_cutter_arrow");
            if (windCut > 0) {
                for (LivingEntity adj : nearbyHostiles(level, shooter, hitPos, 3.2D, target)) {
                    Vec3 pull = hitPos.subtract(adj.position()).scale(0.32D);
                    adj.push(pull.x, 0.14D, pull.z);
                    adj.hurtServer(level, level.damageSources().magic(), 1.6F + windCut);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y + 0.6D, hitPos.z, 2, 0.3D, 0.1D, 0.3D, 0.0D);
            }
            int pierceBow = getTransmutedLevel(weapon, "sun_piercer");
            if (pierceBow > 0 && target.isBlocking()) {
                target.stopUsingItem();
            }
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.28F + 0.14F * fortuneLv)) {
                triggerThreeDollarRoulette(level, shooter, target, fortuneLv);
            }
            if (getTransmutedLevel(weapon, "sticky_root_curse") > 0 && rng.nextFloat() < 0.30F) {
                grantEffect(target, "slowness", 45, 4);
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
            }
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(hitPos.x, hitPos.y, hitPos.z);
                bolt.setCause(shooter);
                level.addFreshEntity(bolt);
            }
        }

        int boomLv = getTransmutedLevel(weapon, "boomerang_arrow");
        if (boomLv > 0 && proj != null && proj.getType() == EntityType.ARROW && !shooter.getAbilities().instabuild && rng.nextFloat() < (0.30F + 0.18F * boomLv)) {
            shooter.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
            proj.discard();
        }

        int windArrowLv = getTransmutedLevel(weapon, "wind_charge_arrow");
        if (windArrowLv > 0 && (proj == null || !proj.isInWater())) {
            for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(hitPos, 5.2D, 5.2D, 5.2D), Entity::isAlive)) {
                Vec3 away = ent.position().add(0.0D, 0.5D, 0.0D).subtract(hitPos);
                double d = Math.max(0.6D, away.length());
                Vec3 boost = away.normalize().scale((0.95D + 0.2D * windArrowLv) / (d * 0.55D));
                ent.setDeltaMovement(ent.getDeltaMovement().add(boost.x, Math.max(0.55D, boost.y), boost.z));
                ent.hurtMarked = true;
                if (ent == shooter) shooter.resetFallDistance();
            }
            level.sendParticles(ParticleTypes.CLOUD, hitPos.x, hitPos.y + 0.3D, hitPos.z, 16, 0.5D, 0.25D, 0.5D, 0.1D);
        }

        int lureBowLv = getTransmutedLevel(weapon, "lure_hook_arrow");
        if (lureBowLv > 0) {
            if (target != null) {
                Vec3 yank = shooter.position().subtract(target.position()).normalize().scale(0.75D + 0.2D * lureBowLv);
                target.push(yank.x, 0.3D, yank.z);
            } else {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.ofSize(hitPos, 18.0D, 8.0D, 18.0D), Entity::isAlive)) {
                    mob.getNavigation().moveTo(hitPos.x, hitPos.y, hitPos.z, 1.2D);
                }
                level.sendParticles(ParticleTypes.HEART, hitPos.x, hitPos.y + 0.6D, hitPos.z, 4, 0.3D, 0.2D, 0.3D, 0.02D);
            }
        }
    }
}
