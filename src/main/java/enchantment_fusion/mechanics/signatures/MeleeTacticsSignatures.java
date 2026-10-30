package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.SignatureRuntimeSupport.*;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.model.FusedPair;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tactical Melee Signatures (Quench-Temper, Mirror-Moon Deflection, Paper-Thin Cleave,
 * Rising Dragon, Blood Vengeance, Crystal Snare, Hellfire/Amber) &amp; Kill-Trophy Triggers.
 */
public final class MeleeTacticsSignatures {
    private MeleeTacticsSignatures() {}

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
            case "rising_dragon_leap" -> {
                if (fullChargeMelee && player.onGround()) {
                    player.push(0.0D, 0.42D, 0.0D);
                    player.hurtMarked = true;
                }
                if (target != null) {
                    player.fallDistance = 0.0F;
                    target.hurtServer(level, level.damageSources().playerAttack(player), 2.6F + 0.4F * sLv);
                    target.push(0.0D, 0.44D, 0.0D);
                }
                return true;
            }
            case "blood_vengeance" -> {
                Long until = RECENT_DAMAGE_UNTIL.get(player.getUUID());
                Float recent = RECENT_DAMAGE_TAKEN.get(player.getUUID());
                if (target != null && until != null && recent != null && level.getGameTime() <= until) {
                    RECENT_DAMAGE_UNTIL.remove(player.getUUID());
                    float payback = Math.min(12.0F, recent * 0.85F + 2.0F);
                    target.hurtServer(level, level.damageSources().magic(), payback);
                    player.heal(Math.min(3.0F, payback * 0.25F));
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.x, pos.y + 1.0D, pos.z, 5, 0.2D, 0.2D, 0.2D, 0.02D);
                }
                return true;
            }
            case "nether_soul_fire" -> {
                if (target != null) {
                    SOUL_FIRE_TICKS.put(target.getUUID(), 100);
                    target.hurtServer(level, level.damageSources().magic(), 2.8F + 0.4F * pLv);
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.7D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.03D);
                return true;
            }
            case "crystal_snare" -> {
                if (target != null) {
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    target.setTicksFrozen(Math.min(320, target.getTicksFrozen() + 180));
                    level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.45F);
                    if (rng.nextFloat() < 0.25F && player.mayBuild()) {
                        AberrantRegistry.placeIceCage3x3x3(level, target.blockPosition(), Blocks.GLASS.defaultBlockState(), false);
                    }
                }
                return true;
            }
            case "overflow_edge" -> {
                if (hostStack.isDamaged()) {
                    hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 2));
                }
                if (target != null) {
                    float integrity = hostStack.isDamageableItem()
                        ? (1.0F - (float) hostStack.getDamageValue() / (float) Math.max(1, hostStack.getMaxDamage()))
                        : 1.0F;
                    float arcDmg = 2.0F + 0.55F * sLv + 1.5F * integrity;
                    target.hurtServer(level, level.damageSources().magic(), arcDmg);
                    for (LivingEntity extra : nearbyHostiles(level, player, pos, 3.0D, target)) {
                        extra.hurtServer(level, level.damageSources().magic(), 1.6F + 0.35F * sLv);
                    }
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.8D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                return true;
            }
            case "shield_piercer" -> {
                Vec3 look = player.getLookAngle().normalize();
                for (int step = 1; step <= 4; step++) {
                    Vec3 p = player.getEyePosition().add(look.scale(step));
                    for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(p, 1.5D, 1.5D, 1.5D), e -> e != player && e.isAlive())) {
                        if (mob.isBlocking()) mob.stopUsingItem();
                        mob.hurtServer(level, level.damageSources().magic(), 2.4F + 0.4F * pLv);
                    }
                }
                return true;
            }
            case "snap_crescent_draw" -> {
                Vec3 step = player.getLookAngle().normalize().scale(0.38D);
                player.push(step.x, 0.08D, step.z);
                player.hurtMarked = true;
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, target)) {
                    mob.hurtServer(level, level.damageSources().playerAttack(player), 2.2F + 0.45F * sLv);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 2, 0.35D, 0.1D, 0.35D, 0.0D);
                return true;
            }
            case "undying_hellfire" -> {
                if (target != null) {
                    target.igniteForSeconds(10.0F + 3.0F * sLv);
                    for (LivingEntity near : nearbyHostiles(level, player, pos, 3.8D, target)) {
                        near.igniteForSeconds(5.0F + sLv);
                        near.hurtServer(level, level.damageSources().onFire(), 1.8F + 0.4F * pLv);
                    }
                }
                return true;
            }
            case "amber_embalm" -> {
                if (target != null) {
                    grantEffect(target, "slowness", 90, 2);
                    SOUL_FIRE_TICKS.put(target.getUUID(), 100);
                    target.hurtServer(level, level.damageSources().magic(), 2.4F + 0.65F * sLv);
                    level.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.35D, 0.3D, 0.04D);
                }
                return true;
            }
            case "moth_to_flame" -> {
                if (target != null) {
                    target.igniteForSeconds(6.0F + sLv);
                    for (LivingEntity moth : nearbyHostiles(level, player, pos, 5.5D, target)) {
                        Vec3 lurePull = pos.subtract(moth.position()).normalize().scale(0.58D + 0.10D * pLv);
                        moth.push(lurePull.x, 0.18D, lurePull.z);
                        moth.igniteForSeconds(3.0F);
                    }
                    level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0D, pos.z, 16, 0.35D, 0.45D, 0.35D, 0.04D);
                }
                return true;
            }
            case "beacon_bonfire" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, null)) {
                    mob.igniteForSeconds(4.0F + pLv);
                    mob.hurtServer(level, level.damageSources().onFire(), 2.0F + 0.5F * pLv);
                }
                AABB lootBox = AABB.ofSize(pos, 12.0D, 6.0D, 12.0D);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, lootBox, Entity::isAlive)) {
                    item.setNoPickUpDelay();
                    item.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
                for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, lootBox, Entity::isAlive)) {
                    orb.teleportTo(player.getX(), player.getY() + 0.4D, player.getZ());
                }
                return true;
            }
            case "quench_temper_blade" -> {
                if (target != null) {
                    if (target.isOnFire()) {
                        int rem = target.getRemainingFireTicks();
                        target.clearFire();
                        float quenchDmg = Math.min(11.0F, 4.2F + 0.75F * sLv + rem * 0.03F);
                        target.hurtServer(level, level.damageSources().magic(), quenchDmg);
                        if (hostStack.isDamaged()) {
                            hostStack.setDamageValue(Math.max(0, hostStack.getDamageValue() - 2));
                        }
                        level.playSound(null, target.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.85F, 1.35F);
                        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8D, pos.z, 18, 0.35D, 0.35D, 0.35D, 0.06D);
                    } else {
                        target.igniteForSeconds(5.0F + sLv);
                    }
                }
                return true;
            }
            case "mirror_moon_sweep" -> {
                AABB reflectBox = AABB.ofSize(player.getEyePosition().add(player.getLookAngle().scale(2.0D)), 5.0D, 3.5D, 5.0D);
                Vec3 aim = player.getLookAngle().normalize();
                for (Projectile proj : level.getEntitiesOfClass(Projectile.class, reflectBox, p -> p.isAlive() && p.getOwner() != player)) {
                    double spd = Math.max(1.2D, proj.getDeltaMovement().length());
                    proj.setOwner(player);
                    proj.setDeltaMovement(aim.scale(spd));
                    proj.hurtMarked = true;
                }
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.6D, null)) {
                    grantEffect(mob, "glowing", 100, 0);
                    mob.hurtServer(level, level.damageSources().magic(), 2.2F + 0.55F * pLv);
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.8D, pos.z, 12, 0.45D, 0.2D, 0.45D, 0.05D);
                return true;
            }
            case "paper_thin_cleave" -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 3.8D, null)) {
                    if (mob != target && mob.getType().getCategory() != MobCategory.MONSTER) continue;
                    boolean pristineTarget = mob.getHealth() >= mob.getMaxHealth() - 0.5F;
                    float dmg = (pristineTarget ? 4.4F : 2.0F) + 0.6F * sLv;
                    mob.hurtServer(level, level.damageSources().playerAttack(player), dmg);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.8D, pos.z, 2, 0.4D, 0.1D, 0.4D, 0.0D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void onEntityKilled(ServerLevel level, ServerPlayer killer, LivingEntity victim, String sigId) {
        switch (sigId) {
            case "pristine_trophy" -> {
                if (level.getRandom().nextFloat() < 0.14F) {
                    EntityType<?> vType = victim.getType();
                    Item skull = null;
                    if (vType == EntityType.ZOMBIE) skull = Items.ZOMBIE_HEAD;
                    else if (vType == EntityType.SKELETON) skull = Items.SKELETON_SKULL;
                    else if (vType == EntityType.WITHER_SKELETON) skull = Items.WITHER_SKELETON_SKULL;
                    else if (vType == EntityType.CREEPER) skull = Items.CREEPER_HEAD;
                    else if (vType == EntityType.PIGLIN) skull = Items.PIGLIN_HEAD;
                    if (skull != null) spawnItem(level, victim.position(), new ItemStack(skull, 1));
                }
            }
            case "curated_harvest" -> {
                if (level.getRandom().nextFloat() < 0.30F) {
                    Item curio = switch (level.getRandom().nextInt(3)) {
                        case 0 -> Items.LEATHER;
                        case 1 -> Items.BONE;
                        default -> Items.FEATHER;
                    };
                    spawnItem(level, victim.position(), new ItemStack(curio, 1 + level.getRandom().nextInt(2)));
                    ExperienceOrb.award(level, victim.position(), 2);
                }
            }
            case "undying_hellfire" -> {
                int bonusSec = Math.max(8, victim.getRemainingFireTicks() / 10);
                for (LivingEntity nextHost : nearbyHostiles(level, killer, victim.position(), 6.0D, victim)) {
                    nextHost.igniteForSeconds(bonusSec);
                    nextHost.hurtServer(level, level.damageSources().onFire(), 2.8F);
                }
                level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY() + 0.7D, victim.getZ(), 20, 0.5D, 0.4D, 0.5D, 0.08D);
            }
            case "amber_embalm" -> {
                for (LivingEntity near : nearbyHostiles(level, killer, victim.position(), 3.6D, victim)) {
                    near.hurtServer(level, level.damageSources().magic(), 3.2F);
                    grantEffect(near, "slowness", 60, 1);
                }
                if (level.getRandom().nextFloat() < 0.30F) {
                    spawnItem(level, victim.position(), new ItemStack(Items.HONEYCOMB, 1));
                }
            }
            default -> {}
        }
    }
}
