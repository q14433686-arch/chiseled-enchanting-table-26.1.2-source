package chiseled_enchanting_table.utils;

import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class InventoryUtils {
    public static ItemStack insertItem(Player player, ItemStack stackToInsert, Optional<Integer> priority_slot) {
        if (stackToInsert.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Container inventory = player.getInventory();
        ItemStack remainingStack = stackToInsert.copy();
        int maxStackSize = Math.max(1, remainingStack.getMaxStackSize());

        for (int i = 0; i < 36; ++i) {
            if (remainingStack.isEmpty()) {
                break;
            }
            ItemStack currentStack = inventory.getItem(i);
            if (!currentStack.isEmpty() && ItemStack.isSameItemSameComponents(currentStack, remainingStack)) {
                int space = currentStack.getMaxStackSize() - currentStack.getCount();
                if (space > 0) {
                    int toTransfer = Math.min(remainingStack.getCount(), space);
                    currentStack.grow(toTransfer);
                    remainingStack.shrink(toTransfer);
                    inventory.setItem(i, currentStack);
                }
            }
        }

        if (!remainingStack.isEmpty()
            && priority_slot.isPresent()
            && inventory.getItem(priority_slot.get()).isEmpty()) {
            int put = Math.min(remainingStack.getCount(), maxStackSize);
            ItemStack piece = remainingStack.copy();
            piece.setCount(put);
            inventory.setItem(priority_slot.get(), piece);
            remainingStack.shrink(put);
        }

        if (!remainingStack.isEmpty()) {
            for (int i = 0; i < 36 && !remainingStack.isEmpty(); ++i) {
                ItemStack currentStack = inventory.getItem(i);
                if (currentStack.isEmpty()) {
                    int put = Math.min(remainingStack.getCount(), maxStackSize);
                    ItemStack piece = remainingStack.copy();
                    piece.setCount(put);
                    inventory.setItem(i, piece);
                    remainingStack.shrink(put);
                }
            }
        }

        while (!remainingStack.isEmpty()) {
            int dropCount = Math.min(remainingStack.getCount(), maxStackSize);
            ItemStack piece = remainingStack.copy();
            piece.setCount(dropCount);
            player.drop(piece, false);
            remainingStack.shrink(dropCount);
        }

        return remainingStack;
    }
}
