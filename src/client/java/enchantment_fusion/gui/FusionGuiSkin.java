package enchantment_fusion.gui;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.model.PayloadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Pixel-art GUI sprite identifiers, named 9-slice {@link NineSliceSprite} tokens (TXT-5),
 * unified typography scale tokens (TXT-1 / TXT-2 / TXT-3), shared {@link HitBox} geometry (GUI-01),
 * and client-only payload palette helpers (CPL-4) for the Enchantment Fusion Table and Grimoire.
 * Uses exclusively genuine pixel-art PNG textures.
 */
public final class FusionGuiSkin {
    /**
     * Shared UI rectangle for hit-testing and pixel-art button rendering across screens (GUI-01).
     */
    public record HitBox(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public boolean containsAt(int originX, int originY, double mx, double my) {
            int gx = originX + x;
            int gy = originY + y;
            return mx >= gx && mx < gx + w && my >= gy && my < gy + h;
        }
    }

    /**
     * Self-describing 9-slice texture token bundling sprite ID, native dimensions, and corner border (TXT-5).
     */
    public record NineSliceSprite(Identifier sprite, int texW, int texH, int border) {}

    /**
     * Result of bounded text fitting, exposing whether truncation occurred so callers can offer full hover tooltips.
     */
    public record FittedText(String text, float scale, boolean truncated) {}

    public static final Identifier PANEL_FRAME = EnchantmentFusion.id("textures/gui/panel_frame.png");
    public static final Identifier GRIMOIRE_SPREAD = EnchantmentFusion.id("textures/gui/grimoire_spread.png");

    public static final Identifier SLOT_PRIMARY = EnchantmentFusion.id("textures/gui/slot_primary.png");
    public static final Identifier SLOT_SECONDARY = EnchantmentFusion.id("textures/gui/slot_secondary.png");
    public static final Identifier SLOT_CATALYST = EnchantmentFusion.id("textures/gui/slot_catalyst.png");
    public static final Identifier SLOT_DEFAULT = EnchantmentFusion.id("textures/gui/slot_default.png");

    public static final Identifier BTN_SWAP_NORMAL = EnchantmentFusion.id("textures/gui/swap_button.png");
    public static final Identifier BTN_SWAP_HOVER = EnchantmentFusion.id("textures/gui/swap_button_hover.png");
    public static final Identifier BTN_SWAP_DISABLED = EnchantmentFusion.id("textures/gui/swap_button_disabled.png");

    public static final Identifier BTN_NORMAL = EnchantmentFusion.id("textures/gui/button_normal.png");
    public static final Identifier BTN_HOVER = EnchantmentFusion.id("textures/gui/button_hover.png");
    public static final Identifier BTN_DISABLED = EnchantmentFusion.id("textures/gui/button_disabled.png");

    public static final Identifier VESSEL_EMPTY = EnchantmentFusion.id("textures/gui/vessel_gem_empty.png");
    public static final Identifier VESSEL_FILLED = EnchantmentFusion.id("textures/gui/vessel_gem_filled.png");

    // 64x22 top folder tabs
    public static final Identifier TAB_ACTIVE = EnchantmentFusion.id("textures/gui/tab_active.png");
    public static final Identifier TAB_HOVER = EnchantmentFusion.id("textures/gui/tab_hover.png");
    public static final Identifier TAB_INACTIVE = EnchantmentFusion.id("textures/gui/tab_inactive.png");

    // 64x18 Mode C enchantment list picker rows (9-slice border = 4)
    public static final Identifier ROW_UNSELECTED = EnchantmentFusion.id("textures/gui/row_unselected.png");
    public static final Identifier ROW_HOVER = EnchantmentFusion.id("textures/gui/row_hover.png");
    public static final Identifier ROW_SELECTED_A = EnchantmentFusion.id("textures/gui/row_selected_a.png");
    public static final Identifier ROW_SELECTED_B = EnchantmentFusion.id("textures/gui/row_selected_b.png");

    // 96x40 ornate Vessel Slot socket cards (9-slice border = 7)
    public static final Identifier SOCKET_EMPTY = EnchantmentFusion.id("textures/gui/socket_card_empty.png");
    public static final Identifier SOCKET_FILLED = EnchantmentFusion.id("textures/gui/socket_card_filled.png");
    public static final Identifier SOCKET_ABERRANT = EnchantmentFusion.id("textures/gui/socket_card_aberrant.png");

    // Pixel-Art UI Micro-Effect Sprites
    public static final Identifier VFX_RUNE_HALO = EnchantmentFusion.id("textures/gui/vfx_rune_halo.png");
    public static final Identifier VFX_ARCANE_SPARK = EnchantmentFusion.id("textures/gui/vfx_arcane_spark.png");
    public static final Identifier VFX_SHOCKWAVE_RING = EnchantmentFusion.id("textures/gui/vfx_shockwave_ring.png");

    // Named 9-slice sprite tokens (TXT-5)
    public static final NineSliceSprite SLICE_PANEL_MAIN = new NineSliceSprite(PANEL_FRAME, 48, 48, 6);
    public static final NineSliceSprite SLICE_PANEL_INSET = new NineSliceSprite(PANEL_FRAME, 48, 48, 5);
    public static final NineSliceSprite SLICE_BTN_NORMAL = new NineSliceSprite(BTN_NORMAL, 32, 32, 4);
    public static final NineSliceSprite SLICE_BTN_HOVER = new NineSliceSprite(BTN_HOVER, 32, 32, 4);
    public static final NineSliceSprite SLICE_BTN_DISABLED = new NineSliceSprite(BTN_DISABLED, 32, 32, 4);
    public static final NineSliceSprite SLICE_ROW_UNSELECTED = new NineSliceSprite(ROW_UNSELECTED, 64, 18, 4);
    public static final NineSliceSprite SLICE_ROW_HOVER = new NineSliceSprite(ROW_HOVER, 64, 18, 4);
    public static final NineSliceSprite SLICE_ROW_SELECTED_A = new NineSliceSprite(ROW_SELECTED_A, 64, 18, 4);
    public static final NineSliceSprite SLICE_ROW_SELECTED_B = new NineSliceSprite(ROW_SELECTED_B, 64, 18, 4);
    public static final NineSliceSprite SLICE_SOCKET_EMPTY = new NineSliceSprite(SOCKET_EMPTY, 96, 40, 7);
    public static final NineSliceSprite SLICE_SOCKET_FILLED = new NineSliceSprite(SOCKET_FILLED, 96, 40, 7);
    public static final NineSliceSprite SLICE_SOCKET_ABERRANT = new NineSliceSprite(SOCKET_ABERRANT, 96, 40, 7);

    // Unified named typography scale tokens & shadow threshold (TXT-1 / TXT-2 / TXT-3)
    public static final float MIN_TEXT_SCALE = 0.54F;
    public static final float SCALE_MICRO = 0.54F;
    public static final float SCALE_SMALL = 0.58F;
    public static final float SCALE_BODY = 0.64F;
    public static final float SCALE_LABEL = 0.72F;
    public static final float SCALE_TITLE = 0.85F;
    public static final float SHADOW_MIN_SCALE = 0.75F;

    public static final int COLOR_GOLD = 0xFFFFDF88;
    public static final int COLOR_CREAM = 0xFFF6EFE2;
    public static final int COLOR_CYAN = 0xFF7CF0FF;
    public static final int COLOR_AMETHYST = 0xFFE3B2FF;
    public static final int COLOR_GREEN = 0xFF8CFFB0;
    public static final int COLOR_WARN = 0xFFFFB86C;
    public static final int COLOR_RED = 0xFFFF7A7A;
    public static final int COLOR_MUTED = 0xFFB5AC9E;

    // Warm dark sepia & illuminated inks for the parchment pages of grimoire_spread.png
    public static final int COLOR_INK_TITLE = 0xFF3A2210;
    public static final int COLOR_INK_BODY = 0xFF4E331B;
    public static final int COLOR_INK_GOLD = 0xFF8C5308;
    public static final int COLOR_INK_TEAL = 0xFF165B63;
    public static final int COLOR_INK_PURPLE = 0xFF5D2675;
    public static final int COLOR_INK_MUTED = 0xFF7B6752;

    private static final float GLYPH_HEIGHT = 8.0F;

    private FusionGuiSkin() {}

    /** Client-only accent color mapping for secondary {@link PayloadType} domains (CPL-4). */
    public static int payloadAccentArgb(PayloadType payload) {
        if (payload == null) {
            return COLOR_CREAM;
        }
        return switch (payload) {
            case PYRO -> 0xFFFF7A36;
            case CRYO -> 0xFF72E4FF;
            case VOLT -> 0xFFFFEA5A;
            case GALE -> 0xFFB8F7E6;
            case TIDE -> 0xFF3FA9FF;
            case RETURN -> 0xFF6CF2B5;
            case PIERCE -> 0xFFFF6B8B;
            case SCATTER -> 0xFFFFB454;
            case GRAVITY -> 0xFFB388FF;
            case EDGE -> 0xFFFFD56B;
            case BANE -> 0xFFD67BFF;
            case FORTUNE -> 0xFF7DFF7A;
            case AEGIS -> 0xFF8CC8FF;
            case MEND -> 0xFF9EFFB0;
        };
    }

    public static boolean isShiftHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);
    }

    public static boolean isCtrlOrCmdHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSUPER)
            || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSUPER);
    }

    public static void drawSprite(GuiGraphicsExtractor ctx, Identifier sprite, int x, int y, int w, int h) {
        ctx.blit(sprite, x, y, x + w, y + h, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    public static void drawNineSlice(GuiGraphicsExtractor ctx, NineSliceSprite slice, int x, int y, int w, int h) {
        drawNineSlice(ctx, slice.sprite(), x, y, w, h, slice.texW(), slice.texH(), slice.border());
    }

    /**
     * Renders a pixel-art 9-slice frame preserving the 4 corner regions at 1:1 pixel ratio while
     * stretching the 4 edge strips and interior body across {@code (w, h)} (TXT-5).
     */
    public static void drawNineSlice(
        GuiGraphicsExtractor ctx,
        Identifier sprite,
        int x,
        int y,
        int w,
        int h,
        int texW,
        int texH,
        int border
    ) {
        int b = Math.min(border, Math.min(w / 2, h / 2));
        float u1 = (float) b / (float) texW;
        float u2 = (float) (texW - b) / (float) texW;
        float v1 = (float) b / (float) texH;
        float v2 = (float) (texH - b) / (float) texH;

        int x1 = x + b;
        int x2 = x + w - b;
        int y1 = y + b;
        int y2 = y + h - b;

        ctx.blit(sprite, x, y, x1, y1, 0.0F, u1, 0.0F, v1);
        ctx.blit(sprite, x2, y, x + w, y1, u2, 1.0F, 0.0F, v1);
        ctx.blit(sprite, x, y2, x1, y + h, 0.0F, u1, v2, 1.0F);
        ctx.blit(sprite, x2, y2, x + w, y + h, u2, 1.0F, v2, 1.0F);

        if (x2 > x1) {
            ctx.blit(sprite, x1, y, x2, y1, u1, u2, 0.0F, v1);
            ctx.blit(sprite, x1, y2, x2, y + h, u1, u2, v2, 1.0F);
        }
        if (y2 > y1) {
            ctx.blit(sprite, x, y1, x1, y2, 0.0F, u1, v1, v2);
            ctx.blit(sprite, x2, y1, x + w, y2, u2, 1.0F, v1, v2);
        }
        if (x2 > x1 && y2 > y1) {
            ctx.blit(sprite, x1, y1, x2, y2, u1, u2, v1, v2);
        }
    }

    public static void drawButtonBox(
        GuiGraphicsExtractor ctx,
        Font font,
        int originX,
        int originY,
        HitBox box,
        boolean enabled,
        boolean hovered,
        String label,
        float scale,
        int enabledColor
    ) {
        NineSliceSprite slice = !enabled ? SLICE_BTN_DISABLED : (hovered ? SLICE_BTN_HOVER : SLICE_BTN_NORMAL);
        int gx = originX + box.x();
        int gy = originY + box.y();
        drawNineSlice(ctx, slice, gx, gy, box.w(), box.h());
        drawCenteredBoundedText(
            ctx, font, label,
            gx + 3, box.w() - 6, gy + box.h() * 0.5F,
            scale, enabled ? enabledColor : COLOR_MUTED
        );
    }

    /**
     * Unified text-fitting solver shared by centered and left-aligned bounded labels (TXT-1 / TXT-2).
     * Never shrinks below {@link #MIN_TEXT_SCALE}; appends an ellipsis only after hitting the floor.
     */
    public static FittedText fitBoundedText(Font font, String text, int maxW, float maxScale) {
        if (text == null || text.isEmpty() || maxW <= 4) {
            return new FittedText("", MIN_TEXT_SCALE, false);
        }
        int rawW = font.width(text);
        if (rawW <= 0) {
            return new FittedText("", MIN_TEXT_SCALE, false);
        }
        float effectiveMax = Math.max(MIN_TEXT_SCALE, maxScale);
        if (rawW * effectiveMax <= maxW) {
            return new FittedText(text, effectiveMax, false);
        }
        float fit = (float) maxW / (float) rawW;
        if (fit >= MIN_TEXT_SCALE) {
            return new FittedText(text, fit, false);
        }
        int maxRaw = Math.max(8, Math.round((maxW / MIN_TEXT_SCALE) - font.width("\u2026")));
        String clipped = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
        return new FittedText(clipped, MIN_TEXT_SCALE, true);
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
        FittedText fitted = fitBoundedText(font, text, boxW, maxScale);
        if (fitted.text().isEmpty()) {
            return;
        }
        float drawX = boxX + (boxW - font.width(fitted.text()) * fitted.scale()) * 0.5F;
        float drawY = centerY - (GLYPH_HEIGHT * fitted.scale()) * 0.5F;
        drawScaledText(ctx, font, fitted.text(), drawX, drawY, fitted.scale(), argbColor);
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
        FittedText fitted = fitBoundedText(font, text, maxW, maxScale);
        if (fitted.text().isEmpty()) {
            return;
        }
        drawScaledText(ctx, font, fitted.text(), x, y, fitted.scale(), argbColor);
    }

    /**
     * Automatically disables the 1px font drop-shadow below {@link #SHADOW_MIN_SCALE} so scaled-down
     * CJK and Latin glyphs stay crisp instead of muddying into half-pixel shadow bleed (TXT-3).
     */
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
        ctx.text(font, text, 0, 0, argbColor, scale >= SHADOW_MIN_SCALE);
        pose.popMatrix();
    }
}
