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
 * Shared drawing primitives for chiseled-enchanting list rows with hardware scissor clipping,
 * bounded auto-scaling/ellipsizing text, compact centered item icons, and horizontal dual-mode / refund XP badges.
 */
public final class RowRenderSupport {
    private static final Identifier ROW_NORMAL = ChiseledEnchantingTable.identifier("gui/row_normal.png");
    private static final Identifier ROW_HOVER = ChiseledEnchantingTable.identifier("gui/row_hover.png");
    private static final Identifier ROW_DISABLED = ChiseledEnchantingTable.identifier("gui/row_disabled.png");
    private static final Identifier ROW_EPIPHANY = ChiseledEnchantingTable.identifier("gui/row_epiphany.png");
    private static final Identifier ROW_EPIPHANY_HOVER = ChiseledEnchantingTable.identifier("gui/row_epiphany_hover.png");
    private static final Identifier ROW_TRANSFORM = ChiseledEnchantingTable.identifier("gui/row_transform.png");
    private static final Identifier ROW_TRANSFORM_HOVER = ChiseledEnchantingTable.identifier("gui/row_transform_hover.png");
    private static final Identifier ROW_CLEANSE = ChiseledEnchantingTable.identifier("gui/row_cleanse.png");
    private static final Identifier ROW_CLEANSE_HOVER = ChiseledEnchantingTable.identifier("gui/row_cleanse_hover.png");
    private static final Identifier ROW_CURSE = ChiseledEnchantingTable.identifier("gui/row_curse.png");
    private static final Identifier ROW_CURSE_HOVER = ChiseledEnchantingTable.identifier("gui/row_curse_hover.png");

    public static final Identifier XP_ORB_1 = ChiseledEnchantingTable.identifier("gui/xp_orb_1.png");
    public static final Identifier XP_ORB_2 = ChiseledEnchantingTable.identifier("gui/xp_orb_2.png");
    public static final Identifier XP_ORB_3 = ChiseledEnchantingTable.identifier("gui/xp_orb_3.png");
    public static final Identifier XP_RITUAL_FLASK = ChiseledEnchantingTable.identifier("gui/xp_ritual_flask.png");
    public static final Identifier XP_REFUND_ORB = ChiseledEnchantingTable.identifier("gui/xp_refund_orb.png");

    public static final int XP_GREEN = 0xFF7CFFA6;
    public static final int JADE_MINT = 0xFF9EFFDB;
    public static final int CURSE_CRIMSON = 0xFFFF9696;
    public static final int ARCANE_PURPLE = 0xFFE5B2FF;
    public static final int RED = 0xFFFF6B6B;
    public static final int WHITE = 0xFFF7F2EA;
    public static final int EPIPHANY_GOLD = 0xFFFFE28A;
    public static final int TRANSFORM_GOLD = 0xFFFFEAB0;
    public static final int CYAN = 0xFF74F3FF;
    public static final int LIGHT_RED = 0xFFFFB4B4;
    public static final int LIGHT_GREEN = 0xFFA8FFC4;

    public static final int PRIMARY_COST_OFFSET_X = 5;
    public static final int NO_ICON_NAME_OFFSET_X = 7;
    public static final int COST_ICON_SIZE = 14;
    public static final float COST_ICON_SCALE = COST_ICON_SIZE / 16.0f;
    public static final int ADDITIONAL_COST_STRIDE = 15;
    public static final int XP_BADGE_WIDTH = 42;
    public static final int NAME_OFFSET_X = PRIMARY_COST_OFFSET_X + COST_ICON_SIZE + 3;

    public enum RowVisualStyle {
        STANDARD,
        EPIPHANY,
        TRANSFORMATION,
        CLEANSE,
        CURSE_PURGE
    }

    private RowRenderSupport() {}

    public static Identifier orbSprite(int deductedTier) {
        if (deductedTier <= 1) return XP_ORB_1;
        if (deductedTier == 2) return XP_ORB_2;
        return XP_ORB_3;
    }

    public static int costIconY(int dy, int dh) {
        return dy + (dh - COST_ICON_SIZE) / 2;
    }

    public static int xpSeparatorX(int dx, int dw) {
        return dx + dw - XP_BADGE_WIDTH;
    }

    public static int additionalCostStartX(int dx, int dw, int shownCount) {
        int shown = Math.clamp(shownCount, 0, 2);
        return xpSeparatorX(dx, dw) - 3 - shown * ADDITIONAL_COST_STRIDE;
    }

    public static int availableTextWidth(int dx, int dw, int shownAdditionalCount) {
        return availableTextWidth(dx, dw, shownAdditionalCount, true);
    }

    public static int availableTextWidth(int dx, int dw, int shownAdditionalCount, boolean hasPrimaryIcon) {
        int shown = Math.clamp(shownAdditionalCount, 0, 2);
        int rightLimit = shown > 0
            ? additionalCostStartX(dx, dw, shown) - 3
            : xpSeparatorX(dx, dw) - 4;
        int leftOffset = hasPrimaryIcon ? NAME_OFFSET_X : NO_ICON_NAME_OFFSET_X;
        return Math.max(24, rightLimit - (dx + leftOffset));
    }

    public static void beginRowContentClip(GuiGraphicsExtractor ctx, int dx, int dy, int dw, int dh) {
        ctx.enableScissor(dx + 2, dy + 2, dx + dw - 2, dy + dh - 2);
    }

    public static void endRowContentClip(GuiGraphicsExtractor ctx) {
        ctx.disableScissor();
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
            case CLEANSE -> (hovered && canEnchant) ? ROW_CLEANSE_HOVER : ROW_CLEANSE;
            case CURSE_PURGE -> (hovered && canEnchant) ? ROW_CURSE_HOVER : ROW_CURSE;
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

    private static void drawCompactCostStack(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        ItemStack stack,
        int x,
        int y,
        boolean affordable
    ) {
        if (stack.isEmpty()) return;
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(COST_ICON_SCALE, COST_ICON_SCALE);
        ctx.item(stack, 0, 0);
        CustomDrawContext.drawItemInSlotWithColor(ctx, client.font, stack, 0, 0, affordable ? WHITE : RED);
        pose.popMatrix();
    }

    public static void drawPrimaryCost(GuiGraphicsExtractor ctx, Minecraft client, ItemStack cost, int x, int y, boolean affordable) {
        drawCompactCostStack(ctx, client, cost, x, y, affordable);
    }

    public static void drawAdditionalCosts(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        List<ItemStack> additionalCosts,
        List<Boolean> additionalAffordability,
        int dx,
        int dw,
        int y
    ) {
        int shown = Math.min(2, additionalCosts.size());
        if (shown <= 0) return;
        int startX = additionalCostStartX(dx, dw, shown);
        for (int i = 0; i < shown; i++) {
            ItemStack additionalCost = additionalCosts.get(i);
            boolean affordable = i < additionalAffordability.size() && Boolean.TRUE.equals(additionalAffordability.get(i));
            drawCompactCostStack(ctx, client, additionalCost, startX + i * ADDITIONAL_COST_STRIDE, y, affordable);
        }
    }

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
        int sepX = xpSeparatorX(dx, dw);
        ctx.fill(sepX, dy + 4, sepX + 1, dy + dh - 4, 0x429480B8);

        if (req.isRefund()) {
            int iconSize = 10;
            int iconX = sepX + 3;
            int iconY = dy + (dh - iconSize) / 2;
            ctx.blit(XP_REFUND_ORB, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
            String label = "+" + req.refundPoints();
            int textLeftLimit = iconX + iconSize + 2;
            int textRightEdge = dx + dw - 6;
            int availW = Math.max(10, textRightEdge - textLeftLimit);
            int rawW = Math.max(1, client.font.width(label));
            float scale = Math.min(0.62f, (float) availW / (float) rawW);
            int scaledW = Math.round(rawW * scale);
            int textX = textRightEdge - scaledW;
            int textY = dy + Math.round((dh - client.font.lineHeight * scale) / 2.0f) + 1;
            renderScaledText(ctx, client, label, textX, textY, scale, XP_GREEN);
            return;
        }

        if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
            String freeText = "0L";
            float scale = 0.65f;
            int w = Math.round(client.font.width(freeText) * scale);
            int centerX = sepX + XP_BADGE_WIDTH / 2;
            int textY = dy + Math.round((dh - client.font.lineHeight * scale) / 2.0f) + 1;
            renderScaledText(ctx, client, freeText, centerX - w / 2, textY, scale, XP_GREEN);
            return;
        }

        Identifier icon = req.vanillaTierMode() ? orbSprite(req.deductedLevels()) : XP_RITUAL_FLASK;
        int iconSize = 10;
        int iconX = sepX + 3;
        int iconY = dy + (dh - iconSize) / 2;
        ctx.blit(icon, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!xpMet) {
            ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x55120A16);
        }

        String label = req.vanillaTierMode()
            ? ("Lv." + req.requiredLevel())
            : ("-" + req.deductedLevels() + "L");
        int textLeftLimit = iconX + iconSize + 2;
        int textRightEdge = dx + dw - 7;
        int availW = Math.max(10, textRightEdge - textLeftLimit);
        int rawW = Math.max(1, client.font.width(label));
        float scale = Math.min(0.60f, (float) availW / (float) rawW);
        int scaledW = Math.round(rawW * scale);
        int textX = textRightEdge - scaledW;
        int textY = dy + Math.round((dh - client.font.lineHeight * scale) / 2.0f) + 1;
        int color = !xpMet ? RED : (req.vanillaTierMode() ? XP_GREEN : ARCANE_PURPLE);
        renderScaledText(ctx, client, label, textX, textY, scale, color);
    }

    public static void renderBoundedText(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        String text,
        int x,
        int y,
        float maxScale,
        int maxWidth,
        int color
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 4) return;
        int rawW = client.font.width(text);
        if (rawW <= 0) return;

        float minScale = 0.48f;
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > maxWidth) {
            float needed = (float) maxWidth / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRawPx = Math.max(8, Math.round(maxWidth / scale) - client.font.width("\u2026"));
                finalStr = client.font.plainSubstrByWidth(text, maxRawPx) + "\u2026";
            }
        }
        int yAdjust = Math.round((maxScale - scale) * client.font.lineHeight * 0.5f);
        renderScaledText(ctx, client, finalStr, x, y + yAdjust, scale, color);
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
