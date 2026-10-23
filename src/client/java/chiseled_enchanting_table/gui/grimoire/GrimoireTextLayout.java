package chiseled_enchanting_table.gui.grimoire;

import java.util.ArrayList;
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
 * Single-pass optical typography, line-wrapping, and strict bounding-box engine for {@code 纂刻秘典}.
 *
 * <p>Core mathematical &amp; UI invariants enforced across every method:</p>
 * <ul>
 *   <li><b>Explicit ARGB Alpha:</b> Every color carries explicit alpha ({@code 0xFF......}).</li>
 *   <li><b>Zero Dry-Run / Render Drift:</b> {@link #planColumn} computes wrapped line sequences,
 *       exact Y coordinates, box heights, and centered first-line baselines in a single pass;
 *       {@link #renderStructuredColumn} draws directly from that immutable {@link ColumnPlan}.</li>
 *   <li><b>True Multi-Line Optical Centering:</b> A wrapped block of {@code n >= 1} lines at scale
 *       {@code s} and step {@code dy} occupies {@code (n - 1) * dy + GLYPH_FULL_HEIGHT * s} pixels
 *       (covering both rows {@code 0..7} and descender row {@code 8}), guaranteeing identical top
 *       and bottom margins inside every card and callout box.</li>
 *   <li><b>Wrap First, Never Crush to Ants:</b> Multi-word boxes use {@link #drawWrappedBox} so text
 *       wraps naturally across readable lines ({@code >= 0.66f}) rather than shrinking horizontally
 *       down to illegible sub-pixel scales.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class GrimoireTextLayout {

    /** Primary cap/CJK glyph ink height (rows 0..7 = 8.0px) for single-line optical centering. */
    public static final float GLYPH_INK_HEIGHT = 8.0f;

    /** Full glyph height including descender row 8 (8.5px optical balance across CJK + Latin). */
    public static final float GLYPH_FULL_HEIGHT = 8.5f;

    // Illuminated Vellum Palette (explicit alpha on every constant)
    public static final int INK_PRIMARY = 0xFF261910;
    public static final int INK_SECONDARY = 0xFF543E2D;
    public static final int INK_MUTED = 0xFF785E47;
    public static final int INK_GOLD_DARK = 0xFF7A4E10;
    public static final int INK_PURPLE = 0xFF4C236E;
    public static final int INK_EMERALD = 0xFF165E3E;
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

    /** Readable body scale candidates ordered largest -> smallest (never dropping to tiny <0.64f). */
    private static final float[] CANDIDATE_SCALES = {0.80f, 0.76f, 0.72f, 0.68f, 0.64f};

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

    private record PlacedBlock(
        PageBlock spec,
        float relY,
        float boxHeight,
        float textScale,
        float lineStep,
        float firstLineRelY,
        int innerWidth,
        List<FormattedCharSequence> lines
    ) {}

    private record ColumnPlan(
        float chosenScale,
        int textWidth,
        float totalHeight,
        List<PlacedBlock> items
    ) {}

    private GrimoireTextLayout() {}

    /** Visual pixel height occupied by {@code n} wrapped lines at {@code scale} and {@code lineStep}. */
    public static float wrappedBlockVisualHeight(int lineCount, float scale, float lineStep) {
        if (lineCount <= 0) {
            return 0.0f;
        }
        return (lineCount - 1) * lineStep + GLYPH_FULL_HEIGHT * scale;
    }

    /** Wraps {@code text} so no rendered line ever exceeds {@code pixelWidth} at {@code scale}. */
    public static List<FormattedCharSequence> wrapAtScale(Font font, Component text, int pixelWidth, float scale) {
        float safeScale = Math.max(0.45f, scale);
        int wrapUnits = Math.max(20, (int) Math.floor((pixelWidth - 0.5f) / safeScale));
        return font.split(text, wrapUnits);
    }

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
     * Left-aligned single-line label optically centered on {@code visualCenterY}, clamped to
     * {@code minScale = 0.56f} so short headers/buttons stay sharp.
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

        float minScale = Math.min(maxScale, 0.56f);
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > maxWidth) {
            float needed = (float) maxWidth / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRaw = Math.max(8, (int) Math.floor(maxWidth / scale) - font.width("\u2026"));
                finalStr = font.plainSubstrByWidth(text, maxRaw) + "\u2026";
            }
        }
        float drawY = visualCenterY - (GLYPH_INK_HEIGHT * scale) * 0.5f;
        drawScaledString(ctx, font, finalStr, x, drawY, scale, argbColor, shadow);
    }

    /**
     * Horizontally AND optically vertically centered single-line label inside {@code [boxX .. boxX + boxW]}.
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

        float minScale = Math.min(maxScale, 0.56f);
        float scale = maxScale;
        String finalStr = text;
        if (rawW * scale > boxW) {
            float needed = (float) boxW / (float) rawW;
            if (needed >= minScale) {
                scale = needed;
            } else {
                scale = minScale;
                int maxRaw = Math.max(8, (int) Math.floor(boxW / scale) - font.width("\u2026"));
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

    /**
     * Multi-line wrapped box renderer: wraps naturally inside {@code [boxX .. boxX + boxW]},
     * picks the largest readable scale in {@code [minScale .. preferredScale]} whose wrapped block
     * height fits {@code boxH}, optically centers the block vertically inside {@code [boxY .. boxY + boxH]},
     * and clips strictly inside {@code enableScissor} so text never shrinks into tiny unreadable lines
     * AND never overflows its container.
     */
    public static void drawWrappedBox(
        GuiGraphicsExtractor ctx,
        Font font,
        Component text,
        int boxX,
        int boxY,
        int boxW,
        int boxH,
        float preferredScale,
        float minScale,
        int argbColor,
        boolean shadow
    ) {
        if (text == null || boxW <= 6 || boxH <= 6) return;
        float floorScale = Math.min(preferredScale, Math.max(0.56f, minScale));
        float chosenScale = floorScale;
        List<FormattedCharSequence> chosenLines = List.of();
        float chosenStep = 10.6f * floorScale;

        for (float s = preferredScale; s >= floorScale - 0.001f; s -= 0.04f) {
            float curScale = Math.max(floorScale, s);
            float step = 10.6f * curScale;
            List<FormattedCharSequence> lines = wrapAtScale(font, text, boxW, curScale);
            float visH = wrappedBlockVisualHeight(lines.size(), curScale, step);
            chosenScale = curScale;
            chosenLines = lines;
            chosenStep = step;
            if (visH <= boxH + 0.5f) {
                break;
            }
        }
        if (chosenLines.isEmpty()) {
            return;
        }

        float visH = wrappedBlockVisualHeight(chosenLines.size(), chosenScale, chosenStep);
        float startY = boxY + Math.max(0.0f, (boxH - visH) * 0.5f);

        ctx.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
        try {
            float ly = startY;
            for (FormattedCharSequence line : chosenLines) {
                drawScaledSequence(ctx, font, line, boxX, ly, chosenScale, argbColor, shadow);
                ly += chosenStep;
            }
        } finally {
            ctx.disableScissor();
        }
    }

    /**
     * Single-pass column layout solver guaranteeing exact pixel equality between planned height
     * and rendered coordinates.
     */
    private static ColumnPlan planColumn(Font font, List<PageBlock> blocks, int textWidth, float bodyScale) {
        float lineStep = 10.8f * bodyScale;
        float y = 0.0f;
        List<PlacedBlock> out = new ArrayList<>(blocks.size());

        for (PageBlock b : blocks) {
            switch (b.kind()) {
                case LEAD -> {
                    int innerW = Math.max(24, textWidth - 11);
                    List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, bodyScale);
                    float visH = wrappedBlockVisualHeight(lines.size(), bodyScale, lineStep);
                    float padY = 3.2f;
                    float boxH = Math.max(13.0f, visH + padY * 2.0f);
                    float firstRelY = (boxH - visH) * 0.5f;
                    out.add(new PlacedBlock(b, y, boxH, bodyScale, lineStep, firstRelY, innerW, lines));
                    y += boxH + 4.5f;
                }
                case SECTION_HEADER -> {
                    if (y > 0.5f) {
                        y += 2.5f;
                    }
                    float headerH = 12.5f;
                    out.add(new PlacedBlock(b, y, headerH, Math.min(0.80f, bodyScale + 0.04f), lineStep, 0.0f, textWidth - 8, List.of()));
                    y += headerH + 2.0f;
                }
                case STEP -> {
                    int innerW = Math.max(24, textWidth - 15);
                    List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, bodyScale);
                    float visH = wrappedBlockVisualHeight(lines.size(), bodyScale, lineStep);
                    float rowH = Math.max(11.5f, visH);
                    float firstRelY = lines.size() == 1 ? Math.max(0.0f, (11.0f - visH) * 0.5f) : 0.5f;
                    out.add(new PlacedBlock(b, y, rowH, bodyScale, lineStep, firstRelY, innerW, lines));
                    y += rowH + 3.2f;
                }
                case BULLET -> {
                    int innerW = Math.max(24, textWidth - 14);
                    List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, bodyScale);
                    float visH = wrappedBlockVisualHeight(lines.size(), bodyScale, lineStep);
                    float rowH = Math.max(11.0f, visH);
                    float firstRelY = lines.size() == 1 ? Math.max(0.0f, (10.5f - visH) * 0.5f) : 0.4f;
                    out.add(new PlacedBlock(b, y, rowH, bodyScale, lineStep, firstRelY, innerW, lines));
                    y += rowH + 3.0f;
                }
                case CALLOUT -> {
                    if (y > 0.5f) {
                        y += 2.5f;
                    }
                    int innerW = Math.max(24, textWidth - 12);
                    float cScale = Math.max(0.62f, bodyScale * 0.95f);
                    float cStep = 10.6f * cScale;
                    List<FormattedCharSequence> lines = wrapAtScale(font, b.body(), innerW, cScale);
                    float visH = wrappedBlockVisualHeight(lines.size(), cScale, cStep);
                    float titleBandH = b.badge().isEmpty() ? 0.0f : 10.0f;
                    float boxH = titleBandH + visH + 6.5f;
                    float firstRelY = 3.2f + titleBandH;
                    out.add(new PlacedBlock(b, y, boxH, cScale, cStep, firstRelY, innerW, lines));
                    y += boxH + 1.5f;
                }
            }
        }
        return new ColumnPlan(bodyScale, textWidth, Math.max(0.0f, y - 1.5f), List.copyOf(out));
    }

    /**
     * Renders a structured chapter prose column inside hardware GPU scissor clipping, picking the
     * largest crisp font scale that fits cleanly on a single page without scrolling whenever possible.
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

        int headerH = 15;
        int pillW = Math.max(20, Math.round(font.width(chapterBadge) * 0.68f) + 8);
        ctx.fill(colX, colY, colX + pillW, colY + headerH, 0xFF4C236E);
        ctx.fill(colX + 1, colY + 1, colX + pillW - 1, colY + 2, 0xFF8B56BA);
        drawCenteredBox(ctx, font, chapterBadge, colX + 2, pillW - 4, colY + headerH * 0.5f, 0.68f, LIGHT_GOLD, false);

        drawLeftCenteredY(
            ctx,
            font,
            chapterTitle.getString(),
            colX + pillW + 5,
            colY + headerH * 0.5f,
            0.88f,
            colW - pillW - 6,
            INK_PRIMARY,
            false
        );

        int ruleY = colY + headerH + 2;
        ctx.fill(colX, ruleY, colX + colW, ruleY + 1, CARD_BORDER);
        ctx.fill(colX, ruleY, colX + 38, ruleY + 1, INK_GOLD_DARK);

        int bodyTop = ruleY + 4;
        int bodyH = Math.max(24, (colY + colH) - bodyTop);

        // First test full-width single-page fit across CANDIDATE_SCALES (largest first):
        ColumnPlan chosenPlan = null;
        for (float candidate : CANDIDATE_SCALES) {
            ColumnPlan trial = planColumn(font, blocks, colW, candidate);
            if (trial.totalHeight() <= bodyH + 0.5f) {
                chosenPlan = trial;
                break;
            }
        }
        // Only if even the smallest candidate exceeds bodyH, plan at (colW - 6) to reserve scrollbar gutter:
        if (chosenPlan == null) {
            chosenPlan = planColumn(font, blocks, Math.max(32, colW - 6), CANDIDATE_SCALES[CANDIDATE_SCALES.length - 1]);
        }

        float contentH = chosenPlan.totalHeight();
        int textAreaW = chosenPlan.textWidth();
        float maxScroll = Math.max(0.0f, contentH - bodyH);
        float clampedScroll = Mth.clamp(scrollOffset, 0.0f, maxScroll);

        ctx.enableScissor(colX, bodyTop, colX + colW, bodyTop + bodyH);
        try {
            float originY = bodyTop - clampedScroll;
            for (PlacedBlock pb : chosenPlan.items()) {
                PageBlock b = pb.spec();
                float y = originY + pb.relY();
                switch (b.kind()) {
                    case LEAD -> {
                        int y0 = Math.round(y);
                        int y1 = Math.round(y + pb.boxHeight());
                        ctx.fill(colX, y0, colX + textAreaW, y1, CALLOUT_BG);
                        ctx.fill(colX, y0, colX + 2, y1, b.accentArgb());
                        float ly = y + pb.firstLineRelY();
                        for (FormattedCharSequence line : pb.lines()) {
                            drawScaledSequence(ctx, font, line, colX + 6, ly, pb.textScale(), INK_PRIMARY, false);
                            ly += pb.lineStep();
                        }
                    }
                    case SECTION_HEADER -> {
                        int hy = Math.round(y);
                        ctx.fill(colX, hy + 3, colX + 3, hy + 9, b.accentArgb());
                        drawLeftCenteredY(
                            ctx,
                            font,
                            b.body().getString(),
                            colX + 6,
                            hy + 6.0f,
                            pb.textScale(),
                            pb.innerWidth(),
                            b.accentArgb(),
                            false
                        );
                        ctx.fill(colX, hy + 11, colX + textAreaW, hy + 12, RULE_LINE);
                    }
                    case STEP -> {
                        int badgeSize = 11;
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
                            0.62f,
                            LIGHT_GOLD,
                            false
                        );
                        float ly = y + pb.firstLineRelY();
                        for (FormattedCharSequence line : pb.lines()) {
                            drawScaledSequence(ctx, font, line, colX + 14, ly, pb.textScale(), INK_PRIMARY, false);
                            ly += pb.lineStep();
                        }
                    }
                    case BULLET -> {
                        int dotY = Math.round(y + 3.0f);
                        ctx.fill(colX + 2, dotY, colX + 6, dotY + 4, b.accentArgb());
                        ctx.fill(colX + 3, dotY + 1, colX + 5, dotY + 3, LIGHT_GOLD);
                        float ly = y + pb.firstLineRelY();
                        for (FormattedCharSequence line : pb.lines()) {
                            drawScaledSequence(ctx, font, line, colX + 11, ly, pb.textScale(), INK_PRIMARY, false);
                            ly += pb.lineStep();
                        }
                    }
                    case CALLOUT -> {
                        int y0 = Math.round(y);
                        int y1 = Math.round(y + pb.boxHeight());
                        ctx.fill(colX, y0, colX + textAreaW, y1, CARD_BG);
                        ctx.fill(colX, y0, colX + textAreaW, y0 + 1, CALLOUT_BORDER);
                        ctx.fill(colX, y1 - 1, colX + textAreaW, y1, CALLOUT_BORDER);
                        ctx.fill(colX, y0, colX + 3, y1, b.accentArgb());

                        if (!b.badge().isEmpty()) {
                            drawLeftCenteredY(
                                ctx,
                                font,
                                b.badge(),
                                colX + 6,
                                y + 7.2f,
                                Math.min(0.74f, pb.textScale() + 0.04f),
                                pb.innerWidth(),
                                b.accentArgb(),
                                false
                            );
                        }
                        float ly = y + pb.firstLineRelY();
                        for (FormattedCharSequence line : pb.lines()) {
                            drawScaledSequence(ctx, font, line, colX + 6, ly, pb.textScale(), INK_SECONDARY, false);
                            ly += pb.lineStep();
                        }
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

        return new ColumnRenderResult(chosenPlan.chosenScale(), contentH, maxScroll);
    }
}
