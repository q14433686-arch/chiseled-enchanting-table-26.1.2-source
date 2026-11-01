package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared, bounded virtual cutting line; it never creates or copies a real weapon/projectile. */
final class VirtualBlade {
    private record Impact(LivingEntity entity, Vec3 point, double distance) {}
    private VirtualBlade() {}

    static int cast(ServerPlayer owner, Vec3 look, @Nullable LivingEntity excluded, double range, float damage,
                    int maxVictims, Predicate<LivingEntity> eligible, ParticleOptions particle) {
        return owner == null ? 0 : castFrom(owner, owner.getEyePosition(), look, excluded, range, damage, maxVictims, eligible, p -> true, particle);
    }
    /** Shared ray settlement, optionally limited to a continuous surface such as an ice path. */
    static int castFrom(ServerPlayer owner, Vec3 start, Vec3 look, @Nullable LivingEntity excluded, double range, float damage,
                        int maxVictims, Predicate<LivingEntity> eligible, Predicate<Vec3> traversable, ParticleOptions particle) {
        return castFrom(owner, start, look, excluded, range, damage, maxVictims, eligible, traversable, particle, null);
    }
    /** A moving blade can react only to accepted, net-damaging impacts, including contacts already overlapping its start. */
    static int sweep(ServerPlayer owner, Vec3 start, Vec3 motion, float damage, int maxVictims,
                     Predicate<LivingEntity> eligible, ParticleOptions particle, Consumer<LivingEntity> landed) {
        if (motion == null || landed == null) return 0;
        return castFrom(owner, start, motion, null, motion.length(), damage, maxVictims, eligible, p -> true, particle, landed);
    }
    private static int castFrom(ServerPlayer owner, Vec3 start, Vec3 look, @Nullable LivingEntity excluded, double range, float damage,
                                int maxVictims, Predicate<LivingEntity> eligible, Predicate<Vec3> traversable, ParticleOptions particle,
                                @Nullable Consumer<LivingEntity> landed) {
        if (owner == null || start == null || look == null || eligible == null || traversable == null || particle == null || !owner.isAlive()
            || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)
            || !Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
            || look.lengthSqr() < 1.0E-8D || !Double.isFinite(range) || range <= 0 || range > 24
            || !Float.isFinite(damage) || damage <= 0 || maxVictims < 1 || maxVictims > 32 || !traversable.test(start)) return 0;
        ServerLevel level = owner.level(); Vec3 unit = look.normalize(), end = start;
        int steps = (int) Math.ceil(range * 2.0D);
        for (int i = 1; i <= steps; i++) {
            Vec3 next = start.add(unit.scale(range * i / steps));
            if (!traversable.test(next) || !VirtualCombatSight.clear(level, owner, start, next)) break;
            end = next;
        }
        List<Impact> impacts = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(0.5D),
            e -> e != excluded && eligible.test(e))) {
            AABB box = candidate.getBoundingBox().inflate(0.2D);
            var intersection = landed != null && box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (intersection.isPresent()) {
                Vec3 point = intersection.get(); impacts.add(new Impact(candidate, point, point.distanceToSqr(start)));
            }
        }
        impacts.sort(Comparator.comparingDouble(Impact::distance));
        int attempted = 0;
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            for (Impact impact : impacts) {
                if (++attempted > maxVictims) break;
                LivingEntity victim = impact.entity();
                if (!eligible.test(victim) || !VirtualCombatSight.clear(level, owner, start, impact.point())) continue;
                damage(level, owner, victim, damage, landed);
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        double actual = Math.sqrt(start.distanceToSqr(end));
        int particles = Math.max(1, (int) Math.ceil(actual * 2));
        for (int i = 0; i <= particles; i++) {
            Vec3 point = start.add(unit.scale(actual * i / particles));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
        return Math.min(attempted, maxVictims);
    }
    /** Exact current overlap also handles an enemy walking into a temporarily stationary blade. */
    static void contact(ServerPlayer owner, Vec3 point, float damage, int maxVictims, Predicate<LivingEntity> eligible,
                        Consumer<LivingEntity> landed) {
        if (owner == null || !owner.isAlive() || point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)
            || !Double.isFinite(point.z) || !Float.isFinite(damage) || damage <= 0 || maxVictims < 1 || maxVictims > 32
            || eligible == null || landed == null) return;
        ServerLevel level = owner.level();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(0.5D),
            target -> eligible.test(target) && target.getBoundingBox().inflate(0.2D).contains(point));
        if (targets.size() > 32) return;
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int attempted = 0;
            for (LivingEntity target : targets) {
                if (++attempted > maxVictims) break;
                if (eligible.test(target) && target.getBoundingBox().inflate(0.2D).contains(point)
                    && VirtualCombatSight.clear(level, owner, point, target.getBoundingBox().getCenter()))
                    damage(level, owner, target, damage, landed);
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
    }
    private static void damage(ServerLevel level, ServerPlayer owner, LivingEntity victim, float damage,
                               @Nullable Consumer<LivingEntity> landed) {
        float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
        boolean accepted = victim.hurtServer(level, level.damageSources().playerAttack(owner), damage);
        if (landed != null && accepted && ConfirmedMeleeMath.actualLoss(health, absorption,
            victim.getHealth(), victim.getAbsorptionAmount()) > 0.0F) landed.accept(victim);
    }
}
