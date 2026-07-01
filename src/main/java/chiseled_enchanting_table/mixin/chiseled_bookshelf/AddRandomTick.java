package chiseled_enchanting_table.mixin.chiseled_bookshelf;

import chiseled_enchanting_table.block.ChiseledBookshelfTick;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Restores the upstream ambient enchanted-book effect for vanilla chiseled bookshelves.
 *
 * <p>The vanilla block is not randomly ticking by default, so this mixin adds an override for the
 * random-tick predicate and the tick body. The actual effect remains in Java mechanism code
 * ({@link ChiseledBookshelfTick}); no tuning tables are hardcoded here.</p>
 */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class AddRandomTick {
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ChiseledBookshelfTick.triggerParticleAndSoundIfEnchantedBook(state, level, pos, random);
    }
}
