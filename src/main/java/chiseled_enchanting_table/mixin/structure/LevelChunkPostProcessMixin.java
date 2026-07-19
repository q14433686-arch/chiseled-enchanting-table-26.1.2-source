package chiseled_enchanting_table.mixin.structure;

import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tier-2 injection consumer: runs at the top of chunk post-processing and replaces any block
 * previously flagged by {@link StructurePieceMarkMixin} that still resolves to a plain bookshelf
 * with a filled chiseled bookshelf via {@link BookshelfReplacerProcessor#postProcessBookshelf}.
 *
 * <p><b>Why {@code @At("HEAD")}, not {@code @At("TAIL")}:</b> vanilla's
 * {@link LevelChunk#postProcessGeneration} iterates the {@code postProcessing} lists and calls
 * {@code list.clear()} inside the loop (once each per-section list is drained). If we injected at
 * {@code TAIL}, the arrays would already be empty and we'd never see the bookshelf positions we
 * marked. Injecting at {@code HEAD} means we run first, on the full lists, before vanilla's
 * fluid/neighbour-update pass consumes them.</p>
 *
 * <p><b>Why {@link ProtoChunk#unpackOffsetCoordinates}:</b> the packed-short encoding used by
 * {@link net.minecraft.world.level.chunk.ChunkAccess#markPosForPostProcessing} (actually
 * implemented in {@link ProtoChunk#packOffsetCoordinates}) is not a trivial
 * {@code (y<<8)|(z<<4)|x} layout; using vanilla's own unpacker is both correct-by-construction
 * and future-proof against any encoding tweak. This is exactly what vanilla
 * {@link LevelChunk#postProcessGeneration} calls internally when it drains the lists.</p>
 */
@Mixin(LevelChunk.class)
public class LevelChunkPostProcessMixin {
    @Inject(method = "postProcessGeneration(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"))
    private void chiseled_enchanting_table$replaceMarkedBookshelves(ServerLevel level, CallbackInfo ci) {
        LevelChunk self = (LevelChunk)(Object)this;
        ShortList[] postProcessing = self.getPostProcessing();
        ChunkPos chunkPos = self.getPos();
        for (int sectionIndex = 0; sectionIndex < postProcessing.length; sectionIndex++) {
            ShortList section = postProcessing[sectionIndex];
            if (section == null || section.isEmpty()) continue;
            int sectionY = self.getSectionYFromSectionIndex(sectionIndex);
            for (int i = 0; i < section.size(); i++) {
                short packed = section.getShort(i);
                BlockPos pos = ProtoChunk.unpackOffsetCoordinates(packed, sectionY, chunkPos);
                if (self.getBlockState(pos).is(Blocks.BOOKSHELF)) {
                    BookshelfReplacerProcessor.postProcessBookshelf(level, pos);
                }
            }
        }
        // Note: we intentionally do NOT clear the postProcessing lists here — vanilla's own loop
        // still needs them (fluid ticks, neighbour-shape updates, etc.). Replacing the block state
        // above means the subsequent vanilla `is(BOOKSHELF)` check for the same coordinate would
        // fail regardless, but the other coordinates in the list are still processed normally.
    }
}
