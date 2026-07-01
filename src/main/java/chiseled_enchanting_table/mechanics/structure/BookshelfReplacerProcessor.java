package chiseled_enchanting_table.mechanics.structure;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Structure processor that turns {@link Blocks#BOOKSHELF} blocks in generated structures
 * (villages, mansions, ancient cities, ...) into filled {@link Blocks#CHISELED_BOOKSHELF} blocks
 * whose contents are chosen from
 * {@link chiseled_enchanting_table.data.ChiseledEnchantingTableData#bookshelfBookPool()}.
 *
 * <p><b>Design:</b> the actual book stacks need a live enchantment {@link net.minecraft.core.Holder}
 * lookup, which requires a {@link ServerLevel} — not available from
 * {@link #processBlock}. So this class splits the work in two:</p>
 * <ul>
 *   <li>{@link #processBlock} rewrites {@code BOOKSHELF} entries in the template block-info list
 *       to {@code CHISELED_BOOKSHELF} block states (empty, no NBT). This is what actually lands
 *       in the world when the template is placed.</li>
 *   <li>{@link #finalizeProcessing} runs afterwards with a {@link ServerLevelAccessor} and calls
 *       {@link BookshelfBookFiller#applyToPlacedBookshelf(ServerLevel, BlockPos, RandomSource)}
 *       on every position we rewrote, so the same enrichment logic is shared with the
 *       stronghold-library post-processing path (via
 *       {@link chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin}).</li>
 * </ul>
 */
public class BookshelfReplacerProcessor extends StructureProcessor {
    public static final BookshelfReplacerProcessor INSTANCE = new BookshelfReplacerProcessor();
    public static final MapCodec<BookshelfReplacerProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    /**
     * Feature-gate the block-replacement side of this processor. Live-test the mixin log lines
     * first; flip to {@code true} once all four upstream call sites are confirmed hitting.
     */
    public static final boolean REPLACEMENT_ENABLED = true;

    private BookshelfReplacerProcessor() {}

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorRegistry.BOOKSHELF_REPLACER;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
        net.minecraft.world.level.LevelReader world,
        BlockPos pos,
        BlockPos pivot,
        StructureTemplate.StructureBlockInfo originalBlockInfo,
        StructureTemplate.StructureBlockInfo currentBlockInfo,
        StructurePlaceSettings data
    ) {
        if (!REPLACEMENT_ENABLED) return currentBlockInfo;
        BlockState state = currentBlockInfo.state();
        if (!state.is(Blocks.BOOKSHELF)) return currentBlockInfo;
        // Rewrite to an empty chiseled bookshelf; the fill happens in finalizeProcessing where a
        // ServerLevelAccessor is available (needed for the dynamic enchantment registry lookup).
        BlockState chiseled = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), chiseled, null);
    }

    /**
     * Called after all blocks in this piece are placed. We revisit every position we rewrote in
     * {@link #processBlock} and enrich the chiseled bookshelf with items + updated slot state.
     */
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor world,
        BlockPos pos,
        BlockPos pivot,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> currentBlockInfos,
        StructurePlaceSettings data
    ) {
        if (!REPLACEMENT_ENABLED) return currentBlockInfos;
        ServerLevel level = world.getLevel();
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(currentBlockInfos.size());
        for (StructureTemplate.StructureBlockInfo info : currentBlockInfos) {
            if (info.state().is(Blocks.CHISELED_BOOKSHELF) && info.nbt() == null) {
                // Positional seed keeps world generation deterministic (same seed -> same fill).
                RandomSource random = RandomSource.create(
                    level.getSeed()
                        ^ ((long) info.pos().getX() * 341873128712L)
                        ^ ((long) info.pos().getY() * 132897987541L)
                        ^ info.pos().getZ()
                );
                BookshelfBookFiller.applyToPlacedBookshelf(level, info.pos(), random);
            }
            result.add(info);
        }
        return result;
    }

    /**
     * Called by {@link chiseled_enchanting_table.mixin.structure.LevelChunkPostProcessMixin} for
     * every block previously flagged by
     * {@link chiseled_enchanting_table.mixin.structure.StructurePieceMarkMixin} that still resolves
     * to a plain bookshelf. Delegates to {@link BookshelfBookFiller}.
     */
    public static void postProcessBookshelf(ServerLevel level, BlockPos pos) {
        if (!REPLACEMENT_ENABLED) return;
        RandomSource random = RandomSource.create(
            level.getSeed()
                ^ ((long) pos.getX() * 341873128712L)
                ^ ((long) pos.getY() * 132897987541L)
                ^ pos.getZ()
        );
        BookshelfBookFiller.applyToPlacedBookshelf(level, pos, random);
    }
}
