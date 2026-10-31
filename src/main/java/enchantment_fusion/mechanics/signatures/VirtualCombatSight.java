package enchantment_fusion.mechanics.signatures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Short bounded virtual-effect rays never ask the world to load unseen terrain. */
final class VirtualCombatSight {
    private VirtualCombatSight() {}
    static boolean clear(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        if (!Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z)
            || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)
            || from.distanceToSqr(to) > 24.0D * 24.0D) return false;
        BlockPos start = BlockPos.containing(from), end = BlockPos.containing(to);
        int minX = Math.min(start.getX(), end.getX()) >> 4, maxX = Math.max(start.getX(), end.getX()) >> 4;
        int minZ = Math.min(start.getZ(), end.getZ()) >> 4, maxZ = Math.max(start.getZ(), end.getZ()) >> 4;
        if (maxX - minX > 2 || maxZ - minZ > 2) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!level.hasChunkAt(new BlockPos(x << 4, start.getY(), z << 4))) return false;
        }
        return level.clipIncludingBorder(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
            .getType() == HitResult.Type.MISS;
    }
}
