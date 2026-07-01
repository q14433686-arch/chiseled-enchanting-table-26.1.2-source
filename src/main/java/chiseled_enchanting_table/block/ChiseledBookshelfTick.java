package chiseled_enchanting_table.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side ambient effect for vanilla chiseled bookshelves that store enchanted books. */
public final class ChiseledBookshelfTick {
    private ChiseledBookshelfTick() {}

    public static void triggerParticleAndSoundIfEnchantedBook(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random
    ) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) return;

        int slot = random.nextInt(ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE);
        if (!shelf.getItem(slot).is(Items.ENCHANTED_BOOK)) return;

        Direction facing = state.getValue(ChiseledBookShelfBlock.FACING);
        Direction right = facing.getClockWise();
        int column = slot % 3;
        int row = slot / 3;

        double sideOffset = (column - 1) * 0.25D;
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.56D + right.getStepX() * sideOffset;
        double y = pos.getY() + (row == 0 ? 0.72D : 0.38D);
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.56D + right.getStepZ() * sideOffset;

        level.playSound(
            null,
            pos,
            SoundEvents.ENCHANTMENT_TABLE_USE,
            SoundSource.BLOCKS,
            0.55F,
            0.85F + random.nextFloat() * 0.30F
        );
        // ENCHANT particles are the same particle family used by vanilla enchanting-table
        // bookshelf streams. In packet form they need a non-zero motion/target vector to be
        // obvious; a simple count-spread packet with near-zero direction can be effectively
        // invisible in-game even though the sound proves the random tick fired. Send a few exact
        // particles drifting out from the shelf face, plus a tiny ENCHANTED_HIT sparkle fallback so
        // the ambient cue remains visible on clients/resource packs where ENCHANT is very subtle.
        for (int i = 0; i < 4; i++) {
            double jitterSide = (random.nextDouble() - 0.5D) * 0.10D;
            double jitterY = (random.nextDouble() - 0.5D) * 0.08D;
            level.sendParticles(
                ParticleTypes.ENCHANT,
                x + right.getStepX() * jitterSide,
                y + jitterY,
                z + right.getStepZ() * jitterSide,
                0,
                facing.getStepX() * 0.35D,
                0.08D + random.nextDouble() * 0.06D,
                facing.getStepZ() * 0.35D,
                1.0D
            );
        }
        level.sendParticles(
            ParticleTypes.ENCHANTED_HIT,
            x,
            y,
            z,
            2,
            0.04D,
            0.04D,
            0.04D,
            0.01D
        );
    }
}
