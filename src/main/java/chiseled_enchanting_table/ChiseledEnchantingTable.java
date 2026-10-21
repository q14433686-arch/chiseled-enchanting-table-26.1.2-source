package chiseled_enchanting_table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.EnchantCostSync;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.LootRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class ChiseledEnchantingTable implements ModInitializer {
    public static final String MOD_ID = "chiseled_enchanting_table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Chiseling the enchant system!");

        // TODO P3: re-evaluate the old Item.components accessor that changed enchanted books to stack to 64.
        // TODO P3: restore loot-table and structure-processor hooks after source/API migration.

        ChiseledEnchantingTableData.init();
        EnchantCostSync.init();
        BlockRegistry.init();
        EntityRegistry.init();
        ScreenHandlerRegistry.init();
        StructureProcessorRegistry.init();
        LootRegistry.init();
    }

    public static Identifier identifier(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
