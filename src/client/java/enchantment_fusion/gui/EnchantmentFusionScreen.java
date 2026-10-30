package enchantment_fusion.gui;

import com.mojang.blaze3d.platform.InputConstants;
import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.gui.FusionGuiSkin.FittedText;
import enchantment_fusion.gui.FusionGuiSkin.HitBox;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionEconomyRules;
import enchantment_fusion.mechanics.FusionEconomyRules.EconomyQuote;
import enchantment_fusion.mechanics.FusionEconomyRules.ReagentKind;
import enchantment_fusion.mechanics.FusionEconomyRules.ReagentSourceBreakdown;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Cutaway Split-Wing Auto-Morphing Enchantment Fusion Altar Screen ({@code 384 x 218}):
 * <ul>
 *   <li><b>Center Cutaway Viewport ({@code 116..232})</b>: Leaves the live 3D Fusion Altar unblurred
 *       and clearly visible through a sculpted Obsidian-Silver arch during the ritual</li>
 *   <li><b>Auto-Morphing Right Wing ({@code 232..384})</b>: Eliminates manual top tabs and duplicate
 *       Flip/Split toolbars, morphing automatically between Book Synthesis and Gear Inscription</li>
 *   <li><b>2.0s Skippable Ritual ({@link FusionRitualDirector})</b>: Synchronizes Gold/Cyan UI conduit
 *       channeling with 3D altar crystal acceleration &amp; purple brazier eruption; second click skips immediately</li>
 *   <li><b>Smart Context Synergy Drawer ({@link FusionContextDrawer})</b>: Slide-out side drawer showing
 *       matched Signature/Transmute synergies for currently loaded items with 1-click auto-selection</li>
 *   <li><b>Audit Enhancements</b>: Native Aberrant Purification ({@link OperationKind#PURIFY_ABERRANT}),
 *       dual-mode Swap Medallion (Left-click {@code [A ▷ B]} order / Right-click or Shift+Click Slot 0↔1),
 *       Reagent Slot-vs-Backpack breakdown tooltips, and pair-fingerprint race protection</li>
 * </ul>
 */
public final class EnchantmentFusionScreen extends AbstractContainerScreen<EnchantmentFusionMenu> {
    public static final int LEFT_WING_X = 0;
    public static final int LEFT_WING_Y = 6;
    public static final int LEFT_WING_W = 116;
    public static final int LEFT_WING_H = 120;

    public static final int ARCH_X = 116;
    public static final int ARCH_Y = 6;
    public static final int ARCH_W = 116;
    public static final int ARCH_H = 118;

    public static final int RIGHT_WING_X = 232;
    public static final int RIGHT_WING_Y = 6;
    public static final int RIGHT_WING_W = 152;
    public static final int RIGHT_WING_H = 120;

    public static final int RIGHT_INNER_X = 240;
    public static final int RIGHT_INNER_W = 136;

    public static final int INV_PEDESTAL_X = 103;
    public static final int INV_PEDESTAL_Y = 128;
    public static final int INV_PEDESTAL_W = 178;
    public static final int INV_PEDESTAL_H = 88;

    public static final HitBox SWAP_BTN = new HitBox(46, 40, 24, 24);
    public static final HitBox GRIMOIRE_BTN = new HitBox(360, 8, 18, 18);
    public static final HitBox EXEC_BTN = new HitBox(240, 95, 136, 23);
    public static final HitBox BOOK_PREVIEW_BOX = new HitBox(RIGHT_INNER_X, 63, RIGHT_INNER_W, 29);

    // Book Mode single inline management buttons
    public static final HitBox BOOK_FLIP_BTN = new HitBox(243, 73, 63, 16);
    public static final HitBox BOOK_SPLIT_BTN = new HitBox(310, 73, 63, 16);

    // Gear Mode single-enchantment toggle chip next to candidate row
    public static final HitBox SINGLE_MODE_BTN = new HitBox(346, 65, 30, 16);

    private int candidateScroll = 0;
    private @Nullable StationPreview cachedFramePreview = null;
    private @Nullable FusionGrimoireScreen grimoireOverlay = null;
    private final FusionContextDrawer contextDrawer = new FusionContextDrawer();
    private int lastSyncedHostMode = -1;

    private @Nullable Component disabledFeedbackText = null;
    private long disabledFeedbackUntilMs = 0L;

    private record BookManageState(
        boolean hasBook,
        boolean isAberrant,
        boolean secondaryOccupied,
        boolean enoughXp,
        int xpCost,
        String pairFingerprint
    ) {
        boolean canExecute() {
            return hasBook && !isAberrant && !secondaryOccupied && enoughXp;
        }
    }

    public EnchantmentFusionScreen(EnchantmentFusionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = EnchantmentFusionMenu.GUI_WIDTH;
        this.imageHeight = EnchantmentFusionMenu.GUI_HEIGHT;
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
            return new Rect2i((this.width - 360) / 2, (this.height - 224) / 2 + 6, 360, 224);
        }
        if (this.contextDrawer.isExpanded()) {
            HitBox db = this.contextDrawer.drawerBounds(this.leftPos, this.topPos, this.imageWidth, this.width);
            int maxRight = Math.max(this.leftPos + this.imageWidth + FusionContextDrawer.TAB_W, db.x() + db.w());
            return new Rect2i(this.leftPos, this.topPos, maxRight - this.leftPos, this.imageHeight);
        }
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth + FusionContextDrawer.TAB_W, this.imageHeight);
    }

    private void openEmbeddedGrimoire() {
        if (this.minecraft == null) {
            return;
        }
        FusionRitualDirector.cancel();
        int initialChapter = isGearModeActive() ? 1 : 0;
        FusionGrimoireScreen overlay = new FusionGrimoireScreen(() -> this.grimoireOverlay = null, initialChapter);
        overlay.init(this.width, this.height);
        this.grimoireOverlay = overlay;
        FusionGuiMicroEffects.playUiSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.85F);
    }

    private boolean isGearModeActive() {
        ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        return CrossDomainTransmutations.isEligibleCrossDomainHost(host);
    }

    private void ensureAutoMorphedTab() {
        int desired = isGearModeActive() ? FusionStationOperations.TAB_SOCKET : FusionStationOperations.TAB_FUSE;
        if (this.menu.activeTab() != desired && this.lastSyncedHostMode != desired) {
            this.lastSyncedHostMode = desired;
            this.candidateScroll = 0;
            sendAction(StationAction.SELECT_TAB, desired, 0, "");
        } else if (this.menu.activeTab() == desired) {
            this.lastSyncedHostMode = desired;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Rendered inside obsidian-silver wing frames with pixel bounding and scissor clipping.
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        // Deliberately omit super.extractBackground while viewing the altar so the 3D world
        // inside the Center Cutaway Viewport remains crystal-clear and unblurred!
        if (this.grimoireOverlay != null) {
            super.extractBackground(ctx, mouseX, mouseY, partialTick);
        }

        ensureAutoMorphedTab();
        StationPreview preview = this.menu.computePreview();
        this.cachedFramePreview = preview;
        boolean gearMode = isGearModeActive();
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }

        boolean readyToExecute = preview.executable() && canAffordPreview(preview);
        boolean highRisk = preview.repulsive() || preview.instabilityChance() >= 0.20;
        String liveToken = preview.expectedExecuteToken();
        FusionRitualDirector.updateFrame(
            liveToken,
            readyToExecute,
            () -> sendAction(StationAction.EXECUTE_PRIMARY, 0, 0, liveToken)
        );

        int shakeX = FusionGuiMicroEffects.currentShakeOffsetX();
        int shakeY = FusionGuiMicroEffects.currentShakeOffsetY();
        int left = this.leftPos + shakeX;
        int top = this.topPos + shakeY;

        boolean channeling = FusionRitualDirector.isChanneling();
        float channelProg = FusionRitualDirector.channelProgress();

        // 1. Left & Right Obsidian-Silver Altar Wings + Bottom Player Inventory Pedestal
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + LEFT_WING_X, top + LEFT_WING_Y, LEFT_WING_W, LEFT_WING_H);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + RIGHT_WING_X, top + RIGHT_WING_Y, RIGHT_WING_W, RIGHT_WING_H);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_PANEL_MAIN, left + INV_PEDESTAL_X, top + INV_PEDESTAL_Y, INV_PEDESTAL_W, INV_PEDESTAL_H);

        // 2. Center Cutaway 3D Viewport Archway + Central Ritual Conduit Bridge
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.VIEWPORT_ARCH, left + ARCH_X, top + ARCH_Y, ARCH_W, ARCH_H);
        float bridgeFill = channeling ? channelProg : (readyToExecute ? 0.24F : 0.0F);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_GOLD, left + 124, top + 113, 50, 5, bridgeFill, false);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_CYAN, left + 174, top + 113, 50, 5, bridgeFill, true);

        if (FusionRitualDirector.hasActiveClimaxBanner()) {
            var banner = FusionRitualDirector.isClimaxAberrant()
                ? FusionGuiSkin.CLIMAX_BANNER_ABERRANT
                : FusionGuiSkin.CLIMAX_BANNER_SIG;
            FusionGuiSkin.drawSprite(ctx, banner, left + 120, top + 10, 108, 17);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, FusionRitualDirector.climaxBannerTitle(),
                left + 124, 100, top + 18.5F,
                0.56F, FusionRitualDirector.isClimaxAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_GOLD
            );
        } else if (this.disabledFeedbackText != null && System.currentTimeMillis() < this.disabledFeedbackUntilMs) {
            FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.CLIMAX_BANNER_ABERRANT, left + 120, top + 10, 108, 17);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, this.disabledFeedbackText.getString(),
                left + 124, 100, top + 18.5F,
                0.52F, FusionGuiSkin.COLOR_WARN
            );
        }

        // 3. 4-Corner Miniature Purple Braziers (matching the 3D Altar's 4 corner braziers!)
        boolean braziersLit = readyToExecute || channeling;
        FusionGuiSkin.drawMiniBrazier(ctx, left + 5, top + 9, braziersLit, channeling);
        FusionGuiSkin.drawMiniBrazier(ctx, left + 97, top + 9, braziersLit, channeling);
        FusionGuiSkin.drawMiniBrazier(ctx, left + 237, top + 9, braziersLit, channeling);
        FusionGuiSkin.drawMiniBrazier(ctx, left + 342, top + 9, braziersLit, channeling);

        // 4. Ambient Pedestal Rune Halo & Motes
        FusionGuiMicroEffects.renderPedestalResonanceBackdrop(
            ctx, left, top, readyToExecute, highRisk || channeling
        );

        // 5. Left Wing Energy Conduits + Role-Coded Slot Frames + [⇄] Medallion
        renderLeftWingPedestal(ctx, left, top, preview, mouseX, mouseY, channeling, channelProg);

        // 6. Top-Right Full Grimoire Quick Button (renders actual Fusion Grimoire item icon!)
        int gx = left + GRIMOIRE_BTN.x();
        int gy = top + GRIMOIRE_BTN.y();
        FusionGuiSkin.drawNineSlice(
            ctx,
            GRIMOIRE_BTN.containsAt(left, top, mouseX, mouseY) ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL,
            gx, gy, GRIMOIRE_BTN.w(), GRIMOIRE_BTN.h()
        );
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), gx + 1, gy + 1);

        // 7. Player Inventory Slot Frames
        for (int i = 4; i < this.menu.slots.size(); i++) {
            Slot s = this.menu.slots.get(i);
            FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_DEFAULT, left + s.x - 1, top + s.y - 1, 18, 18);
        }

        // 8. Scissor-Clipped Auto-Morphing Right Wing
        ctx.enableScissor(
            left + RIGHT_WING_X + 5, top + RIGHT_WING_Y + 5,
            left + RIGHT_WING_X + RIGHT_WING_W - 5, top + RIGHT_WING_Y + RIGHT_WING_H - 5
        );
        try {
            if (gearMode) {
                renderGearInscriptionWing(ctx, left, top, preview, registries, mouseX, mouseY, channeling, channelProg, highRisk);
            } else {
                renderBookSynthesisWing(ctx, left, top, preview, registries, mouseX, mouseY, channeling, channelProg, highRisk);
            }
        } finally {
            ctx.disableScissor();
        }

        // 9. Smart Contextual Synergy Drawer (Right Pull-Tab + Slide-Out Panel)
        this.contextDrawer.render(
            ctx, this.font, this.leftPos, this.topPos, this.imageWidth, this.width,
            preview.selection().candidates(), registries, mouseX, mouseY
        );
    }

    private void renderLeftWingPedestal(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        StationPreview preview,
        int mouseX,
        int mouseY,
        boolean channeling,
        float channelProg
    ) {
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font,
            Component.translatable("block.enchantment_fusion.enchantment_fusion_table").getString(),
            left + 20, 76, top + 16.0F,
            0.58F, FusionGuiSkin.COLOR_GOLD
        );

        String roleGuide = preview.selection().primary() != null && preview.selection().secondary() != null
            ? "[A] \u25B7 [B]"
            : Component.translatable("gui.enchantment_fusion.pedestal.fuse_roles").getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, roleGuide,
            left + 8, 100, top + 31.0F,
            0.52F, FusionGuiSkin.COLOR_MUTED
        );

        boolean hasA = preview.selection().primary() != null;
        boolean hasB = preview.selection().secondary() != null;
        float topFlow = channeling ? Math.min(1.0F, channelProg * 1.45F) : (hasA ? 0.55F : 0.0F);
        float secFlow = channeling ? Math.min(1.0F, channelProg * 1.45F) : (hasB ? 0.55F : 0.0F);
        float coreFlow = channeling ? clamp01((channelProg - 0.25F) * 1.35F) : (preview.executable() ? 0.45F : 0.0F);

        // Gold [A] -> Catalyst conduit
        FusionGuiSkin.drawConduitV(ctx, FusionGuiSkin.CONDUIT_GOLD, left + 25, top + 62, 4, 15, topFlow);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_GOLD, left + 25, top + 77, 24, 4, topFlow, false);

        // Cyan [B] -> Catalyst conduit
        FusionGuiSkin.drawConduitV(ctx, FusionGuiSkin.CONDUIT_CYAN, left + 89, top + 62, 4, 15, secFlow);
        FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.CONDUIT_CYAN, left + 69, top + 77, 24, 4, secFlow, true);

        // Amethyst Catalyst -> Target Pedestal conduit
        FusionGuiSkin.drawConduitV(ctx, FusionGuiSkin.CONDUIT_AMETHYST, left + 56, top + 91, 4, 9, coreFlow);

        // 4 Workstation Slot Frames
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_PRIMARY, left + EnchantmentFusionMenu.SLOT_PRIMARY_X - 1, top + EnchantmentFusionMenu.SLOT_PRIMARY_Y - 1, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_SECONDARY, left + EnchantmentFusionMenu.SLOT_SECONDARY_X - 1, top + EnchantmentFusionMenu.SLOT_SECONDARY_Y - 1, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_CATALYST, left + EnchantmentFusionMenu.SLOT_CATALYST_X - 1, top + EnchantmentFusionMenu.SLOT_CATALYST_Y - 1, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_DEFAULT, left + EnchantmentFusionMenu.SLOT_TARGET_X - 1, top + EnchantmentFusionMenu.SLOT_TARGET_Y - 1, 18, 18);

        // Central [⇄] Swap Medallion (Left-click = [A ▷ B] order, Right-click / Shift+Click = physical Slot 0↔1)
        boolean canSwapAny = canSwapPhysicalSlots() || canSwapSelectionOrder(preview);
        var swapSprite = !canSwapAny
            ? FusionGuiSkin.BTN_SWAP_DISABLED
            : (SWAP_BTN.containsAt(left, top, mouseX, mouseY) ? FusionGuiSkin.BTN_SWAP_HOVER : FusionGuiSkin.BTN_SWAP_NORMAL);
        FusionGuiSkin.drawSprite(ctx, swapSprite, left + SWAP_BTN.x(), top + SWAP_BTN.y(), SWAP_BTN.w(), SWAP_BTN.h());
    }

    private void renderBookSynthesisWing(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        StationPreview preview,
        HolderLookup.Provider registries,
        int mouseX,
        int mouseY,
        boolean channeling,
        float channelProg,
        boolean highRisk
    ) {
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font,
            "\u25C6 " + Component.translatable("gui.enchantment_fusion.tab.fuse").getString(),
            left + 252, 88, top + 16.0F,
            0.60F, FusionGuiSkin.COLOR_GOLD
        );

        // Candidate Picker Rows (2 spacious rows at Y=27..60, W=136)
        renderCandidatePickerRows(ctx, left, top, 27, 2, RIGHT_INNER_W, preview.selection(), registries, mouseX, mouseY, false);

        // Outcome Preview OR Single Inline Fused-Book Management Card (Y=63..91)
        FusionGuiSkin.drawNineSlice(
            ctx,
            highRisk ? FusionGuiSkin.SLICE_SOCKET_ABERRANT : FusionGuiSkin.SLICE_SOCKET_EMPTY,
            left + BOOK_PREVIEW_BOX.x(), top + BOOK_PREVIEW_BOX.y(), BOOK_PREVIEW_BOX.w(), BOOK_PREVIEW_BOX.h()
        );

        BookManageState mState = evaluateBookManage(preview);
        if (!preview.executable() && mState.hasBook() && !mState.isAberrant()) {
            ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
            FusedEnchantmentList mList = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            FusedPair topPair = mList.pairs().getFirst();
            String title = FusionTooltipRenderer.buildPairDisplayName(registries, topPair).getString();
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, title,
                left + RIGHT_INNER_X + 4, RIGHT_INNER_W - 8, top + 68.5F,
                0.52F, topPair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
            );
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_FLIP_BTN,
                mState.canExecute(), BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
                0.54F, FusionGuiSkin.COLOR_CYAN
            );
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_SPLIT_BTN,
                mState.canExecute(), BOOK_SPLIT_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.split").getString(),
                0.54F, FusionGuiSkin.COLOR_GOLD
            );
        } else {
            String headline = resolveOutcomeHeadline(preview, registries);
            int headColor = preview.kind() == OperationKind.INVALID
                ? FusionGuiSkin.COLOR_RED
                : (preview.kind() == OperationKind.PURIFY_ABERRANT
                    ? FusionGuiSkin.COLOR_GREEN
                    : (highRisk ? FusionGuiSkin.COLOR_WARN : (preview.executable() ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED)));
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, headline,
                left + RIGHT_INNER_X + 4, RIGHT_INNER_W - 8, top + 71.0F,
                0.56F, headColor
            );

            String sub;
            if (preview.executable()) {
                sub = formatCompactEconomyLine(preview);
            } else if (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS) {
                sub = Component.translatable("gui.enchantment_fusion.preview.purify_hint").getString();
            } else if (preview.kind() == OperationKind.INVALID) {
                sub = Component.translatable("gui.enchantment_fusion.preview.try_swap_hint").getString();
            } else {
                sub = Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").getString();
            }
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, sub,
                left + RIGHT_INNER_X + 4, RIGHT_INNER_W - 8, top + 83.5F,
                0.51F, preview.executable() && !canAffordPreview(preview) ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_MUTED
            );
        }

        renderRitualExecuteButton(ctx, left, top, preview, mouseX, mouseY, channeling, channelProg, highRisk);
    }

    private void renderGearInscriptionWing(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        StationPreview preview,
        HolderLookup.Provider registries,
        int mouseX,
        int mouseY,
        boolean channeling,
        float channelProg,
        boolean highRisk
    ) {
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            "\u25C6 " + Component.translatable("gui.enchantment_fusion.tab.socket").getString(),
            left + 254, top + 12, 62,
            0.58F, FusionGuiSkin.COLOR_CYAN
        );
        renderVesselDiamonds(ctx, left + 316, top + 11);

        ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        boolean hasGear = VesselCapacityRules.isPotentialEquipmentHost(host);
        int maxSlots = hasGear ? VesselCapacityRules.getMaxVesselSlots(host) : 1;
        FusedEnchantmentList socketed = hasGear
            ? host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            : FusedEnchantmentList.EMPTY;

        for (int idx = 0; idx < 2; idx++) {
            int sy = 27 + idx * 17;
            boolean unlocked = hasGear && idx < maxSlots;
            FusedPair pair = (unlocked && idx < socketed.size()) ? socketed.pairs().get(idx) : null;

            var cardSlice = pair == null
                ? FusionGuiSkin.SLICE_SOCKET_EMPTY
                : (pair.isAberrant() ? FusionGuiSkin.SLICE_SOCKET_ABERRANT : FusionGuiSkin.SLICE_SOCKET_FILLED);
            FusionGuiSkin.drawNineSlice(ctx, cardSlice, left + RIGHT_INNER_X, top + sy, RIGHT_INNER_W, 16);

            if (!hasGear) {
                if (idx == 0) {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.special_cross_host").getString(),
                        left + RIGHT_INNER_X + 6, top + sy + 4, RIGHT_INNER_W - 12,
                        0.54F, FusionGuiSkin.COLOR_CYAN
                    );
                } else if (preview.previewTransmute() != null && preview.singleCandidate() != null) {
                    String tName = FusionTooltipRenderer.buildTransmutedDisplayName(
                        preview.previewTransmute(),
                        Math.min(preview.singleCandidate().maxLevel(), preview.singleCandidate().level()),
                        preview.singleCandidate().maxLevel(),
                        preview.singleCandidate().id()
                    ).getString();
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font, "\u25C6 " + tName,
                        left + RIGHT_INNER_X + 6, top + sy + 4, RIGHT_INNER_W - 12,
                        0.54F, FusionGuiSkin.COLOR_GOLD
                    );
                }
            } else if (!unlocked) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.locked_expand_hint").getString(),
                    left + RIGHT_INNER_X + 6, top + sy + 4, RIGHT_INNER_W - 12,
                    0.53F, FusionGuiSkin.COLOR_MUTED
                );
            } else if (pair == null) {
                if (idx == 0 && preview.previewTransmute() != null && preview.singleCandidate() != null) {
                    String tName = FusionTooltipRenderer.buildTransmutedDisplayName(
                        preview.previewTransmute(),
                        Math.min(preview.singleCandidate().maxLevel(), preview.singleCandidate().level()),
                        preview.singleCandidate().maxLevel(),
                        preview.singleCandidate().id()
                    ).getString();
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font, "\u25C6 " + tName,
                        left + RIGHT_INNER_X + 6, top + sy + 4, RIGHT_INNER_W - 12,
                        0.55F, FusionGuiSkin.COLOR_GOLD
                    );
                } else {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.empty_ready", idx + 1).getString(),
                        left + RIGHT_INNER_X + 6, top + sy + 4, RIGHT_INNER_W - 12,
                        0.54F, FusionGuiSkin.COLOR_CREAM
                    );
                }
            } else {
                String label = FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, label,
                    left + RIGHT_INNER_X + 5, top + sy + 4, 91,
                    0.54F, pair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
                );

                boolean manageable = !pair.isAberrant() && canAffordXp(preview.manageXpCost());
                HitBox flipBox = socketFlipBox(idx);
                HitBox unsockBox = socketUnsocketBox(idx);
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, flipBox,
                    manageable, flipBox.containsAt(left, top, mouseX, mouseY),
                    "\u21C4", 0.56F, FusionGuiSkin.COLOR_CYAN
                );
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, unsockBox,
                    manageable, unsockBox.containsAt(left, top, mouseX, mouseY),
                    "\u25C6", 0.54F, FusionGuiSkin.COLOR_GOLD
                );
            }
        }

        // Candidate Picker Row (Y=65, W=104) + Disjoint [单附] Toggle Chip (X=346, W=30)
        renderCandidatePickerRows(ctx, left, top, 65, 1, 104, preview.selection(), registries, mouseX, mouseY, true);
        boolean singleMode = this.menu.singleOnlyMode();
        boolean hovSingle = SINGLE_MODE_BTN.containsAt(left, top, mouseX, mouseY);
        var singleSlice = singleMode
            ? FusionGuiSkin.SLICE_ROW_SELECTED_A
            : (hovSingle ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL);
        FusionGuiSkin.drawNineSlice(
            ctx, singleSlice,
            left + SINGLE_MODE_BTN.x(), top + SINGLE_MODE_BTN.y(), SINGLE_MODE_BTN.w(), SINGLE_MODE_BTN.h()
        );
        String singleLabel = Component.translatable(
            singleMode ? "gui.enchantment_fusion.btn.single_mode_on" : "gui.enchantment_fusion.btn.single_mode_off"
        ).getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, singleLabel,
            left + SINGLE_MODE_BTN.x() + 2, SINGLE_MODE_BTN.w() - 4, top + SINGLE_MODE_BTN.y() + 8.0F,
            0.50F, singleMode ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );

        // Outcome / Economy Line (Y=84..93)
        String statusLine;
        if (preview.executable()) {
            statusLine = resolveOutcomeHeadline(preview, registries) + " \u00B7 " + formatCompactEconomyLine(preview);
        } else if (preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS) {
            statusLine = Component.translatable("gui.enchantment_fusion.preview.purify_hint").getString();
        } else {
            statusLine = resolveOutcomeHeadline(preview, registries);
        }
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, statusLine,
            left + RIGHT_INNER_X + 2, RIGHT_INNER_W - 4, top + 88.0F,
            0.52F, preview.executable() ? (canAffordPreview(preview) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED) : FusionGuiSkin.COLOR_MUTED
        );

        renderRitualExecuteButton(ctx, left, top, preview, mouseX, mouseY, channeling, channelProg, highRisk);
    }

    private void renderRitualExecuteButton(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        StationPreview preview,
        int mouseX,
        int mouseY,
        boolean channeling,
        float channelProg,
        boolean highRisk
    ) {
        boolean ready = preview.executable() && canAffordPreview(preview);
        boolean hovered = EXEC_BTN.containsAt(left, top, mouseX, mouseY);
        int gx = left + EXEC_BTN.x();
        int gy = top + EXEC_BTN.y();

        if (channeling) {
            FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_BTN_HOVER, gx, gy, EXEC_BTN.w(), EXEC_BTN.h());
            FusionGuiSkin.drawConduitH(ctx, FusionGuiSkin.RITUAL_PROGRESS_FILL, gx + 4, gy + 4, EXEC_BTN.w() - 8, EXEC_BTN.h() - 8, channelProg, false);
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable("gui.enchantment_fusion.ritual.channeling").getString(),
                gx + 4, EXEC_BTN.w() - 8, gy + EXEC_BTN.h() * 0.5F,
                0.58F, FusionGuiSkin.COLOR_GOLD
            );
            return;
        }

        FusionGuiSkin.drawNineSlice(
            ctx,
            !ready ? FusionGuiSkin.SLICE_BTN_DISABLED : (hovered ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL),
            gx, gy, EXEC_BTN.w(), EXEC_BTN.h()
        );
        String label = "\u25C6 " + Component.translatable(primaryActionLabelKey(preview.kind())).getString();
        if (preview.executable()) {
            EconomyQuote q = preview.economyQuote();
            int succPct = q != null ? (int) Math.round((1.0 - q.failureChance()) * 100.0) : 100;
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, label,
                gx + 4, EXEC_BTN.w() - 8, gy + 7.5F,
                0.56F, ready ? (highRisk ? FusionGuiSkin.COLOR_WARN : FusionGuiSkin.COLOR_GOLD) : FusionGuiSkin.COLOR_MUTED
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, preview.xpCost() + "Lv \u00B7 " + succPct + "%",
                gx + 4, EXEC_BTN.w() - 8, gy + 16.5F,
                0.50F, ready ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
            );
        } else {
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, label,
                gx + 4, EXEC_BTN.w() - 8, gy + EXEC_BTN.h() * 0.5F,
                0.58F, FusionGuiSkin.COLOR_MUTED
            );
        }
    }

    private void renderCandidatePickerRows(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        int startY,
        int visibleRows,
        int rowW,
        ResolvedSelection sel,
        HolderLookup.Provider registries,
        int mouseX,
        int mouseY,
        boolean gearMode
    ) {
        List<EnchantCandidate> list = sel.candidates();
        if (list.isEmpty()) {
            FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_ROW_UNSELECTED, left + RIGHT_INNER_X, top + startY, rowW, visibleRows * 17 - 1);
            String emptyKey = gearMode
                ? "gui.enchantment_fusion.picker.empty_gear"
                : "gui.enchantment_fusion.picker.empty_books";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font,
                Component.translatable(emptyKey).getString(),
                left + RIGHT_INNER_X + 6, rowW - 12, top + startY + (visibleRows * 17 - 1) * 0.5F,
                0.54F, FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        int maxScroll = Math.max(0, list.size() - visibleRows);
        candidateScroll = Math.max(0, Math.min(candidateScroll, maxScroll));

        for (int r = 0; r < visibleRows; r++) {
            int idx = candidateScroll + r;
            int ry = startY + r * 17;
            if (idx >= list.size()) {
                FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_ROW_UNSELECTED, left + RIGHT_INNER_X, top + ry, rowW, 16);
                continue;
            }

            EnchantCandidate cand = list.get(idx);
            boolean isA = idx == sel.primaryIndex();
            boolean isB = idx == sel.secondaryIndex();
            HitBox rowBox = new HitBox(RIGHT_INNER_X, ry, rowW, 16);
            boolean hov = rowBox.containsAt(left, top, mouseX, mouseY);

            var rowSlice = isA
                ? FusionGuiSkin.SLICE_ROW_SELECTED_A
                : (isB ? FusionGuiSkin.SLICE_ROW_SELECTED_B : (hov ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED));
            FusionGuiSkin.drawNineSlice(ctx, rowSlice, left + rowBox.x(), top + rowBox.y(), rowBox.w(), rowBox.h());

            String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
            String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u25C6 " : "";
            String enchName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level()).getString();
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, rolePrefix + srcBadge + enchName,
                left + rowBox.x() + 5, top + rowBox.y() + 4, rowBox.w() - 10,
                0.56F, isA ? FusionGuiSkin.COLOR_GOLD : (isB ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_CREAM)
            );
        }
    }

    private void renderVesselDiamonds(GuiGraphicsExtractor ctx, int startX, int y) {
        ItemStack target = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        if (!VesselCapacityRules.isPotentialEquipmentHost(target)) {
            return;
        }
        int maxSlots = VesselCapacityRules.getMaxVesselSlots(target);
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (int i = 0; i < Math.min(2, maxSlots); i++) {
            var sprite = (i < list.size()) ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY;
            FusionGuiSkin.drawSprite(ctx, sprite, startX + i * 13, y, 12, 12);
        }
    }

    private String resolveOutcomeHeadline(StationPreview preview, HolderLookup.Provider registries) {
        if (preview.kind() == OperationKind.INVALID) {
            return Component.translatable(rejectKey(preview.rejectReason())).getString();
        }
        if (preview.kind() == OperationKind.PURIFY_ABERRANT && preview.previewPair() != null) {
            return "\u25C6 " + Component.translatable("gui.enchantment_fusion.btn.purify").getString()
                + ": " + FusionTooltipRenderer.buildPairDisplayName(registries, preview.previewPair()).getString();
        }
        if (preview.previewTransmute() != null && preview.singleCandidate() != null) {
            return "\u25C6 " + FusionTooltipRenderer.buildTransmutedDisplayName(
                preview.previewTransmute(),
                Math.min(preview.singleCandidate().maxLevel(), preview.singleCandidate().level()),
                preview.singleCandidate().maxLevel(),
                preview.singleCandidate().id()
            ).getString();
        }
        if (preview.kind() == OperationKind.EXPAND_VESSEL) {
            return "\u25C6 " + Component.translatable("gui.enchantment_fusion.btn.expand_vessel").getString();
        }
        FusedPair pair = preview.previewPair();
        if (pair != null) {
            return FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
        }
        return Component.translatable("gui.enchantment_fusion.preview.idle_title").getString();
    }

    private String formatCompactEconomyLine(StationPreview preview) {
        EconomyQuote q = preview.economyQuote();
        if (q == null || q.reagent() == ReagentKind.NONE) {
            return Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.xpCost()).getString();
        }
        int succPct = (int) Math.round((1.0 - q.failureChance()) * 100.0);
        return q.xpLevels() + "Lv + " + q.reagentCount() + "\u00D7"
            + Component.translatable(q.reagent().translationKey()).getString()
            + " (" + succPct + "%)";
    }

    private BookManageState evaluateBookManage(StationPreview preview) {
        ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
        FusedEnchantmentList mList = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean hasBook = manageable.is(FusionItems.FUSED_ENCHANTED_BOOK) && !mList.isEmpty();
        FusedPair firstPair = hasBook ? mList.pairs().getFirst() : null;
        boolean aberrant = firstPair != null && firstPair.isAberrant();
        String fp = firstPair != null ? firstPair.fingerprint() : "";
        boolean secBusy = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty() && preview.executable();
        boolean xpOk = canAffordXp(preview.manageXpCost());
        return new BookManageState(hasBook, aberrant, secBusy, xpOk, preview.manageXpCost(), fp);
    }

    private boolean canSwapPhysicalSlots() {
        return !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_PRIMARY).isEmpty()
            || !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty();
    }

    private boolean canSwapSelectionOrder(StationPreview preview) {
        return !this.menu.singleOnlyMode()
            && preview.selection().primaryIndex() >= 0
            && preview.selection().secondaryIndex() >= 0;
    }

    public static HitBox socketFlipBox(int socketIndex) {
        return new HitBox(339, 28 + socketIndex * 17, 16, 14);
    }

    public static HitBox socketUnsocketBox(int socketIndex) {
        return new HitBox(357, 28 + socketIndex * 17, 16, 14);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    // =========================================================================
    // Render State + Modal Grimoire Overlay + Hover Tooltips
    // =========================================================================

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

        this.contextDrawer.appendHoverTooltip(
            lines, left, top, this.imageWidth, this.width,
            preview.selection().candidates(), registries, mouseX, mouseY
        );

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
            // Handled by candidate row truncation hover
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
                if (flipBox.containsAt(left, top, mouseX, mouseY) || unsockBox.containsAt(left, top, mouseX, mouseY)) {
                    boolean isFlip = flipBox.containsAt(left, top, mouseX, mouseY);
                    lines.add(Component.translatable(
                        isFlip ? "gui.enchantment_fusion.btn.flip" : "gui.enchantment_fusion.btn.unsocket"
                    ).withStyle(ChatFormatting.GOLD));
                    if (p.isAberrant()) {
                        appendAberrantPurifyHintLines(lines);
                    } else if (!canAffordXp(preview.manageXpCost())) {
                        lines.add(Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.manageXpCost())
                            .withStyle(ChatFormatting.RED));
                    } else {
                        lines.add(Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.manageXpCost())
                            .withStyle(ChatFormatting.GREEN));
                    }
                    break;
                }
                HitBox textBox = new HitBox(RIGHT_INNER_X, 27 + idx * 17, 96, 16);
                if (textBox.containsAt(left, top, mouseX, mouseY)) {
                    lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy()
                        .withStyle(p.isAberrant() ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
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
                case 0 -> "gui.enchantment_fusion.tip.slot_primary";
                case 1 -> "gui.enchantment_fusion.tip.slot_secondary";
                case 2 -> "gui.enchantment_fusion.tip.slot_catalyst";
                default -> "gui.enchantment_fusion.tip.slot_target_fuse";
            };
            lines.add(Component.translatable(slotTipKey).withStyle(ChatFormatting.GRAY));
        }

        if (!lines.isEmpty()) {
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
        int startY = gearMode ? 65 : 27;
        int rowW = gearMode ? 104 : RIGHT_INNER_W;
        int scroll = Math.max(0, Math.min(candidateScroll, Math.max(0, list.size() - visibleRows)));
        for (int r = 0; r < visibleRows; r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            HitBox rowBox = new HitBox(RIGHT_INNER_X, startY + r * 17, rowW, 16);
            if (rowBox.containsAt(this.leftPos, this.topPos, mouseX, mouseY)) {
                EnchantCandidate cand = list.get(idx);
                boolean isA = idx == sel.primaryIndex();
                boolean isB = idx == sel.secondaryIndex();
                String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
                String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u25C6 " : "";
                Component fullLabel = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level());
                FittedText fitted = FusionGuiSkin.fitBoundedText(
                    this.font, rolePrefix + srcBadge + fullLabel.getString(),
                    rowBox.w() - 10, 0.56F
                );
                if (fitted.truncated()) {
                    lines.add(fullLabel.copy().withStyle(isA ? ChatFormatting.GOLD : (isB ? ChatFormatting.AQUA : ChatFormatting.WHITE)));
                    lines.add(Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").withStyle(ChatFormatting.GRAY));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void appendAberrantPurifyHintLines(List<Component> lines) {
        var econ = FusionMatrixRegistry.clientView().economy();
        lines.add(Component.translatable(
            "tooltip.enchantment_fusion.aberrant_native_purify",
            econ.purifyAberrantStabilizers(),
            econ.purifyAberrantXp()
        ).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(
            CetSoftBridge.isCetLoaded()
                ? "tooltip.enchantment_fusion.aberrant_cet_active"
                : "tooltip.enchantment_fusion.aberrant_cet_required"
        ).withStyle(ChatFormatting.RED));
    }

    private void appendFlipOrSplitHoverLines(List<Component> lines, BookManageState mState, boolean isFlip) {
        lines.add(Component.translatable(
            isFlip ? "gui.enchantment_fusion.btn.flip" : "gui.enchantment_fusion.btn.split"
        ).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(
            isFlip ? "gui.enchantment_fusion.tip.book_flip_explain" : "gui.enchantment_fusion.tip.book_split_explain"
        ).withStyle(ChatFormatting.GRAY));
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
            return;
        }
        var econ = FusionMatrixRegistry.clientView().economy();
        if (q.reagent() != ReagentKind.NONE && q.reagentCount() > 0) {
            int have = this.minecraft != null && this.minecraft.player != null
                ? FusionEconomyRules.countAvailableReagent(this.menu.stationContainer(), this.minecraft.player, q.reagent())
                : 0;
            boolean ok = canAffordPreview(preview);
            lines.add(Component.translatable(
                "gui.enchantment_fusion.tip.fee_detail",
                q.xpLevels(),
                q.reagentCount(),
                Component.translatable(q.reagent().translationKey()),
                Math.min(999, have)
            ).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));

            if (this.minecraft != null && this.minecraft.player != null) {
                ReagentSourceBreakdown src = FusionEconomyRules.inspectReagentSources(
                    this.menu.stationContainer(), this.minecraft.player, q.reagent(), q.reagentCount()
                );
                lines.add(Component.translatable(
                    "gui.enchantment_fusion.tip.reagent_source",
                    src.takenFromSlot(),
                    src.takenFromBackpack()
                ).withStyle(ChatFormatting.GRAY));
            }
        }
        int failPct = (int) Math.round(q.failureChance() * 100.0);
        if (failPct > 0) {
            if (q.highRiskTier()) {
                int minFloorPct = (int) Math.round(econ.repulsiveMinFloor() * 100.0);
                lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_high", failPct, minFloorPct)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                int feeSpendPct = (int) Math.round(econ.normalFailFeeRatio() * 100.0);
                lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_normal", failPct, feeSpendPct)
                    .withStyle(ChatFormatting.YELLOW));
            }
        } else {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.risk_zero").withStyle(ChatFormatting.GREEN));
        }
    }

    private void notifyDisabledClick(Component reason) {
        playClickSound(0.55F);
        if (reason != null) {
            this.disabledFeedbackText = reason;
            this.disabledFeedbackUntilMs = System.currentTimeMillis() + 2400L;
        }
    }

    private Component explainPrimaryDisabledReason(StationPreview preview) {
        if (!preview.executable()) {
            if (preview.kind() == OperationKind.INVALID) {
                return Component.translatable(rejectKey(preview.rejectReason()));
            }
            return Component.translatable("gui.enchantment_fusion.preview.idle_title");
        }
        if (!canAffordXp(preview.xpCost())) {
            return Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.xpCost());
        }
        EconomyQuote q = preview.economyQuote();
        if (q != null && q.reagent() != ReagentKind.NONE && q.reagentCount() > 0) {
            int have = this.minecraft != null && this.minecraft.player != null
                ? FusionEconomyRules.countAvailableReagent(this.menu.stationContainer(), this.minecraft.player, q.reagent())
                : 0;
            return Component.translatable(
                "gui.enchantment_fusion.tip.fee_detail",
                q.xpLevels(),
                q.reagentCount(),
                Component.translatable(q.reagent().translationKey()),
                have
            );
        }
        return Component.translatable("gui.enchantment_fusion.reject.invalid_combination");
    }

    private Component explainBookManageDisabledReason(BookManageState mState) {
        if (!mState.hasBook()) {
            return Component.translatable("gui.enchantment_fusion.tip.manage_need_book");
        }
        if (mState.isAberrant()) {
            var econ = FusionMatrixRegistry.clientView().economy();
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

    // =========================================================================
    // Input Routing (Drawer -> Ritual -> Dual-Mode Medallion -> Picker -> Sockets)
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        StationPreview preview = this.menu.computePreview();
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }
        if (this.contextDrawer.mouseScrolled(
            this.leftPos, this.topPos, this.imageWidth, this.width,
            preview.selection().candidates(), registries, mouseX, mouseY, delta
        )) {
            return true;
        }

        boolean gearMode = isGearModeActive();
        int visibleRows = gearMode ? 1 : 2;
        int listY = gearMode ? 65 : 27;
        if (new HitBox(RIGHT_INNER_X, listY, RIGHT_INNER_W, visibleRows * 17).containsAt(this.leftPos, this.topPos, mouseX, mouseY)) {
            int max = Math.max(0, preview.selection().candidates().size() - visibleRows);
            candidateScroll = Math.max(0, Math.min(max, candidateScroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseClicked(event, isDoubleClick);
        }

        double mx = event.x();
        double my = event.y();
        int button = event.button();
        int left = this.leftPos;
        int top = this.topPos;
        StationPreview preview = this.menu.computePreview();
        HolderLookup.Provider registries = currentRegistries();
        if (registries == null) {
            registries = this.menu.viewer().level().registryAccess();
        }
        boolean gearMode = isGearModeActive();

        // 0. Smart Contextual Synergy Drawer (Pull-tab & 1-click recipe auto-pick)
        if (button == 0 && this.contextDrawer.mouseClicked(
            left, top, this.imageWidth, this.width,
            preview.selection().candidates(), registries, mx, my,
            this::openEmbeddedGrimoire,
            (cand, isPrimary) -> {
                FusionRitualDirector.cancel();
                EnchantCandidate curA = preview.selection().primary();
                EnchantCandidate curB = preview.selection().secondary();
                if (isPrimary) {
                    if (curA == null || !cand.selectionToken().equals(curA.selectionToken())) {
                        sendAction(StationAction.PICK_PRIMARY_A, 0, 0, cand.selectionToken());
                    }
                } else {
                    if (this.menu.singleOnlyMode()) {
                        sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, 0, "");
                    }
                    if (curB == null || !cand.selectionToken().equals(curB.selectionToken()) || this.menu.singleOnlyMode()) {
                        sendAction(StationAction.PICK_SECONDARY_B, 0, 0, cand.selectionToken());
                    }
                }
            }
        )) {
            return true;
        }

        // 1. Top-Right Embedded Grimoire Button
        if (button == 0 && GRIMOIRE_BTN.containsAt(left, top, mx, my)) {
            openEmbeddedGrimoire();
            return true;
        }

        // 2. Central [⇄] Swap Medallion (Left-click = [A ▷ B] order; Right-click or Shift+Left-click = Slot 0↔1)
        if ((button == 0 || button == 1) && SWAP_BTN.containsAt(left, top, mx, my)) {
            FusionRitualDirector.cancel();
            boolean swapPhysical = (button == 1) || FusionGuiSkin.isShiftHeld();
            if (swapPhysical) {
                if (canSwapPhysicalSlots()) {
                    sendAction(StationAction.SWAP_SLOTS, 0, 0, "");
                    playClickSound(1.15F);
                    FusionGuiMicroEffects.triggerSwapPulse(left + SWAP_BTN.x() + 12, top + SWAP_BTN.y() + 12);
                } else {
                    notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_slots.empty"));
                }
            } else {
                if (canSwapSelectionOrder(preview)) {
                    sendAction(StationAction.SWAP_ORDER, 0, 0, "");
                    playClickSound(1.15F);
                    FusionGuiMicroEffects.triggerSwapPulse(left + SWAP_BTN.x() + 12, top + SWAP_BTN.y() + 12);
                } else {
                    notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_order.disabled"));
                }
            }
            return true;
        }

        // 3. Gear Mode: Single-Enchantment Toggle & Inline Vessel Socket [⇄] / [◆] Buttons
        if (button == 0 && gearMode) {
            if (SINGLE_MODE_BTN.containsAt(left, top, mx, my)) {
                FusionRitualDirector.cancel();
                sendAction(StationAction.TOGGLE_SINGLE_MODE, this.menu.singleOnlyMode() ? 0 : 1, 0, "");
                playClickSound(1.06F);
                return true;
            }
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int s = 0; s < Math.min(2, socketed.size()); s++) {
                FusedPair p = socketed.pairs().get(s);
                boolean hitFlip = socketFlipBox(s).containsAt(left, top, mx, my);
                boolean hitUnsock = socketUnsocketBox(s).containsAt(left, top, mx, my);
                if (hitFlip || hitUnsock) {
                    FusionRitualDirector.cancel();
                    if (p.isAberrant()) {
                        var econ = FusionMatrixRegistry.clientView().economy();
                        notifyDisabledClick(Component.translatable(
                            "tooltip.enchantment_fusion.aberrant_native_purify",
                            econ.purifyAberrantStabilizers(),
                            econ.purifyAberrantXp()
                        ));
                    } else if (!canAffordXp(preview.manageXpCost())) {
                        notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.manage_need_xp", preview.manageXpCost()));
                    } else if (hitFlip) {
                        sendAction(StationAction.FLIP_SOCKET, s, 0, p.fingerprint());
                        playClickSound(1.12F);
                    } else {
                        sendAction(StationAction.UNSOCKET_OR_SPLIT, s, 0, p.fingerprint());
                        playClickSound(0.95F);
                    }
                    return true;
                }
            }
        }

        // 4. Book Mode: Single Inline Fused-Book [Flip] / [Split] Buttons (with pair fingerprint)
        if (button == 0 && !gearMode && !preview.executable()) {
            BookManageState mState = evaluateBookManage(preview);
            if (mState.hasBook() && !mState.isAberrant()) {
                if (BOOK_FLIP_BTN.containsAt(left, top, mx, my)) {
                    FusionRitualDirector.cancel();
                    if (mState.canExecute()) {
                        sendAction(StationAction.FLIP_SOCKET, 0, 0, mState.pairFingerprint());
                        playClickSound(1.10F);
                    } else {
                        notifyDisabledClick(explainBookManageDisabledReason(mState));
                    }
                    return true;
                }
                if (BOOK_SPLIT_BTN.containsAt(left, top, mx, my)) {
                    FusionRitualDirector.cancel();
                    if (mState.canExecute()) {
                        sendAction(StationAction.UNSOCKET_OR_SPLIT, 0, 0, mState.pairFingerprint());
                        playClickSound(0.95F);
                    } else {
                        notifyDisabledClick(explainBookManageDisabledReason(mState));
                    }
                    return true;
                }
            }
        }

        // 5. Candidate Picker Row Clicks (Left=Primary [A], Right=Secondary [B], Shift+Left=Single)
        if (button == 0 || button == 1) {
            int visibleRows = gearMode ? 1 : 2;
            int startY = gearMode ? 65 : 27;
            int rowW = gearMode ? 104 : RIGHT_INNER_W;
            List<EnchantCandidate> cands = preview.selection().candidates();
            int curScroll = Math.max(0, Math.min(candidateScroll, Math.max(0, cands.size() - visibleRows)));

            for (int r = 0; r < visibleRows; r++) {
                int idx = curScroll + r;
                if (idx >= cands.size()) break;
                HitBox rowBox = new HitBox(RIGHT_INNER_X, startY + r * 17, rowW, 16);
                if (rowBox.containsAt(left, top, mx, my)) {
                    FusionRitualDirector.cancel();
                    EnchantCandidate cand = cands.get(idx);
                    if (button == 0 && gearMode && FusionGuiSkin.isShiftHeld()) {
                        sendAction(StationAction.PICK_PRIMARY_A, idx, 0, cand.selectionToken());
                        if (!this.menu.singleOnlyMode()) {
                            sendAction(StationAction.TOGGLE_SINGLE_MODE, 1, 0, "");
                        }
                    } else {
                        if (button == 1 && this.menu.singleOnlyMode()) {
                            sendAction(StationAction.TOGGLE_SINGLE_MODE, 0, 0, "");
                        }
                        sendAction(
                            button == 0 ? StationAction.PICK_PRIMARY_A : StationAction.PICK_SECONDARY_B,
                            idx, 0, cand.selectionToken()
                        );
                    }
                    playClickSound(button == 0 ? 1.12F : 0.96F);
                    FusionGuiMicroEffects.triggerCandidatePickSpark((int) mx, (int) my, button == 0);
                    return true;
                }
            }
        }

        // 6. Primary 2.0s Skippable Ritual Execute Button (1st click channels, 2nd click skips immediately!)
        if (button == 0 && EXEC_BTN.containsAt(left, top, mx, my)) {
            triggerOrNotifyPrimaryRitual(preview, registries);
            return true;
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    private void triggerOrNotifyPrimaryRitual(StationPreview preview, HolderLookup.Provider registries) {
        if (preview.executable() && canAffordPreview(preview)) {
            String token = preview.expectedExecuteToken();
            String reveal = resolveOutcomeHeadline(preview, registries);
            boolean highRisk = preview.repulsive() || preview.instabilityChance() >= 0.20;
            FusionRitualDirector.triggerOrSkip(
                token,
                reveal,
                highRisk,
                this.menu.tableBlockPos(),
                () -> sendAction(StationAction.EXECUTE_PRIMARY, 0, 0, token)
            );
        } else {
            notifyDisabledClick(explainPrimaryDisabledReason(preview));
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseDragged(event, dragX, dragY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseReleased(event);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.keyPressed(event);
        }
        int key = event.key();
        if (FusionGuiSkin.isCtrlOrCmdHeld() && key == InputConstants.KEY_G) {
            openEmbeddedGrimoire();
            return true;
        }
        if ((key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
            && FusionRitualDirector.isChanneling()) {
            StationPreview preview = this.menu.computePreview();
            if (preview.executable() && canAffordPreview(preview)) {
                HolderLookup.Provider registries = currentRegistries();
                if (registries == null) {
                    registries = this.menu.viewer().level().registryAccess();
                }
                triggerOrNotifyPrimaryRitual(preview, registries);
                return true;
            }
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            StationPreview preview = this.menu.computePreview();
            HolderLookup.Provider registries = currentRegistries();
            if (registries == null) {
                registries = this.menu.viewer().level().registryAccess();
            }
            triggerOrNotifyPrimaryRitual(preview, registries);
            return true;
        }
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            int visibleRows = isGearModeActive() ? 1 : 2;
            int total = this.menu.computePreview().selection().candidates().size();
            if (total > visibleRows) {
                int max = Math.max(0, total - visibleRows);
                int delta = key == InputConstants.KEY_UP ? -1 : 1;
                candidateScroll = Math.max(0, Math.min(max, candidateScroll + delta));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.keyReleased(event);
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.charTyped(event);
        }
        return super.charTyped(event);
    }

    private @Nullable HolderLookup.Provider currentRegistries() {
        return this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.registryAccess()
            : null;
    }

    private void sendAction(StationAction action, int paramA, int paramB, String expectedToken) {
        if (ClientPlayNetworking.canSend(StationActionPayload.TYPE)) {
            ClientPlayNetworking.send(new StationActionPayload(
                this.menu.containerId,
                action.wireId(),
                paramA,
                paramB,
                expectedToken == null ? "" : expectedToken
            ));
        }
    }

    private static void playClickSound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private boolean canAffordXp(int cost) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        return this.minecraft.player.getAbilities().instabuild || this.minecraft.player.experienceLevel >= cost;
    }

    private boolean canAffordPreview(StationPreview preview) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (!canAffordXp(preview.xpCost())) {
            return false;
        }
        return FusionEconomyRules.canAfford(this.menu.stationContainer(), this.minecraft.player, preview.economyQuote());
    }

    private static String primaryActionLabelKey(OperationKind kind) {
        return switch (kind) {
            case EXPAND_VESSEL -> "gui.enchantment_fusion.btn.expand_vessel";
            case TOP_UP_LEVEL -> "gui.enchantment_fusion.btn.top_up";
            case SOCKET_FUSED_BOOK -> "gui.enchantment_fusion.btn.inscribe";
            case INSCRIBE_SINGLE_CROSS -> "gui.enchantment_fusion.btn.inscribe_cross";
            case FUSE_ONTO_GEAR -> "gui.enchantment_fusion.btn.fuse_gear";
            case PURIFY_ABERRANT -> "gui.enchantment_fusion.btn.purify";
            default -> "gui.enchantment_fusion.btn.fuse";
        };
    }

    private static String rejectKey(RejectReason reason) {
        if (reason == null) {
            return "gui.enchantment_fusion.reject.invalid_combination";
        }
        return switch (reason) {
            case MISSING_INPUT -> "gui.enchantment_fusion.reject.missing_input";
            case IDENTICAL_PAIR -> "gui.enchantment_fusion.reject.identical_pair";
            case BOTH_CURSES -> "gui.enchantment_fusion.reject.both_curses";
            case UNREGISTERED_PRIMARY -> "gui.enchantment_fusion.reject.unregistered_primary";
            case UNREGISTERED_SECONDARY -> "gui.enchantment_fusion.reject.unregistered_secondary";
            case NO_ACTIVE_TRIGGER -> "gui.enchantment_fusion.reject.no_active_trigger";
            case NO_SECONDARY_PAYLOAD -> "gui.enchantment_fusion.reject.no_secondary_payload";
            case HOST_FULL -> "gui.enchantment_fusion.reject.host_full";
            case DUPLICATE_ON_HOST -> "gui.enchantment_fusion.reject.duplicate_on_host";
            case EXCLUSIVE_ON_HOST -> "gui.enchantment_fusion.reject.exclusive_on_host";
            case ABERRANT_LOCKED -> "gui.enchantment_fusion.reject.aberrant_locked";
            case ALREADY_EXPANDED -> "gui.enchantment_fusion.reject.already_expanded";
            case ALREADY_MAX_LEVEL -> "gui.enchantment_fusion.reject.already_max_level";
            case INSUFFICIENT_XP -> "gui.enchantment_fusion.reject.insufficient_xp";
            case INSUFFICIENT_REAGENTS -> "gui.enchantment_fusion.reject.insufficient_reagents";
            case NEED_PURIFY_STABILIZERS -> "gui.enchantment_fusion.reject.need_purify_stabilizers";
            case TRANSMUTE_CAP_FULL -> "gui.enchantment_fusion.reject.transmute_cap_full";
            default -> "gui.enchantment_fusion.reject.invalid_combination";
        };
    }
}
