package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
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
        if (owner == null || look == null || eligible == null || particle == null || !owner.isAlive()
            || !Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
            || look.lengthSqr() < 1.0E-8D || !Double.isFinite(range) || range <= 0 || range > 24
            || !Float.isFinite(damage) || damage <= 0 || maxVictims < 1 || maxVictims > 32) return 0;
        ServerLevel level = owner.level(); Vec3 start = owner.getEyePosition(), unit = look.normalize(), end = start;
        int steps = (int) Math.ceil(range * 2.0D);
        for (int i = 1; i <= steps; i++) {
            Vec3 next = start.add(unit.scale(range * i / steps));
            if (!VirtualCombatSight.clear(level, owner, start, next)) break;
            end = next;
        }
        List<Impact> impacts = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(0.5D),
            e -> e != excluded && eligible.test(e))) {
            var intersection = candidate.getBoundingBox().inflate(0.2D).clip(start, end);
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
                victim.hurtServer(level, level.damageSources().playerAttack(owner), damage);
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
}
