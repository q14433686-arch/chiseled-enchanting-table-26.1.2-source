package enchantment_fusion.gui;

import enchantment_fusion.EnchantmentFusion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Pixel-art sprite identifiers and 9-slice rendering helpers for the Enchantment Fusion Table UI.
 * All paths map 1:1 to real PNG files under assets/enchantment_fusion/textures/gui/*.png
 * (MC 26.1.2 GuiGraphicsExtractor#blit resolves Identifier paths verbatim under assets/<ns>/).
 * Zero solid-color rectangle placeholders are used.
 */
public final class FusionGuiSkin {
    public static final int PANEL_W = 248;
    public static final int PANEL_H = 204;

    // 48x48 9-slice obsidian & gilded arcane panel frame (corner border = 6px)
    public static final Identifier PANEL_FRAME = EnchantmentFusion.id("textures/gui/panel_frame.png");

    // 18x18 role-specific item slot bezels
    public static final Identifier SLOT_PRIMARY = EnchantmentFusion.id("textures/gui/slot_primary.png");
    public static final Identifier SLOT_SECONDARY = EnchantmentFusion.id("textures/gui/slot_secondary.png");
    public static final Identifier SLOT_CATALYST = EnchantmentFusion.id("textures/gui/slot_catalyst.png");
    public static final Identifier SLOT_DEFAULT = EnchantmentFusion.id("textures/gui/slot_default.png");

    // 24x24 [⇄] Swap-Order button states (embedded pixel-art gold/cyan/amethyst swap icon)
    public static final Identifier BTN_SWAP = EnchantmentFusion.id("textures/gui/swap_button.png");
    public static final Identifier BTN_SWAP_HOVER = EnchantmentFusion.id("textures/gui/swap_button_hover.png");
    public static final Identifier BTN_SWAP_DISABLED = EnchantmentFusion.id("textures/gui/swap_button_disabled.png");

    // 32x32 9-slice action button plaques (corner border = 4px)
    public static final Identifier BTN_ACTION = EnchantmentFusion.id("textures/gui/button_normal.png");
    public static final Identifier BTN_ACTION_HOVER = EnchantmentFusion.id("textures/gui/button_hover.png");
    public static final Identifier BTN_ACTION_DISABLED = EnchantmentFusion.id("textures/gui/button_disabled.png");

    // 12x12 Vessel Spirit gem slots
    public static final Identifier VESSEL_EMPTY = EnchantmentFusion.id("textures/gui/vessel_gem_empty.png");
    public static final Identifier VESSEL_FILLED = EnchantmentFusion.id("textures/gui/vessel_gem_filled.png");

    private FusionGuiSkin() {}

    /**
     * Renders a pixel-art 9-slice frame so corners stay crisp at 1:1 pixel ratio without stretching.
     */
    public static void drawNineSlice(
        GuiGraphicsExtractor ctx,
        Identifier sprite,
        int x,
        int y,
        int w,
        int h,
        int texSize,
        int border
    ) {
        int b = Math.min(border, Math.min(w / 2, h / 2));
        float u1 = (float) b / (float) texSize;
        float u2 = (float) (texSize - b) / (float) texSize;

        int[] xs = {x, x + b, x + w - b, x + w};
        int[] ys = {y, y + b, y + h - b, y + h};
        float[] us = {0.0f, u1, u2, 1.0f};

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (xs[col + 1] > xs[col] && ys[row + 1] > ys[row]) {
                    ctx.blit(
                        sprite,
                        xs[col],
                        ys[row],
                        xs[col + 1],
                        ys[row + 1],
                        us[col],
                        us[col + 1],
                        us[row],
                        us[row + 1]
                    );
                }
            }
        }
    }
}
