package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
        render(
            ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant,
            primaryCostMet, additionalCostsMet, xpMet, outputName,
            null, 0, primaryCost, additionalCosts, xpRequirement
        );
    }

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
        @Nullable String customSubtitle,
        int customSubtitleColor,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        XpRequirement xpRequirement
    ) {
        render(
            ctx, client, dx, dy, dw, dh, isFocused, hovered, canEnchant,
            primaryCostMet, additionalCostsMet, xpMet, outputName,
            customSubtitle, customSubtitleColor, primaryCost, additionalCosts, xpRequirement, false
        );
    }

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
        @Nullable String customSubtitle,
        int customSubtitleColor,
        ItemStack primaryCost,
        List<ItemStack> additionalCosts,
        XpRequirement xpRequirement,
        boolean cyclablePrimary
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
            if (!primaryCost.isEmpty()) {
                RowRenderSupport.drawPrimaryCost(ctx, client, primaryCost, itemX, itemY, primaryCostMet, cyclablePrimary);
            } else {
                float midY = dy + dh * 0.5f;
                RowRenderSupport.renderCenteredYText(ctx, client, "\u2714", itemX + 2, midY, 0.90f, 12, RowRenderSupport.LIGHT_GREEN);
            }
            RowRenderSupport.drawAdditionalCosts(ctx, client, additionalCosts, additionalCostsMet, dx, dw, itemY);

            int nameX = dx + RowRenderSupport.NAME_OFFSET_X;
            int maxTextW = RowRenderSupport.availableTextWidth(dx, dw, additionalCosts.size());
            String subText = customSubtitle != null ? customSubtitle : ("\u2726 \u2192 " + outputName);
            int subColor = customSubtitle != null ? customSubtitleColor : RowRenderSupport.LIGHT_GREEN;
            RowRenderSupport.renderTwoLineCenteredBlock(
                ctx,
                client,
                outputName,
                RowRenderSupport.TRANSFORM_GOLD,
                subText,
                subColor,
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
