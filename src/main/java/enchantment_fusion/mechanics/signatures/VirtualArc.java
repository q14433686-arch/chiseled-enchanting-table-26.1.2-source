package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A bounded virtual arc. It neither creates a lightning entity nor modifies world blocks. */
final class VirtualArc {
    private VirtualArc() {}
    static void show(ServerPlayer owner, Vec3 from, Vec3 to) {
        if (!VirtualCombatSight.clear(owner.level(), owner, from, to)) return;
        int steps = Math.max(1, Math.min(72, (int) Math.ceil(from.distanceTo(to) * 3.0D)));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.04D, 0.04D, 0.04D, 0.01D);
        }
    }
    static int chain(ServerPlayer owner, Vec3 origin, @Nullable LivingEntity excluded, Predicate<LivingEntity> eligible,
                     float damage, int maxVictims) {
        if (owner == null || !owner.isAlive() || origin == null || eligible == null || !Float.isFinite(damage)
            || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
            || damage <= 0 || maxVictims < 1 || maxVictims > 8) return 0;
        ServerLevel level = owner.level(); Vec3 current = origin; Set<UUID> seen = new HashSet<>();
        if (excluded != null) seen.add(excluded.getUUID());
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int hit = 0;
            for (int attempts = 0; attempts < 32 && hit < maxVictims; attempts++) {
                Vec3 from = current;
                var candidates = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(4.0D),
                    e -> !seen.contains(e.getUUID()) && eligible.test(e) && e.getBoundingBox().getCenter().distanceToSqr(from) <= 16.0D);
                LivingEntity next = candidates.stream().filter(e -> VirtualCombatSight.clear(level, owner, from, e.getBoundingBox().getCenter()))
                    .min(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(from))).orElse(null);
                if (next == null) break;
                seen.add(next.getUUID()); Vec3 to = next.getBoundingBox().getCenter();
                float health = next.getHealth(), absorption = next.getAbsorptionAmount();
                boolean accepted = next.hurtServer(level, level.damageSources().playerAttack(owner), damage);
                if (!accepted || ConfirmedMeleeMath.actualLoss(health, absorption, next.getHealth(), next.getAbsorptionAmount()) <= 0) continue;
                show(owner, from, to); current = to; hit++;
            }
            return hit;
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
    }
}
