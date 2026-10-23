package chiseled_enchanting_table.mechanics.structure;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
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
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class BookshelfReplacerProcessor extends StructureProcessor {
    public static final BookshelfReplacerProcessor INSTANCE = new BookshelfReplacerProcessor();
    public static final MapCodec<BookshelfReplacerProcessor> CODEC = MapCodec.unit(() -> INSTANCE);

    public static final boolean REPLACEMENT_ENABLED = true;

    private static boolean isActive() {
        return REPLACEMENT_ENABLED && ChiseledEnchantingTableData.bookshelfReplacementEnabled();
    }

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
        if (!isActive()) return currentBlockInfo;
        BlockState state = currentBlockInfo.state();
        if (!state.is(Blocks.BOOKSHELF)) return currentBlockInfo;
        BlockState chiseled = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        return new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), chiseled, null);
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor world,
        BlockPos pos,
        BlockPos pivot,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> currentBlockInfos,
        StructurePlaceSettings data
    ) {
        if (!isActive()) return currentBlockInfos;

        long seed = worldSeed(world);
        HolderGetter<Enchantment> enchantments = null;
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(currentBlockInfos.size());
        for (StructureTemplate.StructureBlockInfo info : currentBlockInfos) {
            if (!info.state().is(Blocks.CHISELED_BOOKSHELF) || info.nbt() != null) {
                result.add(info);
                continue;
            }
            if (enchantments == null) {
                enchantments = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            }
            RandomSource random = RandomSource.create(seedFor(seed, info.pos()));
            BookshelfBookFiller.Fill fill = BookshelfBookFiller.computeFill(info.state(), random, enchantments);
            result.add(new StructureTemplate.StructureBlockInfo(
                info.pos(),
                fill.state(),
                BookshelfBookFiller.toBlockEntityTag(fill.items(), world.registryAccess())
            ));
            markForFacingPass(world, info.pos());
        }
        return result;
    }

    public static void postProcessBookshelf(ServerLevel level, BlockPos pos) {
        if (!isActive()) return;
        RandomSource random = RandomSource.create(seedFor(level.getSeed(), pos));
        BookshelfBookFiller.applyToPlacedBookshelf(level, pos, random);
    }

    public static void postProcessFilledBookshelf(ServerLevel level, BlockPos pos) {
        if (!isActive()) return;
        BookshelfBookFiller.applyFacingToPlacedBookshelf(level, pos);
    }

    private static long seedFor(long worldSeed, BlockPos pos) {
        return worldSeed
            ^ ((long) pos.getX() * 341873128712L)
            ^ ((long) pos.getY() * 132897987541L)
            ^ pos.getZ();
    }

    private static long worldSeed(ServerLevelAccessor level) {
        return level instanceof WorldGenLevel worldGenLevel
            ? worldGenLevel.getSeed()
            : level.getLevel().getSeed();
    }

    private static void markForFacingPass(ServerLevelAccessor level, BlockPos pos) {
        if (!(level instanceof WorldGenLevel)) return;
        try {
            ChunkAccess chunk = level.getChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()),
                ChunkStatus.EMPTY,
                false
            );
            if (chunk instanceof ProtoChunk protoChunk) {
                protoChunk.markPosForPostprocessing(pos);
            }
        } catch (RuntimeException exception) {
            ChiseledEnchantingTable.LOGGER.debug(
                "chiseled-enchanting-table: skipped bookshelf facing pass at {} ({})",
                pos, exception.toString()
            );
        }
    }
}
