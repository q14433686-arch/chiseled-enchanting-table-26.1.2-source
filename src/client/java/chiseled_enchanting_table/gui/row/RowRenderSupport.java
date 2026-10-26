package chiseled_enchanting_table.gui.row;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared drawing primitives for chiseled-enchanting list rows with strict inner-border GPU scissor
 * clipping, unified {@code midY} vertical centering across all icons/labels/badges, crisp disclosure
 * triangles, and nested tree connector rails.
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

    public static final Identifier ROW_CATEGORY_CLOSED = ChiseledEnchantingTable.identifier("gui/row_category_closed.png");
    public static final Identifier ROW_CATEGORY_OPEN = ChiseledEnchantingTable.identifier("gui/row_category_open.png");
    public static final Identifier ROW_CLEANSE_CATEGORY_CLOSED = ChiseledEnchantingTable.identifier("gui/row_cleanse_category_closed.png");
    public static final Identifier ROW_CLEANSE_CATEGORY_OPEN = ChiseledEnchantingTable.identifier("gui/row_cleanse_category_open.png");

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

    // Inner safe border inset (all row_*.png ornate frames occupy [0..3.5] top/bottom; interior is [4..dh-4]).
    public static final int INNER_BORDER_PAD = 4;
    public static final int NESTED_CHILD_INDENT = 8;

    public static final int PRIMARY_COST_OFFSET_X = 6;
    public static final int NO_ICON_NAME_OFFSET_X = 7;
    public static final int COST_ICON_SIZE = 12;
    public static final float COST_ICON_SCALE = COST_ICON_SIZE / 16.0f;
    public static final int ADDITIONAL_COST_STRIDE = 14;
    public static final int XP_BADGE_WIDTH = 40;
    public static final int NAME_OFFSET_X = PRIMARY_COST_OFFSET_X + COST_ICON_SIZE + 4;

    // Actual glyph ink height in Minecraft's default + CJK font (rows 0..7 = 8px ink; row 8 is line spacing).
    private static final float GLYPH_INK_HEIGHT = 8.0f;

    public enum RowVisualStyle {
        STANDARD,
        EPIPHANY,
        TRANSFORMATION,
        CLEANSE,
        CURSE_PURGE
    }

    private RowRenderSupport() {}

    public static float rowCenterY(int dy, int dh) {
        return dy + dh * 0.5f;
    }

    public static Identifier orbSprite(int deductedTier) {
        if (deductedTier <= 1) return XP_ORB_1;
        if (deductedTier == 2) return XP_ORB_2;
        return XP_ORB_3;
    }

    public static int costIconY(int dy, int dh) {
        return Math.round(rowCenterY(dy, dh) - COST_ICON_SIZE * 0.5f);
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
        return Math.max(20, rightLimit - (dx + leftOffset));
    }

    public static void beginRowContentClip(GuiGraphicsExtractor ctx, int dx, int dy, int dw, int dh) {
        ctx.enableScissor(
            dx + INNER_BORDER_PAD,
            dy + INNER_BORDER_PAD,
            dx + dw - INNER_BORDER_PAD,
            dy + dh - INNER_BORDER_PAD
        );
    }

    public static void endRowContentClip(GuiGraphicsExtractor ctx) {
        ctx.disableScissor();
    }

    public static void drawTreeConnector(
        GuiGraphicsExtractor ctx,
        int outerDx,
        int dy,
        int dh,
        boolean lastChild,
        boolean cleanseTheme
    ) {
        int spineX = outerDx + 3;
        int midY = Math.round(rowCenterY(dy, dh));
        int mainColor = cleanseTheme ? 0xCC5CE6B8 : 0xCCA882E8;
        int highlightColor = cleanseTheme ? 0xFFA8FFE0 : 0xFFFFDF8E;

        int spineBottom = lastChild ? (midY + 1) : (dy + dh + 1);
        ctx.fill(spineX, dy - 1, spineX + 1, spineBottom, mainColor);
        ctx.fill(spineX + 1, midY, outerDx + NESTED_CHILD_INDENT, midY + 1, highlightColor);
    }

    /**
     * Draws a razor-sharp pixel-art triangle centered on {@code centerY}:
     * {@code ▶} when collapsed ({@code expanded == false}) or {@code ▼} when expanded ({@code expanded == true}).
     */
    public static void drawDisclosureTriangle(
        GuiGraphicsExtractor ctx,
        int leftX,
        float centerY,
        boolean expanded,
        int fillArgb
    ) {
        int cy = Math.round(centerY);
        int shadowArgb = 0xAA090610;
        if (expanded) {
            // Down-pointing 7x4 pixel triangle centered on (leftX + 3, cy)
            for (int row = 0; row < 4; row++) {
                int half = 3 - row;
                int y = cy - 2 + row;
                int x0 = leftX + 3 - half;
                int x1 = leftX + 4 + half;
                ctx.fill(x0, y + 1, x1, y + 2, shadowArgb);
                ctx.fill(x0, y, x1, y + 1, fillArgb);
            }
        } else {
            // Right-pointing 4x7 pixel triangle centered on (leftX + 2, cy)
            for (int col = 0; col < 4; col++) {
                int half = 3 - col;
                int x = leftX + 1 + col;
                int y0 = cy - half;
                int y1 = cy + half + 1;
                ctx.fill(x + 1, y0, x + 2, y1, shadowArgb);
                ctx.fill(x, y0, x + 1, y1, fillArgb);
            }
        }
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
        pose.popMatrix();

        // Render compact stack count strictly inside [x..x+COST_ICON_SIZE+2] x [y..y+COST_ICON_SIZE+1]
        // so digits NEVER spill onto the bottom border of any row plaque!
        int count = stack.getCount();
        if (count > 1 || !affordable) {
            String countStr = String.valueOf(count);
            float scale = 0.54f;
            int w = Math.max(1, Math.round(client.font.width(countStr) * scale));
            int rightEdge = x + COST_ICON_SIZE + 2;
            int drawX = rightEdge - w;
            int drawY = y + COST_ICON_SIZE - Math.round(GLYPH_INK_HEIGHT * scale) + 1;
            renderScaledText(ctx, client, countStr, drawX, drawY, scale, affordable ? WHITE : RED);
        }
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
        float midY = rowCenterY(dy, dh);
        int sepX = xpSeparatorX(dx, dw);
        ctx.fill(sepX, dy + 5, sepX + 1, dy + dh - 5, 0x489480B8);

        int iconSize = 9;
        int iconX = sepX + 3;
        int iconY = Math.round(midY - iconSize * 0.5f);
        int textLeftLimit = iconX + iconSize + 2;
        int textRightEdge = dx + dw - 5;

        if (req.isRefund()) {
            ctx.blit(XP_REFUND_ORB, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
            String label = req.refundPoints() > 0 ? ("+" + req.refundPoints()) : "0 XP";
            int refundColor = req.refundPoints() > 0 ? XP_GREEN : 0xFFA2C4BC;
            renderRightAlignedCenteredYText(ctx, client, label, textLeftLimit, textRightEdge, midY, 0.60f, refundColor);
            return;
        }

        if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
            renderCenteredBoxText(ctx, client, "0L", sepX + 2, XP_BADGE_WIDTH - 6, midY, 0.62f, XP_GREEN);
            return;
        }

        Identifier icon = req.vanillaTierMode() ? orbSprite(req.deductedLevels()) : XP_RITUAL_FLASK;
        ctx.blit(icon, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!xpMet) {
            ctx.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x55120A16);
        }

        String label = req.vanillaTierMode()
            ? ("Lv." + req.requiredLevel())
            : ("-" + req.deductedLevels() + "L");
        int color = !xpMet ? RED : (req.vanillaTierMode() ? XP_GREEN : ARCANE_PURPLE);
        renderRightAlignedCenteredYText(ctx, client, label, textLeftLimit, textRightEdge, midY, 0.58f, color);
    }

    /**
     * Left-aligned text whose actual visual glyph ink center sits on {@code visualCenterY},
     * automatically scaling down or ellipsizing so width never exceeds {@code maxWidth}.
     */
    public static void renderCenteredYText(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        String text,
        int x,
        float visualCenterY,
        float maxScale,
        int maxWidth,
        int color
    ) { renderCenteredYText(ctx, client.font, text, x, visualCenterY, maxScale, maxWidth, color); }

    public static void renderCenteredYText(
        GuiGraphicsExtractor ctx,
        net.minecraft.client.gui.Font font,
        String text,
        int x,
        float visualCenterY,
        float maxScale,
        int maxWidth,
        int color
    ) {
        if (text == null || text.isEmpty() || maxWidth <= 4) return;
        int rawW = font.width(text);
        if (rawW <= 0) return;

        float minScale = 0.46f;
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > maxWidth) {
            float needed = (float) maxWidth / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRawPx = Math.max(8, Math.round(maxWidth / scale) - font.width("\u2026"));
                finalStr = font.plainSubstrByWidth(text, maxRawPx) + "\u2026";
            }
        }
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        renderScaledTextFloat(ctx, font, finalStr, x, drawY, scale, color);
    }

    /**
     * Horizontally AND vertically centered text strictly inside {@code [boxX .. boxX + boxWidth]}.
     */
    public static void renderCenteredBoxText(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        String text,
        int boxX,
        int boxWidth,
        float visualCenterY,
        float maxScale,
        int color
    ) { renderCenteredBoxText(ctx, client.font, text, boxX, boxWidth, visualCenterY, maxScale, color); }

    public static void renderCenteredBoxText(
        GuiGraphicsExtractor ctx,
        net.minecraft.client.gui.Font font,
        String text,
        int boxX,
        int boxWidth,
        float visualCenterY,
        float maxScale,
        int color
    ) {
        if (text == null || text.isEmpty() || boxWidth <= 4) return;
        int rawW = font.width(text);
        if (rawW <= 0) return;

        float minScale = 0.46f;
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > boxWidth) {
            float needed = (float) boxWidth / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRawPx = Math.max(8, Math.round(boxWidth / scale) - font.width("\u2026"));
                finalStr = font.plainSubstrByWidth(text, maxRawPx) + "\u2026";
            }
        }
        float renderedW = font.width(finalStr) * scale;
        float drawX = boxX + (boxWidth - renderedW) * 0.5f;
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        renderScaledTextFloat(ctx, font, finalStr, drawX, drawY, scale, color);
    }

    /**
     * Right-aligned text inside {@code [leftLimit .. rightEdge]} vertically centered on {@code visualCenterY}.
     */
    public static void renderRightAlignedCenteredYText(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        String text,
        int leftLimit,
        int rightEdge,
        float visualCenterY,
        float maxScale,
        int color
    ) {
        if (text == null || text.isEmpty()) return;
        int availW = Math.max(8, rightEdge - leftLimit);
        int rawW = Math.max(1, client.font.width(text));
        float scale = Math.min(maxScale, (float) availW / (float) rawW);
        float scaledW = rawW * scale;
        float drawX = rightEdge - scaledW;
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        renderScaledTextFloat(ctx, client, text, drawX, drawY, scale, color);
    }

    /**
     * Two-line stacked text block centered around {@code midY = dy + dh/2} strictly inside
     * {@code [dy + 5 .. dy + dh - 5]} so neither line ever touches any plaque border.
     */
    public static void renderTwoLineCenteredBlock(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        String line1,
        int color1,
        String line2,
        int color2,
        int x,
        int dy,
        int dh,
        int maxWidth
    ) {
        float midY = rowCenterY(dy, dh);
        renderCenteredYText(ctx, client, line1, x, midY - 3.6f, 0.58f, maxWidth, color1);
        renderCenteredYText(ctx, client, line2, x, midY + 3.6f, 0.50f, maxWidth, color2);
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
    ) { renderBoundedText(ctx, client.font, text, x, y, maxScale, maxWidth, color); }

    public static void renderBoundedText(
        GuiGraphicsExtractor ctx,
        net.minecraft.client.gui.Font font,
        String text,
        int x,
        int y,
        float maxScale,
        int maxWidth,
        int color
    ) {
        float approxCenterY = y + (GLYPH_INK_HEIGHT * maxScale) * 0.5f;
        renderCenteredYText(ctx, font, text, x, approxCenterY, maxScale, maxWidth, color);
    }

    private static void drawFocusBorder(GuiGraphicsExtractor ctx, int x, int y, int width, int height) {
        int gold = 0xFFFFDF7E;
        ctx.fill(x, y, x + width, y + 1, gold);
        ctx.fill(x, y + height - 1, x + width, y + height, gold);
        ctx.fill(x, y, x + 1, y + height, gold);
        ctx.fill(x + width - 1, y, x + width, y + height, gold);
    }

    private static void renderScaledTextFloat(
        GuiGraphicsExtractor drawContext,
        Minecraft client,
        String text,
        float x,
        float y,
        float scale,
        int color
    ) { renderScaledTextFloat(drawContext, client.font, text, x, y, scale, color); }

    private static void renderScaledTextFloat(
        GuiGraphicsExtractor drawContext,
        net.minecraft.client.gui.Font font,
        String text,
        float x,
        float y,
        float scale,
        int color
    ) {
        var matrixStack = drawContext.pose();
        matrixStack.pushMatrix();
        matrixStack.translate(x, y);
        matrixStack.scale(scale, scale);
        drawContext.text(font, text, 0, 0, color, true);
        matrixStack.popMatrix();
    }

    public static void renderScaledText(GuiGraphicsExtractor drawContext, Minecraft client, String text, int x, int y, float scale, int color) {
        renderScaledTextFloat(drawContext, client, text, x, y, scale, color);
    }
}
