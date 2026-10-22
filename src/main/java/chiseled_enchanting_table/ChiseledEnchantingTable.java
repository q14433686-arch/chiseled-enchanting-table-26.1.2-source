package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModBootstrapConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.EnchantCostSync;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.LootRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.registry.SoundRegistry;
import chiseled_enchanting_table.registry.StructureProcessorRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChiseledEnchantingTable implements ModInitializer {
    public static final String MOD_ID = "chiseled_enchanting_table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Chiseling the enchant system!");

        int bookStackSize = ModBootstrapConfig.load().enchantedBookStackSize();
        if (bookStackSize > 1) {
            DefaultItemComponentEvents.MODIFY.register(context ->
                context.modify(Items.ENCHANTED_BOOK, builder ->
                    builder.set(DataComponents.MAX_STACK_SIZE, bookStackSize)
                )
            );
        }

        ChiseledEnchantingTableData.init();
        EnchantCostSync.init();
        SoundRegistry.init();
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
