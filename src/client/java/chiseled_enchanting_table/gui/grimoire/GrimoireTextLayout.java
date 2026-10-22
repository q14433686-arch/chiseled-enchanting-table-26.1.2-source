package chiseled_enchanting_table.gui.grimoire;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Optical typography and strict bounding-box layout engine for the illustrated manual.
 *
 * <p>Historical UI invariants enforced everywhere in this class:</p>
 * <ul>
 *   <li>Every color carries an explicit alpha byte ({@code 0xFF......} / {@code 0xCC......}).</li>
 *   <li>Dark ink on warm parchment renders with {@code dropShadow = false} so glyph edges stay
 *       razor-sharp at fractional scales with zero muddy dark halos.</li>
 *   <li>Optical vertical centering always anchors {@code GLYPH_INK_HEIGHT = 8.0f} rather than
 *       {@code font.lineHeight} (9px), keeping CJK &amp; Latin text mathematically centered.</li>
 *   <li>Multi-block prose columns automatically solve the largest crisp scale in
 *       {@code [0.58 .. 0.72]} that fits the single page cleanly; if an extreme locale still exceeds
 *       the viewport, vertical scrolling inside hardware {@code enableScissor} prevents 100% of
 *       border touches and overlaps.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class GrimoireTextLayout {

    /** Actual ink rows of Minecraft default + Unifont glyphs (rows 0..7 = 8px ink). */
    public static final float GLYPH_INK_HEIGHT = 8.0f;

    // Illuminated Vellum Palette (every constant carries explicit alpha!)
    public static final int INK_PRIMARY = 0xFF281B12;
    public static final int INK_SECONDARY = 0xFF5A4331;
    public static final int INK_MUTED = 0xFF7D634C;
    public static final int INK_GOLD_DARK = 0xFF7A4E10;
    public static final int INK_PURPLE = 0xFF4C236E;
    public static final int INK_EMERALD = 0xFF175E3E;
    public static final int INK_CRIMSON = 0xFF7D2020;

    public static final int LIGHT_CREAM = 0xFFFFF2D6;
    public static final int LIGHT_GOLD = 0xFFFFDF86;
    public static final int LIGHT_MINT = 0xFF9EFFDB;
    public static final int LIGHT_LAVENDER = 0xFFE6C8FF;

    public static final int CALLOUT_BG = 0xFFEADBC0;
    public static final int CALLOUT_BORDER = 0xFFC5A673;
    public static final int CARD_BG = 0xFFF7EEDB;
    public static final int CARD_HOVER_BG = 0xFFFFF7E6;
    public static final int CARD_BORDER = 0xFFCBA974;
    public static final int CARD_HOVER_BORDER = 0xFF9E6B1E;
    public static final int RULE_LINE = 0x88B89968;

    private static final float[] CANDIDATE_SCALES = {0.72f, 0.67f, 0.62f, 0.58f};

    public enum BlockKind {
        LEAD,
        SECTION_HEADER,
        STEP,
        BULLET,
        CALLOUT
    }

    public record PageBlock(
        BlockKind kind,
        String badge,
        int accentArgb,
        Component body
    ) {
        public static PageBlock lead(Component text) {
            return new PageBlock(BlockKind.LEAD, "", INK_PURPLE, text);
        }

        public static PageBlock header(Component title, int accentArgb) {
            return new PageBlock(BlockKind.SECTION_HEADER, "", accentArgb, title);
        }

        public static PageBlock step(int number, Component text) {
            return new PageBlock(BlockKind.STEP, String.valueOf(number), INK_PURPLE, text);
        }

        public static PageBlock bullet(String tag, int accentArgb, Component text) {
            return new PageBlock(BlockKind.BULLET, tag, accentArgb, text);
        }

        public static PageBlock callout(Component label, Component text, int accentArgb) {
            return new PageBlock(BlockKind.CALLOUT, label.getString(), accentArgb, text);
        }
    }

    public record ColumnRenderResult(float chosenScale, float totalHeight, float maxScroll) {}

    private GrimoireTextLayout() {}

    public static void drawScaledString(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        float x,
        float y,
        float scale,
        int argbColor,
        boolean shadow
    ) {
        if (text == null || text.isEmpty()) return;
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(font, text, 0, 0, argbColor, shadow);
        pose.popMatrix();
    }

    public static void drawScaledSequence(
        GuiGraphicsExtractor ctx,
        Font font,
        FormattedCharSequence seq,
        float x,
        float y,
        float scale,
        int argbColor,
        boolean shadow
    ) {
        if (seq == null) return;
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(font, seq, 0, 0, argbColor, shadow);
        pose.popMatrix();
    }

    /**
     * Left-aligned single-line text optically centered on {@code visualCenterY}, guaranteed never to
     * exceed {@code maxWidth}.
     */
    public static void drawLeftCenteredY(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        int x,
        float visualCenterY,
        float maxScale,
        int maxWidth,
        int argbColor,
        boolean shadow
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
                int maxRaw = Math.max(8, Math.round(maxWidth / scale) - font.width("\u2026"));
                finalStr = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
            }
        }
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        drawScaledString(ctx, font, finalStr, x, drawY, scale, argbColor, shadow);
    }

    /**
     * Horizontally AND optically vertically centered single-line text inside {@code [boxX .. boxX + boxW]}.
     */
    public static void drawCenteredBox(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        int boxX,
        int boxW,
        float visualCenterY,
        float maxScale,
        int argbColor,
        boolean shadow
    ) {
        if (text == null || text.isEmpty() || boxW <= 4) return;
        int rawW = font.width(text);
        if (rawW <= 0) return;

        float minScale = 0.46f;
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > boxW) {
            float needed = (float) boxW / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRaw = Math.max(8, Math.round(boxW / scale) - font.width("\u2026"));
                finalStr = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
            }
        }
        float renderedW = font.width(finalStr) * scale;
        float drawX = boxX + (boxW - renderedW) * 0.5f;
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        drawScaledString(ctx, font, finalStr, drawX, drawY, scale, argbColor, shadow);
    }

    /**
     * Right-aligned single-line text optically centered on {@code visualCenterY}.
     */
    public static void drawRightCenteredY(
        GuiGraphicsExtractor ctx,
        Font font,
        String text,
        int leftLimit,
        int rightEdge,
        float visualCenterY,
        float maxScale,
        int argbColor,
        boolean shadow
    ) {
        if (text == null || text.isEmpty()) return;
        int availW = Math.max(6, rightEdge - leftLimit);
        int rawW = Math.max(1, font.width(text));
        float scale = Math.min(maxScale, (float) availW / (float) rawW);
        float drawX = rightEdge - rawW * scale;
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        drawScaledString(ctx, font, text, drawX, drawY, scale, argbColor, shadow);
    }

    public static List<FormattedCharSequence> wrapAtScale(Font font, Component text, int pixelWidth, float scale) {
        int wrapUnits = Math.max(24, (int) Math.floor((pixelWidth - 1.0f) / scale));
        return font.split(text, wrapUnits);
    }

    /**
     * Draws wrapped paragraph lines strictly inside {@code [x .. x + width]} and returns the next Y.
     */
    public static float drawWrappedParagraph(
        GuiGraphicsExtractor ctx,
        Font font,
        Component text,
        int x,
        float startY,
        int width,
        float scale,
        float lineStep,
        int argbColor,
        boolean shadow
    ) {
        List<FormattedCharSequence> lines = wrapAtScale(font, text, width, scale);
        float y = startY;
        for (FormattedCharSequence line : lines) {
            drawScaledSequence(ctx, font, line, x, y, scale, argbColor, shadow);
            y += lineStep;
        }
        return y;
    }

    public static float measureBlocksHeight(Font font, List<PageBlock> blocks, int width, float bodyScale) {
        float lineStep = 10.2f * bodyScale;
        float y = 0.0f;
        for (PageBlock b : blocks) {
            switch (b.kind()) {
                case LEAD -> {
                    int innerW = width - 11;
                    int n = wrapAtScale(font, b.body(), innerW, bodyScale).size();
                    y += Math.max(12.0f, n * lineStep + 5.0f) + 4.5f;
                }
                case SECTION_HEADER -> {
                    if (y > 1.0f) y += 3.0f;
                    y += 12.5f;
                }
                case STEP, BULLET -> {
                    int textW = width - 15;
                    int n = wrapAtScale(font, b.body(), textW, bodyScale).size();
                    y += Math.max(11.5f, n * lineStep) + 2.6f;
                }
                case CALLOUT -> {
                    if (y > 1.0f) y += 2.5f;
                    int innerW = width - 12;
                    int n = wrapAtScale(font, b.body(), innerW, bodyScale * 0.95f).size();
                    float headerH = b.badge().isEmpty() ? 0.0f : 9.0f;
                    y += headerH + n * (lineStep * 0.95f) + 7.0f;
                }
            }
        }
        return y;
    }

    /**
     * Renders a structured chapter prose column inside hardware GPU scissor clipping, picking the
     * largest crisp font scale that fits cleanly on a single page and clamping scroll safely if a
     * verbose language needs extra vertical room.
     */
    public static ColumnRenderResult renderStructuredColumn(
        GuiGraphicsExtractor ctx,
        Minecraft mc,
        int colX,
        int colY,
        int colW,
        int colH,
        String chapterBadge,
        Component chapterTitle,
        List<PageBlock> blocks,
        float scrollOffset
    ) {
        Font font = mc.font;

        int headerH = 14;
        int pillW = Math.max(18, Math.round(font.width(chapterBadge) * 0.64f) + 8);
        ctx.fill(colX, colY, colX + pillW, colY + headerH, 0xFF4C236E);
        ctx.fill(colX + 1, colY + 1, colX + pillW - 1, colY + 2, 0xFF8B56BA);
        drawCenteredBox(ctx, font, chapterBadge, colX + 2, pillW - 4, colY + headerH * 0.5f, 0.64f, LIGHT_GOLD, false);

        drawLeftCenteredY(
            ctx,
            font,
            chapterTitle.getString(),
            colX + pillW + 5,
            colY + headerH * 0.5f,
            0.84f,
            colW - pillW - 6,
            INK_PRIMARY,
            false
        );

        int ruleY = colY + headerH + 2;
        ctx.fill(colX, ruleY, colX + colW, ruleY + 1, CARD_BORDER);
        ctx.fill(colX, ruleY, colX + 34, ruleY + 1, INK_GOLD_DARK);

        int bodyTop = ruleY + 4;
        int bodyH = Math.max(24, (colY + colH) - bodyTop);

        float chosenScale = CANDIDATE_SCALES[CANDIDATE_SCALES.length - 1];
        float contentH = 0.0f;
        for (float candidate : CANDIDATE_SCALES) {
            float h = measureBlocksHeight(font, blocks, colW - 4, candidate);
            chosenScale = candidate;
            contentH = h;
            if (h <= bodyH + 0.5f) {
                break;
            }
        }

        int textAreaW = contentH > bodyH + 1.0f ? (colW - 6) : colW;
        if (textAreaW != colW - 4) {
            contentH = measureBlocksHeight(font, blocks, textAreaW, chosenScale);
        }

        float maxScroll = Math.max(0.0f, contentH - bodyH);
        float clampedScroll = Mth.clamp(scrollOffset, 0.0f, maxScroll);
        float lineStep = 10.2f * chosenScale;

        ctx.enableScissor(colX, bodyTop, colX + colW, bodyTop + bodyH);
        try {
            float y = bodyTop - clampedScroll;
            for (PageBlock b : blocks) {
                switch (b.kind()) {
                    case LEAD -> {
                        int innerW = textAreaW - 11;
                        List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, chosenScale);
                        float boxH = Math.max(12.0f, lines.size() * lineStep + 5.0f);
                        int y0 = Math.round(y);
                        int y1 = Math.round(y + boxH);
                        ctx.fill(colX, y0, colX + textAreaW, y1, CALLOUT_BG);
                        ctx.fill(colX, y0, colX + 2, y1, b.accentArgb());
                        float ly = y + 2.8f;
                        for (FormattedCharSequence line : lines) {
                            drawScaledSequence(ctx, font, line, colX + 6, ly, chosenScale, INK_PRIMARY, false);
                            ly += lineStep;
                        }
                        y += boxH + 4.5f;
                    }
                    case SECTION_HEADER -> {
                        if (y > bodyTop - clampedScroll + 1.0f) {
                            y += 3.0f;
                        }
                        int hy = Math.round(y);
                        ctx.fill(colX, hy + 3, colX + 3, hy + 8, b.accentArgb());
                        drawLeftCenteredY(
                            ctx,
                            font,
                            b.body().getString(),
                            colX + 6,
                            hy + 5.5f,
                            Math.min(0.74f, chosenScale + 0.05f),
                            textAreaW - 8,
                            b.accentArgb(),
                            false
                        );
                        ctx.fill(colX, hy + 11, colX + textAreaW, hy + 12, RULE_LINE);
                        y += 12.5f;
                    }
                    case STEP -> {
                        int badgeSize = 10;
                        int bx = colX;
                        int by = Math.round(y);
                        ctx.fill(bx, by, bx + badgeSize, by + badgeSize, b.accentArgb());
                        ctx.fill(bx + 1, by + 1, bx + badgeSize - 1, by + 2, 0xFF8C5BC0);
                        drawCenteredBox(
                            ctx,
                            font,
                            b.badge(),
                            bx,
                            badgeSize,
                            by + badgeSize * 0.5f,
                            0.58f,
                            LIGHT_GOLD,
                            false
                        );
                        int textW = textAreaW - 15;
                        List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), textW, chosenScale);
                        float ly = y + 1.0f;
                        for (FormattedCharSequence line : lines) {
                            drawScaledSequence(ctx, font, line, colX + 14, ly, chosenScale, INK_PRIMARY, false);
                            ly += lineStep;
                        }
                        y += Math.max(11.5f, lines.size() * lineStep) + 2.6f;
                    }
                    case BULLET -> {
                        int dotY = Math.round(y + 2.5f);
                        ctx.fill(colX + 2, dotY, colX + 6, dotY + 4, b.accentArgb());
                        ctx.fill(colX + 3, dotY + 1, colX + 5, dotY + 3, LIGHT_GOLD);
                        int textW = textAreaW - 15;
                        List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), textW, chosenScale);
                        float ly = y + 0.6f;
                        for (FormattedCharSequence line : lines) {
                            drawScaledSequence(ctx, font, line, colX + 12, ly, chosenScale, INK_PRIMARY, false);
                            ly += lineStep;
                        }
                        y += Math.max(11.5f, lines.size() * lineStep) + 2.6f;
                    }
                    case CALLOUT -> {
                        if (y > bodyTop - clampedScroll + 1.0f) {
                            y += 2.5f;
                        }
                        int innerW = textAreaW - 12;
                        float cScale = chosenScale * 0.95f;
                        float cStep = lineStep * 0.95f;
                        List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, cScale);
                        float headerExtra = b.badge().isEmpty() ? 0.0f : 9.0f;
                        float totalBoxH = headerExtra + lines.size() * cStep + 6.0f;
                        int y0 = Math.round(y);
                        int y1 = Math.round(y + totalBoxH);
                        ctx.fill(colX, y0, colX + textAreaW, y1, CARD_BG);
                        ctx.fill(colX, y0, colX + textAreaW, y0 + 1, CALLOUT_BORDER);
                        ctx.fill(colX, y1 - 1, colX + textAreaW, y1, CALLOUT_BORDER);
                        ctx.fill(colX, y0, colX + 3, y1, b.accentArgb());

                        float cy = y + 3.0f;
                        if (!b.badge().isEmpty()) {
                            drawLeftCenteredY(
                                ctx,
                                font,
                                b.badge(),
                                colX + 6,
                                cy + 3.5f,
                                Math.min(0.68f, chosenScale + 0.02f),
                                innerW,
                                b.accentArgb(),
                                false
                            );
                            cy += 9.0f;
                        }
                        for (FormattedCharSequence line : lines) {
                            drawScaledSequence(ctx, font, line, colX + 6, cy, cScale, INK_SECONDARY, false);
                            cy += cStep;
                        }
                        y += totalBoxH + 1.0f;
                    }
                }
            }
        } finally {
            ctx.disableScissor();
        }

        if (maxScroll > 0.5f) {
            int trackX = colX + colW - 3;
            ctx.fill(trackX, bodyTop, trackX + 2, bodyTop + bodyH, 0x448A6D46);
            int thumbH = Math.max(14, Math.round(bodyH * (bodyH / contentH)));
            int thumbRange = Math.max(1, bodyH - thumbH);
            int thumbY = bodyTop + Math.round((clampedScroll / maxScroll) * thumbRange);
            ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, INK_PURPLE);
        }

        return new ColumnRenderResult(chosenScale, contentH, maxScroll);
    }
}
