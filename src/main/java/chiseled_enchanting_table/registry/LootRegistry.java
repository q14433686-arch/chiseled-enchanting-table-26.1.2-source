package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.loot.EnchantedBookLootInjector;
import chiseled_enchanting_table.mechanics.loot.RemoveEnchantedBooksFunction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Registers the port's loot-item function MapCodec(s) into the vanilla
 * {@code BuiltInRegistries.LOOT_FUNCTION_TYPE} registry and wires up the runtime injector.
 *
 * <p>In 26.1.2 the "loot function type" registry stores {@code MapCodec<? extends LootItemFunction>}
 * directly — there is no wrapper {@code LootItemFunctionType} class any more, so we register the
 * MapCodec instances straight from the function classes' {@code MAP_CODEC} statics.</p>
 *
 * <p>Only one function type is registered: {@code chiseled_enchanting_table:remove_enchanted_books}
 * (the utility for data packs that want to strip vanilla enchanted books from a specific table).
 * The bulk of the injection uses vanilla {@code SetEnchantmentsFunction} + {@code SetLoreFunction}
 * so there is no need for a bespoke injector-side codec.</p>
 */
public final class LootRegistry {
    public static MapCodec<? extends LootItemFunction> REMOVE_ENCHANTED_BOOKS;

    private LootRegistry() {}

    public static void init() {
        REMOVE_ENCHANTED_BOOKS = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            ChiseledEnchantingTable.identifier("remove_enchanted_books"),
            RemoveEnchantedBooksFunction.MAP_CODEC
        );
        EnchantedBookLootInjector.init();
    }
}
