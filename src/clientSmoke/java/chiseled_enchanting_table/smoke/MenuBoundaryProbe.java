package chiseled_enchanting_table.smoke;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.*;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real server-thread inventory and XP checks, not mocked native registry objects. */
final class MenuBoundaryProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError("economic boundary: " + message);
    }
    static void verify(ChiseledEnchantingTableScreenHandler menu) {
        var player = menu.player;
        var inventory = player.getInventory();
        var saved = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i).copy());
        var savedCost = menu.getCostItem().copy();
        int oldLevel = player.experienceLevel, oldTotal = player.totalExperience;
        float oldProgress = player.experienceProgress;
        boolean oldCreative = player.getAbilities().instabuild;
        try {
            player.getAbilities().instabuild = false;
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
            menu.setCostSlot(new ItemStack(Items.LAPIS_LAZULI, 1));
            inventory.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 5));
            inventory.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 2));
            var duplicateBundle = List.of(new ItemStack(Items.LAPIS_LAZULI, 3),
                new ItemStack(Items.LAPIS_LAZULI, 2), new ItemStack(Items.AMETHYST_SHARD));
            check(menu.isBundleAffordableWithOverflow(duplicateBundle), "overflow/duplicate quote rejected");
            check(menu.getCostItem().getCount() == 1 && inventory.getItem(0).getCount() == 5, "quote mutated inventory");
            menu.deductMatchedBundle(duplicateBundle);
            check(menu.getCostItem().isEmpty(), "primary slot not consumed first");
            check(inventory.getItem(0).getCount() == 1 && inventory.getItem(1).getCount() == 1, "duplicate/overflow debit is not conserved");

            player.experienceLevel = 100;
            player.totalExperience = XpCostConversion.totalXpForLevel(100);
            player.experienceProgress = 0;
            var missing = menu.applyEnchantmentConditionWithReceipt(new ApplyEnchantmentPayload(
                Identifier.parse("chiseled_enchanting_table:missing_boundary_price"), 1));
            check(!missing.accepted() && missing.paidXpPoints() == 0 && missing.paidMaterials().isEmpty(), "missing price did not fail closed");
            check(player.experienceLevel == 100 && inventory.getItem(0).getCount() == 1, "rejected payment mutated state");

            var enchantment = new EnchantmentWithLevel(Identifier.withDefaultNamespace("sharpness"), 1);
            var options = menu.getEffectiveItemCostOptions(enchantment, OptionType.ENCHANTMENT);
            check(!options.isEmpty(), "real fixture has no sharpness price");
            var bundle = ChiseledEnchantingTableData.mergeEquivalentCostBundle(options.getFirst());
            for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
            menu.setCostSlot(bundle.isEmpty() ? ItemStack.EMPTY : bundle.getFirst().copy());
            for (int i = 1; i < bundle.size(); i++) inventory.setItem(i - 1, bundle.get(i).copy());
            int before = XpCostConversion.totalPlayerXpPoints(player);
            var receipt = menu.applyEnchantmentConditionWithReceipt(new ApplyEnchantmentPayload(enchantment.enchantment_id(), 1));
            check(receipt.accepted(), "funded real payment rejected");
            check(receipt.paidXpPoints() == before - XpCostConversion.totalPlayerXpPoints(player), "receipt XP differs from real debit");
            check(receipt.paidMaterials().size() == bundle.size(), "receipt lost material entries");
            for (int i = 0; i < bundle.size(); i++) {
                check(ItemStack.isSameItemSameComponents(receipt.paidMaterials().get(i), bundle.get(i))
                    && receipt.paidMaterials().get(i).getCount() == bundle.get(i).getCount(), "receipt did not preserve paid material copies");
            }
            check(menu.getCostItem().isEmpty(), "payment did not debit the cost slot");
            for (int i = 0; i < inventory.getContainerSize(); i++) check(inventory.getItem(i).isEmpty(), "payment left a required additional material");
            System.out.println("ECONOMIC BOUNDARY PASS: duplicate bundles + slot overflow + read-only quote + missing-price rejection + actual XP/material debit + receipt copies");
        } finally {
            player.getAbilities().instabuild = oldCreative;
            player.experienceLevel = oldLevel;
            player.totalExperience = oldTotal;
            player.experienceProgress = oldProgress;
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            menu.setCostSlot(savedCost);
            inventory.setChanged();
        }
    }
}
