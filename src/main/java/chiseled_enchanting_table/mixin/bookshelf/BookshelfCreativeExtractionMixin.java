package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Authorize Creative extraction only at vanilla's actual player-removal operation, not automation. */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class BookshelfCreativeExtractionMixin {
    @Inject(method = "removeBook", at = @At("HEAD"), require = 1)
    private static void cet$unlockCreativeExtraction(Level level, BlockPos pos, Player player,
        ChiseledBookShelfBlockEntity shelf, int slot, CallbackInfo ci) {
        // UseBlockCallback returning PASS alone cannot bypass the container's lock mixin.
        // Do not unlock on a generic right-click: inserting/using a held item is not extraction.
        if (!level.isClientSide() && player.getAbilities().instabuild
            && slot >= 0 && slot < shelf.getContainerSize() && TomeLockData.unlock(shelf.getItem(slot))) {
            shelf.setChanged();
        }
    }
}
