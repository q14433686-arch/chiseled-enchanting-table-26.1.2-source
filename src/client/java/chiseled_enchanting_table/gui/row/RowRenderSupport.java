package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.utils.CustomDrawContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Shared drawing primitives for chiseled-enchanting list rows. Separating these out lets the
 * enchantment row and the transformation row each own their own content layout while reusing the
 * common chrome (background, 3D border, cost icons, scaled text, XP label).
 *
 * <p>Architecture note (P15): this is the {@code client/gui} decoupling of the previously
 * monolithic row renderer into a shared support class plus per-kind renderers.</p>
 */
public final class RowRenderSupport {
    public static final int LIGHT_GREY = 0xFF919191;
    public static final int GREY = 0xFF717171;
    public static final int DARK_GREY = 0xFF313131;
    public static final int XP_GREEN = 0xFF55FF55;
    public static final int RED = 0xFFEE0000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int CYAN = 0xFF00FFFF;
    public static final int LIGHT_RED = 0xFFFFCCCC;
    public static final int LIGHT_GREEN = 0xFFCCFFCC;

    private RowRenderSupport() {}

    public static int backgroundColor(boolean canEnchant, boolean hovered) {
        if (!canEnchant) return DARK_GREY;
        return hovered ? LIGHT_GREY : GREY;
    }

    public static void drawChrome(GuiGraphicsExtractor ctx, int dx, int dy, int dw, int dh, int backgroundColor, boolean isFocused) {
        ctx.fill(dx, dy, dx + dw, dy + dh, backgroundColor);
        draw3dBorder(ctx, dx, dy, dw, dh, isFocused);
    }

    /** Draw the primary cost item at (x, y), tinted by whether it can be afforded. */
    public static void drawPrimaryCost(GuiGraphicsExtractor ctx, Minecraft client, ItemStack cost, int x, int y, boolean affordable) {
        ctx.item(cost, x, y);
        CustomDrawContext.drawItemInSlotWithColor(ctx, client.font, cost, x, y, affordable ? WHITE : RED);
    }

    /** Draw up to two additional cost icons starting at the right edge. */
    public static void drawAdditionalCosts(GuiGraphicsExtractor ctx, Minecraft client, List<ItemStack> additionalCosts, int startX, int y, boolean affordable) {
        for (int i = 0; i < Math.min(2, additionalCosts.size()); i++) {
            ItemStack additionalCost = additionalCosts.get(i);
            int x = startX + i * 18;
            ctx.item(additionalCost, x, y);
            CustomDrawContext.drawItemInSlotWithColor(ctx, client.font, additionalCost, x, y, affordable ? WHITE : RED);
        }
    }

    public static void drawXpLabel(GuiGraphicsExtractor ctx, Minecraft client, int xpCost, int dx, int dw, int dy, int dh, boolean xpMet) {
        var xpCostText = String.valueOf(xpCost);
        var textWidth = client.font.width(xpCostText);
        var textX = dx + dw - 5 - textWidth;
        var textY = dy + (dh - client.font.lineHeight) / 2;
        ctx.text(client.font, xpCostText, textX, textY, xpMet ? XP_GREEN : RED, true);
    }

    public static void draw3dBorder(GuiGraphicsExtractor ctx, int x, int y, int width, int height, boolean isFocused) {
        var darkColor = isFocused ? 0xff888811 : 0xff111111;
        var cornerColor = isFocused ? 0xffaaaa22 : 0xff333333;
        var lightColor = isFocused ? 0xffcccc22 : 0xff444444;

        ctx.fill(x, y, x + width, y + 1, darkColor);
        ctx.fill(x, y + 1, x + 1, y + height - 1, darkColor);

        ctx.fill(x, y + height - 1, x + width, y + height, lightColor);
        ctx.fill(x + width - 1, y + 1, x + width, y + height - 1, lightColor);

        ctx.fill(x + width - 1, y, x + width, y + 1, cornerColor);
        ctx.fill(x, y + height - 1, x + 1, y + height, cornerColor);
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
