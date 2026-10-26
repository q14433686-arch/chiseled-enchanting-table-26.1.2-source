package chiseled_enchanting_table.data;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.resources.Identifier;

/**
 * Resource identifiers of the datapack JSON tables loaded by
 * {@link ChiseledEnchantingTableData}. Extracted as the bounded, additive part of
 * the data god-class split (2026-10-24 audit): the public record types stay in
 * {@code ChiseledEnchantingTableData} so no API surface moves.
 */
public final class DataIds {

    private DataIds() {}

    public static final Identifier DEFAULT_ENCHANTMENTS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/default_enchantments.json");
    public static final Identifier ENCHANTMENT_COSTS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/enchantment_costs.json");
    public static final Identifier ITEM_TRANSFORMATIONS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/item_transformations.json");
    public static final Identifier LOOT_INJECTIONS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/loot_injections.json");
    public static final Identifier BOOKSHELF_BOOK_POOL_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/bookshelf_book_pool.json");
    public static final Identifier BOOK_UPGRADES_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/book_upgrades.json");
    public static final Identifier TREASURE_EPIPHANY_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/treasure_epiphany.json");
    public static final Identifier DISENCHANTING_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/disenchanting.json");
    public static final Identifier SHELF_GATES_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/shelf_gates.json");
    public static final Identifier LIBRARY_RESONANCE_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/library_resonance.json");
    public static final Identifier PACK_POLICY_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/pack_policy.json");
}
