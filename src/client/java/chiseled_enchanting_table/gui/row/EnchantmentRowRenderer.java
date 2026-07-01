package chiseled_enchanting_table.gui.row;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a real-enchantment list row: the enchantment full name, its costs, and an optional
 * annotation line describing how it interacts with an enchantment already on the item
 * (already present / upgrade / downgrade / conflict).
 */
public final class EnchantmentRowRenderer {
    private EnchantmentRowRenderer() {}

    /** Precomputed annotation line for the second row of text, or {@code null} for none. */
    public record OverrideAnnotation(String text, int color) {}

    public static void render(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        int dx, int dy, int dw, int dh,
        boolean isFocused,
        boolean hovered,
        boolean canEnchant,
        boolean itemCostMet,
        boolean xpMet,
        String enchantmentName,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        int xpCost,
        OverrideAnnotation annotation
    ) {
        int background = RowRenderSupport.backgroundColor(canEnchant, hovered);
        RowRenderSupport.drawChrome(ctx, dx, dy, dw, dh, background, isFocused);

        int itemX = dx + 4;
        int itemY = dy + 4;
        RowRenderSupport.drawPrimaryCost(ctx, client, primaryCost, itemX, itemY, itemCostMet);
        RowRenderSupport.drawAdditionalCosts(ctx, client, additionalCosts, dx + dw - 48, itemY, itemCostMet);

        int nameX = itemX + 20;
        int nameY = dy + 2 + client.font.lineHeight / 3;
        RowRenderSupport.renderScaledText(ctx, client, enchantmentName, nameX, nameY, 2.0f / 3.0f, RowRenderSupport.WHITE);

        if (annotation != null) {
            int subY = dy + dh - 2 - client.font.lineHeight;
            RowRenderSupport.renderScaledText(ctx, client, annotation.text(), nameX, subY, 2.0f / 3.0f, annotation.color());
        }

        RowRenderSupport.drawXpLabel(ctx, client, xpCost, dx, dw, dy, dh, xpMet);
    }
}
