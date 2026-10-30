package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.AberrantRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared runtime state maps, world DoT/snapback tickers, and entity/projectile helpers
 * across all modular Signature domain handlers.
 */
public final class SignatureRuntimeSupport {
    public record SafeGround(ResourceKey<Level> dimension, Vec3 position) {}

    public record KiteSnapback(
        UUID ownerId,
        ResourceKey<Level> dimension,
        Vec3 anchorPos,
        long triggerTick
    ) {}

    public record ConvergingArrowState(
        UUID shooterId,
        Vec3 aimOrigin,
        Vec3 aimDir,
        long spawnTick
    ) {}

    public record BlightCloudZone(
        UUID ownerId,
        ResourceKey<Level> dimension,
        Vec3 pos,
        float radius,
        int pulsesLeft
    ) {}

    public static final List<BlightCloudZone> ACTIVE_BLIGHT_CLOUDS = Collections.synchronizedList(new ArrayList<>());

    public static final Map<UUID, Integer> NEUROTOXIN_STACKS = new ConcurrentHashMap<>();
    public static final Map<UUID, SafeGround> LAST_SAFE_GROUND = new ConcurrentHashMap<>();
    public static final Map<UUID, Float> RECENT_DAMAGE_TAKEN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> RECENT_DAMAGE_UNTIL = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> GLASS_SHIELD_COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> FORTUNE_GRACE_COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LUCKY_GOLDEN_COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> PORCELAIN_BASTION_COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LOYAL_LIFELINE_COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> SOUL_FIRE_TICKS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> SKYFIRE_BOLT_TICKS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> PERSISTENCE_STACKS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> FORGED_QUILL_STACKS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> TEMPERED_STEEL_STACKS = new ConcurrentHashMap<>();
    public static final Map<UUID, KiteSnapback> KITE_SNAPBACK_MAP = new ConcurrentHashMap<>();
    public static final Map<UUID, ConvergingArrowState> CONVERGING_ARROWS = new ConcurrentHashMap<>();
    public static final Set<UUID> FISSION_SPLIT_PROCESSED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<UUID> PLUCKED_LIVING_MOBS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private SignatureRuntimeSupport() {}

    public static void onPlayerDisconnect(UUID playerId) {

        LAST_SAFE_GROUND.remove(playerId);
        RECENT_DAMAGE_TAKEN.remove(playerId);
        RECENT_DAMAGE_UNTIL.remove(playerId);
        GLASS_SHIELD_COOLDOWN.remove(playerId);
        FORTUNE_GRACE_COOLDOWN.remove(playerId);
        LUCKY_GOLDEN_COOLDOWN.remove(playerId);
        PORCELAIN_BASTION_COOLDOWN.remove(playerId);
        LOYAL_LIFELINE_COOLDOWN.remove(playerId);
        PERSISTENCE_STACKS.remove(playerId);
        FORGED_QUILL_STACKS.remove(playerId);
        TEMPERED_STEEL_STACKS.remove(playerId);
    }

    public static void clearRuntimeState() {
        NEUROTOXIN_STACKS.clear();

        LAST_SAFE_GROUND.clear();
        RECENT_DAMAGE_TAKEN.clear();
        RECENT_DAMAGE_UNTIL.clear();
        GLASS_SHIELD_COOLDOWN.clear();
        FORTUNE_GRACE_COOLDOWN.clear();
        LUCKY_GOLDEN_COOLDOWN.clear();
        PORCELAIN_BASTION_COOLDOWN.clear();
        LOYAL_LIFELINE_COOLDOWN.clear();
        SOUL_FIRE_TICKS.clear();
        SKYFIRE_BOLT_TICKS.clear();
        PERSISTENCE_STACKS.clear();
        FORGED_QUILL_STACKS.clear();
        TEMPERED_STEEL_STACKS.clear();
        KITE_SNAPBACK_MAP.clear();
        CONVERGING_ARROWS.clear();
        ACTIVE_BLIGHT_CLOUDS.clear();
        FISSION_SPLIT_PROCESSED.clear();
        PLUCKED_LIVING_MOBS.clear();
    }

    public static void tickWorldDots(MinecraftServer server) {
        if (!KITE_SNAPBACK_MAP.isEmpty()) {
            for (UUID targetId : new ArrayList<>(KITE_SNAPBACK_MAP.keySet())) {
                KiteSnapback ks = KITE_SNAPBACK_MAP.get(targetId);
                if (ks == null) continue;
                ServerLevel dimLevel = server.getLevel(ks.dimension());
                if (dimLevel == null) {
                    KITE_SNAPBACK_MAP.remove(targetId);
                    continue;
                }
                if (dimLevel.getGameTime() >= ks.triggerTick()) {
                    KITE_SNAPBACK_MAP.remove(targetId);
                    Entity ent = dimLevel.getEntity(targetId);
                    if (ent instanceof LivingEntity liv && liv.isAlive()) {
                        Vec3 yank = ks.anchorPos().subtract(liv.position());
                        liv.setDeltaMovement(yank.scale(0.45D).add(0.0D, -0.55D, 0.0D));
                        liv.hurtMarked = true;
                        liv.hurtServer(dimLevel, dimLevel.damageSources().fall(), 3.6F);
                        dimLevel.sendParticles(
                            ParticleTypes.CRIT,
                            liv.getX(), liv.getY() + 0.7D, liv.getZ(),
                            12, 0.3D, 0.3D, 0.3D, 0.1D
                        );
                    }
                }
            }
        }

        if (!ACTIVE_BLIGHT_CLOUDS.isEmpty() && server.getTickCount() % 10 == 0) {
            synchronized (ACTIVE_BLIGHT_CLOUDS) {
                List<BlightCloudZone> keep = new ArrayList<>();
                for (BlightCloudZone z : ACTIVE_BLIGHT_CLOUDS) {
                    ServerLevel dim = server.getLevel(z.dimension());
                    Entity ownerEnt = dim != null ? dim.getEntity(z.ownerId()) : null;
                    if (dim != null && ownerEnt instanceof ServerPlayer sp && sp.isAlive()) {
                        dim.sendParticles(
                            ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            z.pos().x, z.pos().y + 0.25D, z.pos().z,
                            8, z.radius() * 0.45D, 0.15D, z.radius() * 0.45D, 0.01D
                        );
                        for (LivingEntity mob : nearbyHostiles(dim, sp, z.pos(), z.radius(), null)) {
                            grantEffect(mob, "slowness", 45, 1);
                            grantEffect(mob, "wither", 40, 0);
                        }
                        if (z.pulsesLeft() > 1) {
                            keep.add(new BlightCloudZone(z.ownerId(), z.dimension(), z.pos(), z.radius(), z.pulsesLeft() - 1));
                        }
                    }
                }
                ACTIVE_BLIGHT_CLOUDS.clear();
                ACTIVE_BLIGHT_CLOUDS.addAll(keep);
            }
        }

        if (server.getTickCount() % 20 != 0) {
            return;
        }
        if (!SOUL_FIRE_TICKS.isEmpty()) {
            for (UUID entityId : new ArrayList<>(SOUL_FIRE_TICKS.keySet())) {
                Integer remaining = SOUL_FIRE_TICKS.get(entityId);
                if (remaining == null || remaining <= 0) {
                    SOUL_FIRE_TICKS.remove(entityId);
                    continue;
                }
                LivingEntity liv = findLivingAcrossLevels(server, entityId);
                if (liv == null || !liv.isAlive() || !(liv.level() instanceof ServerLevel entLevel)) {
                    SOUL_FIRE_TICKS.remove(entityId);
                    continue;
                }
                liv.hurtServer(entLevel, entLevel.damageSources().magic(), 2.0F);
                entLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    liv.getX(), liv.getY() + 0.6D, liv.getZ(),
                    6, 0.25D, 0.25D, 0.25D, 0.02D
                );
                int next = remaining - 20;
                if (next <= 0) SOUL_FIRE_TICKS.remove(entityId);
                else SOUL_FIRE_TICKS.put(entityId, next);
            }
        }

        if (!SKYFIRE_BOLT_TICKS.isEmpty()) {
            for (UUID entityId : new ArrayList<>(SKYFIRE_BOLT_TICKS.keySet())) {
                Integer remaining = SKYFIRE_BOLT_TICKS.get(entityId);
                if (remaining == null || remaining <= 0) {
                    SKYFIRE_BOLT_TICKS.remove(entityId);
                    continue;
                }
                LivingEntity liv = findLivingAcrossLevels(server, entityId);
                if (liv == null || !liv.isAlive() || !(liv.level() instanceof ServerLevel entLevel)) {
                    SKYFIRE_BOLT_TICKS.remove(entityId);
                    continue;
                }
                if (remaining % 40 == 0) {
                    AberrantRegistry.spawnLightning(entLevel, liv.position());
                }
                int next = remaining - 20;
                if (next <= 0) SKYFIRE_BOLT_TICKS.remove(entityId);
                else SKYFIRE_BOLT_TICKS.put(entityId, next);
            }
        }
    }

    private static @Nullable LivingEntity findLivingAcrossLevels(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e instanceof LivingEntity liv) {
                return liv;
            }
        }
        return null;
    }

    public static void hurtBypassingIframe(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        int prevInvuln = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurtServer(level, source, amount);
        } finally {
            target.invulnerableTime = prevInvuln;
        }
    }

    public static List<LivingEntity> nearbyHostiles(
        ServerLevel level,
        ServerPlayer player,
        Vec3 center,
        double radius,
        @Nullable LivingEntity exclude
    ) {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius, radius * 2.0D);
        return level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            e -> e != player && e != exclude && e.isAlive() && !e.isAlliedTo(player)
        );
    }

    public static void spawnItem(ServerLevel level, Vec3 pos, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, pos.x, pos.y + 0.35D, pos.z, stack);
        level.addFreshEntity(drop);
    }

    public static void grantEffect(LivingEntity entity, String path, int ticks, int amp) {
        BuiltInRegistries.MOB_EFFECT
            .get(Identifier.withDefaultNamespace(path))
            .ifPresent(h -> entity.addEffect(new MobEffectInstance(h, ticks, amp, true, true)));
    }

    public static void spawnBlightCloud(ServerLevel level, ServerPlayer owner, Vec3 pos, float radius, int durationTicks) {
        int pulses = Math.max(2, durationTicks / 10);
        synchronized (ACTIVE_BLIGHT_CLOUDS) {
            if (ACTIVE_BLIGHT_CLOUDS.size() < 48) {
                ACTIVE_BLIGHT_CLOUDS.add(new BlightCloudZone(owner.getUUID(), level.dimension(), pos, radius, pulses));
            }
        }
    }

    public static void spawnCounterSpectralBolt(ServerLevel level, ServerPlayer defender, Vec3 aimDir, double baseDmg) {
        SpectralArrow bolt = new SpectralArrow(level, defender, new ItemStack(Items.SPECTRAL_ARROW), null);
        Vec3 spawn = defender.getEyePosition().add(aimDir.normalize().scale(0.55D));
        bolt.setPos(spawn.x, spawn.y - 0.15D, spawn.z);
        bolt.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        bolt.setBaseDamage(baseDmg);
        bolt.shoot(aimDir.x, aimDir.y + 0.08D, aimDir.z, 1.9F, 1.5F);
        level.addFreshEntity(bolt);
    }

    public static @Nullable Item bonusRawForOre(Block block) {
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.RAW_IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return Items.RAW_GOLD;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.RAW_COPPER;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Items.LAPIS_LAZULI;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Items.COAL;
        return null;
    }

    public static @Nullable Item smeltedResultForBlock(Block block) {
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.IRON_INGOT;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return Items.GOLD_INGOT;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.COPPER_INGOT;
        if (block == Blocks.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        if (block == Blocks.SAND || block == Blocks.RED_SAND) return Items.GLASS;
        return null;
    }
}
