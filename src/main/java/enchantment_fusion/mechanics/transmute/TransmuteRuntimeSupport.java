package enchantment_fusion.mechanics.transmute;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared runtime state maps, roulette, projectile deflection, and item/effect helpers
 * across all modular Cross-Domain Transmutation handlers.
 */
public final class TransmuteRuntimeSupport {
    public static final Map<UUID, UUID> SOUL_CHASE_TARGET = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> SOUL_CHASE_UNTIL = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_SWING_TICK = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> BOTTLE_XP_BUFFER = new ConcurrentHashMap<>();
    public static final Map<String, Long> ACTIVE_SKILL_COOLDOWNS = new ConcurrentHashMap<>();
    public static final Set<UUID> LAUNCH_PROCESSED_PROJ = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<UUID> PICKPOCKETED_MOBS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Map<UUID, Integer> SPEAR_CHARGE_TICKS = new ConcurrentHashMap<>();
    public static final Map<UUID, ItemStack> SPEAR_CHARGE_WEAPON = new ConcurrentHashMap<>();

    public static final ThreadLocal<Boolean> IN_CUSTOM_COMBAT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public static final ThreadLocal<Boolean> IN_FAN_LAUNCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TransmuteRuntimeSupport() {}

    public static void onPlayerDisconnect(UUID playerId) {
        SOUL_CHASE_TARGET.remove(playerId);
        SOUL_CHASE_UNTIL.remove(playerId);
        LAST_SWING_TICK.remove(playerId);
        BOTTLE_XP_BUFFER.remove(playerId);
        SPEAR_CHARGE_TICKS.remove(playerId);
        SPEAR_CHARGE_WEAPON.remove(playerId);
        String prefix = playerId + "|";
        ACTIVE_SKILL_COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void clearRuntimeState() {
        SOUL_CHASE_TARGET.clear();
        SOUL_CHASE_UNTIL.clear();
        LAST_SWING_TICK.clear();
        BOTTLE_XP_BUFFER.clear();
        ACTIVE_SKILL_COOLDOWNS.clear();
        LAUNCH_PROCESSED_PROJ.clear();
        PICKPOCKETED_MOBS.clear();
        SPEAR_CHARGE_TICKS.clear();
        SPEAR_CHARGE_WEAPON.clear();
    }

    public static boolean checkSkillCooldown(ServerPlayer player, String skillKey, long now, int ticks) {
        String key = player.getUUID() + "|" + skillKey;
        Long until = ACTIVE_SKILL_COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        ACTIVE_SKILL_COOLDOWNS.put(key, now + Math.max(1, ticks));
        return true;
    }

    public static void damageItemSafely(ItemStack stack, int amount, ServerPlayer player) {
        if (player.getAbilities().instabuild || !stack.isDamageableItem()) return;
        if (TransmuteCatalog.getTransmutedLevel(stack, "unending_edge") > 0) {
            if (player.getRandom().nextBoolean()) return;
            int maxSafe = Math.max(0, stack.getMaxDamage() - 2);
            stack.setDamageValue(Math.min(maxSafe, stack.getDamageValue() + amount));
            return;
        }
        int next = stack.getDamageValue() + amount;
        if (next < stack.getMaxDamage()) {
            stack.setDamageValue(next);
        }
    }

    public static void deflectNearbyProjectiles(ServerLevel level, ServerPlayer player, double radius) {
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(radius), e -> e.isAlive() && e.getOwner() != player)) {
            p.setDeltaMovement(player.getLookAngle().normalize().scale(1.25D));
            p.setOwner(player);
            p.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1D, 0.1D, 0.1D, 0.05D);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.9F);
        }
    }

    public static void spawnCounterSpectralBolt(ServerLevel level, ServerPlayer defender, Vec3 aimDir, double baseDmg) {
        SpectralArrow bolt = new SpectralArrow(level, defender, new ItemStack(Items.SPECTRAL_ARROW), null);
        Vec3 spawn = defender.getEyePosition().add(aimDir.normalize().scale(0.55D));
        bolt.setPos(spawn.x, spawn.y - 0.15D, spawn.z);
        bolt.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        bolt.setBaseDamage(baseDmg);
        bolt.shoot(aimDir.x, aimDir.y + 0.08D, aimDir.z, 1.9F, 1.2F);
        level.addFreshEntity(bolt);
    }

    public static boolean hasArmorTransmute(ServerPlayer player, String variantId) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (TransmuteCatalog.getTransmutedLevel(player.getItemBySlot(s), variantId) > 0) return true;
        }
        return false;
    }

    public static boolean hasArmorOrShieldTransmute(ServerPlayer player, String variantId) {
        if (hasArmorTransmute(player, variantId)) return true;
        return TransmuteCatalog.getTransmutedLevel(player.getMainHandItem(), variantId) > 0
            || TransmuteCatalog.getTransmutedLevel(player.getOffhandItem(), variantId) > 0;
    }

    public static void triggerThreeDollarRoulette(ServerLevel level, ServerPlayer player, LivingEntity target, int fortuneLv) {
        RandomSource rng = level.getRandom();
        Vec3 pos = target.position();
        switch (rng.nextInt(8)) {
            case 0 -> {
                target.hurtServer(level, level.damageSources().magic(), 3.0F + fortuneLv);
                spawnItem(level, pos, new ItemStack(Items.GOLD_NUGGET, 1 + rng.nextInt(fortuneLv)));
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.1D);
            }
            case 1 -> {
                target.setTicksFrozen(260);
                grantEffect(target, "slowness", 70, 2);
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.8D, pos.z, 12, 0.3D, 0.3D, 0.3D, 0.05D);
            }
            case 2 -> {
                target.igniteForSeconds(4.0F + fortuneLv);
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.6D, pos.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case 3 -> {
                grantEffect(target, "poison", 80, 1);
                grantEffect(target, "weakness", 80, 0);
                level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.7D, pos.z, 10, 0.3D, 0.3D, 0.3D, 0.04D);
            }
            case 4 -> {
                grantEffect(target, "levitation", 32, 1);
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.8D, pos.z, 10, 0.25D, 0.25D, 0.25D, 0.05D);
            }
            case 5 -> {
                for (LivingEntity mob : nearbyHostiles(level, player, pos, 4.0D, null)) {
                    mob.hurtServer(level, level.damageSources().magic(), 2.8F);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.8D, pos.z, 14, 0.35D, 0.35D, 0.35D, 0.08D);
            }
            case 6 -> {
                grantEffect(target, "blindness", 60, 0);
                grantEffect(target, "glowing", 100, 0);
                level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 1.0D, pos.z, 14, 0.3D, 0.3D, 0.3D, 0.08D);
            }
            default -> {
                Vec3 dir = pos.subtract(player.position()).normalize().scale(1.2D);
                target.push(dir.x, 0.42D, dir.z);
                level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5D, pos.z, 10, 0.3D, 0.2D, 0.3D, 0.05D);
            }
        }
    }

    public static Item pickCommonMobDrop(EntityType<?> type, RandomSource rng) {
        if (type == EntityType.SKELETON || type == EntityType.STRAY) return rng.nextBoolean() ? Items.BONE : Items.ARROW;
        if (type == EntityType.CREEPER) return Items.GUNPOWDER;
        if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) return Items.STRING;
        if (type == EntityType.ENDERMAN) return Items.ENDER_PEARL;
        if (type == EntityType.BLAZE) return Items.BLAZE_ROD;
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            return rng.nextFloat() < 0.25F ? Items.IRON_INGOT : Items.ROTTEN_FLESH;
        }
        return Items.GOLD_NUGGET;
    }

    public static List<LivingEntity> nearbyHostiles(
        ServerLevel level,
        ServerPlayer player,
        Vec3 center,
        double radius,
        @Nullable LivingEntity exclude
    ) {
        AABB box = AABB.ofSize(center, radius * 2.0D, 3.5D, radius * 2.0D);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            e -> e != player && e != exclude && e.isAlive() && !e.isAlliedTo(player)
        );
    }

    public static void grantEffect(LivingEntity entity, String effectPath, int durationTicks, int amplifier) {
        BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(effectPath))
            .ifPresent(holder -> entity.addEffect(new MobEffectInstance(holder, Math.max(10, durationTicks), amplifier, false, true)));
    }

    public static void spawnItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ent = new ItemEntity(level, pos.x, pos.y + 0.35D, pos.z, stack);
        ent.setDefaultPickUpDelay();
        level.addFreshEntity(ent);
    }
}
