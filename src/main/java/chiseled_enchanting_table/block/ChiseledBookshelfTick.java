package chiseled_enchanting_table.block;

import chiseled_enchanting_table.registry.BlockRegistry;
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
import org.jetbrains.annotations.Nullable;

/** Server-side ambient effect for vanilla chiseled bookshelves that store enchanted books. */
public final class ChiseledBookshelfTick {
    private ChiseledBookshelfTick() {}

    @Nullable
    private static BlockPos findNearestChiseledTable(ServerLevel level, BlockPos shelfPos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        var sgCfg = chiseled_enchanting_table.data.ChiseledEnchantingTableData.shelfGatesConfig();
        int rx = sgCfg == null ? 3 : Math.max(1, sgCfg.horizontalScanRadius());
        int ry = sgCfg == null ? 2 : Math.max(0, sgCfg.verticalScanRadius());
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rx; dz <= rx; dz++) {
                    BlockPos candidate = shelfPos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate)) continue;
                    if (level.getBlockState(candidate).is(BlockRegistry.CHISELED_ENCHANTING_TABLE)) {
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < bestDist) {
                            bestDist = d;
                            best = candidate.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

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
            0.45F,
            0.85F + random.nextFloat() * 0.30F
        );

        // ParticleTypes.ENCHANT treats (x, y, z) as the DESTINATION (table + (0.5, 2.0, 0.5)) and
        // (dx, dy, dz) as the starting offset toward the bookshelf slot!
        BlockPos tablePos = findNearestChiseledTable(level, pos);
        if (tablePos != null) {
            double destX = tablePos.getX() + 0.5D;
            double destY = tablePos.getY() + 2.0D;
            double destZ = tablePos.getZ() + 0.5D;
            for (int i = 0; i < 4; i++) {
                double jitterSide = (random.nextDouble() - 0.5D) * 0.14D;
                double jitterY = (random.nextDouble() - 0.5D) * 0.12D;
                level.sendParticles(
                    ParticleTypes.ENCHANT,
                    destX,
                    destY,
                    destZ,
                    0,
                    (x + right.getStepX() * jitterSide) - destX,
                    (y + jitterY) - destY,
                    (z + right.getStepZ() * jitterSide) - destZ,
                    1.0D
                );
            }
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
