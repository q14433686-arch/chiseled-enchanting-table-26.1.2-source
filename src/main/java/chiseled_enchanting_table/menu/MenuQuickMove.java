package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.HashSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import static chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler.COST_SLOT;
import static chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler.ENCHANTABLE_SLOT;

/** Quick-move routing owns inventory mutations; narrow access keeps menu internals private. */
final class MenuQuickMove {
    private MenuQuickMove() {}
    interface Access {
        int slotCount();
        Slot slot(int index);
        boolean ritualActive();
        boolean hasTransformation(ItemStack stack);
        void insertCost(ItemStack stack);
        void changed();
    }

    static boolean handle(Access access, Player owner, int slotIndex, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (actionType == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE) {
            if (slotIndex < 0 || slotIndex >= access.slotCount()) return true;
            Slot sourceSlot = access.slot(slotIndex);
            if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) return true;
            ItemStack clickedStack = sourceSlot.getItem();
            if (clickedStack.isEmpty()) return true;

            if (slotIndex == COST_SLOT || slotIndex == ENCHANTABLE_SLOT) {
                if (slotIndex == ENCHANTABLE_SLOT && access.ritualActive()) return true;
                ItemStack moving = clickedStack.copy();
                sourceSlot.set(ItemStack.EMPTY);
                chiseled_enchanting_table.utils.InventoryUtils.insertItem(owner, moving, java.util.Optional.empty());
                access.changed();
                return true;
            }

            Slot targetSlot = access.slot(ENCHANTABLE_SLOT);
            boolean ritualLocked = access.ritualActive();

            if (!ritualLocked && targetSlot.mayPlace(clickedStack)) {
                ItemStack existingTarget = targetSlot.getItem();
                int maxTargetAllowed = Math.max(1, targetSlot.getMaxStackSize(clickedStack));

                if (!existingTarget.isEmpty()
                    && maxTargetAllowed > 1
                    && ItemStack.isSameItemSameComponents(clickedStack, existingTarget)
                    && existingTarget.getCount() < maxTargetAllowed) {
                    int move = Math.min(maxTargetAllowed - existingTarget.getCount(), clickedStack.getCount());
                    ItemStack updatedTarget = existingTarget.copyWithCount(existingTarget.getCount() + move);
                    ItemStack remainingSource = clickedStack.getCount() > move
                        ? clickedStack.copyWithCount(clickedStack.getCount() - move)
                        : ItemStack.EMPTY;
                    targetSlot.set(updatedTarget);
                    sourceSlot.set(remainingSource);
                    access.changed();
                    return true;

                }

                ItemStack clickedStackNoEnchant = clickedStack.copyWithCount(1);
                EnchantmentHelper.updateEnchantments(clickedStackNoEnchant, m -> {
                    var keys = new java.util.HashSet<>(m.keySet());
                    for (var k : keys) m.set(k, 0);
                });
                boolean isBook = clickedStackNoEnchant.is(Items.BOOK) || clickedStackNoEnchant.is(Items.ENCHANTED_BOOK);
                boolean hasExistingEnchants = !EnchantmentHelper.getEnchantmentsForCrafting(clickedStack).isEmpty();
                boolean isDedicatedTransformInput = clickedStack.is(BlockRegistry.DEPLETED_TOME)
                    || ChiseledEnchantingTableData.isResidualBook(clickedStack)
                    || (!targetSlot.hasItem() && access.hasTransformation(clickedStack));

                boolean wantsTargetSlot = clickedStackNoEnchant.isEnchantable()
                    || isDedicatedTransformInput
                    || (hasExistingEnchants && !isBook && !targetSlot.hasItem())
                    || (isBook && !targetSlot.hasItem());

                if (wantsTargetSlot && (!targetSlot.hasItem() || targetSlot.mayPickup(player))) {
                    ItemStack displaced = existingTarget.isEmpty() ? ItemStack.EMPTY : existingTarget.copy();
                    int moveCount = Math.min(maxTargetAllowed, clickedStack.getCount());
                    ItemStack placed = clickedStack.copyWithCount(moveCount);
                    ItemStack remainingSource = clickedStack.getCount() > moveCount
                        ? clickedStack.copyWithCount(clickedStack.getCount() - moveCount)
                        : ItemStack.EMPTY;

                    targetSlot.set(placed);
                    if (displaced.isEmpty()) {
                        sourceSlot.set(remainingSource);
                    } else if (remainingSource.isEmpty()) {
                        sourceSlot.set(displaced);
                    } else {
                        sourceSlot.set(remainingSource);
                        int containerSlot = sourceSlot.getContainerSlot();
                        java.util.Optional<Integer> preferredSlot = (containerSlot >= 0 && containerSlot < 36)
                            ? java.util.Optional.of(containerSlot)
                            : java.util.Optional.empty();
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(owner, displaced, preferredSlot);
                    }
                    access.changed();
                    return true;
                }
            } else {
                // While a ritual is active, never route enchantable gear or blank books into COST_SLOT by accident unless valid cost!
                ItemStack probe = clickedStack.copyWithCount(1);
                EnchantmentHelper.updateEnchantments(probe, m -> {
                    var keys = new java.util.HashSet<>(m.keySet());
                    for (var k : keys) m.set(k, 0);
                });
                if (probe.isEnchantable() || clickedStack.is(BlockRegistry.DEPLETED_TOME)) {
                    return true;
                }
            }

            Slot costSlotObj = access.slot(COST_SLOT);
            if (!costSlotObj.mayPlace(clickedStack)) {
                return true;
            }
            if (!costSlotObj.hasItem()) {
                access.insertCost(clickedStack);
            } else {
                ItemStack costSlotStack = costSlotObj.getItem();
                if (ItemStack.isSameItemSameComponents(clickedStack, costSlotStack)) {
                    access.insertCost(clickedStack);
                } else if (costSlotObj.mayPickup(player)) {
                    int containerSlot = sourceSlot.getContainerSlot();
                    java.util.Optional<Integer> preferredSlot = (containerSlot >= 0 && containerSlot < 36)
                        ? java.util.Optional.of(containerSlot)
                        : java.util.Optional.empty();
                    ItemStack returning = costSlotStack.copy();
                    int maxCostAllowed = Math.max(1, costSlotObj.getMaxStackSize(clickedStack));
                    int moveCount = Math.min(maxCostAllowed, clickedStack.getCount());
                    ItemStack placedCost = clickedStack.copyWithCount(moveCount);
                    ItemStack remainingSource = clickedStack.getCount() > moveCount
                        ? clickedStack.copyWithCount(clickedStack.getCount() - moveCount)
                        : ItemStack.EMPTY;

                    costSlotObj.set(placedCost);
                    if (remainingSource.isEmpty()) {
                        sourceSlot.set(returning);
                    } else {
                        sourceSlot.set(remainingSource);
                        chiseled_enchanting_table.utils.InventoryUtils.insertItem(owner, returning, preferredSlot);
                    }
                }
            }
            access.changed();
            return true;
        }
        return false;
        }
}
