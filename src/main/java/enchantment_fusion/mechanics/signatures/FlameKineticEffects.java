package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.TriggerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The two fully fused Fire Aspect/Knockback signatures. All state is server-side,
 * transient and bounded: neither the pressure burst nor the wake places blocks.
 */
public final class FlameKineticEffects {
    public static final String PRESSURE = "blast_flare";
    public static final String WAKE = "blazing_furrow";
    private static final String FIRE = "minecraft:fire_aspect";
    private static final String KNOCKBACK = "minecraft:knockback";
    private static final int PRESSURE_TICKS = 40;
    private static final int FOLLOW_TICKS = 20;
    private static final int WAKE_TICKS = 60;
    private static final int MAX_ACTIVE_PER_KIND = 192;
    private static final int MAX_AFFECTED_PER_WAKE = 64;

    private record AttackKey(UUID owner, UUID target, ResourceKey<Level> dimension) {}
    private record Pending(AttackKey key, FusedPair pair, String signature, long tick, TriggerType trigger) {}
    private record Pressure(AttackKey key, long armedAfter, long expires, int fireLevel, int knockbackLevel) {}
    private record Segment(Vec3 from, Vec3 to, Vec3 direction, long expires) {}

    private static final class Wake {
        final AttackKey key;
        final long followUntil;
        final int fireLevel;
        final int knockbackLevel;
        final List<Segment> segments = new ArrayList<>();
        final Set<UUID> affected = new HashSet<>();
        Vec3 last;
        boolean following = true;

        Wake(AttackKey key, long followUntil, int fireLevel, int knockbackLevel, Vec3 last) {
            this.key = key;
            this.followUntil = followUntil;
            this.fireLevel = fireLevel;
            this.knockbackLevel = knockbackLevel;
            this.last = last;
        }
    }

    // Accessed only on the logical server thread. Pending attacks live at most one server tick.
    private static final Map<AttackKey, List<Pending>> PENDING = new HashMap<>();
    private static final List<Pending> CONFIRMED = new ArrayList<>();
    private static final List<Pressure> PRESSURES = new ArrayList<>();
    private static final List<Wake> WAKES = new ArrayList<>();

    private FlameKineticEffects() {}

    public static boolean isDefaultPair(FusedPair pair) {
        return pair != null && ((FIRE.equals(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId()))
            || (KNOCKBACK.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId())));
    }

    public static boolean matches(FusedPair pair, String signature) {
        return pair != null && ((PRESSURE.equals(signature) && FIRE.equals(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId()))
            || (WAKE.equals(signature) && KNOCKBACK.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId())));
    }

    /** Record an attempted full melee hit; do nothing until vanilla damage actually succeeds. */
    public static void prepare(ServerLevel level, ServerPlayer player, LivingEntity target, FusedPair pair, String signature,
                               TriggerType trigger) {
        if (!matches(pair, signature) || !canAffect(player, target, true)) return;
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        List<Pending> entries = PENDING.computeIfAbsent(key, unused -> new ArrayList<>());
        if (entries.size() < 2 && entries.stream().noneMatch(p -> p.signature().equals(signature))) {
            entries.add(new Pending(key, pair, signature, level.getGameTime(), trigger));
        }
    }

    /** Called from the actual unblocked player damage event, not from the pre-attack callback. */
    public static void confirm(ServerLevel level, ServerPlayer player, LivingEntity target) {
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        List<Pending> entries = PENDING.remove(key);
        if (entries == null) return;
        for (Pending pending : entries) {
            if (pending.tick() == level.getGameTime() && canAffect(player, target, true)
                && MatrixEffectExecutor.acquireDeferredCooldown(level, player, pending.pair(), pending.trigger())) {
                CONFIRMED.add(pending);
            }
        }
    }

    /** Immediate activation for already-dispatched non-melee trigger contexts. */
    public static void activate(ServerLevel level, ServerPlayer player, LivingEntity target, FusedPair pair, String signature) {
        if (!matches(pair, signature) || !canAffect(player, target, true)) return;
        int fireLevel = Math.min(5, FIRE.equals(pair.primaryId()) ? pair.primaryLevel() : pair.secondaryLevel());
        int knockLevel = Math.min(5, KNOCKBACK.equals(pair.primaryId()) ? pair.primaryLevel() : pair.secondaryLevel());
        Vec3 away = target.position().subtract(player.position());
        Vec3 direction = new Vec3(away.x, 0.0D, away.z).normalize();
        if (direction.lengthSqr() < 0.0001D) {
            Vec3 look = player.getLookAngle();
            direction = new Vec3(look.x, 0.0D, look.z).normalize();
        }
        if (direction.lengthSqr() < 0.0001D) return;
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        double force = 0.85D + 0.20D * knockLevel;
        target.push(direction.x * force, 0.22D, direction.z * force);
        target.hurtMarked = true;
        long now = level.getGameTime();
        if (PRESSURE.equals(signature)) {
            target.igniteForSeconds(2.0F + fireLevel);
            PRESSURES.removeIf(p -> p.key().equals(key));
            if (PRESSURES.size() >= MAX_ACTIVE_PER_KIND) PRESSURES.removeFirst();
            PRESSURES.add(new Pressure(key, now, now + PRESSURE_TICKS, fireLevel, knockLevel));
        } else {
            if (WAKES.size() >= MAX_ACTIVE_PER_KIND) WAKES.removeFirst();
            WAKES.add(new Wake(key, now + FOLLOW_TICKS, fireLevel, knockLevel, center(target)));
        }
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
            10, 0.22D, 0.25D, 0.22D, 0.03D);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    public static void tickServer(MinecraftServer server) {
        // Missed, cancelled and blocked attacks must neither activate nor retain pending state.
        PENDING.clear();
        if (!CONFIRMED.isEmpty()) {
            List<Pending> ready = List.copyOf(CONFIRMED);
            CONFIRMED.clear();
            for (Pending pending : ready) {
                ServerLevel level = server.getLevel(pending.key().dimension());
                ServerPlayer player = server.getPlayerList().getPlayer(pending.key().owner());
                Entity entity = level == null ? null : level.getEntity(pending.key().target());
                if (level == null || player == null || player.level() != level || !player.isAlive()
                    || !(entity instanceof LivingEntity target) || !target.isAlive()) continue;
                if (pending.pair().isAberrant()) {
                    AberrantRegistry.executeAberration(level, player, pending.pair().aberrationId(), target, target.position());
                }
                CompendiumDiscoveryTracker.recordDiscovery(player, pending.pair().primaryId(), pending.pair().secondaryId());
                activate(level, player, target, pending.pair(), pending.signature());
            }
        }
        tickPressures(server);
        tickWakes(server);
    }

    private static void tickPressures(MinecraftServer server) {
        // Damage callbacks may start another fusion, so never hold an iterator while
        // applying the burst to entities.
        for (Pressure pressure : List.copyOf(PRESSURES)) {
            if (!PRESSURES.contains(pressure)) continue;
            ServerLevel level = server.getLevel(pressure.key().dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(pressure.key().owner());
            Entity entity = level == null ? null : level.getEntity(pressure.key().target());
            if (level == null || player == null || player.level() != level || !player.isAlive()
                || !(entity instanceof LivingEntity target) || !target.isAlive()
                || level.getGameTime() > pressure.expires()) {
                PRESSURES.remove(pressure);
                continue;
            }
            long now = level.getGameTime();
            if (now > pressure.armedAfter() && target.horizontalCollision) {
                PRESSURES.remove(pressure);
                burst(level, player, target, pressure.fireLevel(), pressure.knockbackLevel());
            } else if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 2, 0.18D, 0.22D, 0.18D, 0.01D);
            }
        }
    }

    private static void burst(ServerLevel level, ServerPlayer player, LivingEntity target, int fireLevel, int knockLevel) {
        Vec3 center = center(target);
        double radius = Math.min(3.5D, 2.5D + 0.2D * knockLevel);
        DamageSource fire = new DamageSource(level.damageSources().inFire().typeHolder(), player);
        AABB area = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
            e -> canAffect(player, e, e == target) && center(e).distanceToSqr(center) <= radius * radius)) {
            int previousInvulnerability = victim.invulnerableTime;
            victim.invulnerableTime = 0;
            try {
                if (victim.hurtServer(level, fire, 2.5F + fireLevel)) {
                    victim.igniteForSeconds(2.0F + fireLevel);
                }
            } finally {
                victim.invulnerableTime = Math.max(victim.invulnerableTime, previousInvulnerability);
            }
        }
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 28, 0.65D, 0.45D, 0.65D, 0.08D);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 15, 0.45D, 0.3D, 0.45D, 0.08D);
        level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.72F);
    }

    private static void tickWakes(MinecraftServer server) {
        Iterator<Wake> it = WAKES.iterator();
        while (it.hasNext()) {
            Wake wake = it.next();
            ServerLevel level = server.getLevel(wake.key.dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(wake.key.owner());
            if (level == null || player == null || player.level() != level || !player.isAlive()) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            Entity entity = level.getEntity(wake.key.target());
            if (wake.following && now <= wake.followUntil && entity instanceof LivingEntity target && target.isAlive()) {
                Vec3 current = center(target);
                Vec3 movement = current.subtract(wake.last);
                // Discontinuous teleports must not draw or affect a corridor across the gap.
                if (movement.lengthSqr() > 16.0D) {
                    wake.following = false;
                } else if (movement.horizontalDistanceSqr() > 0.0004D) {
                    Vec3 direction = new Vec3(movement.x, 0.0D, movement.z).normalize();
                    wake.segments.add(new Segment(wake.last, current, direction, now + WAKE_TICKS));
                }
                wake.last = current;
            } else {
                wake.following = false;
            }
            wake.segments.removeIf(segment -> now > segment.expires());
            if (wake.segments.isEmpty()) {
                if (!wake.following) it.remove();
                continue;
            }
            AABB bounds = null;
            for (Segment segment : wake.segments) {
                AABB segmentBounds = new AABB(segment.from(), segment.to());
                bounds = bounds == null ? segmentBounds : bounds.minmax(segmentBounds);
                if (now % 5 == 0) {
                    Vec3 mid = segment.from().add(segment.to()).scale(0.5D);
                    level.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
                }
            }
            if (bounds == null || wake.affected.size() >= MAX_AFFECTED_PER_WAKE) continue;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(1.5D),
                e -> !e.getUUID().equals(wake.key.target()) && !wake.affected.contains(e.getUUID()) && canAffect(player, e, false))) {
                AABB hitbox = victim.getBoundingBox().inflate(0.45D);
                for (Segment segment : wake.segments) {
                    if (hitbox.contains(segment.from()) || hitbox.contains(segment.to())
                        || hitbox.clip(segment.from(), segment.to()).isPresent()) {
                        wake.affected.add(victim.getUUID());
                        victim.igniteForSeconds(2.0F + wake.fireLevel);
                        double force = 0.45D + 0.1D * wake.knockbackLevel;
                        victim.push(segment.direction().x * force, 0.10D, segment.direction().z * force);
                        victim.hurtMarked = true;
                        break;
                    }
                }
                if (wake.affected.size() >= MAX_AFFECTED_PER_WAKE) break;
            }
        }
    }

    private static Vec3 center(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static boolean canAffect(ServerPlayer owner, LivingEntity target, boolean directTarget) {
        if (target == owner || !target.isAlive() || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(owner) || owner.isAlliedTo(target)) return false;
        if (target instanceof Player other) {
            return !other.getAbilities().invulnerable && owner.canHarmPlayer(other);
        }
        return directTarget || target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == owner);
    }

    public static void onPlayerDisconnect(UUID id) {
        PENDING.keySet().removeIf(key -> key.owner().equals(id) || key.target().equals(id));
        CONFIRMED.removeIf(p -> p.key().owner().equals(id) || p.key().target().equals(id));
        PRESSURES.removeIf(p -> p.key().owner().equals(id) || p.key().target().equals(id));
        WAKES.removeIf(w -> w.key.owner().equals(id) || w.key.target().equals(id));
    }

    public static void clear() {
        PENDING.clear();
        CONFIRMED.clear();
        PRESSURES.clear();
        WAKES.clear();
    }
}
