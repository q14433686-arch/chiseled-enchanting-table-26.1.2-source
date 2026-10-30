package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.FlyingBladeManager;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Elemental Reactions (Steam, Frostfire Cage, Superconduct, Skyfire), Trident, Mace,
 * and 26.1.2 Spear / Lunge Trajectory-Cache Signature Resonances.
 */
public final class ElementalSpearMaceSignatures {
    private ElementalSpearMaceSignatures() {}

    public static boolean executeCombat(
        ServerLevel level,
        ServerPlayer player,
        ItemStack hostStack,
        FusedPair pair,
        String sigId,
        @Nullable LivingEntity target,
        Vec3 pos,
        boolean fullChargeMelee
    ) {
        RandomSource rng = level.getRandom();
        int pLv = Math.max(1, pair.primaryLevel());
        int sLv = Math.max(1, pair.secondaryLevel());

        switch (sigId) {
            case "steam_scald" -> {
                if (target != null) {
                    float trueSteamDmg = 2.5F + 1.15F * pLv + 1.0F * sLv;
                    target.hurtServer(level, level.damageSources().magic(), trueSteamDmg);
                    target.clearFire();
                    target.push(0.0D, 0.28D, 0.0D);
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.7D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.06D);
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.7D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.04D);
                return true;
            }
            case "frostfire_cage" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 60 + 20 * pLv, 1);
                    target.igniteForSeconds(3.0F + 2.0F * sLv);
                    if (rng.nextFloat() < 0.28F) {
                        AberrantRegistry.placeFrostedIceCage3x3x3(level, target.blockPosition(), false);
                        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85F, 1.3F);
                    }
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.35D, 0.35D, 0.03D);
                return true;
            }
            case "hydro_flying_sword" -> {
                if (fullChargeMelee) {
                    FlyingBladeManager.launchLinearPhantomWeapon(
                        level, player, InteractionHand.MAIN_HAND, hostStack,
                        Math.max(pLv, sLv), FlyingBladeManager.FlightStyle.HYDRO_SWORD_QI
                    );
                }
                return true;
            }
            case "clear_sky_thunder", "thunder_iaido" -> {
                if (fullChargeMelee && target != null && level.canSeeSky(target.blockPosition())) {
                    AberrantRegistry.spawnLightning(level, target.position());
                } else if (fullChargeMelee && target != null) {
                    target.hurtServer(level, level.damageSources().magic(), 3.5F);
                    Vec3 jolt = target.position().subtract(player.position()).normalize().scale(0.45D);
                    target.push(jolt.x, 0.24D, jolt.z);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 16, 0.3D, 0.4D, 0.3D, 0.1D);
                }
                return true;
            }
            case "arc_lightning_cleave" -> {
                if (fullChargeMelee) {
                    float mult = level.isRaining() ? 1.35F : 1.0F;
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                        mob.hurtServer(level, level.damageSources().magic(), (2.4F + 0.55F * pLv) * mult);
                        mob.push(0.0D, 0.22D, 0.0D);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + 0.8D, mob.getZ(), 10, 0.25D, 0.3D, 0.25D, 0.08D);
                    }
                }
                return true;
            }
            case "thunder_avatar", "aurora_thunder_prison" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F + 0.5F * pLv);
                    grantEffect(mob, "slowness", 60, 2);
                    if ("aurora_thunder_prison".equals(sigId) && rng.nextFloat() < 0.38F && player.mayBuild()) {
                        AberrantRegistry.placeFrostedIceSnare(level, mob.blockPosition());
                    }
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 22, 0.6D, 0.3D, 0.6D, 0.1D);
                return true;
            }
            case "superconduct_shatter", "armor_shatter" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    if (mob.isBlocking()) mob.stopUsingItem();
                    float bonus = 3.0F + mob.getArmorValue() * 0.3F;
                    mob.hurtServer(level, level.damageSources().magic(), bonus);
                    grantEffect(mob, "weakness", 80, 1);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.1D);
                return true;
            }
            case "plasma_overload" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    mob.igniteForSeconds(4.0F);
                    mob.hurtServer(level, level.damageSources().magic(), 3.2F);
                    Vec3 blast = mob.position().subtract(pos).normalize().scale(0.75D);
                    mob.push(blast.x, 0.38D, blast.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5D, pos.z, 6, 0.35D, 0.25D, 0.35D, 0.05D);
                return true;
            }
            case "skyfire_lightning" -> {
                if (target != null) {
                    target.igniteForSeconds(6.0F);
                    SKYFIRE_BOLT_TICKS.put(target.getUUID(), 80);
                }
                return true;
            }
            case "flame_tornado" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    mob.igniteForSeconds(5.0F);
                    Vec3 swirl = pos.subtract(mob.position()).scale(0.28D);
                    mob.push(swirl.x, 0.58D, swirl.z);
                }
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 18, 0.5D, 0.5D, 0.5D, 0.06D);
                return true;
            }
            case "blizzard_squall" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.5D, null)) {
                    mob.setTicksFrozen(Math.min(300, mob.getTicksFrozen() + 160));
                    Vec3 gust = mob.position().subtract(player.position()).normalize().scale(0.55D);
                    mob.push(gust.x, 0.25D, gust.z);
                    grantEffect(mob, "slowness", 80, 2);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6D, pos.z, 22, 0.7D, 0.4D, 0.7D, 0.05D);
                return true;
            }
            case "meteor_crush" -> {
                float fall = (float) player.fallDistance;
                player.fallDistance = 0.0F;
                float slam = Math.min(14.0F, 3.0F + fall * 1.1F);
                double radius = Math.min(6.0D, 3.8D + fall * 0.18D);
                double launchY = Math.min(0.75D, 0.36D + fall * 0.04D);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, radius, null)) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), slam);
                    mob.push(0.0D, launchY, 0.0D);
                }
                level.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55F, 0.9F);
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.3D, pos.z, 3, 0.5D, 0.1D, 0.5D, 0.0D);
                return true;
            }
            case "crushing_updraft" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 65, 2);
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.4F + 0.55F * pLv);
                }
                player.fallDistance = 0.0F;
                player.push(0.0D, 0.42D + 0.08D * sLv, 0.0D);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 14, 0.4D, 0.2D, 0.4D, 0.06D);
                return true;
            }
            case "ground_gust" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, null)) {
                    Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                    mob.push(away.x, 0.42D, away.z);
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5D, pos.z, 12, 0.4D, 0.2D, 0.4D, 0.06D);
                return true;
            }
            case "land_water_dragon" -> {
                if (player.isSprinting() || fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.95D);
                    player.push(dash.x, 0.22D, dash.z);
                    player.hurtMarked = true;
                    level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6D, player.getZ(), 18, 0.4D, 0.3D, 0.4D, 0.08D);
                }
                return true;
            }
            case "maelstrom_vault" -> {
                player.fallDistance = 0.0F;
                player.push(0.0D, 0.52D + 0.08D * pLv, 0.0D);
                player.hurtMarked = true;
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.2D, target)) {
                    Vec3 pull = pos.subtract(mob.position()).scale(0.38D);
                    mob.push(pull.x, 0.20D, pull.z);
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.5D, pos.z, 18, 0.45D, 0.3D, 0.45D, 0.08D);
                return true;
            }
            case "gale_vault_thrust" -> {
                if (fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.65D + 0.16D * pLv);
                    player.push(dash.x, 0.24D, dash.z);
                    player.hurtMarked = true;
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                        mob.hurtServer(level, level.damageSources().playerAttack(player), 2.5F + 0.7F * pLv);
                        Vec3 gust = mob.position().subtract(player.position()).normalize().scale(0.65D);
                        mob.push(gust.x, 0.32D, gust.z);
                    }
                    level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.6D, pos.z, 16, 0.4D, 0.3D, 0.4D, 0.08D);
                }
                return true;
            }
            case "sky_spear_dive" -> {
                if (fullChargeMelee) {
                    player.push(0.0D, 0.62D + 0.10D * pLv, 0.0D);
                    player.hurtMarked = true;
                    grantEffect(player, "slow_falling", 50, 0);
                    if (target != null) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 3.0F + 0.8F * sLv);
                    }
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(), 18, 0.45D, 0.2D, 0.45D, 0.09D);
                }
                return true;
            }
            case "blazing_dragoon" -> {
                if (fullChargeMelee) {
                    Vec3 dash = player.getLookAngle().normalize().scale(0.55D + 0.14D * pLv);
                    player.push(dash.x, 0.15D, dash.z);
                    player.hurtMarked = true;
                    if (target != null) {
                        target.igniteForSeconds(5.0F + 2.0F * sLv);
                        target.hurtServer(level, level.damageSources().onFire(), 2.6F + 0.7F * pLv);
                    }
                    level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.05D);
                }
                return true;
            }
            case "searing_charge" -> {
                if (target != null) {
                    target.igniteForSeconds(4.0F + 2.0F * pLv);
                    grantEffect(player, "speed", 70, 1);
                    Vec3 lunge = player.getLookAngle().normalize().scale(0.45D + 0.12D * sLv);
                    player.push(lunge.x, 0.1D, lunge.z);
                    player.hurtMarked = true;
                }
                return true;
            }
            case "tidal_harpoon_rush" -> {
                double scale = player.isInWaterOrRain() ? (0.95D + 0.20D * pLv) : (0.55D + 0.12D * pLv);
                Vec3 surge = player.getLookAngle().normalize().scale(scale);
                player.push(surge.x, 0.18D, surge.z);
                player.hurtMarked = true;
                if (target != null) {
                    float bonus = player.isInWaterOrRain() ? (4.0F + 0.9F * sLv) : (2.4F + 0.6F * pLv);
                    target.hurtServer(level, level.damageSources().trident(player, player), bonus);
                }
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.6D, pos.z, 18, 0.4D, 0.3D, 0.4D, 0.08D);
                return true;
            }
            case "surge_skewer" -> {
                Vec3 look = player.getLookAngle().normalize();
                for (int step = 1; step <= 4 + sLv; step++) {
                    Vec3 p = player.getEyePosition().add(look.scale(step));
                    level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 3, 0.15D, 0.15D, 0.15D, 0.02D);
                    for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.6D, 1.6D, 1.6D), e -> e != player && e.isAlive())) {
                        mob.hurtServer(level, level.damageSources().playerAttack(player), 2.6F + 0.7F * pLv);
                    }
                }
                return true;
            }
            case "boomerang_lance" -> {
                Vec3 start = player.getEyePosition();
                Vec3 end = target != null ? pos.add(player.getLookAngle().normalize().scale(2.2D)) : start.add(player.getLookAngle().normalize().scale(6.5D));
                ProjectilePathTracker.CachedFlightPath corridor = ProjectilePathTracker.recordSyntheticCorridor(
                    level, player, ProjectilePathTracker.PathSourceKind.SPEAR, hostStack, start, end, 8
                );
                ProjectilePathTracker.schedulePathEcho(corridor, 6L, 3.4F + 0.75F * pLv, true, true);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.25D, 0.3D, 0.08D);
                return true;
            }
            case "shadow_recall_lunge" -> {
                if (target != null && player.distanceToSqr(target) > 9.0D && player.distanceToSqr(target) <= 576.0D) {
                    Vec3 behindDir = target.position().subtract(player.position()).normalize();
                    Vec3 dest = target.position().subtract(behindDir.scale(1.4D));
                    ProjectilePathTracker.recordSyntheticCorridor(
                        level, player, ProjectilePathTracker.classifyWeaponItem(hostStack), hostStack,
                        player.position().add(0.0D, 0.8D, 0.0D), dest.add(0.0D, 0.8D, 0.0D), 8
                    );
                    player.fallDistance = 0.0F;
                    player.teleportTo(dest.x, dest.y + 0.1D, dest.z);
                    grantEffect(player, "resistance", 45, 0);
                    target.hurtServer(level, level.damageSources().playerAttack(player), 3.2F + 0.7F * sLv);
                    level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.35F);
                } else if (target != null) {
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.4F + 0.6F * sLv);
                }
                return true;
            }
            case "hundred_step_skewer" -> {
                Vec3 start = player.getEyePosition();
                Vec3 dir = player.getLookAngle().normalize();
                Vec3 end = start.add(dir.scale(7.0D + 0.8D * sLv));
                ProjectilePathTracker.recordSyntheticCorridor(
                    level, player, ProjectilePathTracker.PathSourceKind.SPEAR, hostStack, start, end, 10
                );
                Vec3 gatherPoint = start.add(dir.scale(3.8D));
                for (LivingEntity mob : ProjectilePathTracker.querySweptSegmentHostiles(level, player, start, end, 1.45D)) {
                    hurtBypassingIframe(level, mob, level.damageSources().magic(), 2.8F + 0.65F * pLv + mob.getArmorValue() * 0.15F);
                    Vec3 pull = gatherPoint.subtract(mob.position()).scale(0.36D);
                    mob.push(pull.x, 0.12D, pull.z);
                }
                level.sendParticles(ParticleTypes.CRIT, gatherPoint.x, gatherPoint.y, gatherPoint.z, 14, 0.35D, 0.2D, 0.35D, 0.08D);
                return true;
            }
            case "armor_seam_rush" -> {
                if (target != null) {
                    boolean marked = target.isCurrentlyGlowing();
                    grantEffect(target, "glowing", 120, 0);
                    grantEffect(target, "weakness", 80, 0);
                    float seamDmg = (marked || player.isSprinting() ? 3.8F : 2.2F) + target.getArmorValue() * 0.22F + 0.5F * pLv;
                    target.hurtServer(level, level.damageSources().magic(), seamDmg);
                    Vec3 lunge = player.getLookAngle().normalize().scale(0.42D + 0.10D * sLv);
                    player.push(lunge.x, 0.1D, lunge.z);
                    player.hurtMarked = true;
                }
                return true;
            }
            case "thunderbolt_cavalry" -> {
                Vec3 start = player.getEyePosition();
                Vec3 end = start.add(player.getLookAngle().normalize().scale(6.5D + 0.6D * pLv));
                ProjectilePathTracker.CachedFlightPath corridor = ProjectilePathTracker.recordSyntheticCorridor(
                    level, player, ProjectilePathTracker.PathSourceKind.SPEAR, hostStack, start, end, 8
                );
                ProjectilePathTracker.applyPathTrailEffect(
                    level, player, corridor, ProjectilePathTracker.PathTrailStyle.VOLT_CHAIN, 1.2F
                );
                for (LivingEntity mob : ProjectilePathTracker.querySweptSegmentHostiles(level, player, start, end, 1.5D)) {
                    mob.hurtServer(level, level.damageSources().lightningBolt(), 3.2F + 0.75F * pLv);
                    grantEffect(mob, "slowness", 50, 1);
                }
                return true;
            }
            case "lightning_step_spear" -> {
                if (fullChargeMelee || player.isSprinting()) {
                    Vec3 origin = player.position();
                    Vec3 dash = player.getLookAngle().normalize().scale(1.05D + 0.18D * sLv);
                    player.push(dash.x, 0.18D, dash.z);
                    player.hurtMarked = true;
                    grantEffect(player, "speed", 80, 1);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                        mob.hurtServer(level, level.damageSources().lightningBolt(), 3.0F + 0.8F * sLv);
                        grantEffect(mob, "slowness", 45, 2);
                    }
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y + 0.8D, origin.z, 18, 0.35D, 0.35D, 0.35D, 0.12D);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.12D);
                }
                return true;
            }
            case "kite_string_gale" -> {
                if (target != null) {
                    Vec3 away = target.position().subtract(player.position()).normalize().scale(0.95D + 0.2D * pLv);
                    target.push(away.x, 0.68D + 0.10D * pLv, away.z);
                    target.hurtMarked = true;
                    KITE_SNAPBACK_MAP.put(
                        target.getUUID(),
                        new KiteSnapback(player.getUUID(), level.dimension(), pos, level.getGameTime() + 12L)
                    );
                    level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.6D, pos.z, 16, 0.35D, 0.25D, 0.35D, 0.08D);
                }
                return true;
            }
            case "tailwind_return" -> {
                player.fallDistance = 0.0F;
                player.push(0.0D, 0.42D + 0.08D * sLv, 0.0D);
                player.hurtMarked = true;
                grantEffect(player, "speed", 90, 1);
                grantEffect(player, "slow_falling", 65, 0);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.55F * pLv);
                }
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4D, player.getZ(), 14, 0.4D, 0.2D, 0.4D, 0.07D);
                return true;
            }
            case "tempered_anvil_heart" -> {
                if (hostStack.isDamaged()) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 1));
                }
                int stacks = Math.min(5, TEMPERED_STEEL_STACKS.getOrDefault(player.getUUID(), 0) + 1);
                TEMPERED_STEEL_STACKS.put(player.getUUID(), stacks);
                if (target != null) {
                    float anvilBonus = 1.6F + stacks * (0.65F + 0.15F * pLv);
                    target.hurtServer(level, level.damageSources().playerAttack(player), anvilBonus);
                }
                level.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.32F, 1.55F + stacks * 0.06F);
                return true;
            }
            case "heavy_xp_meteor" -> {
                float xpWeight = Math.min(7.5F, player.experienceLevel * 0.18F) + 1.2F + 0.45F * sLv;
                if (target != null) {
                    target.hurtServer(level, level.damageSources().playerAttack(player), xpWeight);
                    target.push(0.0D, -0.25D, 0.0D);
                }
                for (LivingEntity splash : nearbyHostiles(level, player, pos, 2.8D, target)) {
                    splash.hurtServer(level, level.damageSources().magic(), xpWeight * 0.5F);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.6D, pos.z, 10, 0.35D, 0.25D, 0.35D, 0.05D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
