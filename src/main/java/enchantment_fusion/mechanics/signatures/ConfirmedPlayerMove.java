package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Real, collision-clipped server player movement; subscribers settle only after corrections. */
public final class ConfirmedPlayerMove {
    public record Step(Vec3 from, Vec3 to) {}
    public interface Observer {
        boolean interested(ServerPlayer player);
        /** Most observers remain limited to 2 blocks; an observed dash may request a bounded larger step. */
        default double maxStep() { return 2.0D; }
        default @Nullable Object before(ServerPlayer player, Vec3 requested) { return null; }
        void moved(ServerPlayer player, Step step, @Nullable Object evidence);
        void invalid(ServerPlayer player);
    }
    private static final List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private record Watched(Observer observer, @Nullable Object evidence, double maxStep) {}
    public static final class Scope {
        final @Nullable Scope previous; final ServerPlayer player; final ServerLevel level;
        final Vec3 from, requested; final AABB bounds; final List<Watched> observers = new ArrayList<>(); boolean ambiguous;
        Scope(@Nullable Scope previous, ServerPlayer player, Vec3 requested) {
            this.previous = previous; this.player = player; this.level = player.level(); this.requested = requested;
            this.from = player.position(); this.bounds = player.getBoundingBox();
        }
    }
    private ConfirmedPlayerMove() {}
    public static void register(Observer observer) { if (observer != null && !OBSERVERS.contains(observer)) OBSERVERS.add(observer); }
    public static @Nullable Scope begin(Entity entity, MoverType type, Vec3 requested) {
        Scope previous = CURRENT.get();
        if (previous != null && entity instanceof LivingEntity) previous.ambiguous = true;
        if (!(entity instanceof ServerPlayer player) || !(entity.level() instanceof ServerLevel) || player.noPhysics
            || (type != MoverType.PLAYER && type != MoverType.SELF)) return null;
        List<Observer> interested = new ArrayList<>();
        for (Observer observer : OBSERVERS) try { if (observer.interested(player)) interested.add(observer); }
        catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Player movement observer failed", error); }
        if (interested.isEmpty()) return null;
        Scope scope = new Scope(previous, player, requested);
        if (previous != null || !finite(requested) || requested.lengthSqr() > 144.0D) scope.ambiguous = true;
        CURRENT.set(scope);
        for (Observer observer : interested) {
            try {
                double max = observer.maxStep();
                if (!Double.isFinite(max) || max <= 0 || max > 12.0D) throw new IllegalArgumentException("invalid movement limit");
                scope.observers.add(new Watched(observer, observer.before(player, requested), max));
            } catch (RuntimeException error) {
                scope.ambiguous = true; scope.observers.add(new Watched(observer, null, 2.0D));
                EnchantmentFusion.LOGGER.error("Player movement observer failed", error);
            }
        }
        return scope;
    }
    public static void end(@Nullable Scope scope, boolean completed) {
        if (scope == null) return;
        if (CURRENT.get() != scope) { CURRENT.remove(); invalidate(scope); return; }
        if (scope.previous == null) CURRENT.remove(); else CURRENT.set(scope.previous);
        Vec3 end = scope.player.position(), movement = end.subtract(scope.from); AABB next = scope.player.getBoundingBox(), old = scope.bounds;
        boolean valid = completed && !scope.ambiguous && scope.player.level() == scope.level && finite(scope.from) && finite(end) && finite(movement)
            && movement.lengthSqr() <= 144.0D && same(next.getXsize(), old.getXsize()) && same(next.getYsize(), old.getYsize())
            && same(next.getZsize(), old.getZsize()) && same(next.minX - old.minX, movement.x)
            && same(next.minY - old.minY, movement.y) && same(next.minZ - old.minZ, movement.z);
        if (!valid) { invalidate(scope); return; }
        for (Watched watched : scope.observers) {
            try {
                double maxSquared = watched.maxStep() * watched.maxStep();
                if (scope.requested.lengthSqr() > maxSquared || movement.lengthSqr() > maxSquared)
                    watched.observer().invalid(scope.player);
                else if (movement.lengthSqr() > 1.0E-10D)
                    watched.observer().moved(scope.player, new Step(scope.from, end), watched.evidence());
            } catch (RuntimeException error) {
                EnchantmentFusion.LOGGER.error("Player movement observer failed", error);
                invalidate(scope); return;
            }
        }
    }
    private static void invalidate(Scope scope) {
        for (Watched watched : scope.observers) try { watched.observer().invalid(scope.player); }
        catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Player movement cleanup failed", error); }
    }
    private static boolean same(double a, double b) { return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= 1.0E-5D; }
    private static boolean finite(@Nullable Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean near(Vec3 a, Vec3 b) { return finite(a) && finite(b) && a.distanceToSqr(b) <= 1.0E-6D; }
    /** A gap indicates teleport, correction, unloaded data or an unobserved movement type. */
    public static boolean connected(Vec3 initial, Vec3 actual, List<Step> steps) { return connected(initial, actual, steps, 2.0D); }
    public static boolean connected(Vec3 initial, Vec3 actual, List<Step> steps, double maxStep) {
        if (!finite(initial) || !finite(actual) || steps == null || steps.size() > 4
            || !Double.isFinite(maxStep) || maxStep <= 0 || maxStep > 12.0D) return false;
        Vec3 cursor = initial;
        for (Step step : steps) {
            if (step == null || !near(cursor, step.from()) || !finite(step.to())
                || step.from().distanceToSqr(step.to()) > maxStep * maxStep) return false;
            cursor = step.to();
        }
        return near(cursor, actual);
    }
}
