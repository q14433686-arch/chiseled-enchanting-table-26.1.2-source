package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a real-enchantment or Treasure Epiphany list row with strict anti-overlap text bounds
 * and hardware scissor clipping inside the plaque border.
 */
public final class EnchantmentRowRenderer {
    private EnchantmentRowRenderer() {}

    /** Precomputed annotation line for the second row of text, or {@code null} for none. */
    public record OverrideAnnotation(String text, int color) {}

    public static void render(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        int dx, int dy, int dw, int dh,
        boolean isEpiphany,
        boolean isFocused,
        boolean hovered,
        boolean canEnchant,
        boolean primaryCostMet,
        List<Boolean> additionalCostsMet,
        boolean xpMet,
        String enchantmentName,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        XpRequirement xpRequirement,
        OverrideAnnotation annotation
    ) {
        RowRenderSupport.drawPlaque(
            ctx,
            dx,
            dy,
            dw,
            dh,
            isEpiphany ? RowRenderSupport.RowVisualStyle.EPIPHANY : RowRenderSupport.RowVisualStyle.STANDARD,
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
            int titleColor = isEpiphany ? RowRenderSupport.EPIPHANY_GOLD : RowRenderSupport.WHITE;

            if (annotation != null) {
                RowRenderSupport.renderBoundedText(ctx, client, enchantmentName, nameX, dy + 4, 0.64f, maxTextW, titleColor);
                RowRenderSupport.renderBoundedText(ctx, client, annotation.text(), nameX, dy + 12, 0.58f, maxTextW, annotation.color());
            } else {
                int centeredY = dy + Math.round((dh - client.font.lineHeight * 0.66f) / 2.0f) + 1;
                RowRenderSupport.renderBoundedText(ctx, client, enchantmentName, nameX, centeredY, 0.66f, maxTextW, titleColor);
            }

            RowRenderSupport.drawXpBadge(ctx, client, xpRequirement, dx, dw, dy, dh, xpMet);
        } finally {
            RowRenderSupport.endRowContentClip(ctx);
        }
    }
}
