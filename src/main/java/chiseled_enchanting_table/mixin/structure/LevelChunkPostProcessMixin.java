package chiseled_enchanting_table.mixin.structure;

import chiseled_enchanting_table.mechanics.structure.BookshelfReplacerProcessor;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tier-2 injection consumer: runs at the top of chunk post-processing and handles the two
 * worldgen-deferred bookshelf cases on the <b>server thread</b>, where touching the live level is
 * safe:
 *
 * <ul>
 *   <li><b>Plain {@code BOOKSHELF}</b> — flagged by
 *       {@link StructurePieceMarkMixin} (programmatic pieces such as stronghold libraries, whose
 *       blocks are written straight into the world instead of through a template). Full fill via
 *       {@link BookshelfReplacerProcessor#postProcessBookshelf}.</li>
 *   <li><b>{@code CHISELED_BOOKSHELF}</b> — flagged by
 *       {@link BookshelfReplacerProcessor#finalizeProcessing} (template pieces). The books are
 *       already in the placed block entity; only the facing heuristic runs here, via
 *       {@link BookshelfReplacerProcessor#postProcessFilledBookshelf}, because it needs the
 *       <i>finished</i> geometry (the piece's own walls are still in the template while the
 *       processor runs).</li>
 * </ul>
 *
 * <p><b>Why this cannot move back into worldgen:</b> historically the template path did the whole
 * fill from {@code finalizeProcessing} against the real {@code ServerLevel}, which blocks a
 * worldgen worker on the chunk it is itself generating — see
 * {@code docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md}. This hook is only reached from
 * {@code ChunkMap#prepareTickingChunk}, i.e. after the chunk is {@code FULL} and on the main
 * thread, so {@code getBlockState}/{@code setBlock} here cannot deadlock.</p>
 *
 * <p><b>Why {@code @At("HEAD")}, not {@code @At("TAIL")}:</b> vanilla's
 * {@link LevelChunk#postProcessGeneration} iterates the {@code postProcessing} lists and calls
 * {@code list.clear()} inside the loop (once each per-section list is drained). If we injected at
 * {@code TAIL}, the arrays would already be empty and we'd never see the bookshelf positions we
 * marked. Injecting at {@code HEAD} means we run first, on the full lists, before vanilla's
 * fluid/neighbour-update pass consumes them.</p>
 *
 * <p><b>Why {@link ProtoChunk#unpackOffsetCoordinates}:</b> the packed-short encoding used by
 * {@link net.minecraft.world.level.chunk.ChunkAccess#markPosForPostProcessing}
 * (actually implemented in {@link ProtoChunk#packOffsetCoordinates}) is not a trivial
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
                BlockState state = self.getBlockState(pos);
                if (state.is(Blocks.BOOKSHELF)) {
                    BookshelfReplacerProcessor.postProcessBookshelf(level, pos);
                } else if (state.is(Blocks.CHISELED_BOOKSHELF)) {
                    BookshelfReplacerProcessor.postProcessFilledBookshelf(level, pos);
                }
            }
        }
        // Note: we intentionally do NOT clear the postProcessing lists here — vanilla's own loop
        // still needs them (fluid ticks, neighbour-shape updates, etc.). Replacing the block state
        // above means the subsequent vanilla `is(BOOKSHELF)` check for the same coordinate would
        // fail regardless, but the other coordinates in the list are still processed normally.
    }
}
