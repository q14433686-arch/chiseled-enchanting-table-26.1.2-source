package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a real-enchantment or Treasure Epiphany list row using cohesive painted plaque sprites.
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

        int nameX = itemX + 19;
        int nameY = dy + 3 + client.font.lineHeight / 3;
        int titleColor = isEpiphany ? RowRenderSupport.EPIPHANY_GOLD : RowRenderSupport.WHITE;
        RowRenderSupport.renderScaledText(ctx, client, enchantmentName, nameX, nameY, 2.0f / 3.0f, titleColor);

        if (annotation != null) {
            int subY = dy + dh - 2 - client.font.lineHeight;
            RowRenderSupport.renderScaledText(ctx, client, annotation.text(), nameX, subY, 2.0f / 3.0f, annotation.color());
        }

        RowRenderSupport.drawXpBadge(ctx, client, xpRequirement, dx, dw, dy, dh, xpMet);
    }
}
