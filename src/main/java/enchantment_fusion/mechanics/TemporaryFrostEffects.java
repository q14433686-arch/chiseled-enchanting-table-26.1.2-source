package enchantment_fusion.mechanics;

import enchantment_fusion.registry.FusionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** World changes for the frost seal and wake. Neither replaces an existing water block. */
public final class TemporaryFrostEffects {
    private TemporaryFrostEffects() {}

    private static boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return player.mayBuild() && level.isInWorldBounds(pos) && level.hasChunkAt(pos) && level.mayInteract(player, pos)
            && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static boolean clearOfLivingEntities(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(LivingEntity.class, bounds, LivingEntity::isAlive).isEmpty();
    }

    /**
     * Seal adjacent water in the newly mined air cell; water itself is never replaced. Adjacent
     * lava still turns into permanent cobblestone, subject to the same vanilla permissions.
     */
    public static void sealAdjacentFluids(ServerLevel level, ServerPlayer player, BlockPos minedPos) {
        if (!mayModify(level, player, minedPos)) return;
        boolean adjacentWater = false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = minedPos.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            BlockState state = level.getBlockState(adjacent);
            if (state.getFluidState().is(FluidTags.WATER)) {
                adjacentWater = true;
            } else if (state.is(Blocks.LAVA) && mayModify(level, player, adjacent)) {
                level.setBlockAndUpdate(adjacent, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        if (adjacentWater && level.getBlockState(minedPos).isAir()
            && clearOfLivingEntities(level, new AABB(minedPos))) {
            level.setBlockAndUpdate(minedPos, FusionBlocks.TEMPORARY_FROST.defaultBlockState());
        }
    }

    /**
     * Build a short-lived, 2/16-high bridge in air above source water. Skipping submerged,
     * sneaking and occupied positions lets the wearer and nearby swimmers choose to dive.
     */
    public static void placeWake(ServerLevel level, ServerPlayer player, int frostLevel) {
        if (!player.mayBuild() || player.isShiftKeyDown() || player.isInWater()) return;
        int radius = Math.min(4, 2 + Math.max(1, frostLevel));
        BlockPos feet = player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                // Include the next lower surface so a one-block drop from shore is usable.
                for (int dy = -2; dy <= -1; dy++) {
                    placeThinIceAbove(level, player, feet.offset(dx, dy, dz));
                }
            }
        }
    }

    /**
     * Make room for the depth-strider dive without treating vanilla or datapack ice tags as ours.
     * Thin paths occupy the player's feet cell (their collision shape is only 2/16 high), while
     * a player standing slightly higher may have one immediately below their feet. Do not turn
     * either cell into water, and do not remove full-sized temporary frost cages or seals.
     *
     * This is a temporary-block removal rather than an extra harvest: it consumes no tool or
     * drops. Vanilla world/permission checks are applied; modded protection integrations still
     * need their own in-game compatibility verification.
     */
    public static boolean removeDivePath(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return removeDivePathAt(level, player, feet) || removeDivePathAt(level, player, feet.below());
    }

    private static boolean removeDivePathAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!mayModify(level, player, pos)) return false;
        if (!level.getBlockState(pos).is(FusionBlocks.TEMPORARY_FROST_PATH)) return false;
        return level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /** Small CRYO matrix patch around the event, without replacing water or trapping swimmers. */
    public static void placeSurfacePatch(ServerLevel level, ServerPlayer player, BlockPos center) {
        if (!player.mayBuild() || player.isShiftKeyDown()) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placeThinIceAbove(level, player, center.offset(dx, -1, dz));
            }
        }
    }

    private static void placeThinIceAbove(ServerLevel level, ServerPlayer player, BlockPos water) {
        BlockPos top = water.above();
        if (!level.hasChunkAt(water) || !mayModify(level, player, top)) return;
        BlockState state = level.getBlockState(water);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource() || !level.getBlockState(top).isAir()) return;
        AABB thinIce = new AABB(top.getX(), top.getY(), top.getZ(),
            top.getX() + 1.0D, top.getY() + 0.125D, top.getZ() + 1.0D);
        if (clearOfLivingEntities(level, thinIce)) {
            level.setBlockAndUpdate(top, FusionBlocks.TEMPORARY_FROST_PATH.defaultBlockState());
        }
    }
}
