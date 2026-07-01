package chiseled_enchanting_table.mixin.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin that exposes {@link StructurePiece}'s {@code protected getWorldPos(int, int, int)}
 * helper to our {@link StructurePieceMarkMixin} so we can turn the piece-local {@code (x, y, z)}
 * coordinates that {@link StructurePiece#placeBlock} receives into world coordinates before we
 * mark the position for post-processing.
 *
 * <p>Without this we'd try to mark a chunk position using local coordinates (values like
 * {@code (2, 3, 4)}), which would either fall outside the chunk being generated (silently doing
 * nothing) or mark the wrong world position near {@code (0, 0, 0)} — that's exactly the bug that
 * made stronghold libraries fail to replace their bookshelves in the previous round.</p>
 */
@Mixin(StructurePiece.class)
public interface StructurePieceAccessor {
    @Invoker("getWorldPos")
    BlockPos.MutableBlockPos chiseled_enchanting_table$callGetWorldPos(int x, int y, int z);
}
