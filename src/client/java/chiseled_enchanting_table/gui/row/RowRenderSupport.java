package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import chiseled_enchanting_table.utils.CustomDrawContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared drawing primitives for chiseled-enchanting list rows using cohesive imported plaque sprites
 * and custom pixel-art experience formula badges ({@code xp_orb_1..3} vs {@code xp_ritual_flask}).
 */
public final class RowRenderSupport {
    private static final Identifier ROW_NORMAL = ChiseledEnchantingTable.identifier("gui/row_normal.png");
    private static final Identifier ROW_HOVER = ChiseledEnchantingTable.identifier("gui/row_hover.png");
    private static final Identifier ROW_DISABLED = ChiseledEnchantingTable.identifier("gui/row_disabled.png");
    private static final Identifier ROW_EPIPHANY = ChiseledEnchantingTable.identifier("gui/row_epiphany.png");
    private static final Identifier ROW_EPIPHANY_HOVER = ChiseledEnchantingTable.identifier("gui/row_epiphany_hover.png");
    private static final Identifier ROW_TRANSFORM = ChiseledEnchantingTable.identifier("gui/row_transform.png");
    private static final Identifier ROW_TRANSFORM_HOVER = ChiseledEnchantingTable.identifier("gui/row_transform_hover.png");

    public static final Identifier XP_ORB_1 = ChiseledEnchantingTable.identifier("gui/xp_orb_1.png");
    public static final Identifier XP_ORB_2 = ChiseledEnchantingTable.identifier("gui/xp_orb_2.png");
    public static final Identifier XP_ORB_3 = ChiseledEnchantingTable.identifier("gui/xp_orb_3.png");
    public static final Identifier XP_RITUAL_FLASK = ChiseledEnchantingTable.identifier("gui/xp_ritual_flask.png");

    public static final int XP_GREEN = 0xFF7CFFA6;
    public static final int ARCANE_PURPLE = 0xFFE5B2FF;
    public static final int RED = 0xFFFF6B6B;
    public static final int WHITE = 0xFFF7F2EA;
    public static final int EPIPHANY_GOLD = 0xFFFFE28A;
    public static final int TRANSFORM_GOLD = 0xFFFFEAB0;
    public static final int CYAN = 0xFF74F3FF;
    public static final int LIGHT_RED = 0xFFFFB4B4;
    public static final int LIGHT_GREEN = 0xFFA8FFC4;

    public static final int PRIMARY_COST_OFFSET_X = 5;
    public static final int COST_OFFSET_Y = 4;
    public static final int XP_BADGE_WIDTH = 34;
    public static final int ADDITIONAL_COST_RIGHT_MARGIN = 70;
    public static final int ADDITIONAL_COST_STRIDE = 17;

    public enum RowVisualStyle {
        STANDARD,
        EPIPHANY,
        TRANSFORMATION
    }

    private RowRenderSupport() {}

    public static Identifier orbSprite(int deductedTier) {
        if (deductedTier <= 1) return XP_ORB_1;
        if (deductedTier == 2) return XP_ORB_2;
        return XP_ORB_3;
    }

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

    /**
     * Renders the right-hand dual-mode XP badge:
     * <ul>
     *   <li><b>Vanilla Mass-Production ({@code vanillaTierMode})</b>: 1/2/3 glowing emerald-gold orbs
     *       on top + {@code Lv.X} player threshold below.</li>
     *   <li><b>Arcane Full-Cost Ritual ({@code !vanillaTierMode})</b>: corked violet-gold arcane flask
     *       on top + {@code -XL} full level deduction below.</li>
     * </ul>
     */
    public static void drawXpBadge(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        XpRequirement req,
        int dx,
        int dw,
        int dy,
        int dh,
        boolean xpMet
    ) {
        int sepX = dx + dw - XP_BADGE_WIDTH;
        ctx.fill(sepX, dy + 4, sepX + 1, dy + dh - 4, 0x389480B8);
        int centerX = sepX + XP_BADGE_WIDTH / 2;

        if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
            String freeText = "0L";
            float scale = 0.65f;
            int w = Math.round(client.font.width(freeText) * scale);
            renderScaledText(ctx, client, freeText, centerX - w / 2, dy + 9, scale, XP_GREEN);
            return;
        }

        Identifier icon = req.vanillaTierMode() ? orbSprite(req.deductedLevels()) : XP_RITUAL_FLASK;
        int iconSize = 11;
        int iconX = centerX - iconSize / 2;
        int iconY = dy + 2;
        ctx.blit(icon, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!xpMet) {
            ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x55120A16);
        }

        String label = req.vanillaTierMode()
            ? ("Lv." + req.requiredLevel())
            : ("-" + req.deductedLevels() + "L");
        float scale = 0.58f;
        int labelW = Math.round(client.font.width(label) * scale);
        int color = !xpMet ? RED : (req.vanillaTierMode() ? XP_GREEN : ARCANE_PURPLE);
        renderScaledText(ctx, client, label, centerX - labelW / 2, dy + 15, scale, color);
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
