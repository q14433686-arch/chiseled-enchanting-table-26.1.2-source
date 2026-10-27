package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Frame-scoped tooltip inputs; deliberately has no Screen, transport or mutation capability. */
record TableTooltipContext(ChiseledEnchantingTableScreenHandler menu, TableListRead list,
    Font font, Level level, Set<Region> regions, int tomeButton, int ritualButton,
    boolean isCleanseTab, boolean isTomeTab, boolean isDisenchantAllConfirmPending, boolean canCleanseAllNow,
    Function<ItemStack, List<Component>> itemTooltip, Supplier<List<Component>> resonance) {
    enum Region { isOverCleanseAllButton, isOverCleanseTab, isOverConfigButton, isOverFreezeButton, isOverGrimoireButton, isOverInscribeTab, isOverRerollButton, isOverSacrificeButton, isOverSanctumPanel, isOverShelfHudBadge, isOverTomeTab }
    TableTooltipContext { regions = Set.copyOf(regions); }
    boolean hovered(Region region) { return regions.contains(region); }
    int countMatching(ItemStack target) {
        if (target == null || target.isEmpty() || menu.player == null) return 0;
        var inventory = menu.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) count += stack.getCount();
        }
        return count;
    }
}
