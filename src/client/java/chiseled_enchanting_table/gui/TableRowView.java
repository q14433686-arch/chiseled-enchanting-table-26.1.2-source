package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Frame data, not a mutable row widget or its event handlers. ItemStack is defensively copied. */
record TableRowView(boolean discovery_locked, Identifier enchantment_id, boolean shelf_locked,
    Holder<Enchantment> enchantment, int enchant_level, int required_bookshelves,
    boolean is_category_header, boolean cleanse_theme, String category_title, String category_badge,
    OptionType option_type, boolean is_transformation, ItemStack enchantable_item, OptionalInt existingSameLevel,
    int conflict_refund_points, XpRequirement xp_requirement, boolean xpConditionMet) {
    TableRowView { enchantable_item = enchantable_item == null ? null : enchantable_item.copy(); }
}
