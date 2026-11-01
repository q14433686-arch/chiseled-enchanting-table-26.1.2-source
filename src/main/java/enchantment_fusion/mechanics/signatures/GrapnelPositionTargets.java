package enchantment_fusion.mechanics.signatures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A position mark selects the first actual living body under the player's bounded clear aim ray. */
final class GrapnelPositionTargets {
    private GrapnelPositionTargets() {}
    static @Nullable LivingEntity aimed(ServerPlayer owner, Vec3 mark) {
        if (!Double.isFinite(mark.x) || !Double.isFinite(mark.y) || !Double.isFinite(mark.z)
            || owner.position().distanceToSqr(mark) > 12.0D * 12.0D) return null;
        ServerLevel level = owner.level(); BlockPos markPos = BlockPos.containing(mark);
        if (!level.hasChunkAt(markPos) || !level.isInWorldBounds(markPos) || !level.mayInteract(owner, markPos)
            || level.getServer().isUnderSpawnProtection(level, markPos, owner)) return null;
        Vec3 from = owner.getEyePosition(), look = owner.getLookAngle();
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z) || look.lengthSqr() < 1.0E-8D) return null;
        Vec3 to = from.add(look.normalize().scale(12.0D)); LivingEntity first = null; Vec3 hit = null; double closest = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.3D),
            entity -> entity != owner && entity.isAlive() && !entity.isSpectator() && entity.canBeHitByProjectile())) {
            var intersection = candidate.getBoundingBox().inflate(0.2D).clip(from, to);
            if (intersection.isEmpty()) continue;
            double distance = from.distanceToSqr(intersection.get());
            if (distance < closest) { first = candidate; hit = intersection.get(); closest = distance; }
        }
        if (first == null || hit == null || first.position().distanceToSqr(mark) > 8.0D * 8.0D
            || !level.hasChunkAt(first.blockPosition()) || !VirtualCombatSight.clear(level, owner, from, hit)
            || !VirtualCombatSight.clear(level, owner, mark.add(0, 0.8D, 0), hit)) return null;
        return first; // the caller must still check PvP, allies, neutral and protected entities
    }
}
