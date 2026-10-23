package chiseled_enchanting_table.mechanics.structure;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Structure processor that turns {@link Blocks#BOOKSHELF} blocks in generated structures
 * (villages, mansions, ancient cities, ...) into filled {@link Blocks#CHISELED_BOOKSHELF} blocks
 * whose contents are chosen from
 * {@link chiseled_enchanting_table.data.ChiseledEnchantingTableData#bookshelfBookPool()}.
 *
 * <p><b>Design (post-deadlock-fix, see
 * {@code docs/reports/WORLDGEN_BOOKSHELF_DEADLOCK_REPORT.md}):</b> both hooks of this processor run
 * on a <i>worldgen worker thread</i> during the {@code FEATURES} decoration stage, inside a
 * {@link net.minecraft.server.level.WorldGenRegion}. The rule for both is the same and absolute:
 * <b>never touch the real {@link ServerLevel}, never read/write the live chunk cache.</b> The real
 * level does not contain the chunk being generated yet, so any read there blocks the worker on a
 * future that only the (blocked) server thread could complete — a hard deadlock under mods that
 * generate chunks on their own pool (Distant Horizons' {@code FEATURES} LOD generator, C2ME).</p>
 *
 * <ul>
 *   <li>{@link #processBlock} rewrites {@code BOOKSHELF} entries in the template block-info list
 *       to empty {@code CHISELED_BOOKSHELF} block states. Pure data, no world access.</li>
 *   <li>{@link #finalizeProcessing} rolls the book contents with
 *       {@link BookshelfBookFiller#computeFill} (pure: pool JSON + enchantment registry + the
 *       positional seed) and returns a new {@link StructureTemplate.StructureBlockInfo} carrying
 *       the finished block state and the block-entity NBT. Vanilla's
 *       {@code StructureTemplate.placeInWorld} then places the block and loads the NBT into the
 *       freshly created block entity — on whichever thread owns the generation step, with no
 *       cross-chunk read anywhere.</li>
 *   <li>The facing heuristic needs the <i>finished</i> geometry (a piece's own walls are still in
 *       the template during {@code finalizeProcessing}), so it is deferred: {@code finalizeProcessing}
 *       marks the position for chunk post-processing and
 *       {@link chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin} calls
 *       {@link BookshelfBookFiller#applyFacingToPlacedBookshelf} from the server thread once the
 *       chunk becomes ticking. If that mark is ever unavailable the shelf still keeps its books —
 *       only the facing stays at the template's rotated default.</li>
 * </ul>
 */
public class BookshelfReplacerProcessor implements StructureProcessor {
    public static final BookshelfReplacerProcessor INSTANCE = new BookshelfReplacerProcessor();
    public static final MapCodec<BookshelfReplacerProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    /**
     * Feature-gate the block-replacement side of this processor. Live-test the mixin log lines
     * first; flip to {@code true} once all four upstream call sites are confirmed hitting.
     */
    public static final boolean REPLACEMENT_ENABLED = true;

    private BookshelfReplacerProcessor() {}

    @Override
    public MapCodec<BookshelfReplacerProcessor> codec() {
        return CODEC;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
        net.minecraft.world.level.LevelReader world,
        BlockPos pos,
        BlockPos pivot,
        BlockPos templatePos,
        StructureTemplate.StructureBlockInfo currentBlockInfo,
        StructurePlaceSettings data
    ) {
        if (!REPLACEMENT_ENABLED || !chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookshelfReplacementEnabled()) return currentBlockInfo;
        BlockState state = currentBlockInfo.state();
        if (!state.is(Blocks.BOOKSHELF)) return currentBlockInfo;
        // Rewrite to an empty chiseled bookshelf; the fill happens in finalizeProcessing, which
        // turns the entry into a completely finished shelf (state + block-entity NBT).
        BlockState chiseled = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), chiseled, null);
    }

    /**
     * Called after every block of the current piece-slice has been processed, still on the worldgen
     * worker thread. Replaces each empty chiseled-bookshelf entry with a finished one: the state
     * gains the {@code slot_*_occupied} properties and the entry gains the block-entity NBT with
     * the book stacks.
     *
     * <p><b>No world access.</b> {@code level} is only used for its registry access (the dynamic
     * enchantment lookup) and its seed; both are plain field reads. The book stacks are attached to
     * the returned {@link StructureTemplate.StructureBlockInfo}, and vanilla applies them when it
     * places the block — see {@code StructureTemplate#placeInWorld}, which calls
     * {@code BlockEntity#loadWithComponents(blockInfo.nbt)} right after {@code setBlock}.</p>
     */
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor level,
        BlockPos pos,
        BlockPos pivot,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> currentBlockInfos,
        StructurePlaceSettings data
    ) {
        if (!REPLACEMENT_ENABLED || !chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookshelfReplacementEnabled()) return currentBlockInfos;

        long seed = worldSeed(level);
        HolderGetter<Enchantment> enchantments = null;
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(currentBlockInfos.size());
        for (StructureTemplate.StructureBlockInfo info : currentBlockInfos) {
            if (!info.state().is(Blocks.CHISELED_BOOKSHELF) || info.nbt() != null) {
                result.add(info);
                continue;
            }
            if (enchantments == null) {
                enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            }
            // Positional seed keeps world generation deterministic (same seed + same position ->
            // same fill), and matches the seed the main-thread post-process path uses.
            RandomSource random = RandomSource.create(seedFor(seed, info.pos()));
            BookshelfBookFiller.Fill fill = BookshelfBookFiller.computeFill(info.state(), random, enchantments);
            result.add(new StructureTemplate.StructureBlockInfo(
                info.pos(),
                fill.state(),
                BookshelfBookFiller.toBlockEntityTag(fill.items(), level.registryAccess())
            ));
            // Deferred (server-thread) facing pass; best effort, see the class javadoc.
            markForFacingPass(level, info.pos());
        }
        return result;
    }

    /**
     * Called by {@link chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin} for
     * every block previously flagged by
     * {@link chiseled_enchanting_table.mixin.structure.StructurePieceMarkMixin} that still resolves
     * to a plain bookshelf (programmatic pieces such as stronghold libraries). Delegates to
     * {@link BookshelfBookFiller#applyToPlacedBookshelf} — this runs on the server thread.
     */
    public static void postProcessBookshelf(ServerLevel level, BlockPos pos) {
        if (!REPLACEMENT_ENABLED || !chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookshelfReplacementEnabled()) return;
        RandomSource random = RandomSource.create(seedFor(level.getSeed(), pos));
        BookshelfBookFiller.applyToPlacedBookshelf(level, pos, random);
    }

    /**
     * Called by the same mixin for a chiseled bookshelf that was already filled during worldgen
     * (template path). Only the facing is (re)decided, now that the piece and its neighbours are
     * actually placed. Server thread.
     */
    public static void postProcessFilledBookshelf(ServerLevel level, BlockPos pos) {
        if (!REPLACEMENT_ENABLED || !chiseled_enchanting_table.data.ChiseledEnchantingTableData.bookshelfReplacementEnabled()) return;
        BookshelfBookFiller.applyFacingToPlacedBookshelf(level, pos);
    }

    /** Deterministic per-position seed; shared by both fill paths. */
    private static long seedFor(long worldSeed, BlockPos pos) {
        return worldSeed
            ^ ((long) pos.getX() * 341873128712L)
            ^ ((long) pos.getY() * 132897987541L)
            ^ pos.getZ();
    }

    /**
     * Reads the world seed without reaching through to the live {@link ServerLevel} when we are in
     * a worldgen region ({@link WorldGenLevel#getSeed()} is a cached field, copied by
     * {@code WorldGenRegion} at construction). The {@code getLevel()} fallback only happens for
     * non-worldgen callers such as {@code /place structure}, which run on the server thread anyway.
     */
    private static long worldSeed(ServerLevelAccessor level) {
        return level instanceof WorldGenLevel worldGenLevel
            ? worldGenLevel.getSeed()
            : level.getLevel().getSeed();
    }

    /**
     * Marks {@code pos} for the deferred facing pass. Only {@link ProtoChunk}s can hold that mark
     * (vanilla's {@code ChunkAccess#markPosForPostProcessing} is a logging no-op on anything else),
     * and only chunks that are actually part of the current worldgen region can be reached at all —
     * hence the guard-catch rather than a hard failure: losing the mark costs a facing heuristic,
     * not the shelf.
     */
    private static void markForFacingPass(ServerLevelAccessor level, BlockPos pos) {
        // Only worldgen regions carry ProtoChunks with a live post-processing queue. Anything else
        // (a /place or structure-block placement against a real ServerLevel) must not be touched
        // from here: ServerChunkCache#getChunk is a managedBlock call even for a pure lookup.
        if (!(level instanceof WorldGenLevel)) return;
        try {
            ChunkAccess chunk = level.getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()),
                ChunkStatus.EMPTY,
                false
            );
            if (chunk instanceof ProtoChunk protoChunk) {
                protoChunk.markPosForPostProcessing(pos);
            }
        } catch (RuntimeException exception) {
            // Position outside this generation step's region (e.g. a processor chain that opts
            // into whole-piece state evaluation). Books are already attached; skip the facing pass.
            ChiseledEnchantingTable.LOGGER.debug(
                "chiseled-enchanting-table: skipped bookshelf facing pass at {} ({})",
                pos, exception.toString()
            );
        }
    }
}
