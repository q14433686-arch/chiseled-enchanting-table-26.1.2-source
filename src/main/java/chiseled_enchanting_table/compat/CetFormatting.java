package chiseled_enchanting_table.compat;

import net.minecraft.network.chat.Style;

/**
 * 26.2 compatibility shim for the removed {@code net.minecraft.ChatFormatting} enum.
 *
 * <p>The 26.3 / 26.1.2 feature batches style every tooltip / HUD / lore line with
 * {@code .withStyle(ChatFormatting.GOLD)} etc. Minecraft 26.2 removed that enum; the
 * 26.2 idiom is {@code Style#withColor(int)} with a full {@code 0xAARRGGBB} literal
 * (see {@code CURRENT_BASELINE.md} §9.3). To keep the ported GUI sources structurally
 * identical to the mother line (so future syncs stay a clean diff), this shim exposes
 * {@link Style} constants named exactly like the old enum constants and int ARGB
 * companions for the few {@code Style#withColor(ChatFormatting.X)} call sites.</p>
 *
 * <p>Only color values are used by the port (no bold/italic flags), so
 * {@code Style.EMPTY.withColor(argb)} is behaviourally equivalent to applying the
 * corresponding {@code ChatFormatting} to a component.</p>
 */
public final class CetFormatting {
    public static final int BLACK_ARGB       = 0xFF000000;
    public static final int DARK_BLUE_ARGB   = 0xFF0000AA;
    public static final int DARK_GREEN_ARGB  = 0xFF00AA00;
    public static final int DARK_AQUA_ARGB   = 0xFF00AAAA;
    public static final int DARK_RED_ARGB    = 0xFFAA0000;
    public static final int DARK_PURPLE_ARGB = 0xFFAA00AA;
    public static final int GOLD_ARGB        = 0xFFFFAA00;
    public static final int GRAY_ARGB        = 0xFFAAAAAA;
    public static final int DARK_GRAY_ARGB   = 0xFF555555;
    public static final int BLUE_ARGB        = 0xFF5555FF;
    public static final int GREEN_ARGB       = 0xFF55FF55;
    public static final int AQUA_ARGB        = 0xFF55FFFF;
    public static final int RED_ARGB         = 0xFFFF5555;
    public static final int LIGHT_PURPLE_ARGB= 0xFFFF55FF;
    public static final int YELLOW_ARGB      = 0xFFFFFF55;
    public static final int WHITE_ARGB       = 0xFFFFFFFF;

    public static final Style BLACK        = style(BLACK_ARGB);
    public static final Style DARK_BLUE    = style(DARK_BLUE_ARGB);
    public static final Style DARK_GREEN   = style(DARK_GREEN_ARGB);
    public static final Style DARK_AQUA    = style(DARK_AQUA_ARGB);
    public static final Style DARK_RED     = style(DARK_RED_ARGB);
    public static final Style DARK_PURPLE  = style(DARK_PURPLE_ARGB);
    public static final Style GOLD         = style(GOLD_ARGB);
    public static final Style GRAY         = style(GRAY_ARGB);
    public static final Style DARK_GRAY    = style(DARK_GRAY_ARGB);
    public static final Style BLUE         = style(BLUE_ARGB);
    public static final Style GREEN        = style(GREEN_ARGB);
    public static final Style AQUA         = style(AQUA_ARGB);
    public static final Style RED          = style(RED_ARGB);
    public static final Style LIGHT_PURPLE = style(LIGHT_PURPLE_ARGB);
    public static final Style YELLOW       = style(YELLOW_ARGB);
    public static final Style WHITE        = style(WHITE_ARGB);

    private static Style style(int argb) { return Style.EMPTY.withColor(argb); }

    private CetFormatting() {}
}
