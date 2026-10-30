package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.ProjectilePathTracker;
import enchantment_fusion.model.FusedPair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ranged Bow &amp; Crossbow Signature Resonances: inward-curving pincer volleys,
 * post-pierce fission child arrows, solar lightning arcs, and path-echo bowstrings.
 */
public final class RangedSignatures {
    private RangedSignatures() {}

    public static void tickProjectile(ServerLevel level, Projectile proj) {
        ConvergingArrowState state = CONVERGING_ARROWS.get(proj.getUUID());
        if (state == null) {
            return;
        }
        long age = level.getGameTime() - state.spawnTick();
        if (!proj.isAlive() || proj.onGround() || age > 40L) {
            CONVERGING_ARROWS.remove(proj.getUUID());
            return;
        }
        if (age >= 5L) {
            Vec3 rel = proj.position().subtract(state.aimOrigin());
            double forwardDist = Math.max(2.0D, rel.dot(state.aimDir()));
            Vec3 focusPoint = state.aimOrigin().add(state.aimDir().scale(forwardDist + 2.8D));
            Vec3 curVel = proj.getDeltaMovement();
            double speed = curVel.length();
            if (speed > 0.35D) {
                Vec3 desired = focusPoint.subtract(proj.position()).normalize().scale(speed);
                proj.setDeltaMovement(curVel.scale(0.76D).add(desired.scale(0.24D)));
                proj.hurtMarked = true;
            }
        }
    }

    public static boolean executeCombat(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        FusedPair pair,
        String sigId,
        @Nullable LivingEntity target,
        Vec3 pos
    ) {
        RandomSource rng = level.getRandom();
        int pLv = Math.max(1, pair.primaryLevel());
        int sLv = Math.max(1, pair.secondaryLevel());

        switch (sigId) {
            case "scald_plume_arrow" -> {
                if (target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 2.5F + 1.15F * pLv + 1.0F * sLv);
                    target.clearFire();
                    grantEffect(target, "blindness", 45, 0);
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.7D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.06D);
                return true;
            }
            case "permafrost_brand_arrow" -> {
                if (target != null) {
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 130));
                    target.igniteForSeconds(2.5F + 1.5F * sLv);
                    grantEffect(target, "slowness", 70, 2);
                    if (rng.nextFloat() < 0.40F && player.mayBuild()) {
                        AberrantRegistry.placeFrostedIceSnare(level, target.blockPosition());
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.04D);
                return true;
            }
            case "swift_draw_burst" -> {
                if (target != null) {
                    boolean closeRange = player.distanceToSqr(target) <= 42.0D;
                    if (target.isBlocking()) target.stopUsingItem();
                    float burst = closeRange ? (3.6F + 0.75F * sLv) : (1.8F + 0.35F * sLv);
                    target.hurtServer(level, level.damageSources().magic(), burst);
                    if (closeRange) {
                        target.push(0.0D, 0.34D, 0.0D);
                        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.7D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                }
                grantEffect(player, "haste", 60, 0);
                return true;
            }
            case "overdraw_snipe" -> {
                if (target != null) {
                    boolean alreadyMarked = target.isCurrentlyGlowing();
                    grantEffect(target, "glowing", 100, 0);
                    float bonus = (alreadyMarked ? 3.2F : 1.7F) + 0.45F * pLv + target.getArmorValue() * 0.14F;
                    target.hurtServer(level, level.damageSources().magic(), bonus);
                    grantEffect(player, "haste", 70, 1);
                    grantEffect(player, "speed", 55 + 15 * sLv, 0);
                }
                return true;
            }
            case "starlight_quiver" -> {
                if (target != null) {
                    if (hostStack.isDamaged()) {
                        hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - (2 + sLv)));
                    }
                    float integrity = hostStack.isDamageableItem()
                        ? (1.0F - (float) hostStack.getDamageValue() / (float) Math.max(1, hostStack.getMaxDamage()))
                        : 1.0F;
                    target.hurtServer(level, level.damageSources().magic(), 2.0F + 0.55F * pLv + 1.4F * integrity);
                    level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.8D, pos.z, 10, 0.3D, 0.3D, 0.3D, 0.06D);
                }
                return true;
            }
            case "spectral_condense" -> {
                if (hostStack.isDamaged()) hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                if (target != null) {
                    boolean marked = target.isCurrentlyGlowing();
                    grantEffect(target, "glowing", 120, 0);
                    target.hurtServer(level, level.damageSources().magic(), (marked ? 3.4F : 2.0F) + 0.5F * pLv);
                    level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.9D, pos.z, 10, 0.25D, 0.3D, 0.25D, 0.05D);
                }
                if (rng.nextFloat() < 0.32F) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.SPECTRAL_ARROW, 1));
                }
                return true;
            }
            case "converging_volley" -> {
                if (target != null) {
                    hurtBypassingIframe(level, target, level.damageSources().magic(), 3.0F + 0.55F * sLv);
                }
                for (LivingEntity sideMob : nearbyHostiles(level, player, pos, 3.8D, target)) {
                    Vec3 pull = pos.subtract(sideMob.position()).scale(0.38D);
                    sideMob.push(pull.x, 0.14D, pull.z);
                    hurtBypassingIframe(level, sideMob, level.damageSources().magic(), 1.9F + 0.35F * sLv);
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.8D, pos.z, 10, 0.3D, 0.2D, 0.3D, 0.06D);
                return true;
            }
            case "fission_warhead" -> {
                if (target != null && FISSION_SPLIT_PROCESSED.add(target.getUUID())) {
                    if (FISSION_SPLIT_PROCESSED.size() > 2048) FISSION_SPLIT_PROCESSED.clear();
                    Vec3 forward = player.getLookAngle().normalize();
                    Vec3 spawnBehind = target.getEyePosition().add(forward.scale(0.95D));
                    for (float yawDeg : new float[]{-14.0F, 14.0F}) {
                        Vec3 splitDir = forward.yRot((float) Math.toRadians(-yawDeg)).normalize();
                        Arrow child = new Arrow(level, player, new ItemStack(Items.ARROW), null);
                        child.setPos(spawnBehind.x, spawnBehind.y - 0.15D, spawnBehind.z);
                        child.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        child.setBaseDamage(1.8D + 0.35D * pLv);
                        child.shoot(splitDir.x, splitDir.y, splitDir.z, 2.2F, 0.5F);
                        level.addFreshEntity(child);
                    }
                    level.sendParticles(ParticleTypes.CRIT, spawnBehind.x, spawnBehind.y, spawnBehind.z, 14, 0.35D, 0.2D, 0.35D, 0.15D);
                    level.getServer().execute(() -> FISSION_SPLIT_PROCESSED.remove(target.getUUID()));
                }
                return true;
            }
            case "explosive_flare_arrow", "thunder_pyre_lance" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.igniteForSeconds(4.0F);
                    mob.hurtServer(level, level.damageSources().magic(), "thunder_pyre_lance".equals(sigId) ? 3.6F : 3.2F);
                    Vec3 blast = mob.position().subtract(pos).normalize().scale(0.75D);
                    mob.push(blast.x, 0.38D, blast.z);
                }
                level.sendParticles(
                    "thunder_pyre_lance".equals(sigId) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.5D, pos.z, 10, 0.35D, 0.25D, 0.35D, 0.05D
                );
                return true;
            }
            case "meteor_streak", "sunfire_barrage" -> {
                if (target != null) {
                    target.igniteForSeconds("sunfire_barrage".equals(sigId) ? 7.0F : 6.0F);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, "sunfire_barrage".equals(sigId) ? 4.8D : 2.8D, target)) {
                        mob.igniteForSeconds(5.0F);
                        if ("sunfire_barrage".equals(sigId)) mob.hurtServer(level, level.damageSources().onFire(), 1.8F);
                    }
                }
                return true;
            }
            case "solar_arc_arrow" -> {
                if (target != null) {
                    target.igniteForSeconds(5.0F);
                    target.hurtServer(level, level.damageSources().magic(), 2.2F + 0.6F * pLv);
                    if (level.canSeeSky(target.blockPosition()) && rng.nextFloat() < 0.45F) {
                        AberrantRegistry.spawnLightning(level, target.position());
                    } else {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 16, 0.3D, 0.4D, 0.3D, 0.1D);
                    }
                }
                return true;
            }
            case "eternal_bowstring" -> {
                if (hostStack.isDamaged() && rng.nextFloat() < 0.75F) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                grantEffect(player, "haste", 60, 0);
                ProjectilePathTracker.CachedFlightPath echoCorridor = ProjectilePathTracker.recordSyntheticCorridor(
                    level, player, ProjectilePathTracker.classifyWeaponItem(hostStack), hostStack,
                    player.getEyePosition(), pos, 7
                );
                ProjectilePathTracker.schedulePathEcho(echoCorridor, 8L, 2.5F + 0.55F * sLv, false, false);
                level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.65F);
                return true;
            }
            case "threading_needle" -> {
                Vec3 start = player.getEyePosition();
                List<LivingEntity> threaded = ProjectilePathTracker.querySweptSegmentHostiles(level, player, start, pos, 1.65D);
                for (LivingEntity mob : threaded) {
                    Vec3 stitch = pos.subtract(mob.position()).scale(0.52D);
                    mob.push(stitch.x, 0.16D, stitch.z);
                    mob.hurtServer(level, level.damageSources().magic(), 2.0F + 0.45F * sLv);
                }
                for (LivingEntity near : nearbyHostiles(level, player, pos, 3.8D, target)) {
                    Vec3 stitch = pos.subtract(near.position()).scale(0.45D);
                    near.push(stitch.x, 0.14D, stitch.z);
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.7D, pos.z, 12, 0.35D, 0.25D, 0.35D, 0.04D);
                return true;
            }
            case "bamboo_split_momentum" -> {
                List<LivingEntity> line = ProjectilePathTracker.querySweptSegmentHostiles(level, player, player.getEyePosition(), pos.add(player.getLookAngle().scale(3.0D)), 1.45D);
                int idx = 0;
                for (LivingEntity mob : line) {
                    hurtBypassingIframe(level, mob, level.damageSources().magic(), (2.0F + 0.45F * pLv) * (1.0F + 0.20F * idx++));
                }
                grantEffect(player, "haste", 70, Math.min(2, sLv - 1));
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
