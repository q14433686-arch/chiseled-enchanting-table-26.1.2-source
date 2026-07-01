package chiseled_enchanting_table.registry;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class ScreenHandlerRegistry {

    public static final ExtendedMenuType<ChiseledEnchantingTableScreenHandler, AvailableEnchantmentPayload>
        CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER =
            new ExtendedMenuType<>(
                ChiseledEnchantingTableScreenHandler::new,
                AvailableEnchantmentPayload.AVAILABLE_CHISELED_ENCHANTMENT_CODEC
            );

    public static void init() {
        Registry.register(BuiltInRegistries.MENU, ChiseledEnchantingTable.identifier("chiseled_enchanting_table"), CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER);
        ChiseledEnchantingTableScreenHandler.init();
    }
}
