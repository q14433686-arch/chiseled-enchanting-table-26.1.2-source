package chiseled_enchanting_table.gui;

import static chiseled_enchanting_table.gui.TableSkin.PANEL_X;
import static chiseled_enchanting_table.gui.TableSkin.PANEL_Y;

import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.menu.TableVisualEventPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Pure client-side micro-animation painter for {@link ChiseledEnchantingTableScreen}.
 *
 * <p>All overlays stay strictly inside inner panel wells ({@code [px+8..px+111]x[py+8..py+52]})
 * and the open center slot column ({@code [174..208]x[19..53]}), never overlapping decorative
 * gold/obsidian borders.</p>
 */
public final class GuiMicroEffects {
    private static final long BACKLASH_JOLT_MS = 220L;
    private static final long BACKLASH_GLOW_MS = 360L;
    private static final long SLOT_RIPPLE_MS = 380L;
    private static final long PANEL_FADE_MS = 120L;

    private static final int SLOT_X = 183;
    private static final int SLOT_Y = 28;
    private static final int SLOT_SIZE = 16;

    private static volatile long backlashAtMs = -10_000L;
    @Nullable
    private static volatile BlockPos backlashTablePos;
    private static volatile long rippleAtMs = -10_000L;
    private static volatile int rippleRgb = 0xD99BFF;
    @Nullable
    private static volatile BlockPos rippleTablePos;
    private static volatile long freezePulseAtMs = -10_000L;
    private static volatile long modeSwitchAtMs = -10_000L;
    private static int lastModeSignature = Integer.MIN_VALUE;

    private GuiMicroEffects() {}

    public static void reset() {
        backlashAtMs = -10_000L;
        backlashTablePos = null;
        rippleAtMs = -10_000L;
        rippleTablePos = null;
        freezePulseAtMs = -10_000L;
        modeSwitchAtMs = -10_000L;
        lastModeSignature = Integer.MIN_VALUE;
    }

    public static void onVisualEvent(TableVisualEventPayload payload) {
        if (payload == null) {
            return;
        }
        long now = Util.getMillis();
        switch (payload.kind()) {
            case RUSH_BACKLASH -> {
                backlashAtMs = now;
                backlashTablePos = payload.tablePos();
                triggerSlotRipple(now, payload.tablePos(), 0xFF4258);
            }
            case RITUAL_COMPLETE, ARCANE_PULSE ->
                triggerSlotRipple(now, payload.tablePos(), payload.argbColor() & 0x00FFFFFF);
            case CLEANSE_PURGE_CURSE ->
                triggerSlotRipple(now, payload.tablePos(), 0xFFD866);
            case CLEANSE_EXTRACT_BOOK ->
                triggerSlotRipple(now, payload.tablePos(), 0x5CFFD0);
            case EPIPHANY_FREEZE ->
                freezePulseAtMs = now;
            default -> {}
        }
    }

    public static void triggerSlotRipple(long nowMs, @Nullable BlockPos tablePos, int rgb) {
        rippleAtMs = nowMs;
        rippleTablePos = tablePos;
        rippleRgb = rgb & 0x00FFFFFF;
    }

    public static void resetModeTracking() {
        lastModeSignature = Integer.MIN_VALUE;
    }

    public static void observeModeSignature(int modeSignature) {
        if (lastModeSignature != Integer.MIN_VALUE && lastModeSignature != modeSignature) {
            modeSwitchAtMs = Util.getMillis();
        }
        lastModeSignature = modeSignature;
    }

    /** Horizontal micro-jolt offset ({@code -3..+3 px}) during the 220ms FORCE_RUSH backlash window. */
    public static int currentPanelJoltX() {
        long elapsed = Util.getMillis() - backlashAtMs;
        if (elapsed < 0L || elapsed >= BACKLASH_JOLT_MS) {
            return 0;
        }
        float t = (float) elapsed / (float) BACKLASH_JOLT_MS;
        float wave = Mth.sin(t * Mth.PI * 5.0F) * (1.0F - t);
        return Math.round(wave * 3.0F);
    }

    public static void renderOverlays(
        GuiGraphicsExtractor ctx,
        int leftPos,
        int topPos,
        @Nullable BlockPos openTablePos,
        ChiseledEnchantingTableScreenHandler menu,
        boolean showSanctum,
        boolean inscribeTab
    ) {
        long now = Util.getMillis();
        int px = leftPos + PANEL_X + currentPanelJoltX();
        int py = topPos + PANEL_Y;

        // 1. 120ms soft tab / panel switch highlight inside inner wells
        long fadeElapsed = now - modeSwitchAtMs;
        if (fadeElapsed >= 0L && fadeElapsed < PANEL_FADE_MS) {
            float ft = 1.0F - ((float) fadeElapsed / (float) PANEL_FADE_MS);
            int veilAlpha = Math.round(38.0F * ft);
            if (veilAlpha > 0) {
                int veil = (veilAlpha << 24) | 0xE6D4FF;
                ctx.fill(leftPos + 9, topPos + 9, leftPos + 157, topPos + 23, veil);
                if (showSanctum) {
                    ctx.fill(px + 8, py + 8, px + 111, py + 52, veil);
                }
            }
        }

        // 2. Soft frost-blue breathing inner glow while Epiphany is frozen
        if (showSanctum && inscribeTab && !menu.isRitualActive() && menu.isEpiphanyFrozen()) {
            float breath = 0.5F + 0.5F * Mth.sin((float) (now % 2800L) / 2800.0F * Mth.TWO_PI);
            long freezeElapsed = now - freezePulseAtMs;
            float boost = (freezeElapsed >= 0L && freezeElapsed < 450L)
                ? (1.0F - (float) freezeElapsed / 450.0F) * 0.45F
                : 0.0F;
            int borderAlpha = Mth.clamp(Math.round((36.0F + 48.0F * breath) + boost * 90.0F), 20, 145);
            int fillAlpha = Mth.clamp(Math.round(10.0F + 14.0F * breath + boost * 32.0F), 6, 48);
            int frostBorder = (borderAlpha << 24) | 0x8CE8FF;
            int frostFill = (fillAlpha << 24) | 0x70D8FF;
            drawInnerWellFrame(ctx, px + 8, py + 8, px + 111, py + 52, frostBorder, frostFill, true);
        }

        // 3. Crimson inner-frame backlash flash (matches open table if known)
        long backlashElapsed = now - backlashAtMs;
        if (showSanctum && backlashElapsed >= 0L && backlashElapsed < BACKLASH_GLOW_MS
            && (openTablePos == null || backlashTablePos == null || openTablePos.equals(backlashTablePos))) {
            float bt = 1.0F - ((float) backlashElapsed / (float) BACKLASH_GLOW_MS);
            int borderAlpha = Mth.clamp(Math.round(165.0F * bt), 0, 185);
            int fillAlpha = Mth.clamp(Math.round(58.0F * bt), 0, 72);
            drawInnerWellFrame(
                ctx, px + 8, py + 8, px + 111, py + 52,
                (borderAlpha << 24) | 0xFF3850,
                (fillAlpha << 24) | 0xFF243C,
                false
            );
        }

        // 4. Target-slot completion / cleanse / curse-purge expanding pixel aura ripple
        long rippleElapsed = now - rippleAtMs;
        if (rippleElapsed >= 0L && rippleElapsed < SLOT_RIPPLE_MS
            && (openTablePos == null || rippleTablePos == null || openTablePos.equals(rippleTablePos))) {
            float rt = (float) rippleElapsed / (float) SLOT_RIPPLE_MS;
            int expand = Math.round(rt * 6.0F);
            int ringAlpha = Mth.clamp(Math.round((1.0F - rt) * 195.0F), 0, 210);
            int innerAlpha = Mth.clamp(Math.round((1.0F - rt) * (1.0F - rt) * 72.0F), 0, 80);
            int sx0 = leftPos + SLOT_X - 1 - expand;
            int sy0 = topPos + SLOT_Y - 1 - expand;
            int sx1 = leftPos + SLOT_X + SLOT_SIZE + 1 + expand;
            int sy1 = topPos + SLOT_Y + SLOT_SIZE + 1 + expand;
            int ringCol = (ringAlpha << 24) | rippleRgb;
            ctx.fill(sx0, sy0, sx1, sy0 + 1, ringCol);
            ctx.fill(sx0, sy1 - 1, sx1, sy1, ringCol);
            ctx.fill(sx0, sy0 + 1, sx0 + 1, sy1 - 1, ringCol);
            ctx.fill(sx1 - 1, sy0 + 1, sx1, sy1 - 1, ringCol);
            // Crisp corner diamond sparks
            int sparkCol = (Mth.clamp(ringAlpha + 30, 0, 240) << 24) | 0xFFFFFF;
            ctx.fill(sx0, sy0, sx0 + 2, sy0 + 2, sparkCol);
            ctx.fill(sx1 - 2, sy0, sx1, sy0 + 2, sparkCol);
            ctx.fill(sx0, sy1 - 2, sx0 + 2, sy1, sparkCol);
            ctx.fill(sx1 - 2, sy1 - 2, sx1, sy1, sparkCol);
            if (innerAlpha > 0) {
                ctx.fill(
                    leftPos + SLOT_X, topPos + SLOT_Y,
                    leftPos + SLOT_X + SLOT_SIZE, topPos + SLOT_Y + SLOT_SIZE,
                    (innerAlpha << 24) | rippleRgb
                );
            }
        }
    }

    private static void drawInnerWellFrame(
        GuiGraphicsExtractor ctx,
        int x0, int y0, int x1, int y1,
        int borderArgb, int fillArgb,
        boolean crystalCorners
    ) {
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fillArgb);
        ctx.fill(x0, y0, x1, y0 + 1, borderArgb);
        ctx.fill(x0, y1 - 1, x1, y1, borderArgb);
        ctx.fill(x0, y0 + 1, x0 + 1, y1 - 1, borderArgb);
        ctx.fill(x1 - 1, y0 + 1, x1, y1 - 1, borderArgb);
        if (crystalCorners) {
            ctx.fill(x0 + 1, y0 + 1, x0 + 3, y0 + 3, borderArgb);
            ctx.fill(x1 - 3, y0 + 1, x1 - 1, y0 + 3, borderArgb);
            ctx.fill(x0 + 1, y1 - 3, x0 + 3, y1 - 1, borderArgb);
            ctx.fill(x1 - 3, y1 - 3, x1 - 1, y1 - 1, borderArgb);
        }
    }
}
