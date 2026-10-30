package enchantment_fusion.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Decoupled 2.0-second skippable Arcane Ritual Director synchronizing:
 * <ul>
 *   <li>2D Cutaway UI Gold/Cyan conduit charge + ascending crystalline harmonic chimes</li>
 *   <li>3D World Fusion Altar crystal acceleration, 4-brazier eruption, and inward orbital spiral</li>
 *   <li>Instant second-click / Spacebar skip for rapid batch crafting</li>
 *   <li>Post-commit Climax Reveal Banner across the center archway</li>
 * </ul>
 * Designed with zero coupling to backend enchantment rules so backend refactors never break UI pacing.
 */
public final class FusionRitualDirector {
    public static final long RITUAL_DURATION_MS = 1950L;
    public static final long CLIMAX_BANNER_MS = 1550L;

    private static boolean channeling = false;
    private static long startMs = 0L;
    private static String boundToken = "";
    private static String pendingRevealTitle = "";
    private static boolean pendingAberrant = false;
    private static int chimedStage = 0;
    private static @Nullable BlockPos activeTablePos = null;

    private static long climaxUntilMs = 0L;
    private static String climaxTitle = "";
    private static boolean climaxAberrant = false;

    private FusionRitualDirector() {}

    /**
     * Starts the 2.0s channeling sequence on first click, or skips immediately to commit on second click!
     */
    public static void triggerOrSkip(
        String selectionToken,
        String previewTitle,
        boolean highRiskOrAberrant,
        BlockPos tablePos,
        Runnable commitAction
    ) {
        String safeToken = selectionToken == null ? "" : selectionToken;
        if (channeling && safeToken.equals(boundToken)) {
            // Second click during channeling -> instant skip & commit!
            completeAndCommit(commitAction);
            return;
        }

        channeling = true;
        startMs = System.currentTimeMillis();
        boundToken = safeToken;
        pendingRevealTitle = previewTitle == null ? "" : previewTitle;
        pendingAberrant = highRiskOrAberrant;
        chimedStage = 0;
        activeTablePos = tablePos != null ? tablePos.immutable() : null;

        FusionGuiMicroEffects.playUiSound(SoundEvents.FIRECHARGE_USE, 1.16F, 0.36F);
        FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.88F, 0.72F);
    }

    /**
     * Called each GUI render frame; cancels safely if slot contents changed, or commits at 100%.
     */
    public static void updateFrame(String liveSelectionToken, boolean stillExecutable, Runnable commitAction) {
        if (!channeling) {
            return;
        }
        String safeLive = liveSelectionToken == null ? "" : liveSelectionToken;
        if (!stillExecutable || !safeLive.equals(boundToken)) {
            cancel();
            return;
        }

        float p = channelProgress();
        int stage = (int) (p * 4.0F);
        if (stage > chimedStage && stage < 4) {
            chimedStage = stage;
            float pitch = 0.96F + stage * 0.18F;
            FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.68F);
        }

        if (p >= 1.0F) {
            completeAndCommit(commitAction);
        }
    }

    private static void completeAndCommit(Runnable commitAction) {
        String reveal = pendingRevealTitle;
        boolean ab = pendingAberrant;
        cancel();

        climaxTitle = reveal;
        climaxAberrant = ab;
        climaxUntilMs = System.currentTimeMillis() + CLIMAX_BANNER_MS;

        FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, ab ? 0.68F : 1.24F, 0.85F);
        FusionGuiMicroEffects.playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, ab ? 0.78F : 1.12F, 0.75F);
        commitAction.run();
    }

    public static void cancel() {
        channeling = false;
        boundToken = "";
        chimedStage = 0;
        activeTablePos = null;
    }

    public static boolean isChanneling() {
        return channeling;
    }

    public static float channelProgress() {
        if (!channeling) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        return Mth.clamp((float) elapsed / (float) RITUAL_DURATION_MS, 0.0F, 1.0F);
    }

    /**
     * Queried by {@code EnchantmentFusionTableRenderer} so the 3D world altar inside the center
     * cutaway viewport surges in real-time lockstep with the 2.0s UI channeling bar!
     */
    public static float activeChannelProgressForTable(BlockPos pos) {
        if (!channeling || activeTablePos == null || !activeTablePos.equals(pos)) {
            return 0.0F;
        }
        return channelProgress();
    }

    public static boolean hasActiveClimaxBanner() {
        return System.currentTimeMillis() < climaxUntilMs && !climaxTitle.isEmpty();
    }

    public static String climaxBannerTitle() {
        return climaxTitle;
    }

    public static boolean isClimaxAberrant() {
        return climaxAberrant;
    }
}
