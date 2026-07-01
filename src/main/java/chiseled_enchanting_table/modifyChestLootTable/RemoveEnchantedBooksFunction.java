package chiseled_enchanting_table.modifyChestLootTable;

import com.mojang.serialization.MapCodec;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootFunction;
import net.minecraft.world.level.storage.loot.functions.LootFunctionType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class RemoveEnchantedBooksFunction implements LootFunction {
    // Singleton instance since this is a static function with no parameters
    public static final RemoveEnchantedBooksFunction INSTANCE = new RemoveEnchantedBooksFunction();
    
    // The function type - will be initialized in the register method
    public static LootFunctionType<RemoveEnchantedBooksFunction> TYPE;
    
    private RemoveEnchantedBooksFunction() {
        // Private constructor to enforce singleton
    }
    
    @Override
    public LootFunctionType<RemoveEnchantedBooksFunction> getType() {
        return TYPE;
    }
    
    @Override
    public ItemStack apply(ItemStack stack, LootContext ctx) {
        var loreComponent = stack.get(DataComponents.LORE);
        // Check if the item is an enchanted book - if so, return empty stack
        if (stack.isOf(Items.ENCHANTED_BOOK) && loreComponent.lines().size() == 0) {
            return ItemStack.EMPTY; // Return an empty stack to remove the item
        }
        
        // Otherwise return the original stack
        return stack;
    }
    


    public static final MapCodec<RemoveEnchantedBooksFunction> CODEC = MapCodec.unit(RemoveEnchantedBooksFunction.INSTANCE);
    
 
    // Register this function type
    public static void register() {
        TYPE = Registry.register(
            Registries.LOOT_FUNCTION_TYPE,
            ChiseledEnchantingTable.identifier("remove_enchanted_books"),
            new LootFunctionType<>(CODEC)
        );
    }

}