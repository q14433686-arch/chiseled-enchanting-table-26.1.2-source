package enchantment_fusion.mechanics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Unified Temporary Flight-Path Buffer &amp; Swept-Trajectory Judgment Engine ({@code 路径暂缓存判定})
 * for launched Arrows, Tridents, and projected/thrown Swords, Axes, and Spears.
 *
 * <p>Capabilities:
 * <ul>
 *   <li>Records bounded per-flight node rings ({@link PathSample}) during active flight and retains
 *       completed trajectories in temporary cache for {@link #POST_IMPACT_TTL_TICKS} ({@code 100} ticks / {@code 5s}).</li>
 *   <li>Provides continuous swept-segment capsule hit detection ({@link #querySweptSegmentHostiles}) so fast
 *       arrows, tridents, flying swords/axes, and spears never tunnel past thin targets.</li>
 *   <li>Executes delayed trajectory afterimages ({@link #schedulePathEcho}) marching forward or backward
 *       along cached path nodes (powering e.g. {@code 余音绕梁}, {@code 回马金枪}, {@code 剑气残影}).</li>
 *   <li>Applies corridor field effects along cached segments ({@link PathTrailStyle}) and detects intersecting
 *       trajectories from the same owner ({@link #checkAndTriggerCrossPathResonance} — {@code 双轨交错·十字斩}).</li>
 * </ul>
 */
public final class ProjectilePathTracker {
    public static final int MAX_SAMPLES_PER_PATH = 64;
    public static final long ACTIVE_MAX_AGE_TICKS = 240L;
    public static final long POST_IMPACT_TTL_TICKS = 100L;

    public enum PathSourceKind {
        ARROW,
        TRIDENT,
        SWORD,
        AXE,
        SPEAR,
        OTHER_BLADE
    }

    public enum PathTrailStyle {
        PYRO_WAKE,
        CRYO_WAKE,
        VOLT_CHAIN,
        GALE_VACUUM_LINE,
        SILK_TETHER
    }

    public record PathSample(
        Vec3 pos,
        Vec3 velocity,
        long gameTime,
        boolean inWater
    ) {}

    public static final class CachedFlightPath {
        private final UUID pathId;
        private final UUID ownerId;
        private final ResourceKey<Level> dimension;
        private final PathSourceKind sourceKind;
        private final ItemStack weaponSnapshot;
        private final long createdGameTime;
        private final List<PathSample> samples = new ArrayList<>();
        private final Set<UUID> sweptHitEntities = ConcurrentHashMap.newKeySet();
        private final Set<UUID> crossResonatedPaths = ConcurrentHashMap.newKeySet();
        private volatile boolean completed;
        private volatile long expireGameTime;
        @Nullable
        private volatile Vec3 impactPos;

        CachedFlightPath(
            UUID pathId,
            UUID ownerId,
            ResourceKey<Level> dimension,
            PathSourceKind sourceKind,
            ItemStack weaponSnapshot,
            long gameTime
        ) {
            this.pathId = pathId;
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.sourceKind = sourceKind;
            this.weaponSnapshot = weaponSnapshot == null ? ItemStack.EMPTY : weaponSnapshot.copyWithCount(1);
            this.createdGameTime = gameTime;
            this.expireGameTime = gameTime + ACTIVE_MAX_AGE_TICKS;
        }

        public UUID pathId() {
            return pathId;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public PathSourceKind sourceKind() {
            return sourceKind;
        }

        public ItemStack weaponSnapshot() {
            return weaponSnapshot;
        }

        public long createdGameTime() {
            return createdGameTime;
        }

        public boolean isCompleted() {
            return completed;
        }

        public long expireGameTime() {
            return expireGameTime;
        }

        @Nullable
        public Vec3 impactPos() {
            return impactPos;
        }

        public synchronized List<PathSample> snapshotSamples() {
            return List.copyOf(samples);
        }

        public synchronized List<Vec3> samplePositionsReversed() {
            List<Vec3> rev = new ArrayList<>(samples.size());
            for (int i = samples.size() - 1; i >= 0; i--) {
                rev.add(samples.get(i).pos());
            }
            return Collections.unmodifiableList(rev);
        }

        public Set<UUID> sweptHitEntities() {
            return sweptHitEntities;
        }

        synchronized void appendSample(Vec3 pos, Vec3 velocity, long gameTime, boolean inWater) {
            if (!samples.isEmpty()) {
                Vec3 last = samples.get(samples.size() - 1).pos();
                if (last.distanceToSqr(pos) < 0.015D) {
                    return;
                }
            }
            if (samples.size() >= MAX_SAMPLES_PER_PATH) {
                samples.remove(0);
            }
            samples.add(new PathSample(pos, velocity, gameTime, inWater));
            if (!completed) {
                this.expireGameTime = gameTime + ACTIVE_MAX_AGE_TICKS;
            }
        }

        synchronized void markCompleted(@Nullable Vec3 finalPos, long gameTime) {
            if (finalPos != null) {
                this.impactPos = finalPos;
                appendSample(finalPos, Vec3.ZERO, gameTime, false);
            } else if (!samples.isEmpty()) {
                this.impactPos = samples.get(samples.size() - 1).pos();
            }
            this.completed = true;
            this.expireGameTime = gameTime + POST_IMPACT_TTL_TICKS;
        }
    }

    private static final class ScheduledPathEcho {
        final UUID ownerId;
        final ResourceKey<Level> dimension;
        final List<Vec3> orderedNodes;
        final float damage;
        final boolean reverseOrder;
        final boolean armorPiercing;
        final Set<UUID> hitTargets = ConcurrentHashMap.newKeySet();
        long startGameTime;
        int cursorIndex;

        ScheduledPathEcho(
            UUID ownerId,
            ResourceKey<Level> dimension,
            List<Vec3> nodes,
            long startGameTime,
            float damage,
            boolean reverseOrder,
            boolean armorPiercing
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.orderedNodes = nodes;
            this.startGameTime = startGameTime;
            this.damage = damage;
            this.reverseOrder = reverseOrder;
            this.armorPiercing = armorPiercing;
            this.cursorIndex = 0;
        }
    }

    private static final Map<UUID, CachedFlightPath> PATH_CACHE = new ConcurrentHashMap<>();
    private static final List<ScheduledPathEcho> PENDING_ECHOES = Collections.synchronizedList(new ArrayList<>());

    private ProjectilePathTracker() {}

    public static PathSourceKind classifyWeaponItem(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return PathSourceKind.OTHER_BLADE;
        }
        if (weapon.is(ItemTags.SPEARS)) {
            return PathSourceKind.SPEAR;
        }
        if (weapon.is(ItemTags.SWORDS)) {
            return PathSourceKind.SWORD;
        }
        if (weapon.getItem() instanceof AxeItem || weapon.is(ItemTags.AXES)) {
            return PathSourceKind.AXE;
        }
        return PathSourceKind.OTHER_BLADE;
    }

    public static PathSourceKind classifyProjectile(Projectile proj) {
        if (proj instanceof ThrownTrident) {
            return PathSourceKind.TRIDENT;
        }
        if (proj instanceof AbstractArrow) {
            return PathSourceKind.ARROW;
        }
        return PathSourceKind.OTHER_BLADE;
    }

    public static CachedFlightPath recordStep(
        UUID pathId,
        UUID ownerId,
        ResourceKey<Level> dimension,
        PathSourceKind kind,
        ItemStack weaponSnapshot,
        Vec3 pos,
        Vec3 velocity,
        long gameTime,
        boolean inWater
    ) {
        CachedFlightPath path = PATH_CACHE.computeIfAbsent(
            pathId,
            id -> new CachedFlightPath(id, ownerId, dimension, kind, weaponSnapshot, gameTime)
        );
        path.appendSample(pos, velocity, gameTime, inWater);
        return path;
    }

    public static CachedFlightPath recordProjectileStep(
        ServerLevel level,
        Projectile projectile,
        ServerPlayer owner,
        ItemStack weaponSnapshot
    ) {
        PathSourceKind kind = classifyProjectile(projectile);
        return recordStep(
            projectile.getUUID(),
            owner.getUUID(),
            level.dimension(),
            kind,
            weaponSnapshot,
            projectile.position(),
            projectile.getDeltaMovement(),
            level.getGameTime(),
            projectile.isInWater()
        );
    }

    public static void markPathCompleted(UUID pathId, @Nullable Vec3 impactPos, long gameTime) {
        CachedFlightPath path = PATH_CACHE.get(pathId);
        if (path != null) {
            path.markCompleted(impactPos, gameTime);
        }
    }

    public static Optional<CachedFlightPath> getPath(UUID pathId) {
        return Optional.ofNullable(PATH_CACHE.get(pathId));
    }

    public static Optional<CachedFlightPath> findLatestPathForOwner(UUID ownerId, ResourceKey<Level> dimension) {
        CachedFlightPath best = null;
        for (CachedFlightPath cand : PATH_CACHE.values()) {
            if (!cand.ownerId().equals(ownerId) || !cand.dimension().equals(dimension)) {
                continue;
            }
            if (best == null || cand.createdGameTime() >= best.createdGameTime()) {
                best = cand;
            }
        }
        return Optional.ofNullable(best);
    }

    public static List<CachedFlightPath> getRecentPathsForOwner(UUID ownerId, ResourceKey<Level> dimension) {
        List<CachedFlightPath> result = new ArrayList<>();
        for (CachedFlightPath cand : PATH_CACHE.values()) {
            if (cand.ownerId().equals(ownerId) && cand.dimension().equals(dimension)) {
                result.add(cand);
            }
        }
        return result;
    }

    public static int cachedPathCount() {
        return PATH_CACHE.size();
    }

    /**
     * Exact squared distance from point {@code p} to finite line segment {@code [a, b]}.
     */
    public static double distancePointToSegmentSq(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lenSq = ab.lengthSqr();
        if (lenSq < 1.0E-7D) {
            return p.distanceToSqr(a);
        }
        double t = Math.clamp(p.subtract(a).dot(ab) / lenSq, 0.0D, 1.0D);
        Vec3 proj = a.add(ab.scale(t));
        return p.distanceToSqr(proj);
    }

    /**
     * Closest squared distance between two finite 3D line segments {@code [a1, b1]} and {@code [a2, b2]}.
     */
    public static double segmentsClosestDistanceSq(Vec3 a1, Vec3 b1, Vec3 a2, Vec3 b2) {
        Vec3 d1 = b1.subtract(a1);
        Vec3 d2 = b2.subtract(a2);
        Vec3 r = a1.subtract(a2);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);

        double s;
        double t;
        if (a <= 1.0E-7D && e <= 1.0E-7D) {
            return r.lengthSqr();
        }
        if (a <= 1.0E-7D) {
            s = 0.0D;
            t = Math.clamp(f / e, 0.0D, 1.0D);
        } else {
            double c = d1.dot(r);
            if (e <= 1.0E-7D) {
                t = 0.0D;
                s = Math.clamp(-c / a, 0.0D, 1.0D);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                if (denom != 0.0D) {
                    s = Math.clamp((b * f - c * e) / denom, 0.0D, 1.0D);
                } else {
                    s = 0.0D;
                }
                t = (b * s + f) / e;
                if (t < 0.0D) {
                    t = 0.0D;
                    s = Math.clamp(-c / a, 0.0D, 1.0D);
                } else if (t > 1.0D) {
                    t = 1.0D;
                    s = Math.clamp((b - c) / a, 0.0D, 1.0D);
                }
            }
        }
        Vec3 c1 = a1.add(d1.scale(s));
        Vec3 c2 = a2.add(d2.scale(t));
        return c1.distanceToSqr(c2);
    }

    /**
     * Swept-capsule hit judgment along {@code [from, to]} excluding allies/pets/armor stands.
     */
    public static List<LivingEntity> querySweptSegmentHostiles(
        ServerLevel level,
        ServerPlayer owner,
        Vec3 from,
        Vec3 to,
        double radius
    ) {
        double safeRadius = Math.max(0.35D, radius);
        AABB sweptBox = new AABB(from, to).inflate(safeRadius + 0.65D);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
            LivingEntity.class,
            sweptBox,
            e -> isValidHostileTarget(owner, e)
        );
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> hits = new ArrayList<>();
        for (LivingEntity cand : candidates) {
            Vec3 center = cand.position().add(0.0D, cand.getBbHeight() * 0.5D, 0.0D);
            double effectiveRad = safeRadius + Math.max(cand.getBbWidth(), cand.getBbHeight()) * 0.45D;
            if (distancePointToSegmentSq(center, from, to) <= effectiveRad * effectiveRad) {
                hits.add(cand);
            }
        }
        return hits;
    }

    /**
     * Schedules a delayed trajectory afterimage slash/burst marching along the cached flight path!
     */
    public static void schedulePathEcho(
        CachedFlightPath path,
        long delayTicks,
        float damage,
        boolean reverseOrder,
        boolean armorPiercing
    ) {
        if (path == null) {
            return;
        }
        List<PathSample> samples = path.snapshotSamples();
        if (samples.isEmpty()) {
            return;
        }
        List<Vec3> nodes = new ArrayList<>(samples.size());
        if (reverseOrder) {
            for (int i = samples.size() - 1; i >= 0; i--) {
                nodes.add(samples.get(i).pos());
            }
        } else {
            for (PathSample s : samples) {
                nodes.add(s.pos());
            }
        }
        long baseNow = samples.get(samples.size() - 1).gameTime();
        PENDING_ECHOES.add(new ScheduledPathEcho(
            path.ownerId(),
            path.dimension(),
            nodes,
            baseNow + Math.max(2L, delayTicks),
            Math.max(1.0F, damage),
            reverseOrder,
            armorPiercing
        ));
    }

    /**
     * Schedules a synthetic linear path (e.g. for melee居合 dash or Spear Lunge回马枪) and optionally queues an echo.
     */
    public static CachedFlightPath recordSyntheticCorridor(
        ServerLevel level,
        ServerPlayer owner,
        PathSourceKind kind,
        ItemStack weaponSnapshot,
        Vec3 start,
        Vec3 end,
        int steps
    ) {
        UUID id = UUID.randomUUID();
        long now = level.getGameTime();
        int count = Math.max(2, Math.min(24, steps));
        Vec3 stepVec = end.subtract(start).scale(1.0D / count);
        CachedFlightPath path = null;
        for (int i = 0; i <= count; i++) {
            Vec3 p = start.add(stepVec.scale(i));
            path = recordStep(id, owner.getUUID(), level.dimension(), kind, weaponSnapshot, p, stepVec, now, owner.isInWater());
        }
        if (path != null) {
            path.markCompleted(end, now);
        }
        return path;
    }

    /**
     * Applies elemental or tactical field mechanics along EVERY segment of a cached trajectory!
     */
    public static void applyPathTrailEffect(
        ServerLevel level,
        ServerPlayer owner,
        CachedFlightPath path,
        PathTrailStyle style,
        float powerScale
    ) {
        if (path == null) {
            return;
        }
        List<PathSample> nodes = path.snapshotSamples();
        if (nodes.size() < 2) {
            return;
        }
        Set<UUID> affected = ConcurrentHashMap.newKeySet();
        List<LivingEntity> threaded = (style == PathTrailStyle.SILK_TETHER) ? new ArrayList<>() : List.of();

        for (int i = 1; i < nodes.size(); i++) {
            Vec3 a = nodes.get(i - 1).pos();
            Vec3 b = nodes.get(i).pos();
            Vec3 mid = a.add(b).scale(0.5D);
            double corridorRadius = (style == PathTrailStyle.GALE_VACUUM_LINE) ? 2.75D : 1.55D;

            switch (style) {
                case PYRO_WAKE -> level.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 2, 0.14D, 0.1D, 0.14D, 0.01D);
                case CRYO_WAKE -> level.sendParticles(ParticleTypes.SNOWFLAKE, mid.x, mid.y, mid.z, 2, 0.16D, 0.1D, 0.16D, 0.01D);
                case VOLT_CHAIN -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 3, 0.12D, 0.12D, 0.12D, 0.04D);
                case GALE_VACUUM_LINE -> level.sendParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 2, 0.2D, 0.1D, 0.2D, 0.02D);
                case SILK_TETHER -> level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }

            for (LivingEntity victim : querySweptSegmentHostiles(level, owner, a, b, corridorRadius)) {
                if (!affected.add(victim.getUUID())) {
                    continue;
                }
                switch (style) {
                    case PYRO_WAKE -> {
                        victim.igniteForSeconds(Math.max(3.0F, 3.5F * powerScale));
                        victim.hurtServer(level, level.damageSources().onFire(), 1.6F * powerScale);
                    }
                    case CRYO_WAKE -> {
                        victim.setTicksFrozen(Math.min(340, victim.getTicksFrozen() + Math.round(95 * powerScale)));
                        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Math.round(65 * powerScale), 1));
                    }
                    case VOLT_CHAIN -> {
                        victim.hurtServer(level, level.damageSources().lightningBolt(), 2.4F * powerScale);
                        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
                    }
                    case GALE_VACUUM_LINE -> {
                        Vec3 pull = mid.subtract(victim.position()).scale(0.42D);
                        victim.push(pull.x, 0.14D, pull.z);
                    }
                    case SILK_TETHER -> threaded.add(victim);
                }
            }
        }

        // Threading Needle ("穿针引线"): pulls all enemies pierced along the flight line toward the final anchor enemy!
        if (style == PathTrailStyle.SILK_TETHER && threaded.size() >= 2) {
            LivingEntity anchor = threaded.get(threaded.size() - 1);
            Vec3 anchorPos = anchor.position();
            for (int i = 0; i < threaded.size() - 1; i++) {
                LivingEntity pulled = threaded.get(i);
                Vec3 delta = anchorPos.subtract(pulled.position()).scale(0.52D);
                pulled.push(delta.x, 0.22D, delta.z);
                pulled.hurtServer(level, level.damageSources().magic(), 2.2F * powerScale);
            }
            level.playSound(null, BlockPos.containing(anchorPos), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.55F);
        }
    }

    /**
     * Checks whether {@code activePath}'s latest segment crosses within {@code 1.65} blocks of another recent
     * cached flight path belonging to {@code owner}. When two trajectories intersect near an enemy, triggers
     * Cross-Path X-Slash Resonance ({@code 双轨交错·十字斩})!
     */
    public static boolean checkAndTriggerCrossPathResonance(
        ServerLevel level,
        ServerPlayer owner,
        CachedFlightPath activePath,
        float bonusDamage
    ) {
        if (activePath == null || bonusDamage <= 0.0F) {
            return false;
        }
        List<PathSample> myNodes = activePath.snapshotSamples();
        if (myNodes.size() < 2) {
            return false;
        }
        Vec3 a1 = myNodes.get(myNodes.size() - 2).pos();
        Vec3 b1 = myNodes.get(myNodes.size() - 1).pos();

        for (CachedFlightPath other : PATH_CACHE.values()) {
            if (other.pathId().equals(activePath.pathId())
                || !other.ownerId().equals(owner.getUUID())
                || !other.dimension().equals(level.dimension())
                || activePath.crossResonatedPaths.contains(other.pathId())) {
                continue;
            }
            List<PathSample> otherNodes = other.snapshotSamples();
            if (otherNodes.size() < 2) {
                continue;
            }
            for (int j = 1; j < otherNodes.size(); j++) {
                Vec3 a2 = otherNodes.get(j - 1).pos();
                Vec3 b2 = otherNodes.get(j).pos();
                if (segmentsClosestDistanceSq(a1, b1, a2, b2) <= 2.72D) { // 1.65^2
                    Vec3 crossCenter = b1.add(a2.add(b2).scale(0.5D)).scale(0.5D);
                    List<LivingEntity> victims = querySweptSegmentHostiles(level, owner, crossCenter, crossCenter, 2.8D);
                    if (!victims.isEmpty()) {
                        activePath.crossResonatedPaths.add(other.pathId());
                        other.crossResonatedPaths.add(activePath.pathId());
                        for (LivingEntity v : victims) {
                            v.invulnerableTime = 0;
                            v.hurtServer(level, level.damageSources().magic(), bonusDamage);
                        }
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, crossCenter.x, crossCenter.y + 0.5D, crossCenter.z, 3, 0.35D, 0.2D, 0.35D, 0.0D);
                        level.sendParticles(ParticleTypes.END_ROD, crossCenter.x, crossCenter.y + 0.5D, crossCenter.z, 12, 0.4D, 0.3D, 0.4D, 0.08D);
                        level.playSound(null, BlockPos.containing(crossCenter), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.75F);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void tickServer(MinecraftServer server) {
        // 1. Prune expired path records per dimension clock
        Iterator<Map.Entry<UUID, CachedFlightPath>> it = PATH_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CachedFlightPath> entry = it.next();
            CachedFlightPath path = entry.getValue();
            ServerLevel world = server.getLevel(path.dimension());
            if (world == null || world.getGameTime() >= path.expireGameTime()) {
                it.remove();
            }
        }

        // 2. Advance scheduled delayed trajectory afterimages (2 nodes per tick for crisp wave motion!)
        if (PENDING_ECHOES.isEmpty()) {
            return;
        }
        synchronized (PENDING_ECHOES) {
            Iterator<ScheduledPathEcho> echoIt = PENDING_ECHOES.iterator();
            while (echoIt.hasNext()) {
                ScheduledPathEcho echo = echoIt.next();
                ServerLevel world = server.getLevel(echo.dimension);
                if (world == null || echo.orderedNodes.isEmpty()) {
                    echoIt.remove();
                    continue;
                }
                if (world.getGameTime() < echo.startGameTime) {
                    continue;
                }
                Entity maybeOwner = world.getEntity(echo.ownerId);
                if (!(maybeOwner instanceof ServerPlayer owner) || !owner.isAlive()) {
                    echoIt.remove();
                    continue;
                }

                int stepsThisTick = 0;
                while (echo.cursorIndex < echo.orderedNodes.size() && stepsThisTick < 3) {
                    Vec3 curr = echo.orderedNodes.get(echo.cursorIndex);
                    Vec3 prev = echo.cursorIndex > 0 ? echo.orderedNodes.get(echo.cursorIndex - 1) : curr;
                    world.sendParticles(
                        echo.reverseOrder ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ENCHANTED_HIT,
                        curr.x, curr.y, curr.z, 4, 0.14D, 0.14D, 0.14D, 0.04D
                    );
                    if (echo.cursorIndex % 3 == 0) {
                        world.sendParticles(ParticleTypes.SWEEP_ATTACK, curr.x, curr.y, curr.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                    for (LivingEntity hit : querySweptSegmentHostiles(world, owner, prev, curr, 1.35D)) {
                        if (echo.hitTargets.add(hit.getUUID())) {
                            hit.invulnerableTime = 0;
                            hit.hurtServer(
                                world,
                                echo.armorPiercing ? world.damageSources().magic() : world.damageSources().playerAttack(owner),
                                echo.damage
                            );
                        }
                    }
                    echo.cursorIndex++;
                    stepsThisTick++;
                }

                if (echo.cursorIndex >= echo.orderedNodes.size()) {
                    echoIt.remove();
                }
            }
        }
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PATH_CACHE.entrySet().removeIf(e -> playerId.equals(e.getValue().ownerId()));
        synchronized (PENDING_ECHOES) {
            PENDING_ECHOES.removeIf(e -> playerId.equals(e.ownerId));
        }
    }

    public static void flushAll() {
        PATH_CACHE.clear();
        PENDING_ECHOES.clear();
    }

    private static boolean isValidHostileTarget(ServerPlayer owner, LivingEntity e) {
        if (e == null || e == owner || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) {
            return false;
        }
        if (e instanceof ServerPlayer other && owner.isAlliedTo(other)) {
            return false;
        }
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
            return false;
        }
        return true;
    }
}
