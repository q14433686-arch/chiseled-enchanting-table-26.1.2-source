package enchantment_fusion.gui;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.gui.FusionGuiSkin.FittedText;
import enchantment_fusion.gui.FusionGuiSkin.HitBox;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionEconomyRules;
import enchantment_fusion.mechanics.FusionEconomyRules.EconomyQuote;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.EnchantCandidate;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.FusionStationOperations.ResolvedSelection;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.menu.FusionNetworking.StationActionPayload;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Compact Split-Wing Live-Stage Enchantment Fusion Altar ({@code 304 x 218}):
 * <ul>
 *   <li><b>Left Input Pedestal</b> ({@code 4..92, 6..118}): Slot 0 [A], Dual-Mode Swap Medallion,
 *       Slot 1 [B], Slot 2 Catalyst, Slot 3 Target/Output, and flowing conduits</li>
 *   <li><b>Center Real-Time 3D Live Stage</b> ({@code 96..184, 6..118}): Scissored live stage rendering
 *       the 3D Altar, floating crystal, continuous Gold/Cyan ribbons, and 8 bespoke operation animations</li>
 *   <li><b>Right Auto-Morphing Wing</b> ({@code 188..300, 6..118}): Seamless Book Synthesis &lt;-&gt; Gear
 *       Inscription panel + In-Panel Synergy Codex Popover + Obsidian Star-Codex Grimoire</li>
 * </ul>
 * Sized at {@code 304px} wide so JEI and REI sidebars never collide even at GUI Scale 3/4.
 */
public final class EnchantmentFusionScreen extends AbstractContainerScreen<EnchantmentFusionMenu> {
    public static final int LEFT_WING_X = 4;
    public static final int LEFT_WING_Y = 6;
    public static final int LEFT_WING_W = 88;
    public static final int LEFT_WING_H = 112;

    public static final int STAGE_X = 96;
    public static final int STAGE_Y = 6;
    public static final int STAGE_W = 88;
    public static final int STAGE_H = 112;

    public static final int RIGHT_WING_X = 188;
    public static final int RIGHT_WING_Y = 6;
    public static final int RIGHT_WING_W = 112;
    public static final int RIGHT_WING_H = 112;

    public static final int RIGHT_INNER_X = 193;
    public static final int RIGHT_INNER_W = 102;

    // Interactive HitBoxes (relative to leftPos, topPos)
    public static final HitBox SWAP_BTN = new HitBox(38, 29, 20, 20);
    public static final HitBox GRIMOIRE_BTN = new HitBox(279, 8, 18, 16);
    public static final HitBox EXEC_BTN = new HitBox(193, 96, 102, 18);

    // Book Synthesis Mode HitBoxes
    public static final HitBox BOOK_FLIP_BTN = new HitBox(193, 62, 49, 14);
    public static final HitBox BOOK_SPLIT_BTN = new HitBox(246, 62, 49, 14);
    public static final HitBox BOOK_PREVIEW_BOX = new HitBox(193, 62, 102, 32);

    // Gear Inscription Mode HitBoxes
    public static final HitBox SINGLE_MODE_BTN = new HitBox(269, 63, 26, 15);

    private int candidateScroll = 0;
    private @Nullable Boolean lastObservedGearMode = null;
    private final FusionContextDrawer contextDrawer = new FusionContextDrawer();
    private @Nullable FusionGrimoireScreen grimoireOverlay = null;
    private @Nullable StationPreview cachedFramePreview = null;
    private @Nullable Component disabledFeedbackText = null;
    private long disabledFeedbackUntilMs = 0L;

    private record BookManageState(
        boolean hasBook,
        boolean isAberrant,
        boolean secondaryOccupied,
        boolean enoughXp,
        int xpCost,
        String pairFingerprint,
        ItemStack splitLeftSample,
        ItemStack splitRightSample
    ) {
        boolean canExecute() {
            return hasBook && !isAberrant && !secondaryOccupied && enoughXp;
        }
    }

    public EnchantmentFusionScreen(EnchantmentFusionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, EnchantmentFusionMenu.GUI_WIDTH, EnchantmentFusionMenu.GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        FusionRitualDirector.cancel();
        if (this.grimoireOverlay != null && this.minecraft != null) {
            this.grimoireOverlay.init(this.width, this.height);
        }
    }

    public Rect2i guiBounds() {
        if (this.grimoireOverlay != null) {
            return new Rect2i(
                (this.width - FusionGrimoireScreen.BOOK_W) / 2,
                (this.height - FusionGrimoireScreen.BOOK_H) / 2 + 6,
                FusionGrimoireScreen.BOOK_W,
                FusionGrimoireScreen.BOOK_H
            );
        }
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Nullable
    private static HolderLookup.Provider currentRegistries() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.registryAccess() : null;
    }

    private boolean isGearModeActive() {
        ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        return CrossDomainTransmutations.isEligibleCrossDomainHost(host);
    }

    private void ensureAutoMorphedTab() {
        boolean gearMode = isGearModeActive();
        if (this.lastObservedGearMode != null && this.lastObservedGearMode != gearMode) {
            FusionLiveStageRenderer.triggerModeMorph();
            FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, gearMode ? 1.15F : 0.95F, 0.45F);
        }
        this.lastObservedGearMode = gearMode;

        int desiredTab = gearMode ? FusionStationOperations.TAB_SOCKET : FusionStationOperations.TAB_FUSE;
        if (this.menu.activeTab() != desiredTab) {
            sendAction(StationAction.SELECT_TAB, desiredTab, "");
        }
    }

    private void openEmbeddedGrimoire() {
        if (this.minecraft == null) {
            return;
        }
        FusionRitualDirector.cancel();
        this.contextDrawer.close();
        int initCh = isGearModeActive() ? 2 : 1;
        FusionGrimoireScreen overlay = new FusionGrimoireScreen(() -> this.grimoireOverlay = null, initCh);
        overlay.init(this.width, this.height);
        this.grimoireOverlay = overlay;
        FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.85F);
    }

    private boolean canSwapPhysicalSlots() {
        return !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_PRIMARY).isEmpty()
            || !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty();
    }

    private boolean canSwapSelectionOrder(StationPreview preview) {
        return !this.menu.singleOnlyMode() && preview.selection().candidates().size() >= 2;
    }

    private void notifyDisabledClick(Component reason) {
        this.disabledFeedbackText = reason;
        this.disabledFeedbackUntilMs = System.currentTimeMillis() + 2200L;
        FusionGuiMicroEffects.playUiSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.72F, 0.65F);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Bounded labels rendered inside extractBackground.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        ensureAutoMorphedTab();
        FusionGuiMicroEffects.updateStationOrigin(this.leftPos, this.topPos);

        StationPreview preview = this.menu.computePreview();
        this.cachedFramePreview = preview;
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }
        boolean gearMode = isGearModeActive();

        String execToken = preview.expectedExecuteToken();
        FusionRitualDirector.updateFrame(
            execToken,
            preview.executable(),
            () -> sendAction(StationAction.EXECUTE_PRIMARY, 0, execToken)
        );

        this.contextDrawer.rebuildIfChanged(
            preview.selection().candidates(),
            this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET),
            registries
        );

        int shakeX = FusionGuiMicroEffects.currentShakeOffsetX();
        int shakeY = FusionGuiMicroEffects.currentShakeOffsetY();
        int left = this.leftPos + shakeX;
        int top = this.topPos + shakeY;

        boolean highRisk = preview.economyQuote() != null && preview.economyQuote().highRiskTier();
        float chProg = FusionRitualDirector.channelProgress();

        // 1. Left Input Pedestal + Right Auto-Morphing Wing + Bottom Player Inventory
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + LEFT_WING_X, top + LEFT_WING_Y, LEFT_WING_W, LEFT_WING_H);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + RIGHT_WING_X, top + RIGHT_WING_Y, RIGHT_WING_W, RIGHT_WING_H);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + 63, top + 121, 178, 95);

        FusionGuiMicroEffects.renderPedestalResonanceBackdrop(ctx, left, top, preview.executable(), highRisk, chProg);

        // 2. Center Real-Time 3D Live Fusion Stage + Ornate Obsidian Archway Frame
        FusionLiveStageRenderer.renderStage(
            ctx,
            left + STAGE_X, top + STAGE_Y, STAGE_W, STAGE_H,
            this.menu, preview, gearMode
        );
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.VIEWPORT_ARCH, left + 94, top + 4, 92, 116);

        if (chProg > 0.0F) {
            FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.CONDUIT_TRACK, left + 104, top + 110, 72, 4);
            int barW = Math.max(1, Math.round(70 * chProg));
            FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.RITUAL_PROGRESS_FILL, left + 105, top + 111, barW, 2);
        }

        // 3. Left Pedestal Slots, Conduits & Dual-Mode Swap Medallion
        renderLeftPedestal(ctx, left, top, mouseX, mouseY, preview, gearMode, chProg);

        // 4. Right Auto-Morphing Control Wing (scissored inside bezel so Scene 8 Mode Morph slides cleanly!)
        int morphSlideX = FusionLiveStageRenderer.currentRightWingMorphOffsetX(gearMode);
        ctx.enableScissor(left + RIGHT_WING_X + 2, top + 8, left + RIGHT_WING_X + RIGHT_WING_W - 2, top + 116);
        if (gearMode) {
            renderRightWingGearMode(ctx, left + morphSlideX, top, mouseX, mouseY, preview, registries);
        } else {
            renderRightWingBookMode(ctx, left + morphSlideX, top, mouseX, mouseY, preview, registries);
        }
        ctx.disableScissor();

        // 5. Top-Right Synergy Badge + Embedded Grimoire Icon Button
        this.contextDrawer.renderBadge(ctx, this.font, left, top, mouseX, mouseY);
        renderGrimoireButton(ctx, left, top, mouseX, mouseY);

        // 6. Climax Reveal Banner / Disabled Feedback Banner across the Stage Header
        if (FusionRitualDirector.hasActiveClimaxBanner()) {
            boolean ab = FusionRitualDirector.isClimaxAberrant();
            FusionGuiSkin.drawNineSlice(
                ctx,
                new FusionGuiSkin.NineSliceSprite(
                    ab ? FusionGuiSkin.CLIMAX_BANNER_ABERRANT : FusionGuiSkin.CLIMAX_BANNER_SIG,
                    64, 18, 4
                ),
                left + 98, top + 9, 84, 15
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, FusionRitualDirector.climaxBannerTitle(),
                left + 101, 78, top + 16.5F,
                0.56F, ab ? FusionGuiSkin.COLOR_CRIMSON : FusionGuiSkin.COLOR_GOLD
            );
        } else if (this.disabledFeedbackText != null && System.currentTimeMillis() < this.disabledFeedbackUntilMs) {
            FusionGuiSkin.drawNineSlice(
                ctx,
                new FusionGuiSkin.NineSliceSprite(FusionGuiSkin.CLIMAX_BANNER_ABERRANT, 64, 18, 4),
                left + 96, top + 9, 88, 15
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, this.disabledFeedbackText.getString(),
                left + 99, 82, top + 16.5F,
                0.52F, FusionGuiSkin.COLOR_CRIMSON
            );
        }

        // 7. Player Inventory Title
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, this.playerInventoryTitle.getString(),
            left + 71, top + 125, 110,
            FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CREAM
        );

        // 8. In-Panel Synergy Codex Popover (strictly inside 304px bounds!)
        this.contextDrawer.renderPopover(ctx, this.font, left, top, mouseX, mouseY);
    }

    private void renderLeftPedestal(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int mouseX,
        int mouseY,
        StationPreview preview,
        boolean gearMode,
        float chProg
    ) {
        String pedTitle = gearMode
            ? Component.translatable("gui.enchantment_fusion.pedestal.gear_roles").getString()
            : Component.translatable("gui.enchantment_fusion.pedestal.fuse_roles").getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, pedTitle,
            left + 8, 80, top + 14.0F,
            0.56F, FusionGuiSkin.COLOR_GOLD
        );

        boolean hasSlot0 = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_PRIMARY).isEmpty();
        boolean hasSlot1 = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty();
        boolean hasCat = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_CATALYST).isEmpty();

        float goldFill = chProg > 0.0F ? clamp01(chProg * 1.4F) : (hasSlot0 ? (preview.executable() ? 0.55F : 0.25F) : 0.0F);
        float cyanFill = chProg > 0.0F ? clamp01(chProg * 1.4F) : (hasSlot1 ? (preview.executable() ? 0.55F : 0.25F) : 0.0F);
        float catFill = chProg > 0.0F ? clamp01((chProg - 0.25F) / 0.75F) : (hasCat ? 0.45F : 0.0F);

        // Conduits linking Slots 0/1/2/3 into the Center Live Stage
        FusionGuiSkin.drawConduitV(ctx, FusionGuiSkin.CONDUIT_GOLD, left + 20, top + 49, 4, 28, goldFill);
        FusionGuiSkin.drawConduitV(ctx, FusionGuiSkin.CONDUIT_CYAN, left + 70, top + 49, 4, 28, cyanFill);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_AMETHYST, left + 31, top + 84, 32, 4, catFill, false);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_GOLD, left + 81, top + 38, 15, 4, goldFill, false);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_AMETHYST, left + 81, top + 84, 15, 4, Math.max(goldFill, catFill), false);

        // 4 Slot Bezels matching EnchantmentFusionMenu coordinates (14,32), (64,32), (14,78), (64,78)
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_PRIMARY, left + 13, top + 31, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_SECONDARY, left + 63, top + 31, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_CATALYST, left + 13, top + 77, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_DEFAULT, left + 63, top + 77, 18, 18);

        // Dual-Mode Swap Medallion [⇄] at (38, 29, 20, 20)
        boolean swapEnabled = !FusionRitualDirector.isChanneling()
            && (canSwapSelectionOrder(preview) || canSwapPhysicalSlots());
        boolean hovSwap = SWAP_BTN.containsAt(left, top, mouseX, mouseY);
        var swapTex = !swapEnabled
            ? FusionGuiSkin.BTN_SWAP_DISABLED
            : (hovSwap ? FusionGuiSkin.BTN_SWAP_HOVER : FusionGuiSkin.BTN_SWAP_NORMAL);
        FusionGuiSkin.drawSprite(ctx, swapTex, left + SWAP_BTN.x(), top + SWAP_BTN.y(), SWAP_BTN.w(), SWAP_BTN.h());

        String s0 = Component.translatable(
            gearMode ? "gui.enchantment_fusion.slot.inlay_book" : "gui.enchantment_fusion.slot.primary_short"
        ).getString();
        String s1 = Component.translatable(
            gearMode ? "gui.enchantment_fusion.slot.aux_book" : "gui.enchantment_fusion.slot.secondary_short"
        ).getString();
        String s2 = Component.translatable("gui.enchantment_fusion.slot.catalyst_short").getString();
        String s3 = Component.translatable(
            gearMode ? "gui.enchantment_fusion.slot.host_gear" : "gui.enchantment_fusion.slot.output_book"
        ).getString();

        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, s0, left + 6, 32, top + 25.0F, 0.50F, FusionGuiSkin.COLOR_GOLD);
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, s1, left + 56, 32, top + 25.0F, 0.50F, FusionGuiSkin.COLOR_CYAN);
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, s2, left + 6, 32, top + 100.0F, 0.50F, FusionGuiSkin.COLOR_MUTED);
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, s3, left + 56, 32, top + 100.0F, 0.50F, FusionGuiSkin.COLOR_CREAM);
    }

    private void renderRightWingBookMode(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int mouseX,
        int mouseY,
        StationPreview preview,
        HolderLookup.Provider registries
    ) {
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            "\u25C6 " + Component.translatable("gui.enchantment_fusion.tab.fuse").getString(),
            left + RIGHT_INNER_X, top + 12, 58,
            0.58F, FusionGuiSkin.COLOR_GOLD
        );

        renderCandidateRows(ctx, left, top, mouseX, mouseY, preview.selection(), registries, 27, 2, RIGHT_INNER_W);

        BookManageState mState = evaluateBookManage(preview);
        if (!preview.executable() && mState.hasBook() && !mState.isAberrant()) {
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_FLIP_BTN,
                mState.canExecute(), BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
                0.56F, FusionGuiSkin.COLOR_GOLD
            );
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_SPLIT_BTN,
                mState.canExecute(), BOOK_SPLIT_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.split").getString(),
                0.56F, FusionGuiSkin.COLOR_CYAN
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable("gui.enchantment_fusion.preview.xp_cost", mState.xpCost()).getString(),
                left + RIGHT_INNER_X, RIGHT_INNER_W, top + 85.0F,
                0.52F, mState.enoughXp() ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_CRIMSON
            );
        } else {
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.SLICE_PANEL_INSET,
                left + BOOK_PREVIEW_BOX.x(), top + BOOK_PREVIEW_BOX.y(),
                BOOK_PREVIEW_BOX.w(), BOOK_PREVIEW_BOX.h()
            );
            String headline = buildPreviewTitleLine(preview, registries);
            int headCol = preview.executable()
                ? FusionGuiSkin.COLOR_GOLD
                : (preview.rejectReason() == RejectReason.NONE ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_CRIMSON);
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, headline,
                left + BOOK_PREVIEW_BOX.x() + 4, top + BOOK_PREVIEW_BOX.y() + 5,
                BOOK_PREVIEW_BOX.w() - 8, 0.56F, headCol
            );
            String sub = preview.executable()
                ? buildCostSummaryLine(preview)
                : (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS
                    ? Component.translatable("gui.enchantment_fusion.preview.purify_hint").getString()
                    : Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").getString());
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, sub,
                left + BOOK_PREVIEW_BOX.x() + 4, top + BOOK_PREVIEW_BOX.y() + 18,
                BOOK_PREVIEW_BOX.w() - 8, 0.50F,
                preview.executable() ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_MUTED
            );
        }

        renderPrimaryRitualButton(ctx, left, top, mouseX, mouseY, preview);
    }

    private void renderRightWingGearMode(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int mouseX,
        int mouseY,
        StationPreview preview,
        HolderLookup.Provider registries
    ) {
        ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        int maxSlots = VesselCapacityRules.getMaxVesselSlots(host);
        FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);

        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            "\u25C6 " + Component.translatable("gui.enchantment_fusion.tab.socket").getString(),
            left + RIGHT_INNER_X, top + 12, 58,
            0.58F, FusionGuiSkin.COLOR_GOLD
        );

        for (int idx = 0; idx < 2; idx++) {
            int cy = top + 27 + idx * 17;
            if (maxSlots == 0) {
                if (idx == 0) {
                    FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_SOCKET_FILLED, left + RIGHT_INNER_X, cy, RIGHT_INNER_W, 33);
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.special_cross_host").getString(),
                        left + RIGHT_INNER_X + 4, RIGHT_INNER_W - 8, cy + 16.5F,
                        0.52F, FusionGuiSkin.COLOR_CYAN
                    );
                }
                continue;
            }
            if (idx >= maxSlots) {
                FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_SOCKET_EMPTY, left + RIGHT_INNER_X, cy, RIGHT_INNER_W, 16);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.locked_expand_hint").getString(),
                    left + RIGHT_INNER_X + 5, cy + 4, RIGHT_INNER_W - 10,
                    0.50F, FusionGuiSkin.COLOR_MUTED
                );
                continue;
            }
            if (idx < socketed.size()) {
                FusedPair p = socketed.pairs().get(idx);
                var cardSlice = p.isAberrant() ? FusionGuiSkin.SLICE_SOCKET_ABERRANT : FusionGuiSkin.SLICE_SOCKET_FILLED;
                FusionGuiSkin.drawNineSlice(ctx, cardSlice, left + RIGHT_INNER_X, cy, RIGHT_INNER_W, 16);
                String pairName = FusionTooltipRenderer.buildPairDisplayName(registries, p).getString();
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, pairName,
                    left + RIGHT_INNER_X + 4, cy + 4, 62,
                    0.52F, p.isAberrant() ? FusionGuiSkin.COLOR_CRIMSON : FusionGuiSkin.COLOR_GOLD
                );
                boolean canAct = !p.isAberrant()
                    && this.menu.viewer().experienceLevel >= preview.manageXpCost()
                    && !FusionRitualDirector.isChanneling();
                HitBox flipBox = socketFlipBox(idx);
                HitBox unsockBox = socketUnsocketBox(idx);
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, flipBox,
                    canAct, flipBox.containsAt(left, top, mouseX, mouseY),
                    "\u21C4", 0.52F, FusionGuiSkin.COLOR_GOLD
                );
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, unsockBox,
                    canAct, unsockBox.containsAt(left, top, mouseX, mouseY),
                    "\u2715", 0.52F, FusionGuiSkin.COLOR_CRIMSON
                );
            } else {
                FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_SOCKET_EMPTY, left + RIGHT_INNER_X, cy, RIGHT_INNER_W, 16);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.empty_ready", idx + 1).getString(),
                    left + RIGHT_INNER_X + 5, cy + 4, RIGHT_INNER_W - 10,
                    0.50F, FusionGuiSkin.COLOR_MUTED
                );
            }
        }

        renderCandidateRows(ctx, left, top, mouseX, mouseY, preview.selection(), registries, 63, 1, 74);

        boolean singleActive = this.menu.singleOnlyMode();
        boolean hovSingle = SINGLE_MODE_BTN.containsAt(left, top, mouseX, mouseY);
        var modeSlice = singleActive
            ? FusionGuiSkin.SLICE_ROW_SELECTED_B
            : (hovSingle ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED);
        FusionGuiSkin.drawNineSlice(
            ctx, modeSlice,
            left + SINGLE_MODE_BTN.x(), top + SINGLE_MODE_BTN.y(),
            SINGLE_MODE_BTN.w(), SINGLE_MODE_BTN.h()
        );
        String chipLabel = Component.translatable(
            singleActive ? "gui.enchantment_fusion.mode.single_short" : "gui.enchantment_fusion.mode.pair_short"
        ).getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, chipLabel,
            left + SINGLE_MODE_BTN.x() + 2, SINGLE_MODE_BTN.w() - 4,
            top + SINGLE_MODE_BTN.y() + 7.5F,
            0.50F, singleActive ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
        );

        String statusLine = preview.executable()
            ? buildPreviewTitleLine(preview, registries) + " \u00B7 " + buildCostSummaryLine(preview)
            : (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS
                ? Component.translatable("gui.enchantment_fusion.preview.purify_hint").getString()
                : buildPreviewTitleLine(preview, registries));
        int statusCol = preview.executable()
            ? FusionGuiSkin.COLOR_GREEN
            : (preview.rejectReason() == RejectReason.NONE ? FusionGuiSkin.COLOR_MUTED : FusionGuiSkin.COLOR_CRIMSON);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, statusLine,
            left + RIGHT_INNER_X, top + 82, RIGHT_INNER_W,
            0.52F, statusCol
        );

        renderPrimaryRitualButton(ctx, left, top, mouseX, mouseY, preview);
    }

    private void renderPrimaryRitualButton(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int mouseX,
        int mouseY,
        StationPreview preview
    ) {
        boolean ch = FusionRitualDirector.isChanneling();
        boolean hovExec = EXEC_BTN.containsAt(left, top, mouseX, mouseY);

        if (ch) {
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.SLICE_BTN_HOVER,
                left + EXEC_BTN.x(), top + EXEC_BTN.y(), EXEC_BTN.w(), EXEC_BTN.h()
            );
            int fillW = Math.max(2, Math.round((EXEC_BTN.w() - 4) * FusionRitualDirector.channelProgress()));
            FusionGuiSkin.drawSprite(
                ctx, FusionGuiSkin.RITUAL_PROGRESS_FILL,
                left + EXEC_BTN.x() + 2, top + EXEC_BTN.y() + EXEC_BTN.h() - 4,
                fillW, 2
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable("gui.enchantment_fusion.ritual.channeling").getString(),
                left + EXEC_BTN.x() + 4, EXEC_BTN.w() - 8,
                top + EXEC_BTN.y() + EXEC_BTN.h() * 0.45F,
                0.54F, FusionGuiSkin.COLOR_GOLD
            );
        } else {
            String label = "\u25C6 " + Component.translatable(primaryActionLabelKey(preview.kind())).getString();
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, EXEC_BTN,
                preview.executable(), hovExec,
                label, 0.58F, FusionGuiSkin.COLOR_GOLD
            );
        }
    }

    private void renderCandidateRows(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int mouseX,
        int mouseY,
        ResolvedSelection sel,
        HolderLookup.Provider registries,
        int startY,
        int visibleRows,
        int rowW
    ) {
        List<EnchantCandidate> list = sel.candidates();
        if (list.isEmpty()) {
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.SLICE_ROW_UNSELECTED,
                left + RIGHT_INNER_X, top + startY, rowW, visibleRows * 16 - 1
            );
            String emptyKey = visibleRows == 1
                ? "gui.enchantment_fusion.picker.empty_gear"
                : "gui.enchantment_fusion.picker.empty_books";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable(emptyKey).getString(),
                left + RIGHT_INNER_X + 4, rowW - 8,
                top + startY + (visibleRows * 16) * 0.48F,
                0.52F, FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        int maxScroll = Math.max(0, list.size() - visibleRows);
        this.candidateScroll = Math.max(0, Math.min(this.candidateScroll, maxScroll));

        for (int r = 0; r < visibleRows; r++) {
            int idx = this.candidateScroll + r;
            if (idx >= list.size()) {
                break;
            }
            EnchantCandidate cand = list.get(idx);
            HitBox rowBox = new HitBox(RIGHT_INNER_X, startY + r * 17, rowW, 15);
            boolean isA = idx == sel.primaryIndex();
            boolean isB = idx == sel.secondaryIndex();
            boolean hov = rowBox.containsAt(left, top, mouseX, mouseY);

            var slice = isA ? FusionGuiSkin.SLICE_ROW_SELECTED_A
                : (isB ? FusionGuiSkin.SLICE_ROW_SELECTED_B
                    : (hov ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED));
            FusionGuiSkin.drawNineSlice(ctx, slice, left + rowBox.x(), top + rowBox.y(), rowBox.w(), rowBox.h());

            String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
            String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u25C6" : "";
            String enchName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level()).getString();
            int textColor = isA ? FusionGuiSkin.COLOR_GOLD : (isB ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_CREAM);
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font,
                rolePrefix + srcBadge + enchName,
                left + rowBox.x() + 4, top + rowBox.y() + 4,
                rowBox.w() - (maxScroll > 0 && r == 0 ? 18 : 8),
                0.52F, textColor
            );
        }

        if (maxScroll > 0) {
            String ind = (this.candidateScroll + 1) + "/" + list.size();
            FusionGuiSkin.drawRightBoundedText(
                ctx, this.font, ind,
                left + RIGHT_INNER_X + rowW - 3, top + startY + 4, 20,
                0.48F, FusionGuiSkin.COLOR_MUTED
            );
        }
    }

    private void renderGrimoireButton(GuiGraphicsExtractor ctx, int left, int top, int mouseX, int mouseY) {
        boolean hov = GRIMOIRE_BTN.containsAt(left, top, mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx, hov ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL,
            left + GRIMOIRE_BTN.x(), top + GRIMOIRE_BTN.y(), GRIMOIRE_BTN.w(), GRIMOIRE_BTN.h()
        );
        ctx.pose().pushMatrix();
        ctx.pose().translate(left + GRIMOIRE_BTN.x() + 9.0F, top + GRIMOIRE_BTN.y() + 8.0F);
        ctx.pose().scale(0.75F, 0.75F);
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), -8, -8);
        ctx.pose().popMatrix();
    }

    private String buildPreviewTitleLine(StationPreview preview, HolderLookup.Provider registries) {
        if (preview.kind() == OperationKind.INSCRIBE_SINGLE_CROSS
            && preview.previewTransmute() != null
            && preview.singleCandidate() != null) {
            return "\u2726 " + Component.translatable(preview.previewTransmute().titleKey()).getString();
        }
        if (preview.kind() == OperationKind.PURIFY_ABERRANT && preview.previewPair() != null) {
            return "\u25C6 " + Component.translatable("gui.enchantment_fusion.btn.purify").getString();
        }
        if (preview.previewPair() != null) {
            return FusionTooltipRenderer.buildPairDisplayName(registries, preview.previewPair()).getString();
        }
        if (preview.kind() == OperationKind.EXPAND_VESSEL) {
            return "\u25C6 " + Component.translatable("gui.enchantment_fusion.btn.expand_vessel").getString();
        }
        if (preview.rejectReason() != RejectReason.NONE) {
            return Component.translatable(rejectKey(preview.rejectReason())).getString();
        }
        return Component.translatable("gui.enchantment_fusion.preview.idle_title").getString();
    }

    private String buildCostSummaryLine(StationPreview preview) {
        EconomyQuote q = preview.economyQuote();
        if (q == null) {
            return Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.xpCost()).getString();
        }
        return q.xpLevels() + "Lv \u00B7 " + q.reagentCount() + "x "
            + Component.translatable(q.reagent().translationKey()).getString();
    }

    private BookManageState evaluateBookManage(StationPreview preview) {
        ItemStack out = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        boolean hasFused = out.is(FusionItems.FUSED_ENCHANTED_BOOK);
        FusedEnchantmentList list = out.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!hasFused || list.isEmpty()) {
            return new BookManageState(false, false, false, false, preview.manageXpCost(), "", ItemStack.EMPTY, ItemStack.EMPTY);
        }
        FusedPair first = list.pairs().get(0);
        boolean ab = list.hasAberration();
        boolean secOcc = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty();
        boolean xpOk = this.menu.viewer().experienceLevel >= preview.manageXpCost();
        ItemStack bookSample = new ItemStack(Items.ENCHANTED_BOOK);
        return new BookManageState(true, ab, secOcc, xpOk, preview.manageXpCost(), first.fingerprint(), bookSample, bookSample);
    }

    public static HitBox socketFlipBox(int socketIndex) {
        return new HitBox(261, 28 + socketIndex * 17, 15, 14);
    }

    public static HitBox socketUnsocketBox(int socketIndex) {
        return new HitBox(278, 28 + socketIndex * 17, 15, 14);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        if (this.grimoireOverlay != null) {
            super.extractRenderState(ctx, -1000, -1000, partialTick);
            this.grimoireOverlay.extractRenderState(ctx, mouseX, mouseY, partialTick);
            return;
        }

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        FusionGuiMicroEffects.stepAndRenderOverlayParticles(ctx);
        this.extractTooltip(ctx, mouseX, mouseY);
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) {
            renderHoverTooltips(ctx, mouseX, mouseY);
        }
    }

    private void renderHoverTooltips(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        StationPreview preview = this.cachedFramePreview != null ? this.cachedFramePreview : this.menu.computePreview();
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }
        boolean gearMode = isGearModeActive();
        int left = this.leftPos;
        int top = this.topPos;

        this.contextDrawer.appendHoverTooltip(lines, left, top, mouseX, mouseY);
        if (this.contextDrawer.isExpanded() && this.contextDrawer.popoverBox(left, top).contains(mouseX, mouseY)) {
            flushTooltip(ctx, lines, mouseX, mouseY);
            return;
        }

        if (lines.isEmpty() && GRIMOIRE_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("item.enchantment_fusion.fusion_grimoire").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.grimoire_btn").withStyle(ChatFormatting.GRAY));
        } else if (lines.isEmpty() && SWAP_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.desc").withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap_slots.desc").withStyle(ChatFormatting.GRAY));
        } else if (lines.isEmpty() && gearMode && SINGLE_MODE_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.single_mode.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.single_mode.desc").withStyle(ChatFormatting.GRAY));
        } else if (lines.isEmpty() && !gearMode && !preview.executable() && evaluateBookManage(preview).hasBook()
            && !evaluateBookManage(preview).isAberrant()
            && (BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY) || BOOK_SPLIT_BTN.containsAt(left, top, mouseX, mouseY))) {
            appendFlipOrSplitHoverLines(lines, evaluateBookManage(preview), BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY));
        } else if (lines.isEmpty() && appendHoveredPickerRowTooltip(lines, registries, preview.selection(), gearMode, mouseX, mouseY)) {
            // Handled
        } else if (lines.isEmpty() && EXEC_BTN.containsAt(left, top, mouseX, mouseY)
            && preview.kind() == OperationKind.INSCRIBE_SINGLE_CROSS
            && preview.previewTransmute() != null
            && preview.singleCandidate() != null) {
            EnchantCandidate cand = preview.singleCandidate();
            int lv = Math.min(cand.maxLevel(), cand.level());
            lines.add(FusionTooltipRenderer.buildTransmutedDisplayName(
                preview.previewTransmute(), lv, cand.maxLevel(), cand.id()
            ));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.cross_zero_slot").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(
                "tooltip.enchantment_fusion.transmute_origin_desc",
                FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), lv),
                Component.translatable(preview.previewTransmute().descKey())
            ).withStyle(ChatFormatting.DARK_AQUA));
            appendEconomyTooltipLines(lines, preview);
        } else if (lines.isEmpty()
            && (EXEC_BTN.containsAt(left, top, mouseX, mouseY) || (!gearMode && BOOK_PREVIEW_BOX.containsAt(left, top, mouseX, mouseY)))
            && preview.kind() == OperationKind.PURIFY_ABERRANT
            && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));
            lines.add(Component.translatable("gui.enchantment_fusion.btn.purify").withStyle(ChatFormatting.GOLD));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy().withStyle(ChatFormatting.GREEN));
            lines.add(FusionTooltipRenderer.buildEffectSummary(p, form).copy().withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.enchantment_fusion.preview.purify_ready").withStyle(ChatFormatting.GRAY));
            appendEconomyTooltipLines(lines, preview);
        } else if (lines.isEmpty()
            && (EXEC_BTN.containsAt(left, top, mouseX, mouseY) || (!gearMode && BOOK_PREVIEW_BOX.containsAt(left, top, mouseX, mouseY)))
            && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(FusionTooltipRenderer.buildEffectSummary(p, form).copy().withStyle(ChatFormatting.AQUA));
            if (preview.executable()) {
                appendEconomyTooltipLines(lines, preview);
                FusedPair rev = p.flipped();
                lines.add(Component.translatable("gui.enchantment_fusion.tip.preview.reverse_hint").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, rev).copy().withStyle(ChatFormatting.GOLD));
                lines.add(FusionTooltipRenderer.buildEffectSummary(rev, form).copy().withStyle(ChatFormatting.GRAY));
            } else if (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS) {
                appendAberrantPurifyHintLines(lines);
            }
        } else if (lines.isEmpty() && EXEC_BTN.containsAt(left, top, mouseX, mouseY) && !preview.executable()) {
            lines.add(Component.translatable(primaryActionLabelKey(preview.kind())).withStyle(ChatFormatting.GOLD));
            lines.add(explainPrimaryDisabledReason(preview).copy().withStyle(ChatFormatting.RED));
            if (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS) {
                appendAberrantPurifyHintLines(lines);
            }
        } else if (lines.isEmpty() && gearMode) {
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int idx = 0; idx < Math.min(2, socketed.size()); idx++) {
                FusedPair p = socketed.pairs().get(idx);
                HitBox flipBox = socketFlipBox(idx);
                HitBox unsockBox = socketUnsocketBox(idx);
                HitBox cardBox = new HitBox(RIGHT_INNER_X, 27 + idx * 17, RIGHT_INNER_W, 16);
                if (flipBox.containsAt(left, top, mouseX, mouseY) || unsockBox.containsAt(left, top, mouseX, mouseY)) {
                    String key = flipBox.containsAt(left, top, mouseX, mouseY)
                        ? "gui.enchantment_fusion.btn.flip"
                        : "gui.enchantment_fusion.btn.unsocket";
                    lines.add(Component.translatable(key).withStyle(ChatFormatting.GOLD));
                    if (p.isAberrant()) {
                        appendAberrantPurifyHintLines(lines);
                    } else if (this.menu.viewer().experienceLevel < preview.manageXpCost()) {
                        lines.add(Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.manageXpCost())
                            .withStyle(ChatFormatting.RED));
                    } else {
                        lines.add(Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.manageXpCost())
                            .withStyle(ChatFormatting.GREEN));
                    }
                    break;
                } else if (cardBox.containsAt(left, top, mouseX, mouseY)) {
                    lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p));
                    lines.add(FusionTooltipRenderer.buildEffectSummary(p, HostForm.resolve(host)).copy()
                        .withStyle(ChatFormatting.AQUA));
                    if (p.isAberrant()) {
                        appendAberrantPurifyHintLines(lines);
                    }
                    break;
                }
            }
        } else if (lines.isEmpty() && this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.hoveredSlot.index < 4) {
            String slotTipKey = switch (this.hoveredSlot.index) {
                case 0 -> gearMode ? "gui.enchantment_fusion.tip.slot_primary_gear" : "gui.enchantment_fusion.tip.slot_primary";
                case 1 -> gearMode ? "gui.enchantment_fusion.tip.slot_secondary_gear" : "gui.enchantment_fusion.tip.slot_secondary";
                case 2 -> "gui.enchantment_fusion.tip.slot_catalyst";
                default -> gearMode ? "gui.enchantment_fusion.tip.slot_target_gear" : "gui.enchantment_fusion.tip.slot_target_fuse";
            };
            lines.add(Component.translatable(slotTipKey).withStyle(ChatFormatting.GRAY));
        }

        flushTooltip(ctx, lines, mouseX, mouseY);
    }

    private void flushTooltip(GuiGraphicsExtractor ctx, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int maxTipWidth = Math.max(160, Math.min(250, ctx.guiWidth() - 32));
        for (Component c : lines) {
            wrapped.addAll(this.font.split(c, maxTipWidth));
        }
        ctx.setTooltipForNextFrame(
            this.font, wrapped, Optional.empty(),
            DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false, null
        );
    }

    private boolean appendHoveredPickerRowTooltip(
        List<Component> lines,
        HolderLookup.Provider registries,
        ResolvedSelection sel,
        boolean gearMode,
        int mouseX,
        int mouseY
    ) {
        List<EnchantCandidate> list = sel.candidates();
        if (list.isEmpty()) {
            return false;
        }
        int visibleRows = gearMode ? 1 : 2;
        int startY = gearMode ? 63 : 27;
        int rowW = gearMode ? 74 : RIGHT_INNER_W;
        int scroll = Math.max(0, Math.min(this.candidateScroll, Math.max(0, list.size() - visibleRows)));
        for (int r = 0; r < visibleRows; r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            HitBox rowBox = new HitBox(RIGHT_INNER_X, startY + r * 17, rowW, 15);
            if (rowBox.containsAt(this.leftPos, this.topPos, mouseX, mouseY)) {
                EnchantCandidate cand = list.get(idx);
                boolean isA = idx == sel.primaryIndex();
                boolean isB = idx == sel.secondaryIndex();
                String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
                String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u25C6" : "";
                Component fullLabel = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level());
                FittedText fitted = FusionGuiSkin.fitBoundedText(
                    this.font, rolePrefix + srcBadge + fullLabel.getString(),
                    rowBox.w() - 10, 0.52F
                );
                if (fitted.truncated()) {
                    lines.add(fullLabel.copy().withStyle(isA ? ChatFormatting.GOLD : (isB ? ChatFormatting.AQUA : ChatFormatting.WHITE)));
                    lines.add(Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").withStyle(ChatFormatting.GRAY));
                    return true;
                }
            }
        }
        return false;
    }

    private void appendAberrantPurifyHintLines(List<Component> lines) {
        var econ = FusionMatrixRegistry.snapshotFor(true).economy();
        lines.add(Component.translatable(
            "tooltip.enchantment_fusion.aberrant_native_purify",
            econ.purifyAberrantStabilizers(),
            econ.purifyAberrantXp()
        ).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(
            CetSoftBridge.isCetLoaded()
                ? "tooltip.enchantment_fusion.aberrant_cet_active"
                : "tooltip.enchantment_fusion.aberrant_cet_required"
        ).withStyle(CetSoftBridge.isCetLoaded() ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
    }

    private void appendFlipOrSplitHoverLines(List<Component> lines, BookManageState mState, boolean isFlip) {
        lines.add(Component.translatable(isFlip ? "gui.enchantment_fusion.btn.flip" : "gui.enchantment_fusion.btn.split")
            .withStyle(ChatFormatting.GOLD));
        if (!mState.hasBook()) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.manage_need_book").withStyle(ChatFormatting.RED));
        } else if (mState.isAberrant()) {
            appendAberrantPurifyHintLines(lines);
        } else if (mState.secondaryOccupied()) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.manage_clear_secondary").withStyle(ChatFormatting.YELLOW));
        } else if (!mState.enoughXp()) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", mState.xpCost()).withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("gui.enchantment_fusion.preview.xp_cost", mState.xpCost()).withStyle(ChatFormatting.GREEN));
        }
    }

    private void appendEconomyTooltipLines(List<Component> lines, StationPreview preview) {
        EconomyQuote q = preview.economyQuote();
        if (q == null) {
            lines.add(Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.xpCost())
                .withStyle(ChatFormatting.GREEN));
            return;
        }
        lines.add(Component.translatable("gui.enchantment_fusion.preview.xp_cost", q.xpLevels())
            .withStyle(ChatFormatting.GREEN));

        if (q.reagentCount() > 0) {
            Player viewer = this.menu.viewer();
            var src = FusionEconomyRules.inspectReagentSources(
                this.menu.stationContainer(), viewer, q.reagent(), q.reagentCount()
            );
            Component reagentName = Component.translatable(q.reagent().translationKey());
            ChatFormatting col = src.totalAvailable() >= q.reagentCount() ? ChatFormatting.AQUA : ChatFormatting.RED;
            lines.add(Component.translatable(
                "gui.enchantment_fusion.tip.fee_detail",
                q.xpLevels(), q.reagentCount(), reagentName, src.totalAvailable()
            ).withStyle(col));
            lines.add(Component.translatable(
                "gui.enchantment_fusion.tip.reagent_source",
                src.takenFromSlot(), src.takenFromBackpack()
            ).withStyle(ChatFormatting.GRAY));
        }
        if (q.stabilizersUsed() > 0) {
            lines.add(Component.translatable(
                "gui.enchantment_fusion.preview.stabilizer_used",
                q.stabilizersUsed()
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        int failPct = Math.round((float) (q.failureChance() * 100.0D));
        var econ = FusionMatrixRegistry.snapshotFor(true).economy();
        int minFloorPct = Math.round((float) (econ.repulsiveMinFloor() * 100.0D));
        int feeSpendPct = Math.round((float) (econ.normalFailFeeRatio() * 100.0D));
        if (q.highRiskTier()) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_high", failPct, minFloorPct)
                .withStyle(ChatFormatting.RED));
        } else if (failPct > 0) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_normal", failPct, feeSpendPct)
                .withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_zero").withStyle(ChatFormatting.GREEN));
        }
    }

    private Component explainPrimaryDisabledReason(StationPreview preview) {
        if (preview.rejectReason() != RejectReason.NONE) {
            return Component.translatable(rejectKey(preview.rejectReason()));
        }
        if (preview.kind() == OperationKind.NONE) {
            return Component.translatable("gui.enchantment_fusion.preview.idle_title");
        }
        if (this.menu.viewer().experienceLevel < preview.xpCost()) {
            return Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.xpCost());
        }
        return Component.translatable("gui.enchantment_fusion.reject.invalid_combination");
    }

    private Component explainManageDisabledReason(BookManageState mState) {
        if (!mState.hasBook()) {
            return Component.translatable("gui.enchantment_fusion.tip.manage_need_book");
        }
        if (mState.isAberrant()) {
            var econ = FusionMatrixRegistry.snapshotFor(true).economy();
            return Component.translatable(
                "tooltip.enchantment_fusion.aberrant_native_purify",
                econ.purifyAberrantStabilizers(),
                econ.purifyAberrantXp()
            );
        }
        if (mState.secondaryOccupied()) {
            return Component.translatable("gui.enchantment_fusion.tip.manage_clear_secondary");
        }
        return Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", mState.xpCost());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.grimoireOverlay != null) {
            if (event.key() == 256) {
                this.grimoireOverlay = null;
                FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.7F);
                return true;
            }
            return this.grimoireOverlay.keyPressed(event);
        }

        if (event.key() == 256 && this.contextDrawer.isExpanded()) {
            this.contextDrawer.close();
            return true;
        }

        if (event.key() == 256 && FusionRitualDirector.isChanneling()) {
            FusionRitualDirector.cancel();
            return true;
        }

        if ((event.key() == 32 || event.key() == 257 || event.key() == 335) && FusionRitualDirector.isChanneling()) {
            StationPreview preview = this.menu.computePreview();
            String token = preview.expectedExecuteToken();
            FusionRitualDirector.triggerOrSkip(
                preview.kind(),
                token,
                "",
                false,
                this.menu.tableBlockPos(),
                () -> sendAction(StationAction.EXECUTE_PRIMARY, 0, token)
            );
            return true;
        }

        if (FusionGuiSkin.isCtrlOrCmdDown() && event.key() == 71) { // Ctrl + G -> Grimoire
            openEmbeddedGrimoire();
            return true;
        }

        if (event.key() == 257 || event.key() == 335) { // Enter -> Start Ritual
            StationPreview preview = this.menu.computePreview();
            if (preview.executable()) {
                launchOrSkipPrimaryRitual(preview);
                return true;
            }
        }

        if (event.key() == 265 || event.key() == 264) {
            StationPreview preview = this.menu.computePreview();
            int visibleRows = isGearModeActive() ? 1 : 2;
            int maxScroll = Math.max(0, preview.selection().candidates().size() - visibleRows);
            if (maxScroll > 0) {
                int delta = event.key() == 265 ? -1 : 1;
                this.candidateScroll = Math.max(0, Math.min(maxScroll, this.candidateScroll + delta));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseClicked(event, doubleClick);
        }

        double mx = event.x();
        double my = event.y();
        int btn = event.button();
        StationPreview preview = this.menu.computePreview();
        boolean gearMode = isGearModeActive();

        // 1. In-Panel Synergy Codex Badge & Popover
        if (btn == 0 && this.contextDrawer.mouseClicked(
            mx, my, this.leftPos, this.topPos,
            preview.selection().candidates(),
            (idx, secondary) -> sendAction(secondary ? StationAction.PICK_SECONDARY_B : StationAction.PICK_PRIMARY_A, idx, ""),
            () -> {
                if (gearMode && !this.menu.singleOnlyMode()) {
                    sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, "");
                }
            },
            () -> {
                if (this.menu.singleOnlyMode()) {
                    sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, "");
                }
            },
            this::openEmbeddedGrimoire
        )) {
            return true;
        }

        // 2. Top-Right Embedded Grimoire Button
        if (btn == 0 && GRIMOIRE_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
            openEmbeddedGrimoire();
            return true;
        }

        // 3. Dual-Mode Swap Medallion [⇄]: Left = Swap [A ▷ B] Order, Right or Shift+Left = Swap Physical Slots!
        if (SWAP_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
            if (FusionRitualDirector.isChanneling()) {
                return true;
            }
            boolean swapSlotsRequested = (btn == 1) || (btn == 0 && FusionGuiSkin.isShiftDown());
            if (swapSlotsRequested) {
                if (canSwapPhysicalSlots()) {
                    FusionGuiMicroEffects.triggerFlipFeedback(this.leftPos, this.topPos, true);
                    sendAction(StationAction.SWAP_SLOTS, 0, "");
                } else {
                    notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_slots.empty"));
                }
                return true;
            }
            if (btn == 0) {
                if (canSwapSelectionOrder(preview)) {
                    FusionGuiMicroEffects.triggerFlipFeedback(this.leftPos, this.topPos, false);
                    sendAction(StationAction.SWAP_ORDER, 0, "");
                } else {
                    notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_order.disabled"));
                }
                return true;
            }
        }

        if (btn == 0 || btn == 1) {
            if (!gearMode) {
                BookManageState mState = evaluateBookManage(preview);
                if (!preview.executable() && mState.hasBook() && !mState.isAberrant()) {
                    if (BOOK_FLIP_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
                        if (btn == 0 && mState.canExecute()) {
                            FusionGuiMicroEffects.triggerFlipFeedback(this.leftPos, this.topPos, false);
                            sendAction(StationAction.FLIP_SOCKET, 0, mState.pairFingerprint());
                        } else if (btn == 0) {
                            notifyDisabledClick(explainManageDisabledReason(mState));
                        }
                        return true;
                    }
                    if (BOOK_SPLIT_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
                        if (btn == 0 && mState.canExecute()) {
                            FusionGuiMicroEffects.triggerSplitFeedback(
                                this.leftPos, this.topPos, mState.splitLeftSample(), mState.splitRightSample()
                            );
                            sendAction(StationAction.UNSOCKET_OR_SPLIT, 0, mState.pairFingerprint());
                        } else if (btn == 0) {
                            notifyDisabledClick(explainManageDisabledReason(mState));
                        }
                        return true;
                    }
                }
            } else {
                ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
                FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
                for (int idx = 0; idx < Math.min(2, socketed.size()); idx++) {
                    FusedPair p = socketed.pairs().get(idx);
                    boolean canAct = !p.isAberrant()
                        && this.menu.viewer().experienceLevel >= preview.manageXpCost()
                        && !FusionRitualDirector.isChanneling();
                    if (socketFlipBox(idx).containsAt(this.leftPos, this.topPos, mx, my)) {
                        if (btn == 0 && canAct) {
                            FusionGuiMicroEffects.triggerFlipFeedback(this.leftPos, this.topPos, false);
                            sendAction(StationAction.FLIP_SOCKET, idx, p.fingerprint());
                        } else if (btn == 0) {
                            notifyDisabledClick(p.isAberrant()
                                ? Component.translatable("gui.enchantment_fusion.preview.purify_hint")
                                : Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.manageXpCost()));
                        }
                        return true;
                    }
                    if (socketUnsocketBox(idx).containsAt(this.leftPos, this.topPos, mx, my)) {
                        if (btn == 0 && canAct) {
                            ItemStack fusedSample = new ItemStack(FusionItems.FUSED_ENCHANTED_BOOK);
                            FusionGuiMicroEffects.triggerSplitFeedback(this.leftPos, this.topPos, host, fusedSample);
                            sendAction(StationAction.UNSOCKET_OR_SPLIT, idx, p.fingerprint());
                        } else if (btn == 0) {
                            notifyDisabledClick(p.isAberrant()
                                ? Component.translatable("gui.enchantment_fusion.preview.purify_hint")
                                : Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.manageXpCost()));
                        }
                        return true;
                    }
                }

                if (btn == 0 && SINGLE_MODE_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
                    FusionRitualDirector.cancel();
                    FusionGuiMicroEffects.playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.15F, 0.65F);
                    sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, "");
                    return true;
                }
            }

            // Candidate Picker Rows
            int visibleRows = gearMode ? 1 : 2;
            int startY = gearMode ? 63 : 27;
            int rowW = gearMode ? 74 : RIGHT_INNER_W;
            List<EnchantCandidate> cands = preview.selection().candidates();
            for (int r = 0; r < visibleRows; r++) {
                int idx = this.candidateScroll + r;
                if (idx >= cands.size()) break;
                HitBox rowBox = new HitBox(RIGHT_INNER_X, startY + r * 17, rowW, 15);
                if (rowBox.containsAt(this.leftPos, this.topPos, mx, my)) {
                    FusionRitualDirector.cancel();
                    FusionGuiMicroEffects.playUiSound(SoundEvents.AMETHYST_CLUSTER_STEP, btn == 1 ? 1.25F : 1.0F, 0.7F);
                    if (btn == 0 && gearMode && FusionGuiSkin.isShiftDown()) {
                        if (!this.menu.singleOnlyMode()) {
                            sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, "");
                        }
                        sendAction(StationAction.PICK_PRIMARY_A, idx, "");
                    } else if (btn == 1) {
                        if (this.menu.singleOnlyMode()) {
                            sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, "");
                        }
                        sendAction(StationAction.PICK_SECONDARY_B, idx, "");
                    } else {
                        sendAction(StationAction.PICK_PRIMARY_A, idx, "");
                    }
                    return true;
                }
            }

            // Primary Ritual Execute / Skip Button
            if (btn == 0 && EXEC_BTN.containsAt(this.leftPos, this.topPos, mx, my)) {
                if (preview.executable() || FusionRitualDirector.isChanneling()) {
                    launchOrSkipPrimaryRitual(preview);
                } else {
                    notifyDisabledClick(explainPrimaryDisabledReason(preview));
                }
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void launchOrSkipPrimaryRitual(StationPreview preview) {
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }
        String revealTitle = buildPreviewTitleLine(preview, registries);
        boolean highRisk = preview.economyQuote() != null && preview.economyQuote().highRiskTier();
        String execToken = preview.expectedExecuteToken();

        FusionRitualDirector.triggerOrSkip(
            preview.kind(),
            execToken,
            revealTitle,
            highRisk,
            this.menu.tableBlockPos(),
            () -> sendAction(StationAction.EXECUTE_PRIMARY, 0, execToken)
        );
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseScrolled(mx, my, scrollX, scrollY);
        }
        if (this.contextDrawer.mouseScrolled(mx, my, scrollY, this.leftPos, this.topPos)) {
            return true;
        }
        StationPreview preview = this.menu.computePreview();
        int visibleRows = isGearModeActive() ? 1 : 2;
        int maxScroll = Math.max(0, preview.selection().candidates().size() - visibleRows);
        if (maxScroll > 0 && mx >= this.leftPos + RIGHT_WING_X && mx <= this.leftPos + RIGHT_WING_X + RIGHT_WING_W) {
            int delta = scrollY > 0 ? -1 : 1;
            this.candidateScroll = Math.max(0, Math.min(maxScroll, this.candidateScroll + delta));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        FusionRitualDirector.cancel();
        super.onClose();
    }

    private void sendAction(StationAction action, int index, String token) {
        ClientPlayNetworking.send(
            new StationActionPayload(this.menu.containerId, action, index, 0, token == null ? "" : token)
        );
    }

    private static String primaryActionLabelKey(OperationKind kind) {
        return switch (kind) {
            case FUSE_BOOKS -> "gui.enchantment_fusion.btn.fuse";
            case SOCKET_FUSED_BOOK -> "gui.enchantment_fusion.btn.inscribe";
            case FUSE_ONTO_GEAR -> "gui.enchantment_fusion.btn.fuse_gear";
            case TOP_UP_LEVEL -> "gui.enchantment_fusion.btn.top_up";
            case EXPAND_VESSEL -> "gui.enchantment_fusion.btn.expand_vessel";
            case INSCRIBE_SINGLE_CROSS -> "gui.enchantment_fusion.btn.inscribe_cross";
            case PURIFY_ABERRANT -> "gui.enchantment_fusion.btn.purify";
            default -> "gui.enchantment_fusion.btn.fuse";
        };
    }

    private static String rejectKey(RejectReason reason) {
        if (reason == null || reason == RejectReason.NONE || reason.langKey().isEmpty()) {
            return "gui.enchantment_fusion.reject.invalid_combination";
        }
        return reason.langKey();
    }
}
