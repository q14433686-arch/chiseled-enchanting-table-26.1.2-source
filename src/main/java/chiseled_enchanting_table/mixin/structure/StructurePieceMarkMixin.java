package chiseled_enchanting_table.mixin.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tier-2 injection: programmatic structures (strongholds being the notable one — their libraries
 * put bookshelves down via {@link StructurePiece#placeBlock} rather than through a template file).
 * We can't intercept those with a template-side processor, so we mark every plain
 * {@code minecraft:bookshelf} block placement for chunk post-processing; the actual replacement
 * runs from {@link LevelChunkPostProcessMixin}.
 *
 * <p><b>Coordinate note (this is the actual bug that broke strongholds last round):</b> the
 * {@code (x, y, z)} arguments to {@link StructurePiece#placeBlock} are <b>piece-local</b>, not
 * world coordinates. Internally vanilla immediately converts them via
 * {@link StructurePiece#getWorldPos} (which factors in the piece's rotation, mirror, and origin).
 * We <b>must</b> do the same before calling {@code markPosForPostProcessing}, otherwise we mark a
 * random chunk somewhere near {@code (0,0,0)} in the world and the mixin fires but has zero
 * effect. That's what happened in the previous round — the mixin was being hit, so testing
 * "confirmed the mixin fires", but the marks landed off-target.</p>
 *
 * <p>We inject at {@code HEAD} rather than the more mixin-ninja {@code INVOKE_ASSIGN} that
 * upstream used because for our purposes we just need to know "is this a bookshelf placement in
 * this bounding box"; we don't need any of the local variables computed later in the method. HEAD
 * also runs before the vanilla bounding-box-inside check, so we re-do that same check ourselves
 * (using the world position, since {@code isInside} expects world coords).</p>
 */
@Mixin(StructurePiece.class)
public class StructurePieceMarkMixin {
    @Inject(
        method = "placeBlock(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/block/state/BlockState;IIILnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
        at = @At("HEAD")
    )
    private void chiseled_enchanting_table$markBookshelfForPostProcess(
        WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box, CallbackInfo ci
    ) {
        if (!state.is(Blocks.BOOKSHELF)) return;
        // Piece-local -> world. This uses the same helper vanilla uses two lines into placeBlock.
        BlockPos worldPos = ((StructurePieceAccessor)(Object)this).chiseled_enchanting_table$callGetWorldPos(x, y, z);
        // Re-do vanilla's isInside check on the world coord.
        if (!box.isInside(worldPos)) return;
        // Copy to an immutable BlockPos so the mutable one returned by getWorldPos can't be
        // recycled under us if vanilla reuses it later in the same call.
        BlockPos frozenPos = worldPos.immutable();
        level.getChunk(frozenPos).markPosForPostProcessing(frozenPos);
    }
}
