package enchantment_fusion.gui;

import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Operation-aware skippable Arcane Ritual Director synchronizing:
 * <ul>
 *   <li>Operation-specific soundscapes &amp; channeling durations (Book Fuse, Gear Socket,
 *       Cross-Domain Transmute, Vessel Expand, Aberrant Purify)</li>
 *   <li>Live 3D Stage choreography in {@link FusionLiveStageRenderer}</li>
 *   <li>3D World Fusion Altar crystal acceleration, 4-brazier eruption, and inward orbital spiral</li>
 *   <li>Instant second-click / Spacebar / Enter skip for rapid batch crafting</li>
 * </ul>
 */
public final class FusionRitualDirector {
    public static final long DEFAULT_RITUAL_MS = 1850L;
    public static final long CLIMAX_BANNER_MS = 1550L;

    private static boolean channeling = false;
    private static long startMs = 0L;
    private static long activeDurationMs = DEFAULT_RITUAL_MS;
    private static OperationKind activeKind = OperationKind.NONE;
    private static String boundToken = "";
    private static String pendingRevealTitle = "";
    private static boolean pendingAberrant = false;
    private static int chimedStage = 0;
    private static @Nullable BlockPos activeTablePos = null;

    private static long climaxUntilMs = 0L;
    private static String climaxTitle = "";
    private static boolean climaxAberrant = false;
    private static OperationKind climaxKind = OperationKind.NONE;

    private FusionRitualDirector() {}

    public static void triggerOrSkip(
        OperationKind kind,
        String selectionToken,
        String previewTitle,
        boolean highRiskOrAberrant,
        BlockPos tablePos,
        Runnable commitAction
    ) {
        String safeToken = selectionToken == null ? "" : selectionToken;
        if (channeling && safeToken.equals(boundToken)) {
            completeAndCommit(commitAction);
            return;
        }

        channeling = true;
        startMs = System.currentTimeMillis();
        activeKind = kind == null ? OperationKind.FUSE_NEW_BOOK : kind;
        activeDurationMs = switch (activeKind) {
            case EXPAND_VESSEL -> 1450L;
            case INSCRIBE_SINGLE_CROSS -> 1550L;
            case PURIFY_ABERRANT -> 1950L;
            default -> DEFAULT_RITUAL_MS;
        };
        boundToken = safeToken;
        pendingRevealTitle = previewTitle == null ? "" : previewTitle;
        pendingAberrant = highRiskOrAberrant;
        chimedStage = 0;
        activeTablePos = tablePos != null ? tablePos.immutable() : null;

        playStartSound(activeKind);
    }

    private static void playStartSound(OperationKind kind) {
        switch (kind) {
            case SOCKET_FUSED_BOOK, FUSE_INTO_GEAR, UPGRADE_GEAR_PAIR -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.ANVIL_PLACE, 1.48F, 0.28F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.05F, 0.72F);
            }
            case INSCRIBE_SINGLE_CROSS -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.TRIDENT_RETURN, 1.35F, 0.45F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_CLUSTER_STEP, 1.30F, 0.70F);
            }
            case EXPAND_VESSEL -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.SCULK_CLICKING, 1.25F, 0.55F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.90F, 0.75F);
            }
            case PURIFY_ABERRANT -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.BEACON_ACTIVATE, 1.32F, 0.45F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.18F, 0.75F);
            }
            default -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.FIRECHARGE_USE, 1.16F, 0.36F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.88F, 0.72F);
            }
        }
    }

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
            playStageSound(activeKind, stage);
        }

        if (p >= 1.0F) {
            completeAndCommit(commitAction);
        }
    }

    private static void playStageSound(OperationKind kind, int stage) {
        float pitch = 0.96F + stage * 0.18F;
        switch (kind) {
            case SOCKET_FUSED_BOOK, FUSE_INTO_GEAR, UPGRADE_GEAR_PAIR ->
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_CLUSTER_PLACE, pitch + 0.1F, 0.58F);
            case EXPAND_VESSEL ->
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.82F + stage * 0.22F, 0.65F);
            case PURIFY_ABERRANT ->
                FusionGuiMicroEffects.playUiSound(SoundEvents.BELL_RESONATE, 1.10F + stage * 0.15F, 0.48F);
            default ->
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.68F);
        }
    }

    private static void completeAndCommit(Runnable commitAction) {
        String reveal = pendingRevealTitle;
        boolean ab = pendingAberrant;
        OperationKind finishedKind = activeKind;
        cancel();

        climaxTitle = reveal;
        climaxAberrant = ab;
        climaxKind = finishedKind;
        climaxUntilMs = System.currentTimeMillis() + CLIMAX_BANNER_MS;

        switch (finishedKind) {
            case PURIFY_ABERRANT -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.TOTEM_USE, 1.48F, 0.32F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.55F, 0.85F);
            }
            case EXPAND_VESSEL -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.35F, 0.55F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.40F, 0.85F);
            }
            case SOCKET_FUSED_BOOK, FUSE_INTO_GEAR, UPGRADE_GEAR_PAIR -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.SMITHING_TABLE_USE, 1.22F, 0.62F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.18F, 0.72F);
            }
            default -> {
                FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_BLOCK_RESONATE, ab ? 0.68F : 1.24F, 0.85F);
                FusionGuiMicroEffects.playUiSound(SoundEvents.ENCHANTMENT_TABLE_USE, ab ? 0.78F : 1.12F, 0.75F);
            }
        }
        commitAction.run();
    }

    public static void cancel() {
        channeling = false;
        boundToken = "";
        chimedStage = 0;
        activeKind = OperationKind.NONE;
        activeTablePos = null;
    }

    public static boolean isChanneling() {
        return channeling;
    }

    public static OperationKind activeOperationKind() {
        return channeling ? activeKind : OperationKind.NONE;
    }

    public static float channelProgress() {
        if (!channeling) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        return Mth.clamp((float) elapsed / (float) Math.max(200L, activeDurationMs), 0.0F, 1.0F);
    }

    public static float activeChannelProgressForTable(BlockPos pos) {
        if (!channeling || activeTablePos == null || !activeTablePos.equals(pos)) {
            return 0.0F;
        }
        return channelProgress();
    }

    public static boolean hasActiveClimaxBanner() {
        return System.currentTimeMillis() < climaxUntilMs && !climaxTitle.isEmpty();
    }

    public static float climaxProgress() {
        long rem = climaxUntilMs - System.currentTimeMillis();
        if (rem <= 0L) {
            return 0.0F;
        }
        return 1.0F - Mth.clamp((float) rem / (float) CLIMAX_BANNER_MS, 0.0F, 1.0F);
    }

    public static OperationKind climaxOperationKind() {
        return climaxKind;
    }

    public static String climaxBannerTitle() {
        return climaxTitle;
    }

    public static boolean isClimaxAberrant() {
        return climaxAberrant;
    }
}
