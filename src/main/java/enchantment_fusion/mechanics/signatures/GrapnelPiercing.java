package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Box;
import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Point;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Wait for real, continuous arrow movement completely through the damaged target's impact-time bounds. */
public final class GrapnelPiercing {
    private static final int MAX_CANDIDATES = 16;
    private static final long MAX_TICKS = 20;
    private static final class Candidate {
        final UUID owner;
        final WeakReference<LivingEntity> target;
        final Point impact, direction;
        final Box bounds;
        final long born, firstStep;
        @Nullable Point previousEnd;
        Candidate(ServerPlayer owner, LivingEntity target, Point impact, Point direction, Box bounds, long born, long firstStep) {
            this.owner = owner.getUUID(); this.target = new WeakReference<>(target); this.impact = impact; this.direction = direction;
            this.bounds = bounds; this.born = born; this.firstStep = firstStep;
        }
    }
    public static final class Step {
        private final @Nullable Step previous;
        private final AbstractArrow arrow;
        private final Point start, velocity;
        private final long id;
        private Step(@Nullable Step previous, AbstractArrow arrow, BlockHitResult ray, long id) {
            this.previous = previous; this.arrow = arrow; this.start = point(arrow.position());
            // The collision ray uses velocity from before water inertia is applied. The
            // current delta is already damped, and would falsely reject real underwater steps.
            this.velocity = point(ray.getLocation().subtract(arrow.position())); this.id = id;
        }
    }
    private static final Map<AbstractArrow, List<Candidate>> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Step> CURRENT = new ThreadLocal<>();
    private static long nextStep;
    private GrapnelPiercing() {}
    private static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
    private static Box box(AABB box) { return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ); }
    public static Step begin(AbstractArrow arrow, BlockHitResult ray) {
        Step frame = new Step(CURRENT.get(), arrow, ray, ++nextStep); CURRENT.set(frame); return frame;
    }
    /** Only a confirmed damage call inside this arrow's actual collision step may be reserved. */
    public static void prepare(AbstractArrow arrow, ServerPlayer owner, LivingEntity target, Vec3 impact, AABB impactBounds) {
        Step frame = CURRENT.get();
        var ignored = ((AbstractArrowAccessor) arrow).ef$getPiercingIgnoreEntityIds();
        Vec3 motion = arrow.getDeltaMovement();
        if (frame == null || frame.arrow != arrow || arrow.getPierceLevel() <= 0 || ignored == null || !ignored.contains(target.getId())
            || motion.lengthSqr() < 1.0E-8D || !Double.isFinite(motion.x) || !Double.isFinite(motion.y) || !Double.isFinite(motion.z)) return;
        Point direction = point(motion.normalize());
        if (!GrapnelPiercingMath.unit(direction)) return;
        List<Candidate> entries = PENDING.computeIfAbsent(arrow, ignoredArrow -> new ArrayList<>());
        if (entries.size() >= MAX_CANDIDATES) return;
        entries.add(new Candidate(owner, target, point(impact), direction, box(impactBounds), owner.level().getGameTime(), frame.id));
    }
    public static void end(Step frame, boolean completed) {
        if (CURRENT.get() == frame) {
            if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        }
        List<Candidate> entries = PENDING.get(frame.arrow);
        if (entries == null) return;
        if (!completed) { PENDING.remove(frame.arrow); return; }
        AbstractArrow arrow = frame.arrow; Point end = point(arrow.position()); long now = arrow.level().getGameTime();
        for (Candidate candidate : List.copyOf(entries)) {
            if (candidate.previousEnd == null && candidate.firstStep != frame.id) continue;
            LivingEntity target = candidate.target.get();
            boolean valid = target != null && arrow.isAlive() && !arrow.noPhysics
                && now >= candidate.born && now - candidate.born <= MAX_TICKS
                && GrapnelPiercingMath.validStep(frame.start, end, frame.velocity, candidate.direction)
                && (candidate.previousEnd == null
                    ? GrapnelPiercingMath.containsImpact(frame.start, end, candidate.impact, candidate.direction)
                    : GrapnelPiercingMath.continuous(candidate.previousEnd, frame.start));
            if (!valid) { entries.remove(candidate); continue; }
            if (GrapnelPiercingMath.passed(candidate.bounds, end, candidate.direction)) {
                entries.remove(candidate); // commit before messages and any callbacks
                GrapnelSkills.confirmArrowPiercing(arrow, target, candidate.owner);
            } else if (((AbstractArrowAccessor) arrow).ef$isInGround()) {
                entries.remove(candidate);
            } else candidate.previousEnd = end;
        }
        if (entries.isEmpty()) PENDING.remove(arrow);
    }
    public static void tickServer(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            AbstractArrow arrow = entry.getKey(); long now = arrow.level().getGameTime();
            entry.getValue().removeIf(candidate -> server.getPlayerList().getPlayer(candidate.owner) == null
                || candidate.target.get() == null || now < candidate.born || now - candidate.born > MAX_TICKS);
            return !arrow.isAlive() || entry.getValue().isEmpty();
        });
    }
    public static void clearPlayer(UUID id) {
        PENDING.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(candidate -> candidate.owner.equals(id)); return entry.getValue().isEmpty();
        });
    }
    public static void clearServer() { PENDING.clear(); CURRENT.remove(); }
}
