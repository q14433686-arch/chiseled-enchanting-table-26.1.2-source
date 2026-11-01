package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Point;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Underwater current assembled only from actual, continuous collision-clipped arrow steps. */
public final class RangedTideTrail {
    private record Segment(Vec3 from, Vec3 to) {}
    private static final class Trace {
        final UUID owner; final ResourceKey<Level> dimension; final RangedTideSkills.Gear gear; final long born;
        final List<Segment> segments = new ArrayList<>(); Vec3 previous; @Nullable Vec3 hit;
        double distance; int steps;
        Trace(ServerPlayer owner, AbstractArrow arrow, RangedTideSkills.Gear gear) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = gear;
            this.born = owner.level().getGameTime(); this.previous = arrow.position();
        }
    }
    static final class Current {
        final UUID owner; final ResourceKey<Level> dimension; final RangedTideSkills.Gear gear;
        final long born; final List<Segment> segments;
        Current(ServerPlayer owner, Trace trace) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.gear = trace.gear;
            this.born = owner.level().getGameTime(); this.segments = List.copyOf(trace.segments);
        }
    }
    public static final class Step {
        final AbstractArrow arrow; final Trace trace; final ServerPlayer owner; final Vec3 start, ray;
        final Map<BlockPos, FluidState> water;
        Step(AbstractArrow arrow, Trace trace, ServerPlayer owner, Vec3 start, Vec3 ray, Map<BlockPos, FluidState> water) {
            this.arrow = arrow; this.trace = trace; this.owner = owner; this.start = start; this.ray = ray; this.water = water;
        }
    }
    private static final int MAX_FLIGHTS = 256, MAX_OWNERS = 256, MAX_STEPS = 128;
    private static final long FLIGHT_TICKS = 200, CURRENT_TICKS = 100;
    private static final double MAX_STEP = 16.0D, MAX_PATH = 32.0D;
    private static final Map<AbstractArrow, Trace> TRACES = new WeakHashMap<>();
    private static final Map<UUID, Current> CURRENTS = new HashMap<>();
    private RangedTideTrail() {}
    private static Point point(Vec3 v) { return new Point(v.x, v.y, v.z); }
    static void launch(ServerPlayer owner, AbstractArrow arrow, RangedTideSkills.Gear gear) {
        if (TRACES.containsKey(arrow) || !RangedTideSkills.owns(owner, gear) || arrow.getOwner() != owner
            || owner.level().getEntity(arrow.getUUID()) != arrow || arrow.noPhysics) return;
        // A late older hit cannot replace a newer trail from the exact same launcher.
        TRACES.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner.getUUID()) && entry.getValue().gear.item() == gear.item());
        if (TRACES.size() >= MAX_FLIGHTS || TRACES.values().stream().filter(trace -> trace.owner.equals(owner.getUUID())).count() >= 4) return;
        TRACES.put(arrow, new Trace(owner, arrow, gear));
    }
    private static boolean validTrace(ServerPlayer owner, AbstractArrow arrow, Trace trace) {
        long now = owner.level().getGameTime();
        return RangedTideMovement.ready(owner) && owner.getUUID().equals(trace.owner) && owner.level() == arrow.level()
            && owner.level().dimension().equals(trace.dimension) && arrow.getOwner() == owner && !arrow.noPhysics
            && !PhantomVolley.isGhost(arrow) && now >= trace.born && now - trace.born < FLIGHT_TICKS
            && RangedTideSkills.owns(owner, trace.gear);
    }
    private static @Nullable Map<BlockPos, FluidState> snapshot(ServerLevel level, Vec3 from, Vec3 to) {
        if (!RangedTideMath.finite(from) || !RangedTideMath.finite(to) || from.distanceToSqr(to) > MAX_STEP * MAX_STEP
            || !VirtualCombatSight.loaded(level, from, to)) return null;
        int count = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4.0D));
        Map<BlockPos, FluidState> result = new HashMap<>(); Vec3 delta = to.subtract(from);
        for (int i = 0; i <= count; i++) {
            BlockPos cell = BlockPos.containing(from.add(delta.scale(i / (double) count)));
            if (!level.isInWorldBounds(cell) || !level.hasChunkAt(cell)) return null;
            result.put(cell, level.getFluidState(cell));
        }
        return result;
    }
    private static boolean stillWater(ServerLevel level, Vec3 from, Vec3 to, Map<BlockPos, FluidState> snapshot) {
        int count = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4.0D)); Vec3 delta = to.subtract(from);
        for (int i = 0; i <= count; i++) {
            BlockPos cell = BlockPos.containing(from.add(delta.scale(i / (double) count)));
            FluidState before = snapshot.get(cell);
            if (before == null || !before.is(FluidTags.WATER) || !level.hasChunkAt(cell)
                || !before.equals(level.getFluidState(cell))) return false;
        }
        return true;
    }
    /** Called around the real AbstractArrow.stepMoveAndHit, never from a predicted flight. */
    public static @Nullable Step begin(AbstractArrow arrow, BlockHitResult hit) {
        Trace trace = TRACES.get(arrow);
        if (trace == null || !(arrow.getOwner() instanceof ServerPlayer owner)) return null;
        Vec3 start = arrow.position(), ray = hit.getLocation().subtract(start);
        Map<BlockPos, FluidState> water = snapshot(owner.level(), start, hit.getLocation());
        if (!validTrace(owner, arrow, trace) || owner.level().getEntity(arrow.getUUID()) != arrow || water == null
            || !RangedTideMath.finite(ray) || ray.lengthSqr() < 1.0E-8D
            || !GrapnelPiercingMath.continuous(point(trace.previous), point(start)) || ++trace.steps > MAX_STEPS) {
            TRACES.remove(arrow); return null;
        }
        return new Step(arrow, trace, owner, start, ray, water);
    }
    /** Damage evidence is recorded during the same collision step; placement waits for the actual end. */
    static void hit(ConfirmedArrow.Hit hit) {
        Trace trace = TRACES.get(hit.arrow());
        if (trace != null && trace.gear.item() == hit.launcher() && trace.gear.pair().equals(hit.pair())
            && validTrace(hit.owner(), hit.arrow(), trace) && hit.owner().isInWater()) trace.hit = hit.location();
    }
    public static void end(@Nullable Step step, boolean completed) {
        if (step == null || TRACES.get(step.arrow) != step.trace) return;
        Trace trace = step.trace; Vec3 end = step.arrow.position(), dir = step.ray.normalize(); ServerPlayer owner = step.owner;
        if (!completed || !validTrace(owner, step.arrow, trace) || !RangedTideMath.finite(end)
            || !GrapnelPiercingMath.validStep(point(step.start), point(end), point(step.ray), point(dir))
            || !stillWater(owner.level(), step.start, end, step.water)) { TRACES.remove(step.arrow); return; }
        double distance = step.start.distanceTo(end), take = Math.min(distance, Math.max(0.0D, MAX_PATH - trace.distance));
        if (take > 1.0E-5D) {
            trace.segments.add(new Segment(step.start, step.start.add(end.subtract(step.start).scale(take / distance))));
            trace.distance += take;
        }
        trace.previous = end;
        if (trace.hit != null) {
            boolean realImpact = GrapnelPiercingMath.containsImpact(point(step.start), point(end), point(trace.hit), point(dir));
            if (realImpact && !trace.segments.isEmpty() && (CURRENTS.containsKey(owner.getUUID()) || CURRENTS.size() < MAX_OWNERS)) {
                CURRENTS.put(owner.getUUID(), new Current(owner, trace));
                CompendiumDiscoveryTracker.recordDiscovery(owner, trace.gear.pair().primaryId(), trace.gear.pair().secondaryId());
            }
            TRACES.remove(step.arrow);
        } else if (step.arrow.isRemoved() || ((AbstractArrowAccessor) step.arrow).ef$isInGround()
            || step.arrow.getDeltaMovement().dot(step.ray) < 0.0D) TRACES.remove(step.arrow);
    }
    private static boolean validCurrent(ServerPlayer owner, @Nullable Current current) {
        if (current == null || !RangedTideMovement.ready(owner) || !owner.getUUID().equals(current.owner)
            || !owner.level().dimension().equals(current.dimension) || !RangedTideSkills.owns(owner, current.gear)) return false;
        long now = owner.level().getGameTime(); return now >= current.born && now - current.born < CURRENT_TICKS;
    }
    static @Nullable Current current(ServerPlayer owner) {
        Current current = CURRENTS.get(owner.getUUID()); return validCurrent(owner, current) ? current : null;
    }
    /** A verified water-swim step may take a single forward push from one still-water segment. */
    static void ride(ServerPlayer owner, @Nullable Current current, List<ConfirmedPlayerMove.Step> steps) {
        if (current == null || CURRENTS.get(owner.getUUID()) != current || !validCurrent(owner, current) || !owner.isInWater()) return;
        double height = owner.getBoundingBox().getYsize() * 0.5D;
        for (ConfirmedPlayerMove.Step step : steps) for (Segment segment : current.segments) {
            Vec3 motion = step.to().subtract(step.from()), flow = segment.to().subtract(segment.from());
            double ml = motion.horizontalDistanceSqr(), fl = flow.horizontalDistanceSqr();
            if (ml < 1.0E-8D || fl < 1.0E-8D || motion.x * flow.x + motion.z * flow.z <= .25D * Math.sqrt(ml * fl)) continue;
            Vec3 from = step.from().add(0, height, 0), to = step.to().add(0, height, 0);
            if (RangedTideMath.distanceSquared(from, to, segment.from(), segment.to()) > RangedTideMath.RIDE_RADIUS * RangedTideMath.RIDE_RADIUS) continue;
            Vec3 point = RangedTideMath.closest(segment.from(), segment.to(), to);
            Map<BlockPos, FluidState> water = snapshot(owner.level(), segment.from(), segment.to());
            if (water == null || !stillWater(owner.level(), segment.from(), segment.to(), water)
                || !VirtualCombatSight.clear(owner.level(), owner, to, point)) continue;
            CURRENTS.remove(owner.getUUID(), current);
            double force = .20D + .025D * Math.clamp(current.gear.pair().secondaryLevel(), 1, 6);
            Vec3 push = flow.normalize().scale(force); owner.push(push.x, Math.clamp(push.y, -.10D, .10D), push.z);
            owner.hurtMarked = true; owner.connection.send(new ClientboundSetEntityMotionPacket(owner));
            owner.level().sendParticles(ParticleTypes.BUBBLE, point.x, point.y, point.z, 8, .15D, .15D, .15D, .02D);
            return;
        }
    }
    static void tickServer(MinecraftServer server) {
        TRACES.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue().owner);
            return owner == null || !validTrace(owner, entry.getKey(), entry.getValue()) || entry.getKey().isRemoved();
        });
        CURRENTS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Current current = entry.getValue();
            if (owner == null || !validCurrent(owner, current)) return true;
            if (owner.level().getGameTime() % 10 == 0) for (int i = 0; i < current.segments.size(); i += 4) {
                Vec3 pos = current.segments.get(i).from();
                owner.level().sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 1, .02D, .02D, .02D, 0);
            }
            return false;
        });
    }
    static void clearPlayer(UUID id) { CURRENTS.remove(id); TRACES.entrySet().removeIf(entry -> entry.getValue().owner.equals(id)); }
    static void clearServer() { CURRENTS.clear(); TRACES.clear(); }
}
