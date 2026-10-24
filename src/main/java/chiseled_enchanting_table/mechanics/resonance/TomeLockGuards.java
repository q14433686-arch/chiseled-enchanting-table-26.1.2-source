package chiseled_enchanting_table.mechanics.resonance;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

/** Server-side world guards that keep locked tomes physically inside their shelves. */
public final class TomeLockGuards {
    private TomeLockGuards() {}

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !state.is(Blocks.CHISELED_BOOKSHELF)) {
                return true;
            }
            if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)) {
                return true;
            }
            if (player.getAbilities().instabuild) {
                return true;
            }
            for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
                if (TomeLockData.isEffectivelyLocked(shelf.getItem(slot))) {
                    player.displayClientMessage(
                        Component.translatable("message.chiseled_enchanting_table.tome_lock.shelf_break_blocked"),
                        true
                    );
                    return false;
                }
            }
            return true;
        });
    }

    /** True when any slot in {@code shelf} holds a non-expired locked enchanted book. */
    public static boolean shelfHasLockedBook(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) {
            return false;
        }
        for (int slot = 0; slot < ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE; slot++) {
            var stack = shelf.getItem(slot);
            if (stack.is(Items.ENCHANTED_BOOK) && TomeLockData.isEffectivelyLocked(stack)) {
                return true;
            }
        }
        return false;
    }
}
