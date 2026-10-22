package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Book-upgrade routes hosted by the chiseled enchanting table. */
public final class BookUpgradeApplication {
    private BookUpgradeApplication() {}

    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        if (payload.option_type() == OptionType.BOOK_COMBINE_UPGRADE) {
            return applyCombine(handler, payload);
        }
        if (payload.option_type() == OptionType.BOOK_RESEARCH_UPGRADE) {
            return applyResearch(handler, payload);
        }
        return false;
    }

    private static boolean applyCombine(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        if (!ChiseledEnchantingTableData.bookCombineEnabled()) return false;
        ItemStack targetBook = handler.get_enchantable_item();
        ItemStack costBook = handler.get_cost_item();
        if (!targetBook.is(Items.ENCHANTED_BOOK) || !costBook.is(Items.ENCHANTED_BOOK)) return false;

        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), handler.world());
        int targetLevel = payload.enchantment_level();
        int sourceLevel = targetLevel - 1;
        if (sourceLevel < 1 || targetLevel > enchantment.value().getMaxLevel()) return false;

        ItemEnchantments targetEnchants = EnchantmentHelper.getEnchantmentsForCrafting(targetBook);
        ItemEnchantments costEnchants = EnchantmentHelper.getEnchantmentsForCrafting(costBook);
        if (targetEnchants.getLevel(enchantment) != sourceLevel || costEnchants.getLevel(enchantment) != sourceLevel) return false;

        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), targetLevel);
        int xpCost = handler.getEnchantmentXpLevelCost(ewl, OptionType.BOOK_COMBINE_UPGRADE);
        if (!canAffordXp(handler, xpCost)) return false;
        deductXp(handler, xpCost);

        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(targetEnchants);
        for (var entry : costEnchants.entrySet()) {
            Holder<Enchantment> incoming = entry.getKey();
            int current = merged.getLevel(incoming);
            int incomingLevel = entry.getIntValue();
            int mergedLevel = current == incomingLevel
                ? Math.min(incoming.value().getMaxLevel(), incomingLevel + 1)
                : Math.max(current, incomingLevel);
            merged.set(incoming, mergedLevel);
        }
        merged.set(enchantment, targetLevel);
        writeEnchantments(targetBook, merged);

        handler.setCostSlot(costBook.transmuteCopy(Items.BOOK, 1));
        finish(handler);
        Advancement.give(handler.player, "upgrade_enchanted_book");
        Advancement.checkBookMilestones(handler.player, handler.world(), handler.get_enchantable_item());
        return true;
    }

    private static boolean applyResearch(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        if (!ChiseledEnchantingTableData.bookResearchEnabled()) return false;
        ItemStack targetBook = handler.get_enchantable_item();
        if (!targetBook.is(Items.ENCHANTED_BOOK)) return false;

        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), handler.world());
        int targetLevel = payload.enchantment_level();
        int sourceLevel = targetLevel - 1;
        if (sourceLevel < 1 || targetLevel > enchantment.value().getMaxLevel()) return false;

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(targetBook);
        if (current.getLevel(enchantment) != sourceLevel) return false;

        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), targetLevel);
        int xpCost = handler.getEnchantmentXpLevelCost(ewl, OptionType.BOOK_RESEARCH_UPGRADE);
        List<ItemStack> matchedBundle = handler.findMatchingAffordableCostBundle(ewl, OptionType.BOOK_RESEARCH_UPGRADE);

        // Validate BOTH XP and any player-chosen material alternative atomically before consuming either.
        if (!canAffordXp(handler, xpCost) || (!handler.player.getAbilities().instabuild && matchedBundle == null)) return false;
        deductXp(handler, xpCost);
        handler.deductMatchedBundle(matchedBundle);

        ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(current);
        upgraded.set(enchantment, targetLevel);
        writeEnchantments(targetBook, upgraded);
        finish(handler);
        Advancement.give(handler.player, "upgrade_enchanted_book");
        Advancement.checkBookMilestones(handler.player, handler.world(), handler.get_enchantable_item());
        return true;
    }

    private static void writeEnchantments(ItemStack stack, ItemEnchantments.Mutable enchantments) {
        EnchantmentHelper.updateEnchantments(stack, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var key : keys) m.set(key, 0);
            for (var key : enchantments.keySet()) {
                m.set(key, enchantments.getLevel(key));
            }
        });
    }

    private static boolean canAffordXp(ChiseledEnchantingTableScreenHandler handler, int levelCost) {
        return handler.player.getAbilities().instabuild || handler.player.experienceLevel >= levelCost;
    }

    private static void deductXp(ChiseledEnchantingTableScreenHandler handler, int levelCost) {
        if (!handler.player.getAbilities().instabuild && levelCost > 0) {
            handler.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(handler.player, levelCost));
        }
    }

    private static boolean canAffordItems(ChiseledEnchantingTableScreenHandler handler, List<ItemStack> costs) {
        if (handler.player.getAbilities().instabuild) return true;
        if (costs.isEmpty()) return false;
        ItemStack primary = costs.getFirst();
        ItemStack costSlot = handler.get_cost_item();
        if (primary.isEmpty() || !ItemStack.isSameItemSameComponents(primary, costSlot) || costSlot.getCount() < primary.getCount()) return false;
        for (int i = 1; i < costs.size(); i++) {
            if (countInInventory(handler, costs.get(i)) < costs.get(i).getCount()) return false;
        }
        return true;
    }

    private static void deductItems(ChiseledEnchantingTableScreenHandler handler, List<ItemStack> costs) {
        if (handler.player.getAbilities().instabuild) return;
        ItemStack primary = costs.getFirst();
        handler.get_cost_item().shrink(primary.getCount());
        for (int i = 1; i < costs.size(); i++) {
            consumeFromInventory(handler, costs.get(i).copy());
        }
        handler.player.getInventory().setChanged();
    }

    private static int countInInventory(ChiseledEnchantingTableScreenHandler handler, ItemStack required) {
        int found = 0;
        for (int slot = 0; slot < handler.player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = handler.player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(candidate, required)) found += candidate.getCount();
        }
        return found;
    }

    private static void consumeFromInventory(ChiseledEnchantingTableScreenHandler handler, ItemStack required) {
        for (int slot = 0; slot < handler.player.getInventory().getContainerSize() && !required.isEmpty(); slot++) {
            ItemStack candidate = handler.player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(candidate, required)) {
                int consumed = Math.min(candidate.getCount(), required.getCount());
                candidate.shrink(consumed);
                required.shrink(consumed);
            }
        }
    }

    private static void finish(ChiseledEnchantingTableScreenHandler handler) {
        handler.markInventoryChanged();
        handler.player.awardStat(Stats.ENCHANT_ITEM);
        handler.world().playSound(null, handler.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, handler.world().getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
