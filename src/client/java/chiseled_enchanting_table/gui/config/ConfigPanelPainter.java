package chiseled_enchanting_table.gui.config;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Stateless panel skin and typography; no editor, input, persistence or authority decisions. */
final class ConfigPanelPainter {
    private ConfigPanelPainter() {}
    private static final int BACKDROP_WASH = 0x6E0B0715;
    private static final float GLYPH_INK_H = 8.0f;
    private static final Identifier BACKDROP_TILE = ChiseledEnchantingTable.identifier("gui/config/backdrop_tile.png");
    private static final Identifier DIVIDER_ORNAMENT = ChiseledEnchantingTable.identifier("gui/config/divider_ornament.png");

    // ---- Nine-Slice & Tiled Layer Primitives ----

    static void blitNineSliceRect(
        GuiGraphicsExtractor ctx,
        Identifier texture,
        int x,
        int y,
        int w,
        int h,
        int cornerX,
        int cornerY,
        int texW,
        int texH
    ) {
        if (w <= 0 || h <= 0) return;
        int cx = Math.min(cornerX, w / 2);
        int cy = Math.min(cornerY, h / 2);
        float u0 = (float) cx / (float) texW;
        float u1 = 1.0f - u0;
        float v0 = (float) cy / (float) texH;
        float v1 = 1.0f - v0;

        // 4 corners
        ctx.blit(texture, x, y, x + cx, y + cy, 0.0f, u0, 0.0f, v0);
        ctx.blit(texture, x + w - cx, y, x + w, y + cy, u1, 1.0f, 0.0f, v0);
        ctx.blit(texture, x, y + h - cy, x + cx, y + h, 0.0f, u0, v1, 1.0f);
        ctx.blit(texture, x + w - cx, y + h - cy, x + w, y + h, u1, 1.0f, v1, 1.0f);

        // Top & bottom edges
        if (w > cx * 2) {
            ctx.blit(texture, x + cx, y, x + w - cx, y + cy, u0, u1, 0.0f, v0);
            ctx.blit(texture, x + cx, y + h - cy, x + w - cx, y + h, u0, u1, v1, 1.0f);
        }
        // Left & right edges
        if (h > cy * 2) {
            ctx.blit(texture, x, y + cy, x + cx, y + h - cy, 0.0f, u0, v0, v1);
            ctx.blit(texture, x + w - cx, y + cy, x + w, y + h - cy, u1, 1.0f, v0, v1);
        }
        // Center fill
        if (w > cx * 2 && h > cy * 2) {
            ctx.blit(texture, x + cx, y + cy, x + w - cx, y + h - cy, u0, u1, v0, v1);
        }
    }

    static void blitTiledBackdrop(GuiGraphicsExtractor ctx, Box area) {
        int tileSize = 64;
        for (int ty = area.y(); ty < area.bottom(); ty += tileSize) {
            int drawH = Math.min(tileSize, area.bottom() - ty);
            float vMax = (float) drawH / (float) tileSize;
            for (int tx = area.x(); tx < area.right(); tx += tileSize) {
                int drawW = Math.min(tileSize, area.right() - tx);
                float uMax = (float) drawW / (float) tileSize;
                ctx.blit(BACKDROP_TILE, tx, ty, tx + drawW, ty + drawH, 0.0f, uMax, 0.0f, vMax);
            }
        }
        ctx.fill(area.x(), area.y(), area.right(), area.bottom(), BACKDROP_WASH);
    }

    static void blitOrnamentDivider(GuiGraphicsExtractor ctx, Box divBox) {
        int cy = Math.round(divBox.centerY());
        ctx.fill(divBox.x(), cy, divBox.right(), cy + 1, 0x665E4788);
        int ornW = Math.min(136, divBox.w() - 16);
        if (ornW >= 48) {
            int ox = Math.round(divBox.centerX() - ornW * 0.5f);
            ctx.blit(DIVIDER_ORNAMENT, ox, cy - 5, ox + ornW, cy + 6, 0.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    // ---- Typography Helpers Enforcing >= 0.85x Primary & >= 0.75x Secondary Floors ----

    static String fitTextAtScale(Font font, String text, float scale, int maxWidthPx) {
        if (text == null || text.isEmpty() || maxWidthPx <= 4) return "";
        int maxRaw = Math.max(4, (int) Math.floor(maxWidthPx / scale));
        if (font.width(text) <= maxRaw) {
            return text;
        }
        String ellipsis = "\u2026";
        int ellW = font.width(ellipsis);
        int subW = Math.max(2, maxRaw - ellW);
        return font.plainSubstrByWidth(text, subW) + ellipsis;
    }

    static float chooseClampedScale(Font font, String text, float preferredScale, float minScale, int maxWidthPx) {
        if (text == null || text.isEmpty() || maxWidthPx <= 4) return preferredScale;
        int rawW = Math.max(1, font.width(text));
        if (rawW * preferredScale <= maxWidthPx) {
            return preferredScale;
        }
        return Math.max(minScale, Math.min(preferredScale, (float) maxWidthPx / (float) rawW));
    }

    static void drawClampedLeftText(Font font,
        GuiGraphicsExtractor ctx,
        String text,
        Box box,
        float preferredScale,
        float minScale,
        int argbColor
    ) {
        if (text == null || text.isEmpty() || box.w() <= 4) return;
        float scale = chooseClampedScale(font, text, preferredScale, minScale, box.w());
        String fitted = fitTextAtScale(font, text, scale, box.w());
        if (fitted.isEmpty()) return;
        float drawY = box.centerY() - (GLYPH_INK_H * scale) * 0.5f;
        drawScaledString(font, ctx, fitted, box.x(), drawY, scale, argbColor);
    }

    static void drawClampedCenteredText(Font font,
        GuiGraphicsExtractor ctx,
        String text,
        Box box,
        float preferredScale,
        float minScale,
        int argbColor
    ) {
        if (text == null || text.isEmpty() || box.w() <= 4) return;
        float scale = chooseClampedScale(font, text, preferredScale, minScale, box.w());
        String fitted = fitTextAtScale(font, text, scale, box.w());
        if (fitted.isEmpty()) return;
        float renderedW = font.width(fitted) * scale;
        float drawX = box.x() + (box.w() - renderedW) * 0.5f;
        float drawY = box.centerY() - (GLYPH_INK_H * scale) * 0.5f;
        drawScaledString(font, ctx, fitted, drawX, drawY, scale, argbColor);
    }

    static void drawScaledString(Font font, GuiGraphicsExtractor ctx, String text, float x, float y, float scale, int color) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

}
