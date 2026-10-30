package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.FlyingBladeManager;
import enchantment_fusion.mechanics.ProjectilePathTracker;
import enchantment_fusion.model.FusedPair;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Core Melee Sword / Axe / Plunder / 3D Phantom-Wave Signature Resonances.
 */
public final class MeleeBladeSignatures {
    private MeleeBladeSignatures() {}

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
            case "sanctified_execute" -> {
                if (target != null) {
                    for (int h = 0; h <= 4; h++) {
                        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + h * 0.75D, pos.z, 4, 0.12D, 0.15D, 0.12D, 0.02D);
                    }
                    float armorBypass = Math.min(6.0F, 1.5F + target.getArmorValue() * 0.22F);
                    target.hurtServer(level, level.damageSources().magic(), armorBypass);
                    if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()).is(EntityTypeTags.UNDEAD)
                        && target.getHealth() <= target.getMaxHealth() * 0.25F) {
                        target.hurtServer(level, level.damageSources().magic(), target.getHealth() + 10.0F);
                        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 3.0F));
                        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.6F);
                    }
                }
                return true;
            }
            case "radiant_shockwave" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.4D, target)) {
                    Vec3 radial = mob.position().subtract(pos).normalize().scale(0.68D);
                    mob.push(radial.x, 0.26D, radial.z);
                    if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).is(EntityTypeTags.UNDEAD)) {
                        mob.igniteForSeconds(4.0F);
                        mob.hurtServer(level, level.damageSources().magic(), 3.5F + 0.7F * pLv);
                    } else {
                        mob.hurtServer(level, level.damageSources().magic(), 1.8F);
                        grantEffect(mob, "weakness", 70, 0);
                        grantEffect(mob, "blindness", 45, 0);
                    }
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.7D, pos.z, 20, 0.65D, 0.25D, 0.65D, 0.10D);
                return true;
            }
            case "hamstring_rend" -> {
                if (target != null) {
                    boolean airborneOrMoving = !target.onGround() || target.getDeltaMovement().horizontalDistanceSqr() > 0.03D;
                    target.setDeltaMovement(0.0D, Math.min(-0.22D, target.getDeltaMovement().y), 0.0D);
                    target.hurtMarked = true;
                    grantEffect(target, "slowness", 85, 2);
                    if (airborneOrMoving) {
                        target.hurtServer(level, level.damageSources().playerAttack(player), 2.8F + 0.55F * pLv);
                    }
                }
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.45D, pos.z, 12, 0.3D, 0.15D, 0.3D, 0.08D);
                return true;
            }
            case "neurotoxin_bleed" -> {
                if (target != null) {
                    grantEffect(target, "poison", 60, 0);
                    int stacks = NEUROTOXIN_STACKS.merge(target.getUUID(), 1, Integer::sum);
                    if (stacks >= 3) {
                        NEUROTOXIN_STACKS.remove(target.getUUID());
                        target.hurtServer(level, level.damageSources().magic(), 5.5F + 0.8F * sLv);
                        Vec3 stagger = target.position().subtract(player.position()).normalize().scale(0.55D);
                        target.push(stagger.x, 0.22D, stagger.z);
                        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.08D);
                    }
                }
                return true;
            }
            case "blight_halo" -> {
                spawnBlightCloud(level, player, pos, 2.6F + 0.2F * pLv, 60);
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    Vec3 inward = pos.subtract(mob.position()).scale(0.28D);
                    mob.push(inward.x, 0.08D, inward.z);
                }
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.5D, pos.z, 16, 0.55D, 0.2D, 0.55D, 0.04D);
                return true;
            }
            case "soul_cocoon" -> {
                if (target != null) {
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + 140));
                    grantEffect(target, "slowness", 65, 3);
                    player.heal(1.5F + 0.35F * sLv);
                }
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.7D, pos.z, 12, 0.3D, 0.35D, 0.3D, 0.03D);
                return true;
            }
            case "blast_flare" -> {
                if (FlameKineticEffects.matches(pair, sigId)) {
                    if (target != null) FlameKineticEffects.activate(level, player, target, pair, sigId);
                } else {
                    // A datapack may reuse this ID for a different pair: keep its
                    // previous behavior outside the two confirmed redesigns.
                    if (target != null) {
                        Vec3 dir = target.position().subtract(player.position()).normalize().scale(0.85D + 0.25D * sLv);
                        target.push(dir.x, 0.35D, dir.z);
                        target.igniteForSeconds(4.0F * pLv);
                        for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.2D, target)) extra.igniteForSeconds(3.0F);
                    }
                    level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.4D, pos.z, 8, 0.3D, 0.2D, 0.3D, 0.05D);
                }
                return true;
            }
            case "blazing_furrow" -> {
                if (FlameKineticEffects.matches(pair, sigId)) {
                    if (target != null) FlameKineticEffects.activate(level, player, target, pair, sigId);
                } else if (target != null) {
                    Vec3 dir = target.position().subtract(player.position()).normalize();
                    target.push(dir.x * (0.7D + 0.25D * pLv), 0.28D, dir.z * (0.7D + 0.25D * pLv));
                    target.igniteForSeconds(4.0F);
                    if (player.mayBuild() && !player.isShiftKeyDown()) {
                        for (int step = 1; step <= 3; step++) {
                            BlockPos firePos = BlockPos.containing(pos.add(dir.scale(step)));
                            if (level.getBlockState(firePos).isAir() && !level.getBlockState(firePos.below()).isAir()) {
                                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }
                return true;
            }
            case "wildfire_arc" -> {
                Vec3 look = player.getLookAngle().normalize();
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.5D, null)) {
                    Vec3 toMob = mob.position().subtract(player.position()).normalize();
                    if (toMob.dot(look) < -0.15D) continue;
                    if (mob.isBlocking()) mob.stopUsingItem();
                    mob.igniteForSeconds(5.0F + 2.0F * pLv);
                    mob.push(toMob.x * 0.55D, 0.22D, toMob.z * 0.55D);
                    mob.hurtServer(level, level.damageSources().onFire(), 1.8F + 0.5F * sLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 3, 0.45D, 0.1D, 0.45D, 0.0D);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.7D, pos.z, 18, 0.65D, 0.2D, 0.65D, 0.05D);
                return true;
            }
            case "soulburn_detonate" -> {
                if (target != null && target.isOnFire()) {
                    int remTicks = target.getRemainingFireTicks();
                    target.clearFire();
                    float burst = Math.min(10.5F, 3.2F + remTicks * 0.04F);
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                        mob.hurtServer(level, level.damageSources().magic(), burst);
                        Vec3 out = mob.position().subtract(pos).normalize().scale(0.52D);
                        mob.push(out.x, 0.46D, out.z);
                    }
                    level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.6D, pos.z, 3, 0.3D, 0.2D, 0.3D, 0.0D);
                    level.playSound(null, BlockPos.containing(pos), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.85F);
                } else if (target != null) {
                    target.igniteForSeconds(4.0F);
                }
                return true;
            }
            case "cyclone_repulse" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, player.position(), 4.5D, null)) {
                    Vec3 push = mob.position().subtract(player.position()).normalize().scale(0.85D + 0.2D * pLv);
                    mob.push(push.x, 0.32D, push.z);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.8D, player.getZ(), 3, 0.6D, 0.1D, 0.6D, 0.0D);
                return true;
            }
            case "gale_vortex" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 5.5D, target)) {
                    Vec3 pull = pos.subtract(mob.position()).scale(0.42D);
                    mob.push(pull.x, 0.15D, pull.z);
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5D, pos.z, 12, 0.5D, 0.2D, 0.5D, 0.03D);
                return true;
            }
            case "trophy_shockwave" -> {
                if (target != null) {
                    double wealthMass = 1.0D + Math.min(1.3D, target.getArmorValue() * 0.05D + target.getMaxHealth() * 0.015D);
                    Vec3 dir = target.position().subtract(player.position()).normalize();
                    target.push(dir.x * (0.85D + 0.20D * sLv) * wealthMass, 0.34D, dir.z * (0.85D + 0.20D * sLv) * wealthMass);
                    Vec3 bowlingEnd = pos.add(dir.scale(3.6D));
                    int bowled = 0;
                    for (LivingEntity behind : ProjectilePathTracker.querySweptSegmentHostiles(level, player, pos, bowlingEnd, 1.45D)) {
                        if (behind == target) continue;
                        behind.hurtServer(level, level.damageSources().playerAttack(player), 2.6F + 0.65F * pLv);
                        behind.push(dir.x * 0.55D, 0.24D, dir.z * 0.55D);
                        bowled++;
                    }
                    if (bowled > 0 || rng.nextFloat() < 0.25F + 0.06F * pLv) {
                        spawnItem(level, pos, new ItemStack(bowled > 0 ? Items.EMERALD : Items.GOLD_NUGGET, 1 + Math.min(2, bowled)));
                    }
                }
                return true;
            }
            case "reaping_gale" -> {
                if (fullChargeMelee) {
                    AABB vacuumBox = AABB.ofSize(player.position(), 15.0D, 6.0D, 15.0D);
                    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, vacuumBox, Entity::isAlive)) {
                        item.setNoPickUpDelay();
                        item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                    }
                    for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, vacuumBox, Entity::isAlive)) {
                        orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                    }
                    for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                        float dmg = (mob.getHealth() <= mob.getMaxHealth() * 0.18F) ? (mob.getHealth() + 6.0F) : (2.0F + 0.5F * pLv);
                        mob.hurtServer(level, level.damageSources().playerAttack(player), dmg);
                    }
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 3, 0.5D, 0.15D, 0.5D, 0.0D);
                }
                return true;
            }
            case "concussive_plunder" -> {
                if (target != null) {
                    Vec3 knock = target.position().subtract(player.position()).normalize().scale(0.88D + 0.18D * pLv);
                    target.push(knock.x, 0.30D, knock.z);
                    grantEffect(target, "weakness", 80, 0);
                    grantEffect(target, "slowness", 60, 0);
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.1F + 0.5F * sLv);
                    if (rng.nextFloat() < 0.34F) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(rng.nextFloat() < 0.25F ? Items.EMERALD : Items.GOLD_NUGGET, 1));
                        ExperienceOrb.award(level, player.position(), 1 + sLv);
                    }
                }
                return true;
            }
            case "legion_plunder" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, null)) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 1.8F + 0.45F * pLv);
                    if (PLUCKED_LIVING_MOBS.add(mob.getUUID()) && rng.nextFloat() < 0.48F) {
                        Item plucked = switch (rng.nextInt(4)) {
                            case 0 -> Items.FEATHER;
                            case 1 -> Items.LEATHER;
                            case 2 -> Items.BONE;
                            default -> Items.GOLD_NUGGET;
                        };
                        spawnItem(level, mob.position(), new ItemStack(plucked, 1));
                    }
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 2, 0.4D, 0.1D, 0.4D, 0.0D);
                return true;
            }
            case "bounty_jackpot" -> {
                if (target != null) target.hurtServer(level, level.damageSources().magic(), 1.6F + 0.45F * pLv);
                if (rng.nextFloat() < 0.28F + 0.06F * pLv) {
                    Item jackpot = switch (rng.nextInt(4)) {
                        case 0 -> Items.EMERALD;
                        case 1 -> Items.LAPIS_LAZULI;
                        case 2 -> Items.AMETHYST_SHARD;
                        default -> Items.GOLD_NUGGET;
                    };
                    spawnItem(level, pos, new ItemStack(jackpot, 1));
                    ExperienceOrb.award(level, pos, 2 + sLv);
                    level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.55F);
                }
                return true;
            }
            case "searing_feast", "alchemic_pyre", "midas_cleave" -> {
                if (target != null) {
                    if (!"midas_cleave".equals(sigId)) {
                        target.igniteForSeconds(5.0F);
                        target.hurtServer(level, level.damageSources().magic(), 2.2F + 0.5F * pLv);
                    } else {
                        for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.2D, null)) {
                            mob.hurtServer(level, level.damageSources().playerAttack(player), 1.8F + 0.45F * pLv);
                        }
                    }
                }
                if (rng.nextFloat() < 0.30F) {
                    Item bonus = "alchemic_pyre".equals(sigId)
                        ? (rng.nextBoolean() ? Items.GOLD_NUGGET : Items.BLAZE_POWDER)
                        : ("midas_cleave".equals(sigId) ? Items.EMERALD : Items.GOLD_NUGGET);
                    spawnItem(level, pos, new ItemStack(bonus, 1));
                }
                return true;
            }
            case "phantom_triple_slash" -> {
                if (fullChargeMelee) {
                    FlyingBladeManager.launchAngledPhantomWeapon(
                        level, player, InteractionHand.MAIN_HAND, hostStack, pLv,
                        FlyingBladeManager.FlightStyle.PHANTOM_LANCE, -12.0F
                    );
                    FlyingBladeManager.launchAngledPhantomWeapon(
                        level, player, InteractionHand.MAIN_HAND, hostStack, pLv,
                        FlyingBladeManager.FlightStyle.PHANTOM_LANCE, 12.0F
                    );
                    if (target != null) {
                        hurtBypassingIframe(level, target, level.damageSources().magic(), 1.8F + 0.35F * pLv);
                    }
                }
                return true;
            }
            case "flying_sword_qi" -> {
                if (fullChargeMelee) {
                    FlyingBladeManager.launchLinearPhantomWeapon(
                        level, player, InteractionHand.MAIN_HAND, hostStack,
                        Math.max(pLv, sLv), FlyingBladeManager.FlightStyle.PHANTOM_LANCE
                    );
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
