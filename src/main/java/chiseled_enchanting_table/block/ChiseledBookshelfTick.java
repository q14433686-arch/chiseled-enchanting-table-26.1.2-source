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
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(sgCfg)) {
            BlockPos candidate = shelfPos.offset(offset);
            if (!level.hasChunkAt(candidate)) continue;
            if (level.getBlockState(candidate).is(BlockRegistry.CHISELED_ENCHANTING_TABLE)) {
                double d = offset.distSqr(BlockPos.ZERO);
                if (d < bestDist) {
                    bestDist = d;
                    best = candidate.immutable();
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
        // Audit MIXIN-05: skip block-entity lookup, 343-block table scan, and S2C particles when
        // no player is within ambient perception range of the library.
        if (!level.hasNearbyAlivePlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 24.0D)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) return;

        int slot = random.nextInt(ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE);
        var picked = shelf.getItem(slot);
        if (!picked.is(Items.ENCHANTED_BOOK)) return;
        boolean locked = chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(picked);

        Direction facing = state.getValue(ChiseledBookShelfBlock.FACING);
        Direction right = facing.getCounterClockWise();
        double[] coords = chiseled_enchanting_table.mechanics.resonance.TomeParticleEffects
            .slotWorldCoordinates(pos, facing, slot);
        double x = coords[0];
        double y = coords[1];
        double z = coords[2];

        if (locked) {
            level.sendParticles(ParticleTypes.WITCH, x, y + 0.05D, z, 5, 0.05D, 0.05D, 0.05D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 3, 0.03D, 0.03D, 0.03D, 0.01D);
        }

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
