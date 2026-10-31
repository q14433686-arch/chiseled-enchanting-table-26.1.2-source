package enchantment_fusion.mechanics.transmute;

import static enchantment_fusion.mechanics.transmute.TransmuteCatalog.getTransmutedLevel;
import static enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport.*;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Armor, Shield, Elytra, Kill-Loot &amp; Player Passive Cross-Domain Transmutations.
 */
public final class ArmorRelicTransmutes {
    private ArmorRelicTransmutes() {}

    public static boolean allowIncomingDamage(ServerLevel level, ServerPlayer player, DamageSource source) {
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (source != null && source.getDirectEntity() instanceof LightningBolt && hasArmorTransmute(player, "lightning_conductor_armor")) {
            grantEffect(player, "strength", 140, 0);
            grantEffect(player, "speed", 140, 1);
            for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 5.0D, null)) {
                mob.hurtServer(level, level.damageSources().magic(), 5.0F);
            }
            return false;
        }
        return true;
    }

    public static void onAfterDamage(ServerLevel level, ServerPlayer player, DamageSource source, float damageTaken, boolean blocked) {
        if (Boolean.TRUE.equals(IN_CUSTOM_COMBAT.get())) return;
        IN_CUSTOM_COMBAT.set(Boolean.TRUE);
        try {
            LivingEntity attacker = source.getEntity() instanceof LivingEntity liv ? liv : null;
            RandomSource rng = level.getRandom();
            if (PICKPOCKETED_MOBS.size() > 4096) PICKPOCKETED_MOBS.clear();

            ItemStack useItem = player.isBlocking() ? player.getUseItem() : ItemStack.EMPTY;
            if (blocked && !useItem.isEmpty()) {
                int spikeLv = getTransmutedLevel(useItem, "spiked_shield");
                if (spikeLv > 0 && attacker != null) {
                    attacker.hurtServer(level, level.damageSources().thorns(player), 1.6F + 1.2F * spikeLv);
                    Vec3 shove = attacker.position().subtract(player.position()).normalize().scale(0.35D);
                    attacker.push(shove.x, 0.16D, shove.z);
                }
                int bastionLv = getTransmutedLevel(useItem, "bastion_shield");
                if (bastionLv > 0 && useItem.isDamaged() && rng.nextFloat() < 0.22F * bastionLv) {
                    useItem.setDamageValue(Math.max(0, useItem.getDamageValue() - 1));
                }
                if (getTransmutedLevel(useItem, "fireproof_shield") > 0) player.clearFire();
                int catchLv = getTransmutedLevel(useItem, "arrow_catch_shield");
                if (catchLv > 0 && source.getDirectEntity() instanceof Projectile && rng.nextFloat() < 0.28F * catchLv) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, 1));
                }
                if (getTransmutedLevel(useItem, "shield_sweep") > 0 || getTransmutedLevel(useItem, "reflex_shield") > 0) {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.2D, null)) {
                        Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.65D);
                        mob.push(away.x, 0.2D, away.z);
                    }
                }
                if (getTransmutedLevel(useItem, "splinter_shield") > 0 && source.getDirectEntity() instanceof Projectile) {
                    int sent = 0;
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 8.0D, null)) {
                        Vec3 aim = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
                        spawnCounterSpectralBolt(level, player, aim, 2.2D);
                        if (++sent >= 3) break;
                    }
                }
            }

            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            int parryLv = getTransmutedLevel(main, "blade_parry");
            if (parryLv > 0 && damageTaken > 0.5F) {
                long lastSwing = LAST_SWING_TICK.getOrDefault(player.getUUID(), -100L);
                boolean activeParry = (level.getGameTime() - lastSwing) <= 12L;
                player.heal(damageTaken * (activeParry ? 0.32F : (0.05F * parryLv)));
                if (activeParry) {
                    deflectNearbyProjectiles(level, player, 2.8D);
                    damageItemSafely(main, 1, player);
                    level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.85F);
                }
            }
            if (source.is(DamageTypeTags.IS_EXPLOSION) && damageTaken > 0.5F) {
                int blastMit = Math.max(
                    Math.max(getTransmutedLevel(main, "blast_breech"), getTransmutedLevel(off, "blast_breech")),
                    Math.max(getTransmutedLevel(main, "blast_guard"), getTransmutedLevel(off, "blast_guard"))
                );
                if (blastMit > 0) player.heal(damageTaken * Math.min(0.65F, 0.25F + 0.10F * blastMit));
            }

            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
            };
            for (EquipmentSlot slot : slots) {
                ItemStack gear = player.getItemBySlot(slot);
                if (gear.isEmpty()) continue;

                if (getTransmutedLevel(gear, "soul_escape") > 0 && damageTaken > 0.5F) {
                    grantEffect(player, "speed", 65, 1);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.5D, player.getZ(), 8, 0.3D, 0.2D, 0.3D, 0.04D);
                }
                int rimLv = getTransmutedLevel(gear, "bladed_rim");
                if (rimLv > 0 && attacker != null && player.distanceToSqr(attacker) < 9.0D) {
                    attacker.hurtServer(level, level.damageSources().thorns(player), 0.9F + 0.55F * rimLv);
                    Vec3 away = attacker.position().subtract(player.position()).normalize().scale(0.32D);
                    attacker.push(away.x, 0.14D, away.z);
                }
                int undeadWard = getTransmutedLevel(gear, "undead_ward");
                if (undeadWard > 0 && attacker != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(attacker.getType()).is(EntityTypeTags.UNDEAD)) {
                    player.heal(Math.min(damageTaken * 0.25F, 2.5F));
                    attacker.igniteForSeconds(3.0F);
                }
                int repulseLv = getTransmutedLevel(gear, "repulse_guard");
                if (repulseLv > 0 && attacker != null && player.distanceToSqr(attacker) < 10.0D) {
                    Vec3 push = attacker.position().subtract(player.position()).normalize().scale(0.55D * repulseLv);
                    attacker.push(push.x, 0.25D, push.z);
                }
                int guardianLv = getTransmutedLevel(gear, "guardian_ward");
                if (guardianLv > 0 && (player.isInWaterOrRain()
                    || (attacker != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(attacker.getType()).is(EntityTypeTags.AQUATIC)))) {
                    player.heal(Math.min(2.5F, damageTaken * 0.20F));
                    if (attacker != null) attacker.hurtServer(level, level.damageSources().thorns(player), 1.4F * guardianLv);
                }
                if (getTransmutedLevel(gear, "gale_shield") > 0 && !player.isUnderWater() && damageTaken >= 2.0F) {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.2D, null)) {
                        Vec3 away = mob.position().subtract(player.position()).normalize().scale(0.85D);
                        mob.push(away.x, 0.35D, away.z);
                    }
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.6D, player.getZ(), 12, 0.4D, 0.2D, 0.4D, 0.08D);
                }
                int pickLv = getTransmutedLevel(gear, "pickpocket_guard");
                if (pickLv > 0 && attacker != null && !(attacker instanceof Player)
                    && !PICKPOCKETED_MOBS.contains(attacker.getUUID())
                    && rng.nextFloat() < (0.14F * pickLv)) {
                    PICKPOCKETED_MOBS.add(attacker.getUUID());
                    spawnItem(level, player.position(), new ItemStack(pickCommonMobDrop(attacker.getType(), rng), 1));
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
                }
                int emberLv = getTransmutedLevel(gear, "ember_mantle");
                if (emberLv > 0) {
                    if (attacker != null && player.distanceToSqr(attacker) < 12.0D) attacker.igniteForSeconds(2.5F + 1.5F * emberLv);
                    if (source.is(DamageTypeTags.IS_FIRE)) grantEffect(player, "fire_resistance", 55, 0);
                }
                int rimeLv = getTransmutedLevel(gear, "rime_aegis");
                if (rimeLv > 0) {
                    if (player.isOnFire()) player.clearFire();
                    if (attacker != null && player.distanceToSqr(attacker) < 12.0D) {
                        attacker.setTicksFrozen(Math.min(320, attacker.getTicksFrozen() + 70 * rimeLv));
                        grantEffect(attacker, "slowness", 50, rimeLv - 1);
                    }
                }
                int counterLv = getTransmutedLevel(gear, "counter_volley");
                if (counterLv > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) {
                    deflectNearbyProjectiles(level, player, 3.0D + 0.5D * counterLv);
                    if (attacker != null) {
                        Vec3 aim = attacker.getEyePosition().subtract(player.getEyePosition()).normalize();
                        spawnCounterSpectralBolt(level, player, aim, 2.0D + 0.4D * counterLv);
                    }
                }
                int plumeLv = getTransmutedLevel(gear, "updraft_plume");
                if (plumeLv > 0 && (source.is(DamageTypeTags.IS_FALL) || player.isFallFlying()) && damageTaken > 1.0F) {
                    player.push(0.0D, 0.48D + 0.08D * plumeLv, 0.0D);
                    player.hurtMarked = true;
                    player.resetFallDistance();
                    grantEffect(player, "slow_falling", 65, 0);
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2D, player.getZ(), 14, 0.4D, 0.15D, 0.4D, 0.07D);
                }
                int hemLv = getTransmutedLevel(gear, "whirlwind_hem");
                if (hemLv > 0 && attacker != null && player.distanceToSqr(attacker) < 12.0D) {
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 3.0D, null)) {
                        mob.hurtServer(level, level.damageSources().thorns(player), 1.0F + 0.7F * hemLv);
                    }
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                int prismLv = getTransmutedLevel(gear, "refracted_carapace");
                if (prismLv > 0 && damageTaken >= 1.5F) {
                    int sent = 0;
                    for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 7.0D, null)) {
                        Vec3 aim = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
                        spawnCounterSpectralBolt(level, player, aim, 1.9D + 0.45D * prismLv);
                        if (++sent >= 3) break;
                    }
                }
            }
        } finally {
            IN_CUSTOM_COMBAT.set(Boolean.FALSE);
        }
    }

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim) {
        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty()) return;
        RandomSource rng = level.getRandom();

        if (getTransmutedLevel(weapon, "intact_blade") > 0 && rng.nextFloat() < 0.10F) {
            EntityType<?> vt = victim.getType();
            if (vt == EntityType.ZOMBIE) spawnItem(level, victim.position(), new ItemStack(Items.ZOMBIE_HEAD, 1));
            else if (vt == EntityType.SKELETON) spawnItem(level, victim.position(), new ItemStack(Items.SKELETON_SKULL, 1));
            else if (vt == EntityType.CREEPER) spawnItem(level, victim.position(), new ItemStack(Items.CREEPER_HEAD, 1));
            else if (vt == EntityType.SHEEP) spawnItem(level, victim.position(), new ItemStack(Items.WHITE_WOOL, 2));
        }

        int seaLv = getTransmutedLevel(weapon, "ocean_bounty");
        if (seaLv > 0 && (victim.isInWaterOrRain() || killer.isInWaterOrRain()
            || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(victim.getType()).is(EntityTypeTags.AQUATIC))
            && rng.nextFloat() < 0.16F * seaLv) {
            Item treasure = switch (rng.nextInt(5)) {
                case 0 -> Items.PRISMARINE_SHARD;
                case 1 -> Items.PRISMARINE_CRYSTALS;
                case 2 -> Items.LAPIS_LAZULI;
                case 3 -> Items.EMERALD;
                default -> Items.NAUTILUS_SHELL;
            };
            spawnItem(level, victim.position(), new ItemStack(treasure, 1));
        }
    }

    public static void tickServerPlayer(ServerLevel level, ServerPlayer player) {
        UUID pid = player.getUUID();
        MeleeSpearTransmutes.tickSpearHoldChargeAndRelease(level, player);

        if (hasArmorTransmute(player, "featherstep_lining") && player.fallDistance > 0.4F && player.fallDistance < 3.0F) {
            BlockPos below = player.blockPosition().below();
            BlockState bs = level.getBlockState(below);
            BlockState at = level.getBlockState(player.blockPosition());
            if (bs.is(Blocks.FARMLAND) || at.is(Blocks.FARMLAND) || bs.is(Blocks.TURTLE_EGG) || at.is(Blocks.TURTLE_EGG)) {
                player.resetFallDistance();
            }
        }
        if (hasArmorTransmute(player, "anvil_stomp")) {
            if (player.isInWater() && !player.onGround()) {
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(v.x, Math.max(-0.45D, v.y - 0.035D), v.z);
                player.hurtMarked = true;
            }
            if (!player.onGround() && player.fallDistance >= 3.2D) {
                for (LivingEntity under : nearbyHostiles(level, player, player.position().subtract(0.0D, 0.8D, 0.0D), 1.8D, null)) {
                    float crush = (float) Math.min(16.0D, player.fallDistance * 1.4D);
                    under.hurtServer(level, level.damageSources().playerAttack(player), crush);
                    player.fallDistance *= 0.45D;
                    level.playSound(null, under.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.1F);
                }
            }
        }

        if (level.getGameTime() % 10L != 0L) return;

        Long chaseUntil = SOUL_CHASE_UNTIL.get(pid);
        if (chaseUntil != null && level.getGameTime() < chaseUntil) grantEffect(player, "speed", 25, 1);
        if (player.isShiftKeyDown() && getTransmutedLevel(player.getMainHandItem(), "silent_arrow") > 0) {
            grantEffect(player, "speed", 25, 0);
        }
        if (player.isShiftKeyDown() && hasArmorOrShieldTransmute(player, "shroud_cloak")) {
            grantEffect(player, "invisibility", 30, 0);
        }
        if (player.isSprinting() && level.getGameTime() % 50L == 0L && hasArmorOrShieldTransmute(player, "lunge_wind_stride")) {
            Vec3 horiz = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
            if (horiz.lengthSqr() > 0.001D) {
                Vec3 boost = horiz.normalize().scale(0.42D);
                player.push(boost.x, 0.08D, boost.z);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.15D, player.getZ(), 5, 0.2D, 0.1D, 0.2D, 0.03D);
            }
        }
        if (player.getY() < level.getMinY() - 6
            && (hasArmorOrShieldTransmute(player, "void_homing") || hasArmorOrShieldTransmute(player, "auto_reel"))
            && checkSkillCooldown(player, "void_homing_rescue", level.getGameTime(), 1200)) {
            int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
            player.resetFallDistance();
            player.teleportTo(player.getX(), Math.max(level.getMinY() + 68, terrainY + 4), player.getZ());
            grantEffect(player, "slow_falling", 180, 0);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.1F);
        }
        if (player.isInWaterOrRain()) {
            if (hasArmorOrShieldTransmute(player, "tidal_carapace") || hasArmorOrShieldTransmute(player, "gill_membrane")) {
                player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 25));
            }
            if (player.isInWater() && hasArmorOrShieldTransmute(player, "pelagic_stream")) {
                grantEffect(player, "dolphins_grace", 35, 0);
            }
        }
        if (player.isFallFlying()) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int slipLv = getTransmutedLevel(chest, "slipstream_wings");
            if (slipLv > 0 && player.getXRot() < 8.0F && player.getXRot() > -30.0F) {
                Vec3 vel = player.getDeltaMovement();
                if (vel.horizontalDistanceSqr() < 2.2D) {
                    Vec3 nudge = player.getLookAngle().normalize().scale(0.06D + 0.025D * slipLv);
                    player.setDeltaMovement(vel.add(nudge.x, 0.015D, nudge.z));
                    player.hurtMarked = true;
                }
            }
            int stormLv = getTransmutedLevel(chest, "storm_glider");
            if (stormLv > 0 && player.isInWaterOrRain()) {
                Vec3 boost = player.getLookAngle().normalize().scale(0.14D + 0.05D * stormLv);
                player.setDeltaMovement(player.getDeltaMovement().add(boost));
                player.hurtMarked = true;
            }
        }
        if (hasArmorOrShieldTransmute(player, "venom_ward")) {
            BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace("poison")).ifPresent(player::removeEffect);
        }
        if (getTransmutedLevel(player.getMainHandItem(), "shepherd_lure") > 0
            || getTransmutedLevel(player.getOffhandItem(), "shepherd_lure") > 0) {
            for (Mob animal : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(9.0D), m -> isFarmAnimal(m.getType()))) {
                animal.getNavigation().moveTo(player, 1.1D);
            }
        }
        if (hasArmorOrShieldTransmute(player, "monster_beacon")) {
            for (Mob monster : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(18.0D), m -> m.getType().getCategory() == MobCategory.MONSTER)) {
                if (monster.getTarget() == null) monster.setTarget(player);
                monster.getNavigation().moveTo(player, 1.15D);
            }
        }
        EquipmentSlot[] allSlots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        for (EquipmentSlot slot : allSlots) {
            ItemStack gear = player.getItemBySlot(slot);
            if (!gear.isEmpty() && getTransmutedLevel(gear, "unending_edge") > 0
                && gear.isDamageableItem() && gear.getDamageValue() >= gear.getMaxDamage() - 1) {
                gear.setDamageValue(Math.max(0, gear.getMaxDamage() - 2));
            }
        }
    }

    private static boolean isFarmAnimal(EntityType<?> type) {
        return type == EntityType.COW || type == EntityType.SHEEP || type == EntityType.PIG
            || type == EntityType.CHICKEN || type == EntityType.GOAT || type == EntityType.RABBIT
            || type == EntityType.MOOSHROOM;
    }
}
