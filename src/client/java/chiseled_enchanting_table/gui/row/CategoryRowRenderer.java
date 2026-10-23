package chiseled_enchanting_table.gui.row;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Renders a horizontal collapsible category header row per enchantment attribute, with a crisp
 * stepped disclosure triangle ({@code ▶} folded / {@code ▼} unfolded), base enchantment title,
 * and right-aligned tier/essence summary strictly bounded inside the frame.
 */
public final class CategoryRowRenderer {
    private CategoryRowRenderer() {}

    public static void render(
        GuiGraphicsExtractor ctx,
        Minecraft client,
        int dx,
        int dy,
        int dw,
        int dh,
        boolean expanded,
        boolean hovered,
        boolean isFocused,
        boolean cleanseTheme,
        String categoryTitle,
        String rightBadgeText,
        int rightBadgeColor,
        int refundPreviewPoints
    ) {
        Identifier bg = cleanseTheme
            ? (expanded ? RowRenderSupport.ROW_CLEANSE_CATEGORY_OPEN : RowRenderSupport.ROW_CLEANSE_CATEGORY_CLOSED)
            : (expanded ? RowRenderSupport.ROW_CATEGORY_OPEN : RowRenderSupport.ROW_CATEGORY_CLOSED);

        ctx.blit(bg, dx, dy, dx + dw, dy + dh, 0.0f, 1.0f, 0.0f, 1.0f);
        if (hovered) {
            ctx.fill(dx + 2, dy + 2, dx + dw - 2, dy + dh - 2, cleanseTheme ? 0x1E7CFFA6 : 0x1EFFE28A);
        }
        if (isFocused) {
            int gold = 0xFFFFDF7E;
            ctx.fill(dx, dy, dx + dw, dy + 1, gold);
            ctx.fill(dx, dy + dh - 1, dx + dw, dy + dh, gold);
            ctx.fill(dx, dy, dx + 1, dy + dh, gold);
            ctx.fill(dx + dw - 1, dy, dx + dw, dy + dh, gold);
        }

        ctx.enableScissor(dx + 3, dy + 3, dx + dw - 3, dy + dh - 3);
        try {
            float midY = RowRenderSupport.rowCenterY(dy, dh);
            int triColor = cleanseTheme
                ? (expanded ? 0xFF9EFFDB : 0xFF66D9B2)
                : (expanded ? RowRenderSupport.EPIPHANY_GOLD : 0xFFD6B8FF);
            RowRenderSupport.drawDisclosureTriangle(ctx, dx + 9, midY, expanded, triColor);

            int sepX = RowRenderSupport.xpSeparatorX(dx, dw);
            ctx.fill(sepX, dy + 5, sepX + 1, dy + dh - 5, cleanseTheme ? 0x405CE6B8 : 0x429480B8);

            int nameX = dx + 18;
            int maxTitleW = Math.max(24, sepX - 4 - nameX);
            int titleColor = cleanseTheme
                ? (expanded ? RowRenderSupport.JADE_MINT : 0xFFD9FFF2)
                : (expanded ? RowRenderSupport.EPIPHANY_GOLD : RowRenderSupport.WHITE);
            RowRenderSupport.renderCenteredYText(ctx, client, categoryTitle, nameX, midY, 0.68f, maxTitleW, titleColor);

            if (refundPreviewPoints > 0) {
                int iconSize = 9;
                int iconX = sepX + 3;
                int iconY = Math.round(midY - iconSize * 0.5f);
                ctx.blit(RowRenderSupport.XP_REFUND_ORB, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
                RowRenderSupport.renderRightAlignedCenteredYText(
                    ctx,
                    client,
                    "+" + refundPreviewPoints,
                    iconX + iconSize + 2,
                    dx + dw - 5,
                    midY,
                    0.58f,
                    RowRenderSupport.XP_GREEN
                );
            } else if (rightBadgeText != null && !rightBadgeText.isEmpty()) {
                RowRenderSupport.renderCenteredBoxText(
                    ctx,
                    client,
                    rightBadgeText,
                    sepX + 2,
                    RowRenderSupport.XP_BADGE_WIDTH - 5,
                    midY,
                    0.56f,
                    rightBadgeColor
                );
            }
        } finally {
            ctx.disableScissor();
        }
    }
}
