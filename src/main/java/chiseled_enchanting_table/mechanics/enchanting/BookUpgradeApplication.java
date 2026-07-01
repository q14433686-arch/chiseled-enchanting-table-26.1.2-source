package chiseled_enchanting_table.mechanics.enchanting;

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
        if (!chargeXp(handler, handler.getEnchantmentXpLevelCost(ewl, OptionType.BOOK_COMBINE_UPGRADE))) return false;

        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(targetEnchants);
        for (var entry : costEnchants.entrySet()) {
            Holder<Enchantment> incoming = entry.getKey();
            int current = merged.getLevel(incoming);
            int incomingLevel = entry.getIntValue();
            int mergedLevel = current == incomingLevel
                ? Math.min(incoming.value().getMaxLevel(), incomingLevel + 1)
                : Math.max(current, incomingLevel);
            if (canAddToBook(merged, incoming)) {
                merged.set(incoming, mergedLevel);
            }
        }
        merged.set(enchantment, targetLevel);
        writeEnchantments(targetBook, merged);
        // The lower/cost book is the sacrificial knowledge source for this table-hosted
        // anvil-style route. Mirror this mod's book-transfer semantics: after its enchantment is
        // consumed, leave a plain book in the cost slot so the player sees exactly what happened.
        // Do this even in creative/instabuild, because the slot transformation is the visible result
        // of the operation rather than a material affordability check.
        handler.setCostSlot(costBook.transmuteCopy(Items.BOOK, 1));
        finish(handler);
        Advancement.give(handler.player, "upgrade_enchanted_book");
        Advancement.checkBookMilestones(handler.player, handler.world(), handler.get_enchantable_item());
        return true;
    }

    private static boolean applyResearch(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        ItemStack targetBook = handler.get_enchantable_item();
        if (!targetBook.is(Items.ENCHANTED_BOOK)) return false;

        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), handler.world());
        int targetLevel = payload.enchantment_level();
        int sourceLevel = targetLevel - 1;
        if (sourceLevel < 1 || targetLevel > enchantment.value().getMaxLevel()) return false;

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(targetBook);
        if (current.getLevel(enchantment) != sourceLevel) return false;

        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), targetLevel);
        if (!chargeXp(handler, handler.getEnchantmentXpLevelCost(ewl, OptionType.BOOK_RESEARCH_UPGRADE))) return false;
        if (!chargeItems(handler, handler.getEnchantmentItemCosts(ewl, OptionType.BOOK_RESEARCH_UPGRADE))) return false;

        ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(current);
        upgraded.set(enchantment, targetLevel);
        writeEnchantments(targetBook, upgraded);
        finish(handler);
        Advancement.give(handler.player, "upgrade_enchanted_book");
        Advancement.checkBookMilestones(handler.player, handler.world(), handler.get_enchantable_item());
        return true;
    }

    private static boolean canAddToBook(ItemEnchantments.Mutable merged, Holder<Enchantment> incoming) {
        for (Holder<Enchantment> existing : new HashSet<>(merged.keySet())) {
            if (!existing.equals(incoming) && !Enchantment.areCompatible(existing, incoming)) {
                return false;
            }
        }
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

    private static boolean chargeXp(ChiseledEnchantingTableScreenHandler handler, int levelCost) {
        if (handler.player.getAbilities().instabuild) return true;
        if (handler.player.experienceLevel < levelCost) return false;
        handler.player.giveExperiencePoints(-XpCostConversion.pointsForLevelCost(handler.player, levelCost));
        return true;
    }

    private static boolean chargeItems(ChiseledEnchantingTableScreenHandler handler, List<ItemStack> costs) {
        if (handler.player.getAbilities().instabuild) return true;
        if (costs.isEmpty()) return false;
        ItemStack primary = costs.getFirst();
        ItemStack costSlot = handler.get_cost_item();
        if (primary.isEmpty() || !ItemStack.isSameItemSameComponents(primary, costSlot) || costSlot.getCount() < primary.getCount()) return false;
        for (int i = 1; i < costs.size(); i++) {
            if (countInInventory(handler, costs.get(i)) < costs.get(i).getCount()) return false;
        }
        costSlot.shrink(primary.getCount());
        for (int i = 1; i < costs.size(); i++) consumeFromInventory(handler, costs.get(i).copy());
        handler.player.getInventory().setChanged();
        return true;
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
