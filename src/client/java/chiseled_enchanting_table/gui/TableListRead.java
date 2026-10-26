package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Explicit hover/selection queries; no Widget reference, row events, scrolling or selection setters. */
interface TableListRead {
    boolean hasEntries();
    TomeBookEntry selectedTomeEntry();
    List<TomeBookEntry> selectedTomes();
    TomeBookEntry hoveredTomeEntry(double x, double y);
    ItemStack hoveredCostItem(double x, double y);
    List<List<ItemStack>> hoveredCostOptions(double x, double y);
    Identifier hoveredEnchantmentId(double x, double y);
    OptionType hoveredOptionType(double x, double y);
    TableRowView hoveredXpBadgeEntry(double x, double y);
    TableRowView hoveredSpecialRowEntry(double x, double y);
    TableRowView hoveredActionRowEntry(double x, double y);
    String displayName(AvailableOption option);
}
