package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Bounded, non-explosive virtual wind: real damage and resisted push, never a world explosion. */
final class VirtualWindRing {
    private VirtualWindRing() {}
    static void push(ServerPlayer owner, LivingEntity victim, Vec3 from, double strength) {
        if (owner == null || victim == null || !victim.isAlive() || from == null || !Double.isFinite(strength) || strength <= 0) return;
        Vec3 direction = victim.position().subtract(from);
        if (direction.horizontalDistanceSqr() < 1.0E-6D) direction = owner.getLookAngle();
        if (direction.horizontalDistanceSqr() < 1.0E-6D) return;
        victim.knockback(strength, -direction.x, -direction.z);
        if (victim instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    static void emit(ServerPlayer owner, Vec3 center, @Nullable LivingEntity excluded, double radius, float damage,
                     int maxVictims, Predicate<LivingEntity> eligible) {
        if (owner == null || !owner.isAlive() || center == null || !Double.isFinite(center.x) || !Double.isFinite(center.y)
            || !Double.isFinite(center.z) || !Double.isFinite(radius) || radius <= 0 || radius > 8 || !Float.isFinite(damage)
            || damage <= 0 || maxVictims <= 0 || maxVictims > 16 || eligible == null) return;
        ServerLevel level = owner.level(); Vec3 eye = center.add(0, 0.8D, 0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2.5D, radius),
            target -> target != excluded && eligible.test(target) && target.position().distanceToSqr(center) <= radius * radius).stream()
            .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center))).limit(maxVictims).toList();
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            for (LivingEntity victim : targets) {
                if (!eligible.test(victim) || !VirtualCombatSight.clear(level, owner, eye, victim.getBoundingBox().getCenter())) continue;
                float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
                boolean accepted = victim.hurtServer(level, level.damageSources().playerAttack(owner), damage);
                if (accepted && ConfirmedMeleeMath.actualLoss(health, absorption, victim.getHealth(), victim.getAbsorptionAmount()) > 0.0F
                    && eligible.test(victim)) push(owner, victim, center, 0.6D);
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        for (int i = 0; i < 16; i++) {
            double angle = 2.0D * Math.PI * i / 16;
            level.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(angle) * radius, center.y + 0.5D,
                center.z + Math.sin(angle) * radius, 1, 0.03D, 0.03D, 0.03D, 0.01D);
        }
    }
}
