package enchantment_fusion.mechanics.transmute;

import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.classifyTarget;
import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getTransmutedLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.FlyingBladeManager;
import enchantment_fusion.mechanics.SignaturePairs;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Melee Sword / Axe / Spear / Mace / Trident Cross-Domain Transmutations,
 * Right-Click Active Skills, and 26.1.2 Spear Hold-Right-Click Charge Release.
 */
public final class MeleeSpearTransmutes {
    private MeleeSpearTransmutes() {}

    public static double modifyEntityReach(Player player, double baseReach) {
        ItemStack held = player.getMainHandItem();
        if (getTransmutedLevel(held, "yishui") > 0 && player.isInWaterOrRain()) {
            return baseReach + 1.5D;
        }
        return baseReach;
    }

    public static float modifyAttackDelay(Player player, float baseDelay) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return baseDelay;

        float mult = 1.0F;
        if (getTransmutedLevel(held, "light_feather") > 0) {
            mult *= (1.0F - 0.06F * getTransmutedLevel(held, "light_feather"));
        }
        int swiftLv = getTransmutedLevel(held, "swift_swing");
        if (swiftLv > 0) mult *= Math.max(0.55F, 1.0F - 0.07F * swiftLv);
        int waveLv = getTransmutedLevel(held, "wave_parting");
        if (waveLv > 0 && player.isInWaterOrRain()) mult *= Math.max(0.60F, 1.0F - 0.12F * waveLv);

        int frostLv = getTransmutedLevel(held, "frost_edge");
        if (frostLv == 1) mult *= 1.14F;
        else if (frostLv == 2) mult *= 1.05F;

        int serratedLv = getTransmutedLevel(held, "serrated_hook");
        if (serratedLv == 1) mult *= 1.15F;
        else if (serratedLv == 2) mult *= 1.08F;
        else if (serratedLv >= 3) mult *= 1.03F;

        if (getTransmutedLevel(held, "heavy_plummet") > 0) mult *= 1.12F;
        return baseDelay * mult;
    }

    public static boolean allowAttackTarget(Player player, Entity target) {
        ItemStack weapon = player.getMainHandItem();
        if (getTransmutedLevel(weapon, "intact_blade") > 0 && target instanceof LivingEntity liv && !(liv instanceof Player)) {
            if (liv.hasCustomName()) return false;
            if (liv instanceof Mob mob && mob.isLeashed()) return false;
        }
        return true;
    }

    public static void onMeleeAttack(ServerLevel level, ServerPlayer player, LivingEntity target, boolean fullCharge) {
        if (Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) return;
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) return;

        LAST_SWING_TICK.put(player.getUUID(), level.getGameTime());
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            Vec3 pos = target.position();
            RandomSource rng = level.getRandom();

            if (getTransmutedLevel(weapon, "yishui") > 0 && player.isInWaterOrRain()) {
                target.hurtServer(level, level.damageSources().magic(), 3.2F);
                level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.8D, pos.z, 10, 0.3D, 0.25D, 0.3D, 0.08D);
            }
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 70 * frostLv));
                grantEffect(target, "slowness", 55, frostLv - 1);
                if (target.getTicksFrozen() >= 140 || rng.nextFloat() < 0.25F) {
                    AberrantRegistry.placeSingleFrostedIce(level, target.blockPosition());
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.8D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.04D);
            }
            int lungeStrikeLv = getTransmutedLevel(weapon, "lunge_dash_strike");
            if (lungeStrikeLv > 0 && fullCharge) {
                Vec3 dash = player.getLookAngle().normalize().scale(0.42D + 0.14D * lungeStrikeLv);
                player.push(dash.x, 0.12D, dash.z);
                player.hurtMarked = true;
                target.hurtServer(level, level.damageSources().playerAttack(player), 1.0F * lungeStrikeLv);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4D, player.getZ(), 8, 0.25D, 0.15D, 0.25D, 0.05D);
            }
            if (getTransmutedLevel(weapon, "light_feather") > 0) {
                target.setDeltaMovement(target.getDeltaMovement().scale(0.35D));
                if (weapon.getItem() instanceof MaceItem && player.fallDistance > 1.5D) {
                    player.fallDistance *= 0.45D;
                }
            }
            if (getTransmutedLevel(weapon, "soul_chase") > 0) {
                SOUL_CHASE_TARGET.put(player.getUUID(), target.getUUID());
                SOUL_CHASE_UNTIL.put(player.getUUID(), level.getGameTime() + 90L);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.6D, pos.z, 6, 0.2D, 0.2D, 0.2D, 0.03D);
            }
            int silentLv = getTransmutedLevel(weapon, "silent_blade");
            if (silentLv > 0 && (player.isShiftKeyDown() || player.getLookAngle().dot(target.getLookAngle()) > 0.35D)) {
                if (player.isShiftKeyDown() && player.distanceToSqr(target) <= 16.0D) {
                    Vec3 flank = pos.subtract(target.getLookAngle().normalize().scale(1.1D));
                    player.teleportTo(flank.x, flank.y + 0.05D, flank.z);
                }
                target.hurtServer(level, level.damageSources().magic(), 2.2F + 1.0F * silentLv);
                level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.8D, pos.z, 6, 0.2D, 0.2D, 0.2D, 0.03D);
            }
            int hookLv = getTransmutedLevel(weapon, "serrated_hook");
            if (hookLv > 0) {
                target.hurtServer(level, level.damageSources().magic(), 1.0F + 0.7F * hookLv);
                Vec3 yank = player.position().subtract(pos);
                if (yank.lengthSqr() > 0.25D) {
                    Vec3 pull = yank.normalize().scale(0.55D + 0.15D * hookLv);
                    target.push(pull.x, 0.22D, pull.z);
                }
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.8D, pos.z, 5, 0.2D, 0.2D, 0.2D, 0.04D);
            }
            if (getTransmutedLevel(weapon, "flame_cut") > 0) {
                player.clearFire();
                BlockPos tp = target.blockPosition();
                if (level.getBlockState(tp).is(Blocks.FIRE)) level.setBlockAndUpdate(tp, Blocks.AIR.defaultBlockState());
            }
            int defuseLv = getTransmutedLevel(weapon, "creeper_defuse");
            if (defuseLv > 0 && target.getType() == EntityType.CREEPER && rng.nextFloat() < (0.35F + 0.15F * defuseLv)) {
                if (target instanceof Mob mob) mob.setTarget(null);
                Vec3 pushAway = pos.subtract(player.position()).normalize().scale(1.15D);
                target.push(pushAway.x, 0.25D, pushAway.z);
                grantEffect(target, "slowness", 40, 2);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.02D);
                level.playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
            }
            int deflectLv = getTransmutedLevel(weapon, "arrow_deflect");
            if (deflectLv > 0) deflectNearbyProjectiles(level, player, 2.8D + 0.5D * deflectLv);
            if (getTransmutedLevel(weapon, "blade_parry") > 0 && fullCharge) {
                deflectNearbyProjectiles(level, player, 2.4D);
            }
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.28F + 0.14F * fortuneLv)) {
                triggerThreeDollarRoulette(level, player, target, fortuneLv);
            }
            int shoveLv = getTransmutedLevel(weapon, "warding_shove");
            if (shoveLv > 0) {
                Vec3 dir = pos.subtract(player.position()).normalize().scale(0.55D * shoveLv);
                target.push(dir.x, 0.24D, dir.z);
            }
            int cleaveLv = getTransmutedLevel(weapon, "heavy_cleave");
            if (cleaveLv > 0 && fullCharge) {
                for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.2D, target)) {
                    extra.hurtServer(level, level.damageSources().playerAttack(player), 1.8F + 1.1F * cleaveLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            int powerLv = getTransmutedLevel(weapon, "full_draw_cleave");
            if (powerLv > 0) {
                if (fullCharge) target.hurtServer(level, level.damageSources().playerAttack(player), 1.4F + 0.75F * powerLv);
                else target.heal(Math.min(2.0F, target.getMaxHealth() - target.getHealth()));
            }
            int twinLv = getTransmutedLevel(weapon, "twin_shadow_slash");
            if (twinLv > 0 && fullCharge) {
                FlyingBladeManager.launchAngledPhantomWeapon(
                    level, player, InteractionHand.MAIN_HAND, weapon, twinLv,
                    FlyingBladeManager.FlightStyle.PHANTOM_LANCE, -11.0F
                );
                FlyingBladeManager.launchAngledPhantomWeapon(
                    level, player, InteractionHand.MAIN_HAND, weapon, twinLv,
                    FlyingBladeManager.FlightStyle.PHANTOM_LANCE, 11.0F
                );
            }
            int skewerLv = getTransmutedLevel(weapon, "skewer_thrust");
            if (skewerLv > 0) {
                if (target.isBlocking()) target.stopUsingItem();
                target.hurtServer(level, level.damageSources().magic(), 0.9F * skewerLv);
                Vec3 lineCenter = pos.add(player.getLookAngle().normalize().scale(2.0D));
                for (LivingEntity behind : nearbyHostiles(level, player, lineCenter, 2.2D, target)) {
                    behind.hurtServer(level, level.damageSources().magic(), 2.2F + 0.7F * skewerLv);
                }
            }
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.setPos(pos.x, pos.y, pos.z);
                bolt.setCause(player);
                level.addFreshEntity(bolt);
                if (player.distanceToSqr(target) < 6.25D && !hasArmorTransmute(player, "lightning_conductor_armor")) {
                    grantEffect(player, "slowness", 20, 1);
                }
            }
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 2.0F * impaleLv);
            }
            int densityLv = getTransmutedLevel(weapon, "heavy_plummet");
            if (densityLv > 0 && player.fallDistance > 1.8D) {
                float smash = (float) Math.min(18.0D, player.fallDistance * 0.85D * densityLv);
                target.hurtServer(level, level.damageSources().playerAttack(player), smash);
                player.resetFallDistance();
                level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45F, 1.25F);
            }
            int breachLv = getTransmutedLevel(weapon, "hard_breach");
            if (breachLv > 0) {
                if (target.getArmorValue() > 0 || target.isBlocking()) {
                    if (target.isBlocking()) target.stopUsingItem();
                    target.hurtServer(level, level.damageSources().magic(), 1.3F * breachLv);
                } else {
                    target.heal(Math.min(1.0F, target.getMaxHealth() - target.getHealth()));
                }
            }
            int windLv = getTransmutedLevel(weapon, "pogo_gale_slash");
            if (windLv > 0 && !player.onGround() && !player.isUnderWater()) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, 0.68D + 0.14D * windLv, v.z);
                player.hurtMarked = true;
                player.resetFallDistance();
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4D, pos.z, 12, 0.35D, 0.15D, 0.35D, 0.08D);
            }
            if (getTransmutedLevel(weapon, "taunt_blade") > 0 && target instanceof Mob mob) {
                mob.setTarget(player);
            }
            if (getTransmutedLevel(weapon, "sticky_root_curse") > 0 && rng.nextFloat() < 0.28F) {
                grantEffect(target, "slowness", 35, 4);
                target.setDeltaMovement(0.0D, Math.min(0.0D, target.getDeltaMovement().y), 0.0D);
            }
            if (classifyTarget(weapon) == TargetRole.SPEAR && fullCharge && player.isSprinting()) {
                int spearDashLv = getTransmutedLevel(weapon, "aqua_spiral_dash");
                if (spearDashLv > 0 && checkSkillCooldown(player, "spear_sprint_dash", level.getGameTime(), 24)) {
                    boolean wet = player.isInWaterOrRain();
                    Vec3 surge = player.getLookAngle().normalize().scale(wet ? (0.95D + 0.24D * spearDashLv) : (0.58D + 0.14D * spearDashLv));
                    player.push(surge.x, 0.14D, surge.z);
                    player.hurtMarked = true;
                    target.hurtServer(level, level.damageSources().playerAttack(player), (wet ? 3.8F : 2.2F) + 1.1F * spearDashLv);
                    level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y + 0.8D, pos.z, 14, 0.35D, 0.25D, 0.35D, 0.08D);
                }
            }
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    public static void onFlyingBladeHit(ServerLevel level, ServerPlayer player, ItemStack weapon, LivingEntity target) {
        if (weapon.isEmpty() || Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) return;
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            Vec3 pos = target.position();
            RandomSource rng = level.getRandom();
            if (getTransmutedLevel(weapon, "yishui") > 0 && (player.isInWaterOrRain() || target.isInWaterOrRain())) {
                target.hurtServer(level, level.damageSources().magic(), 2.8F);
            }
            int frostLv = getTransmutedLevel(weapon, "frost_edge");
            if (frostLv > 0) {
                target.setTicksFrozen(Math.min(350, target.getTicksFrozen() + 65 * frostLv));
                grantEffect(target, "slowness", 50, frostLv - 1);
            }
            int hookLv = getTransmutedLevel(weapon, "serrated_hook");
            if (hookLv > 0) target.hurtServer(level, level.damageSources().magic(), 1.0F + 0.65F * hookLv);
            int fortuneLv = getTransmutedLevel(weapon, "three_dollar_luck");
            if (fortuneLv > 0 && rng.nextFloat() < (0.25F + 0.12F * fortuneLv)) {
                triggerThreeDollarRoulette(level, player, target, fortuneLv);
            }
            if (getTransmutedLevel(weapon, "storm_rod_strike") > 0 && level.isThundering() && level.canSeeSky(target.blockPosition())) {
                AberrantRegistry.spawnLightning(level, pos);
            }
            int impaleLv = getTransmutedLevel(weapon, "drenched_bane");
            if (impaleLv > 0 && (target.isInWaterOrRain() || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.AQUATIC))) {
                target.hurtServer(level, level.damageSources().magic(), 1.8F * impaleLv);
            }
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    public static boolean canTriggerDirectRightClick(boolean playerSneaking, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (classifyTarget(stack) == TargetRole.SPEAR) return playerSneaking;
        return true;
    }

    public static boolean hasReadyRightClickAbility(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) return false;
        if (!canTriggerDirectRightClick(player.isShiftKeyDown(), stack)) return false;
        boolean isSpearSneak = classifyTarget(stack) == TargetRole.SPEAR;
        if (getTransmutedLevel(stack, "returning_blade_throw") > 0) return true;
        if (getTransmutedLevel(stack, "phantom_trident") > 0) return true;
        if (!isSpearSneak && getTransmutedLevel(stack, "arrow_deflect") > 0) return true;
        if (getTransmutedLevel(stack, "aqua_spiral_dash") > 0) return true;
        if (getTransmutedLevel(stack, "pogo_gale_slash") > 0 && !player.isUnderWater()) return true;
        return getTransmutedLevel(stack, "jetstream_boost") > 0
            && !(stack.getItem() instanceof FishingRodItem)
            && !player.isUnderWater();
    }

    public static boolean onUseItem(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty()) return false;
        long now = level.getGameTime();
        boolean activatedSkill = false;
        boolean isSpear = classifyTarget(stack) == TargetRole.SPEAR;
        boolean itemReady = canTriggerDirectRightClick(player.isShiftKeyDown(), stack) && !player.getCooldowns().isOnCooldown(stack);
        int maxVisualCooldown = 0;

        int throwLv = getTransmutedLevel(stack, "returning_blade_throw");
        if (itemReady && throwLv > 0) {
            int cd = Math.max(14, 34 - 5 * throwLv);
            if (checkSkillCooldown(player, "returning_blade_throw", now, cd)
                && FlyingBladeManager.launchReturningBlade(level, player, hand, stack, throwLv)) {
                CompendiumDiscoveryTracker.recordTransmuteProgress(player, "returning_blade_throw", CompendiumDiscoveryTracker.STATE_MASTERED);
                maxVisualCooldown = Math.max(maxVisualCooldown, cd);
                activatedSkill = true;
            }
        }
        int phantomLv = getTransmutedLevel(stack, "phantom_trident");
        if (itemReady && phantomLv > 0 && checkSkillCooldown(player, "phantom_trident", now, 18)) {
            if (FlyingBladeManager.launchLinearPhantomWeapon(level, player, hand, stack, phantomLv, FlyingBladeManager.FlightStyle.PHANTOM_LANCE)) {
                CompendiumDiscoveryTracker.recordTransmuteProgress(player, "phantom_trident", CompendiumDiscoveryTracker.STATE_MASTERED);
                maxVisualCooldown = Math.max(maxVisualCooldown, 18);
                activatedSkill = true;
            }
        }
        int deflectLv = getTransmutedLevel(stack, "arrow_deflect");
        if (itemReady && !isSpear && deflectLv > 0 && checkSkillCooldown(player, "arrow_deflect", now, 18)) {
            deflectNearbyProjectiles(level, player, 3.6D + 0.65D * deflectLv);
            Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(1.1D));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y - 0.2D, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.75F, 1.35F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 18);
            activatedSkill = true;
        }
        int dashLv = Math.max(getTransmutedLevel(stack, "aqua_spiral_dash"), getTransmutedLevel(stack, "surge_ram"));
        if (itemReady && dashLv > 0 && !(stack.getItem() instanceof ShieldItem && !player.isInWaterOrRain())
            && checkSkillCooldown(player, "aqua_spiral_dash", now, 26)) {
            boolean wet = player.isInWaterOrRain();
            double scale = wet ? (1.20D + 0.34D * dashLv) : (0.76D + 0.16D * dashLv);
            Vec3 launch = player.getLookAngle().normalize().scale(scale);
            player.setDeltaMovement(launch.x, Math.max(0.24D, launch.y * 0.85D), launch.z);
            player.hurtMarked = true;
            player.resetFallDistance();
            for (LivingEntity hit : nearbyHostiles(level, player, player.position().add(launch), 2.8D, null)) {
                hit.hurtServer(level, level.damageSources().playerAttack(player), (wet ? 4.8F : 3.2F) + 1.4F * dashLv);
            }
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.8D, player.getZ(), 18, 0.45D, 0.3D, 0.45D, 0.1D);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.85F, wet ? 1.15F : 1.40F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 26);
            activatedSkill = true;
        }
        int activeWindLv = Math.max(getTransmutedLevel(stack, "pogo_gale_slash"), (stack.getItem() instanceof FishingRodItem) ? 0 : getTransmutedLevel(stack, "jetstream_boost"));
        if (itemReady && activeWindLv > 0 && !player.isUnderWater() && checkSkillCooldown(player, "pogo_gale_boost", now, 28)) {
            Vec3 look = player.getLookAngle().normalize();
            if (player.isFallFlying()) {
                player.setDeltaMovement(player.getDeltaMovement().scale(0.45D).add(look.scale(1.15D + 0.25D * activeWindLv)));
            } else {
                player.setDeltaMovement(look.x * (0.55D + 0.12D * activeWindLv), Math.max(0.56D + 0.10D * activeWindLv, look.y * 0.75D), look.z * (0.55D + 0.12D * activeWindLv));
            }
            player.hurtMarked = true;
            player.resetFallDistance();
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3D, player.getZ(), 16, 0.4D, 0.2D, 0.4D, 0.08D);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.80F, 0.85F);
            maxVisualCooldown = Math.max(maxVisualCooldown, 28);
            activatedSkill = true;
        }
        if (maxVisualCooldown > 0) player.getCooldowns().addCooldown(stack, maxVisualCooldown);

        if (player.isFallFlying() && !player.isUnderWater()) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int wornStorm = player.isInWaterOrRain() ? getTransmutedLevel(chest, "storm_glider") : 0;
            int boostLv = Math.max(getTransmutedLevel(chest, "jetstream_boost"), wornStorm);
            if (boostLv > 0 && !player.getCooldowns().isOnCooldown(chest) && checkSkillCooldown(player, "worn_elytra_jet", now, 34)) {
                Vec3 look = player.getLookAngle().normalize();
                player.setDeltaMovement(player.getDeltaMovement().scale(0.5D).add(look.scale(1.10D + 0.24D * boostLv)));
                player.hurtMarked = true;
                player.getCooldowns().addCooldown(chest, 34);
                level.sendParticles(wornStorm > 0 ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5D, player.getZ(), 18, 0.4D, 0.3D, 0.4D, 0.08D);
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.35F);
                activatedSkill = true;
            }
        }
        if (!stack.has(DataComponents.CONSUMABLE) && getTransmutedLevel(stack, "unbreaking_ration") > 0 && !player.getAbilities().instabuild) {
            Item expectedItem = stack.getItem();
            int countBefore = stack.getCount();
            ItemStack snapshot = stack.copyWithCount(1);
            level.getServer().execute(() -> {
                if (!player.isAlive()) return;
                ItemStack after = player.getItemInHand(hand);
                if (after.isEmpty() || (after.is(expectedItem) && after.getCount() < countBefore)) {
                    RelicWorldItemTransmutes.tryRefundRationItemImmediate(level, player, snapshot);
                }
            });
        }
        return activatedSkill;
    }

    public static void tickSpearHoldChargeAndRelease(ServerLevel level, ServerPlayer player) {
        UUID pid = player.getUUID();
        ItemStack activeUse = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (!activeUse.isEmpty() && classifyTarget(activeUse) == TargetRole.SPEAR) {
            int ticks = Math.max(1, player.getTicksUsingItem());
            SPEAR_CHARGE_TICKS.put(pid, ticks);
            SPEAR_CHARGE_WEAPON.put(pid, activeUse.copyWithCount(1));

            int deflectLv = Math.max(getTransmutedLevel(activeUse, "arrow_deflect"), getTransmutedLevel(activeUse, "blade_parry"));
            if ((deflectLv > 0 || SignaturePairs.stackHasSignature(activeUse, "mirror_moon_sweep")) && ticks >= 3 && ticks % 4 == 0) {
                deflectNearbyProjectiles(level, player, 3.0D + 0.55D * Math.max(1, deflectLv));
            }
            int wetRush = Math.max(getTransmutedLevel(activeUse, "aqua_spiral_dash"), getTransmutedLevel(activeUse, "wave_parting"));
            if ((wetRush > 0 || SignaturePairs.stackHasSignature(activeUse, "tidal_harpoon_rush"))
                && player.isInWaterOrRain() && ticks >= 5 && ticks % 8 == 0) {
                Vec3 push = player.getLookAngle().normalize().scale(0.28D + 0.08D * Math.max(1, wetRush));
                player.push(push.x, Math.max(0.02D, push.y * 0.3D), push.z);
                player.hurtMarked = true;
            }
            return;
        }
        Integer prevTicks = SPEAR_CHARGE_TICKS.remove(pid);
        ItemStack prevSpear = SPEAR_CHARGE_WEAPON.remove(pid);
        if (prevTicks != null && prevTicks >= 10 && prevSpear != null && !prevSpear.isEmpty()
            && hasSpearChargeReleaseProjection(prevSpear, player)
            && checkSkillCooldown(player, "spear_charge_release_lance", level.getGameTime(), 18)) {
            int tier = prevTicks >= 22 ? 4 : (prevTicks >= 15 ? 3 : 2);
            FlyingBladeManager.launchLinearPhantomWeapon(
                level, player, InteractionHand.MAIN_HAND, prevSpear, tier, FlyingBladeManager.FlightStyle.SPEAR_CHARGE_LANCE
            );
        }
    }

    private static boolean hasSpearChargeReleaseProjection(ItemStack spear, ServerPlayer player) {
        if (getTransmutedLevel(spear, "unending_edge") > 0
            || getTransmutedLevel(spear, "sun_piercer") > 0
            || getTransmutedLevel(spear, "twin_shadow_slash") > 0
            || getTransmutedLevel(spear, "storm_rod_strike") > 0
            || getTransmutedLevel(spear, "full_draw_cleave") > 0) {
            return true;
        }
        return SignaturePairs.stackHasSignature(spear, "flying_sword_qi")
            || SignaturePairs.stackHasSignature(spear, "hydro_flying_sword")
            || SignaturePairs.stackHasSignature(spear, "boomerang_lance")
            || SignaturePairs.stackHasSignature(spear, "hundred_step_skewer")
            || SignaturePairs.stackHasSignature(spear, "thunderbolt_cavalry")
            || SignaturePairs.playerHasEquippedSignature(player, "hundred_step_skewer");
    }
}
