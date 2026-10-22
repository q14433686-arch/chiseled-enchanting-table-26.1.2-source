package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.utils.CustomDrawContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared drawing primitives for chiseled-enchanting list rows using cohesive imported plaque sprites.
 */
public final class RowRenderSupport {
    private static final Identifier ROW_NORMAL = ChiseledEnchantingTable.identifier("gui/row_normal.png");
    private static final Identifier ROW_HOVER = ChiseledEnchantingTable.identifier("gui/row_hover.png");
    private static final Identifier ROW_DISABLED = ChiseledEnchantingTable.identifier("gui/row_disabled.png");
    private static final Identifier ROW_EPIPHANY = ChiseledEnchantingTable.identifier("gui/row_epiphany.png");
    private static final Identifier ROW_EPIPHANY_HOVER = ChiseledEnchantingTable.identifier("gui/row_epiphany_hover.png");
    private static final Identifier ROW_TRANSFORM = ChiseledEnchantingTable.identifier("gui/row_transform.png");
    private static final Identifier ROW_TRANSFORM_HOVER = ChiseledEnchantingTable.identifier("gui/row_transform_hover.png");

    public static final int XP_GREEN = 0xFF7CFFA6;
    public static final int RED = 0xFFFF6B6B;
    public static final int WHITE = 0xFFF7F2EA;
    public static final int EPIPHANY_GOLD = 0xFFFFE28A;
    public static final int TRANSFORM_GOLD = 0xFFFFEAB0;
    public static final int CYAN = 0xFF74F3FF;
    public static final int LIGHT_RED = 0xFFFFB4B4;
    public static final int LIGHT_GREEN = 0xFFA8FFC4;

    public static final int PRIMARY_COST_OFFSET_X = 5;
    public static final int COST_OFFSET_Y = 4;
    public static final int ADDITIONAL_COST_RIGHT_MARGIN = 56;
    public static final int ADDITIONAL_COST_STRIDE = 18;

    public enum RowVisualStyle {
        STANDARD,
        EPIPHANY,
        TRANSFORMATION
    }

    private RowRenderSupport() {}

    public static void drawPlaque(
        GuiGraphicsExtractor ctx,
        int dx,
        int dy,
        int dw,
        int dh,
        RowVisualStyle style,
        boolean canEnchant,
        boolean hovered,
        boolean isFocused
    ) {
        Identifier sprite = switch (style) {
            case EPIPHANY -> (hovered && canEnchant) ? ROW_EPIPHANY_HOVER : ROW_EPIPHANY;
            case TRANSFORMATION -> (hovered && canEnchant) ? ROW_TRANSFORM_HOVER : ROW_TRANSFORM;
            case STANDARD -> !canEnchant ? ROW_DISABLED : (hovered ? ROW_HOVER : ROW_NORMAL);
        };
        ctx.blit(sprite, dx, dy, dx + dw, dy + dh, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!canEnchant && style != RowVisualStyle.STANDARD) {
            ctx.fill(dx + 1, dy + 1, dx + dw - 1, dy + dh - 1, 0x58100A18);
        }
        if (isFocused) {
            drawFocusBorder(ctx, dx, dy, dw, dh);
        }
    }

    /** Draw the primary cost item at (x, y), tinted by whether it can be afforded. */
    public static void drawPrimaryCost(GuiGraphicsExtractor ctx, Minecraft client, ItemStack cost, int x, int y, boolean affordable) {
        ctx.item(cost, x, y);
        CustomDrawContext.drawItemInSlotWithColor(ctx, client.font, cost, x, y, affordable ? WHITE : RED);
    }

    /** Draw up to two additional cost icons starting near the right edge, with per-stack affordability coloring. */
    public static void drawAdditionalCosts(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        List<ItemStack> additionalCosts,
        List<Boolean> additionalAffordability,
        int startX,
        int y
    ) {
        int shown = Math.min(2, additionalCosts.size());
        for (int i = 0; i < shown; i++) {
            ItemStack additionalCost = additionalCosts.get(i);
            boolean affordable = i < additionalAffordability.size() && Boolean.TRUE.equals(additionalAffordability.get(i));
            int x = startX + i * ADDITIONAL_COST_STRIDE;
            ctx.item(additionalCost, x, y);
            CustomDrawContext.drawItemInSlotWithColor(ctx, client.font, additionalCost, x, y, affordable ? WHITE : RED);
        }
    }

    public static void drawXpLabel(GuiGraphicsExtractor ctx, Minecraft client, int xpCost, int dx, int dw, int dy, int dh, boolean xpMet) {
        var xpCostText = String.valueOf(xpCost);
        var textWidth = client.font.width(xpCostText);
        var textX = dx + dw - 6 - textWidth;
        var textY = dy + (dh - client.font.lineHeight) / 2 + 1;
        ctx.text(client.font, xpCostText, textX, textY, xpMet ? XP_GREEN : RED, true);
    }

    private static void drawFocusBorder(GuiGraphicsExtractor ctx, int x, int y, int width, int height) {
        int gold = 0xFFFFDF7E;
        ctx.fill(x, y, x + width, y + 1, gold);
        ctx.fill(x, y + height - 1, x + width, y + height, gold);
        ctx.fill(x, y, x + 1, y + height, gold);
        ctx.fill(x + width - 1, y, x + width, y + height, gold);
    }

    public static void renderScaledText(GuiGraphicsExtractor drawContext, Minecraft client, String text, int x, int y, float scale, int color) {
        var matrixStack = drawContext.pose();
        matrixStack.pushMatrix();
        matrixStack.scale(scale, scale);
        var scaledX = Math.round(x / scale);
        var scaledY = Math.round(y / scale);
        drawContext.text(client.font, text, (int) scaledX, (int) scaledY, color, true);
        matrixStack.popMatrix();
    }
}
