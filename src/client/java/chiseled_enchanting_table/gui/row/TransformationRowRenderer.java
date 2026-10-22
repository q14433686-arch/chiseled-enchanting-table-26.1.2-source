package chiseled_enchanting_table.gui.row;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a transformation list row: input -> output item conversion.
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
        boolean primaryCostMet,
        List<Boolean> additionalCostsMet,
        boolean xpMet,
        String outputName,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        int xpCost
    ) {
        int background = RowRenderSupport.backgroundColor(canEnchant, hovered);
        RowRenderSupport.drawChrome(ctx, dx, dy, dw, dh, background, isFocused);

        int itemX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
        int itemY = dy + RowRenderSupport.COST_OFFSET_Y;
        RowRenderSupport.drawPrimaryCost(ctx, client, primaryCost, itemX, itemY, primaryCostMet);
        RowRenderSupport.drawAdditionalCosts(
            ctx,
            client,
            additionalCosts,
            additionalCostsMet,
            dx + dw - RowRenderSupport.ADDITIONAL_COST_RIGHT_MARGIN,
            itemY
        );

        int nameX = itemX + 20;
        int nameY = dy + 2 + client.font.lineHeight / 3;
        RowRenderSupport.renderScaledText(ctx, client, outputName, nameX, nameY, 2.0f / 3.0f, RowRenderSupport.WHITE);

        int subY = dy + dh - 2 - client.font.lineHeight;
        RowRenderSupport.renderScaledText(ctx, client, "\u2192 " + outputName, nameX, subY, 2.0f / 3.0f, RowRenderSupport.LIGHT_GREEN);

        RowRenderSupport.drawXpLabel(ctx, client, xpCost, dx, dw, dy, dh, xpMet);
    }
}
