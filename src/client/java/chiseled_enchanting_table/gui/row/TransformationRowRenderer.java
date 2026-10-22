package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a transformation list row with unified {@code midY} centering and inner-border scissor clipping.
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
        XpRequirement xpRequirement
    ) {
        RowRenderSupport.drawPlaque(
            ctx,
            dx,
            dy,
            dw,
            dh,
            RowRenderSupport.RowVisualStyle.TRANSFORMATION,
            canEnchant,
            hovered,
            isFocused
        );

        RowRenderSupport.beginRowContentClip(ctx, dx, dy, dw, dh);
        try {
            int itemX = dx + RowRenderSupport.PRIMARY_COST_OFFSET_X;
            int itemY = RowRenderSupport.costIconY(dy, dh);
            RowRenderSupport.drawPrimaryCost(ctx, client, primaryCost, itemX, itemY, primaryCostMet);
            RowRenderSupport.drawAdditionalCosts(ctx, client, additionalCosts, additionalCostsMet, dx, dw, itemY);

            int nameX = dx + RowRenderSupport.NAME_OFFSET_X;
            int maxTextW = RowRenderSupport.availableTextWidth(dx, dw, additionalCosts.size());
            RowRenderSupport.renderTwoLineCenteredBlock(
                ctx,
                client,
                outputName,
                RowRenderSupport.TRANSFORM_GOLD,
                "\u2726 \u2192 " + outputName,
                RowRenderSupport.LIGHT_GREEN,
                nameX,
                dy,
                dh,
                maxTextW
            );

            RowRenderSupport.drawXpBadge(ctx, client, xpRequirement, dx, dw, dy, dh, xpMet);
        } finally {
            RowRenderSupport.endRowContentClip(ctx);
        }
    }
}
