package chiseled_enchanting_table.modifyChestLootTable.abandoned_mineshaft;

import java.util.List;

import com.mojang.serialization.MapCodec;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LoreComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.functions.LootFunction;
import net.minecraft.world.level.storage.loot.functions.LootFunctionType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

public class DepthBasedEfficiency implements LootFunction {
    // Singleton instance since this is a static function with no parameters
    public static final DepthBasedEfficiency INSTANCE = new DepthBasedEfficiency();

    // The function type - will be initialized in the register method
    public static LootFunctionType<DepthBasedEfficiency> TYPE;

    private DepthBasedEfficiency() {
        // Private constructor to enforce singleton
    }

    @Override
    public LootFunctionType<DepthBasedEfficiency> getType() {
        return TYPE;
    }

    // Mineshaft can go between Y Sea level - 10 to world bottom

    // We want toto ince

    @Override
    public ItemStack apply(ItemStack stack, LootContext ctx) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return stack;
        var world = ctx.getWorld();
        var efficiency = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(Identifier.withDefaultNamespace("efficiency")).get();
        var mineshaftTopY = world.getSeaLevel();
        var mineshaftBottomY = world.getBottomY();
        var currentY = ctx.get(LootContextParameters.ORIGIN).y;
        // Calculate a rounded value between 2 and 5 based on currentY's proximity to mineshaftTopY and mineshaftBottomY
        var roundedValue = Math.round(2 + (float)(mineshaftTopY - currentY) / (mineshaftTopY - mineshaftBottomY) * 3);
        var v2_5 = Math.max(2, Math.min(5, roundedValue)); // Clamp the value between 2 and 5
        
        stack.addEnchantment(efficiency, v2_5);
        var lore = new LoreComponent(List.of(
            Component.literal("Chiseled Enchanting Table")
            .styled(style -> style.withColor(0x800080))
        ));
        stack.set(DataComponents.LORE, lore);
        return stack;
    }

    public static final MapCodec<DepthBasedEfficiency> CODEC = MapCodec.unit(DepthBasedEfficiency.INSTANCE);

    // Register this function type
    public static void register() {
        TYPE = Registry.register(
                Registries.LOOT_FUNCTION_TYPE,
                ChiseledEnchantingTable.identifier("reprocess_abandoned_mineshaft_loot"),
                new LootFunctionType<>(CODEC));
    }
    
    public static class DepthBasedEfficiencyBuilder implements LootFunction.Builder   {
        public static final DepthBasedEfficiencyBuilder INSTANCE = new DepthBasedEfficiencyBuilder();

        public DepthBasedEfficiency build() {
            return DepthBasedEfficiency.INSTANCE;
        }
    }

}