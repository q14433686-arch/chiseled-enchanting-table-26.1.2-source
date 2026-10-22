package chiseled_enchanting_table.utils;

import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;

public class InventoryUtils {
    public static ItemStack insertItem(Player player, ItemStack stackToInsert, Optional<Integer> priority_slot) {
        if (stackToInsert.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Container inventory = player.getInventory();
        ItemStack remainingStack = stackToInsert.copy();

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

        if (priority_slot.isPresent() && inventory.getItem(priority_slot.get()).isEmpty()) {
            inventory.setItem(priority_slot.get(), remainingStack.copy());
            remainingStack = ItemStack.EMPTY;
        }

        if (!remainingStack.isEmpty()) {
            for (int i = 0; i < 36; ++i) {
                if (remainingStack.isEmpty()) {
                    break;
                }
                ItemStack currentStack = inventory.getItem(i);
                if (currentStack.isEmpty()) {
                    inventory.setItem(i, remainingStack.copy());
                    remainingStack = ItemStack.EMPTY;
                    break; 
                }
            }
        }
        
        if (!remainingStack.isEmpty()) {
            player.drop(remainingStack, false, Prediction.PREDICTED);
            remainingStack = ItemStack.EMPTY;
        }

        return remainingStack;
    }
}
