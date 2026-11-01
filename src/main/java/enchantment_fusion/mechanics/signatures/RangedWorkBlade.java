package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Point;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A short virtual blade follows only continuous, physically observed movement of the real arrow. */
public final class RangedWorkBlade {
    private static final int MAX_FLIGHTS = 128, MAX_PER_OWNER = 2, MAX_CANDIDATES = 64, MAX_STEPS = 128;
    private static final long MAX_TICKS = 200;
    private static final double MAX_STEP = 16.0D;
    private record Snapshot(LivingEntity entity, AABB box, boolean eligible) {}
    private static final class Blade {
        final UUID owner; final ResourceKey<Level> dimension; final RangedWorkSkills.Gear gear;
        final long born; final int edges; final Set<UUID> attempted = new HashSet<>();
        Vec3 previous; double distance; int steps; boolean settling;
        Blade(ServerPlayer owner, AbstractArrow arrow, RangedWorkSkills.Gear gear, int edges) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.edges = edges; this.born = owner.level().getGameTime(); this.previous = arrow.position();
        }
    }
    public static final class Step {
        private final AbstractArrow arrow; private final Blade blade; private final ServerPlayer owner;
        private final Vec3 start, velocity, limit; private final Map<UUID, Snapshot> snapshots;
        private Step(AbstractArrow arrow, Blade blade, ServerPlayer owner, Vec3 start, Vec3 velocity,
                     Vec3 limit, Map<UUID, Snapshot> snapshots) {
            this.arrow = arrow; this.blade = blade; this.owner = owner; this.start = start; this.velocity = velocity;
            this.limit = limit; this.snapshots = snapshots;
        }
    }
    private static final Map<AbstractArrow, Blade> FLIGHTS = new WeakHashMap<>();
    private RangedWorkBlade() {}
    static boolean canArm(ServerPlayer owner) {
        return FLIGHTS.size() < MAX_FLIGHTS && FLIGHTS.values().stream().filter(f -> f.owner.equals(owner.getUUID())).count() < MAX_PER_OWNER;
    }
    static void arm(ServerPlayer owner, AbstractArrow arrow, RangedWorkSkills.Gear gear, int edges) {
        FLIGHTS.put(arrow, new Blade(owner, arrow, gear, Math.clamp(edges, 1, RangedWorkMath.MAX_EDGES)));
    }
    private static boolean valid(ServerPlayer owner, AbstractArrow arrow, Blade blade) {
        long now = owner.level().getGameTime();
        return RangedWorkSkills.ready(owner) && owner.getUUID().equals(blade.owner) && owner.level() == arrow.level()
            && owner.level().dimension().equals(blade.dimension) && now >= blade.born && now - blade.born < MAX_TICKS
            && arrow.getOwner() == owner && !arrow.noPhysics && !PhantomVolley.isGhost(arrow)
            && RangedWorkSkills.owns(owner, blade.gear);
    }
    private static boolean eligible(ServerPlayer owner, LivingEntity entity) {
        return LoyaltyGuard.eligibleTarget(owner, entity) && owner.level().mayInteract(owner, entity.blockPosition());
    }
    private static Point point(Vec3 value) { return new Point(value.x, value.y, value.z); }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    private static List<LivingEntity> candidates(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(RangedWorkMath.BLADE_WIDTH + 0.25D),
            entity -> entity != owner && !entity.isSpectator());
    }
    /** Called inside the actual AbstractArrow collision step, after the world insertion was confirmed. */
    public static @Nullable Step begin(AbstractArrow arrow, BlockHitResult block) {
        Blade blade = FLIGHTS.get(arrow);
        if (blade == null || !(arrow.getOwner() instanceof ServerPlayer owner)) return null;
        Vec3 start = arrow.position(), velocity = arrow.getDeltaMovement(), limit = block.getLocation();
        if (blade.settling || !valid(owner, arrow, blade) || owner.level().getEntity(arrow.getUUID()) != arrow
            || !finite(start) || !finite(velocity) || !finite(limit) || velocity.lengthSqr() < 1.0E-8D
            || velocity.lengthSqr() > MAX_STEP * MAX_STEP || start.distanceToSqr(limit) > MAX_STEP * MAX_STEP
            || !GrapnelPiercingMath.continuous(point(blade.previous), point(start))
            || !VirtualCombatSight.loaded(owner.level(), start, limit) || ++blade.steps > MAX_STEPS) {
            FLIGHTS.remove(arrow); return null;
        }
        List<LivingEntity> found = candidates(owner.level(), owner, start, limit);
        if (found.size() > MAX_CANDIDATES) { FLIGHTS.remove(arrow); return null; }
        Map<UUID, Snapshot> snapshots = new HashMap<>();
        for (LivingEntity target : found) snapshots.put(target.getUUID(), new Snapshot(target, target.getBoundingBox(), eligible(owner, target)));
        return new Step(arrow, blade, owner, start, velocity, limit, snapshots);
    }
    /** Earliest protected, new, moved, removed or otherwise ambiguous body closes the virtual blade. */
    private static double safeLength(Step step, Vec3 end) {
        Vec3 start = step.start; double limit = start.distanceTo(end);
        List<Snapshot> obstacles = new ArrayList<>(step.snapshots.values());
        List<LivingEntity> now = candidates(step.owner.level(), step.owner, start, end);
        if (now.size() > MAX_CANDIDATES) return 0.0D;
        for (LivingEntity entity : now) {
            Snapshot prior = step.snapshots.get(entity.getUUID());
            if (prior == null || prior.entity() != entity || !prior.box().equals(entity.getBoundingBox()))
                obstacles.add(new Snapshot(entity, entity.getBoundingBox(), false));
        }
        for (Snapshot snap : obstacles) {
            LivingEntity entity = snap.entity();
            boolean stable = snap.eligible() && entity.isAlive() && entity.level() == step.owner.level()
                && step.owner.level().getEntity(entity.getUUID()) == entity && snap.box().equals(entity.getBoundingBox())
                && eligible(step.owner, entity);
            if (stable) continue;
            AABB expanded = snap.box().inflate(RangedWorkMath.BLADE_WIDTH);
            var hit = expanded.contains(start) ? java.util.Optional.of(start) : expanded.clip(start, end);
            if (hit.isPresent()) limit = Math.min(limit, Math.max(0.0D, start.distanceTo(hit.get()) - 0.02D));
        }
        return limit;
    }
    private static boolean eligibleSnapshot(Step step, LivingEntity target, Vec3 to) {
        Snapshot snap = step.snapshots.get(target.getUUID());
        if (FLIGHTS.get(step.arrow) != step.blade || !valid(step.owner, step.arrow, step.blade)
            || snap == null || snap.entity() != target || !snap.eligible() || !snap.box().equals(target.getBoundingBox())
            || step.owner.level().getEntity(target.getUUID()) != target || !eligible(step.owner, target)
            || step.blade.attempted.contains(target.getUUID())) return false;
        // Damage callbacks can move/spawn protected bodies while the blade is settling.
        AABB expanded = snap.box().inflate(RangedWorkMath.BLADE_WIDTH);
        var hit = expanded.contains(step.start) ? java.util.Optional.of(step.start) : expanded.clip(step.start, to);
        return hit.isPresent() && step.start.distanceTo(hit.get()) <= safeLength(step, to) + 1.0E-6D;
    }
    public static void end(@Nullable Step step, boolean completed) {
        if (step == null) return;
        AbstractArrow arrow = step.arrow; Blade blade = step.blade; ServerPlayer owner = step.owner;
        if (FLIGHTS.get(arrow) != blade) return;
        Vec3 end = arrow.position(); Vec3 direction = step.velocity.normalize();
        boolean good = completed && valid(owner, arrow, blade) && finite(end)
            && GrapnelPiercingMath.validStep(point(step.start), point(end), point(step.velocity), point(direction))
            && step.start.distanceTo(end) <= step.start.distanceTo(step.limit) + 0.15D;
        if (!good) { FLIGHTS.remove(arrow); return; }
        double distance = step.start.distanceTo(end), remaining = RangedWorkMath.BLADE_RANGE - blade.distance;
        double used = Math.min(distance, Math.max(0.0D, remaining));
        Vec3 pathEnd = distance > 1.0E-6D ? step.start.add(end.subtract(step.start).scale(used / distance)) : step.start;
        blade.previous = end; blade.distance += used;
        double safe = safeLength(step, pathEnd);
        boolean blocked = safe + 0.001D < used;
        Vec3 visibleEnd = used > 1.0E-6D ? step.start.add(pathEnd.subtract(step.start).scale(safe / used)) : step.start;
        blade.settling = true;
        try {
            if (safe > 0.001D) {
                Vec3 motion = visibleEnd.subtract(step.start);
                String theme = blade.gear.pair().secondaryId(); int rank = Math.clamp(blade.gear.pair().secondaryLevel(), 1, 10);
                float damage = RangedWorkMath.bladeDamage(blade.gear.pair().primaryLevel(), rank, blade.edges, RangedWearSkills.POWER.equals(theme));
                VirtualBlade.sweepWide(owner, step.start, motion, damage, RangedWorkMath.MAX_VICTIMS - blade.attempted.size(),
                    target -> eligibleSnapshot(step, target, visibleEnd), ParticleTypes.SWEEP_ATTACK, RangedWorkMath.BLADE_WIDTH,
                    target -> blade.attempted.add(target.getUUID()), target -> {
                        if (!target.isAlive() || target.level() != owner.level() || !valid(owner, arrow, blade) || !eligible(owner, target)) return;
                        if (RangedWearSkills.PUNCH.equals(theme) && motion.horizontalDistanceSqr() > 1.0E-6D)
                            target.knockback(0.20D + 0.10D * rank, -motion.x, -motion.z);
                        else if (RangedWearSkills.FLAME.equals(theme)) target.igniteForSeconds(2.0F + 0.5F * rank);
                    });
            }
        } finally { blade.settling = false; }
        if (blocked || blade.distance >= RangedWorkMath.BLADE_RANGE || blade.attempted.size() >= RangedWorkMath.MAX_VICTIMS
            || arrow.isRemoved() || ((AbstractArrowAccessor) arrow).ef$isInGround()
            || arrow.getDeltaMovement().dot(step.velocity) < 0.0D) FLIGHTS.remove(arrow);
    }
    static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner);
            return owner == null || !valid(owner, entry.getKey(), entry.getValue()) || entry.getKey().isRemoved();
        });
    }
    static void clearPlayer(UUID owner) { FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)); }
    static void clear() { FLIGHTS.clear(); }
}
