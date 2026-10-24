package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.loot.ApplyWildBookWearFunction;
import chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics;
import chiseled_enchanting_table.mechanics.loot.DepthBasedEfficiencyFunction;
import chiseled_enchanting_table.mechanics.loot.EnchantedBookLootInjector;
import chiseled_enchanting_table.mechanics.loot.RemoveEnchantedBooksFunction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

public final class LootRegistry {
    public static MapCodec<? extends LootItemFunction> REMOVE_ENCHANTED_BOOKS;
    public static MapCodec<? extends LootItemFunction> DEPTH_BASED_EFFICIENCY;
    public static MapCodec<? extends LootItemFunction> APPLY_WILD_BOOK_WEAR;

    private LootRegistry() {}

    public static void init() {
        REMOVE_ENCHANTED_BOOKS = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            ChiseledEnchantingTable.identifier("remove_enchanted_books"),
            RemoveEnchantedBooksFunction.MAP_CODEC
        );
        DEPTH_BASED_EFFICIENCY = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            ChiseledEnchantingTable.identifier("depth_based_efficiency"),
            DepthBasedEfficiencyFunction.MAP_CODEC
        );
        APPLY_WILD_BOOK_WEAR = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            ChiseledEnchantingTable.identifier("apply_wild_book_wear"),
            ApplyWildBookWearFunction.MAP_CODEC
        );
        EnchantedBookLootInjector.init();
        DepletedTomeWorldMechanics.initChestLootInjection();
    }
}
