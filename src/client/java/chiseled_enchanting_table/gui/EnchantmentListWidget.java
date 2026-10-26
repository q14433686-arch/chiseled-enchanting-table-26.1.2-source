package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.minecraft.client.Minecraft;

/**
 * Canonical properly-spelled widget class for the Chiseled Enchanting Table enchantment list
 * (Audit API-01). Extends the legacy {@link EnchantementListWidget} bridge so both
 * {@code instanceof EnchantmentListWidget} and {@code instanceof EnchantementListWidget} hold.
 */
@SuppressWarnings("deprecation")
public class EnchantmentListWidget extends EnchantementListWidget {
    public EnchantmentListWidget(
        Minecraft client,
        ChiseledEnchantingTableScreenHandler handler,
        int width,
        int height,
        int y,
        int itemHeight
    ) {
        super(client, handler, width, height, y, itemHeight);
    }
}
