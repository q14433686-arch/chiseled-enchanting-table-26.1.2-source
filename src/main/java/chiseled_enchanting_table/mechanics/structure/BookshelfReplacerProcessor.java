package chiseled_enchanting_table.mechanics.structure;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces vanilla {@link Blocks#BOOKSHELF} generated inside structures with
 * {@link Blocks#CHISELED_BOOKSHELF} when {@code enabled} is {@code true} in
 * {@code bookshelf_book_pool.json} ({@code D7}), using a deterministic position-seeded orientation
 * that avoids neighbor block updates during world generation.
 */
public class BookshelfReplacerProcessor extends StructureProcessor {
    public static final MapCodec<BookshelfReplacerProcessor> CODEC =
        MapCodec.unit(BookshelfReplacerProcessor::new);

    @Override
    @Nullable
    public StructureTemplate.StructureBlockInfo processBlock(
        LevelReader world,
        BlockPos pos,
        BlockPos pivot,
        StructureTemplate.StructureBlockInfo original,
        StructureTemplate.StructureBlockInfo current,
        StructurePlaceSettings data
    ) {
        if (!ChiseledEnchantingTableData.bookshelfReplacementEnabled()
            || !current.state().is(Blocks.BOOKSHELF)) {
            return current;
        }
        var state = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        var facing = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(
            net.minecraft.util.RandomSource.create(current.pos().asLong())
        );
        state = state.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
        return new StructureTemplate.StructureBlockInfo(current.pos(), state, current.nbt());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorRegistry.BOOKSHELF_REPLACER_PROCESSOR_TYPE;
    }
}
