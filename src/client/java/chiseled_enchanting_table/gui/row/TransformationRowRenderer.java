package chiseled_enchanting_table.gui.row;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a transformation list row: input -> output item conversion. Unlike an enchantment row it
 * has no "overrides existing enchantment" annotation line; it just shows the output name plus costs.
 */
public final class TransformationRowRenderer {
    private TransformationRowRenderer() {}

    public static void render(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        int dx, int dy, int dw, int dh,
        boolean isFocused,
        boolean hovered,
        boolean canEnchant,
        boolean itemCostMet,
        boolean xpMet,
        String outputName,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        int xpCost
    ) {
        int background = RowRenderSupport.backgroundColor(canEnchant, hovered);
        RowRenderSupport.drawChrome(ctx, dx, dy, dw, dh, background, isFocused);

        int itemX = dx + 4;
        int itemY = dy + 4;
        RowRenderSupport.drawPrimaryCost(ctx, client, primaryCost, itemX, itemY, itemCostMet);
        RowRenderSupport.drawAdditionalCosts(ctx, client, additionalCosts, dx + dw - 48, itemY, itemCostMet);

        int nameX = itemX + 20;
        int nameY = dy + 2 + client.font.lineHeight / 3;
        RowRenderSupport.renderScaledText(ctx, client, outputName, nameX, nameY, 2.0f / 3.0f, RowRenderSupport.WHITE);

        // Secondary line clarifies it is a conversion.
        int subY = dy + dh - 2 - client.font.lineHeight;
        RowRenderSupport.renderScaledText(ctx, client, "\u2192 " + outputName, nameX, subY, 2.0f / 3.0f, RowRenderSupport.LIGHT_GREEN);

        RowRenderSupport.drawXpLabel(ctx, client, xpCost, dx, dw, dy, dh, xpMet);
    }
}
