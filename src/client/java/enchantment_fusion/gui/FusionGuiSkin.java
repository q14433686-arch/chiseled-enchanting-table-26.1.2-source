package enchantment_fusion.gui;

import enchantment_fusion.EnchantmentFusion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Pixel-art GUI sprite identifiers and bounded text helpers for the Enchantment Fusion Table.
 * Uses exclusively genuine pixel-art PNG textures (zero raw solid-color placeholder boxes).
 */
public final class FusionGuiSkin {
    public static final Identifier PANEL_BG = EnchantmentFusion.id("gui/fusion_panel.png");
    public static final Identifier SLOT_FRAME = EnchantmentFusion.id("gui/slot_frame.png");

    public static final Identifier BTN_SWAP_NORMAL = EnchantmentFusion.id("gui/btn_swap_normal.png");
    public static final Identifier BTN_SWAP_HOVER = EnchantmentFusion.id("gui/btn_swap_hover.png");

    public static final Identifier BTN_FUSE_NORMAL = EnchantmentFusion.id("gui/btn_fuse_normal.png");
    public static final Identifier BTN_FUSE_HOVER = EnchantmentFusion.id("gui/btn_fuse_hover.png");
    public static final Identifier BTN_FUSE_DISABLED = EnchantmentFusion.id("gui/btn_fuse_disabled.png");

    public static final Identifier BTN_SMALL_NORMAL = EnchantmentFusion.id("gui/btn_action_small_normal.png");
    public static final Identifier BTN_SMALL_HOVER = EnchantmentFusion.id("gui/btn_action_small_hover.png");
    public static final Identifier BTN_SMALL_DISABLED = EnchantmentFusion.id("gui/btn_action_small_disabled.png");

    public static final Identifier VESSEL_EMPTY = EnchantmentFusion.id("gui/vessel_empty.png");
    public static final Identifier VESSEL_FILLED = EnchantmentFusion.id("gui/vessel_filled.png");
    public static final Identifier VESSEL_ABERRANT = EnchantmentFusion.id("gui/vessel_aberrant.png");

    public static final int COLOR_GOLD = 0xFFFFDF88;
    public static final int COLOR_CREAM = 0xFFF6EFE2;
    public static final int COLOR_CYAN = 0xFF7CF0FF;
    public static final int COLOR_AMETHYST = 0xFFE3B2FF;
    public static final int COLOR_GREEN = 0xFF8CFFB0;
    public static final int COLOR_WARN = 0xFFFFB86C;
    public static final int COLOR_RED = 0xFFFF7A7A;
    public static final int COLOR_MUTED = 0xFFB5AC9E;

    private static final float GLYPH_HEIGHT = 8.0F;

    private FusionGuiSkin() {}

    public static void drawSprite(GuiGraphicsExtractor ctx, Identifier sprite, int x, int y, int w, int h) {
        ctx.blit(sprite, x, y, x + w, y + h, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    public static void drawCenteredBoundedText(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        int boxX,
        int boxW,
        float centerY,
        float maxScale,
        int argbColor
    ) {
        if (text == null || text.isEmpty() || boxW <= 4) {
            return;
        }
        int rawW = font.width(text);
        if (rawW <= 0) {
            return;
        }
        float minScale = 0.50F;
        float scale = maxScale;
        String shown = text;
        if (rawW * scale > boxW) {
            float fit = (float) boxW / (float) rawW;
            if (fit >= minScale) {
                scale = fit;
            } else {
                scale = minScale;
                int maxRaw = Math.max(8, Math.round(boxW / scale) - font.width("\u2026"));
                shown = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
            }
        }
        float drawX = boxX + (boxW - font.width(shown) * scale) * 0.5F;
        float drawY = centerY - (GLYPH_HEIGHT * scale) * 0.5F;
        drawScaledText(ctx, font, shown, drawX, drawY, scale, argbColor);
    }

    public static void drawLeftBoundedText(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        int x,
        int y,
        int maxW,
        float maxScale,
        int argbColor
    ) {
        if (text == null || text.isEmpty() || maxW <= 4) {
            return;
        }
        int rawW = font.width(text);
        if (rawW <= 0) {
            return;
        }
        float minScale = 0.52F;
        float scale = maxScale;
        String shown = text;
        if (rawW * scale > maxW) {
            float fit = (float) maxW / (float) rawW;
            if (fit >= minScale) {
                scale = fit;
            } else {
                scale = minScale;
                int maxRaw = Math.max(8, Math.round(maxW / scale) - font.width("\u2026"));
                shown = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
            }
        }
        drawScaledText(ctx, font, shown, x, y, scale, argbColor);
    }

    private static void drawScaledText(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        float x,
        float y,
        float scale,
        int argbColor
    ) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(font, text, 0, 0, argbColor, true);
        pose.popMatrix();
    }
}
