package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.block.ChiseledBookshelfTick;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Enables random ticks on occupied {@link ChiseledBookShelfBlock} states only (empty decorative
 * shelves incur zero chunk random-tick cost) and delegates to {@link ChiseledBookshelfTick#randomTick}.
 */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class AddRandomTick extends BaseEntityBlock {
    protected AddRandomTick(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        for (var prop : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            if (state.hasProperty(prop) && state.getValue(prop)) {
                return true;
            }
        }
        return super.isRandomlyTicking(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        ChiseledBookshelfTick.triggerParticleAndSoundIfEnchantedBook(state, level, pos, random);
    }
}
