package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedPair;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared server-authoritative pull; callers claim the real mark before applying any impulse. */
final class GrapnelPull {
    private GrapnelPull() {}
    static boolean attempt(ServerPlayer owner, LivingEntity target, FusedPair pair) {
        ServerLevel level = owner.level();
        Vec3 from = owner.getEyePosition(), to = target.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) > 12.0D * 12.0D || !VirtualCombatSight.clear(level, owner, from, to)) return false;
        double awayX = target.getX() - owner.getX(), awayZ = target.getZ() - owner.getZ();
        if (awayX * awayX + awayZ * awayZ < 0.01D) return false;
        Vec3 before = target.getDeltaMovement();
        if (!Double.isFinite(before.x) || !Double.isFinite(before.y) || !Double.isFinite(before.z)) return false;
        double force = Math.min(1.25D, 0.45D + 0.10D * Math.clamp(pair.secondaryLevel(), 1, 8));
        target.knockback(force, awayX, awayZ); // ordinary knockback resistance and movement collisions still apply
        if (target.getDeltaMovement().distanceToSqr(before) < 1.0E-8D) return false;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        level.sendParticles(ParticleTypes.FISHING, target.getX(), target.getY(0.6D), target.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.02D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
        return true;
    }
}
