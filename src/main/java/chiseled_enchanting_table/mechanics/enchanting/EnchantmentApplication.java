package chiseled_enchanting_table.mechanics.enchanting;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.utils.Advancement;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashSet;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * Real-enchantment gameplay mechanic for the chiseled enchanting menu: applying unlocked
 * enchantments, and transferring an enchantment from an enchanted book.
 */
public final class EnchantmentApplication {
    private EnchantmentApplication() {}

    /** Apply one of the menu's unlocked enchantments (or an active Grand Library Treasure Epiphany) to the enchantable item. */
    public static boolean apply(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Level world = handler.world();
        ItemStack enchantable_item = handler.get_enchantable_item();
        if (chiseled_enchanting_table.data.ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), world);
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(enchantable_item))) {
            return false;
        }
        int existingSameLevel = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item).getLevel(enchantment);
        // Never allow accidental same-level or downgrade via direct enchant (downgrades are free & grant XP in Cleanse mode).
        if (existingSameLevel >= enchantment_level) {
            return false;
        }

        boolean exist = false;
        if (payload.option_type() == chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType.TREASURE_EPIPHANY) {
            exist = handler.matchesActiveTreasureEpiphany(payload);
        } else {
            for (EnchantmentWithLevel e : handler.unlocked_enchantements) {
                if (e.enchantment_id().equals(payload.enchantment_id()) && e.enchantment_level() == enchantment_level) {
                    exist = true;
                    break;
                }
            }
            if (!exist && existingSameLevel >= 1) {
                exist = handler.canDirectUpgradeExistingEnchantment(enchantment, payload.enchantment_id(), existingSameLevel, enchantment_level);
            }
        }
        if (!exist) {
            return false;
        }
        if (!handler.applyEnchantementCondition(payload)) {
            return false;
        }

        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item));
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK))) {
            var keys = new HashSet<>(newEnchantmentsComponent.keySet());
            for (var key : keys) {
                if (!Enchantment.areCompatible(key, enchantment)) {
                    newEnchantmentsComponent.set(key, 0);
                }
            }
        }
        newEnchantmentsComponent.set(enchantment, enchantment_level);

        if (enchantable_item.is(Items.BOOK)) {
            // Create a 100% canonical vanilla Enchanted Book so same-enchant books always stack cleanly up to 64!
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, 1);
            var customName = enchantable_item.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (customName != null) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, customName);
            }
            handler.setEnchantableSlot(stack);
            enchantable_item = handler.get_enchantable_item();
        }

        ItemStack target = enchantable_item;
        EnchantmentHelper.updateEnchantments(target, m -> {
            var mKeys = new HashSet<>(m.keySet());
            for (var k : mKeys) m.set(k, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });

        boolean consumedEpiphany = payload.option_type() == chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType.TREASURE_EPIPHANY;
        handler.advanceTableEpiphanySeed(consumedEpiphany);
        finish(handler);
        if (consumedEpiphany) {
            Advancement.give(handler.player, "inscribe_epiphany");
        }
        if (target.is(Items.ENCHANTED_BOOK)) {
            Advancement.give(handler.player, "enchant_to_book");
            Advancement.checkBookMilestones(handler.player, world, target);
        }
        return true;
    }

    /** Transfer an enchantment from the enchanted book in the cost slot to the enchantable item. */
    public static boolean applyFromBook(ChiseledEnchantingTableScreenHandler handler, ApplyEnchantmentPayload payload) {
        Level world = handler.world();
        ItemStack enchanted_book = handler.get_cost_item();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book);
        boolean exists = false;
        for (var entry : enchantments.entrySet()) {
            if (ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id()) && entry.getIntValue() == payload.enchantment_level()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            return false;
        }

        ItemStack enchantable_item = handler.get_enchantable_item();
        if (chiseled_enchanting_table.data.ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            return false;
        }
        int enchantment_level = payload.enchantment_level();
        Holder<Enchantment> enchantment = ChiseledEnchantingTableScreenHandler.IdentifierToEnchantment(payload.enchantment_id(), world);
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchantment.value().canEnchant(enchantable_item))) {
            return false;
        }

        ItemEnchantments.Mutable new_enchanted_book_enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (!ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(entry.getKey()).equals(payload.enchantment_id())) {
                new_enchanted_book_enchants.set(entry.getKey(), entry.getIntValue());
            }
        }
        EnchantmentHelper.updateEnchantments(enchanted_book, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var entry : new_enchanted_book_enchants.keySet()) m.set(entry, new_enchanted_book_enchants.getLevel(entry));
        });

        if (EnchantmentHelper.getEnchantmentsForCrafting(enchanted_book).isEmpty()) {
            // Drained source book becomes a stackable Depleted Tome requiring Blank Overwrite before reuse.
            handler.setCostSlot(new ItemStack(chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME, 1));
        }

        ItemEnchantments.Mutable newEnchantmentsComponent = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item));
        if (!(enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK))) {
            var keys = new HashSet<>(newEnchantmentsComponent.keySet());
            for (var key : keys) {
                if (!Enchantment.areCompatible(key, enchantment)) {
                    newEnchantmentsComponent.set(key, 0);
                }
            }
        }
        newEnchantmentsComponent.set(enchantment, enchantment_level);

        if (enchantable_item.is(Items.BOOK)) {
            ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, 1);
            var customName = enchantable_item.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (customName != null) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, customName);
            }
            handler.setEnchantableSlot(stack);
            enchantable_item = handler.get_enchantable_item();
        }

        ItemStack target = enchantable_item;
        EnchantmentHelper.updateEnchantments(target, m -> {
            var keys = new HashSet<>(m.keySet());
            for (var k : keys) m.set(k, 0);
            for (var key : newEnchantmentsComponent.keySet()) {
                m.set(key, newEnchantmentsComponent.getLevel(key));
            }
        });

        finish(handler);
        Advancement.give(handler.player, "enchant_from_book");
        if (target.is(Items.ENCHANTED_BOOK)) {
            Advancement.give(handler.player, "transfer_enchanted_book");
            Advancement.checkBookMilestones(handler.player, world, target);
        }
        return true;
    }

    private static void finish(ChiseledEnchantingTableScreenHandler handler) {
        Player player = handler.player;
        Level world = handler.world();
        handler.markInventoryChanged();
        player.awardStat(Stats.ENCHANT_ITEM);
        world.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 2.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
