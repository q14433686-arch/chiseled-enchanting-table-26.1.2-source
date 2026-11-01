package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.registry.FusionBlocks;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A virtual line constrained to existing ice; it never writes, melts or breaks blocks. */
final class FrostGroundBlade {
    private FrostGroundBlade() {}
    private static boolean ice(ServerLevel level, double x, double footY, double z) {
        BlockPos below = BlockPos.containing(x, footY - 0.05D, z);
        if (!level.isInWorldBounds(below) || !level.hasChunkAt(below)) return false;
        BlockState support = level.getBlockState(below);
        return support.is(BlockTags.ICE) || support.is(FusionBlocks.TEMPORARY_FROST) || support.is(FusionBlocks.TEMPORARY_FROST_PATH);
    }
    static boolean onIce(ServerPlayer owner) {
        return owner != null && owner.isAlive() && owner.onGround() && ice(owner.level(), owner.getX(), owner.getY(), owner.getZ());
    }
    static int cast(ServerPlayer owner, @Nullable LivingEntity excluded, float damage, Predicate<LivingEntity> eligible) {
        if (!onIce(owner)) return 0;
        Vec3 look = owner.getLookAngle(); Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0E-8D) return 0;
        double footY = owner.getY(); Vec3 start = new Vec3(owner.getX(), footY + 0.45D, owner.getZ());
        return VirtualBlade.castFrom(owner, start, horizontal, excluded, 6.0D, damage, 6, eligible,
            point -> ice(owner.level(), point.x, footY, point.z), ParticleTypes.SNOWFLAKE);
    }
}
