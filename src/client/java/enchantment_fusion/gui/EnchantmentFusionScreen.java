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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Streamlined 2-Tab Workshop + Embedded Top-Right {@code 《融合秘典》} Grimoire Button GUI
 * for the Enchantment Fusion Table ({@code 308 x 214}).
 * Uses exclusively pixel-art PNG sprites (zero solid rectangle draw calls).
 */
public final class EnchantmentFusionScreen extends AbstractContainerScreen<EnchantmentFusionMenu> {
    public static final int TAB_W = 104;
    public static final int TAB_H = 20;
    public static final int TAB0_X = 12;
    public static final int TAB1_X = 120;
    public static final int TAB_Y = 0;

    public static final HitBox GRIMOIRE_BTN = new HitBox(232, 0, 68, 18);

    // GUI-1: Separate physical slot swap [⇄] (Slot 0 <-> Slot 1) and selection order swap [⇅] ([A ▷ B] <-> [B ▷ A])
    public static final HitBox SWAP_SLOTS_BTN = new HitBox(37, 51, 18, 18);
    public static final HitBox SWAP_ORDER_BTN = new HitBox(57, 51, 18, 18);

    public static final int RIGHT_X = 112;
    public static final int RIGHT_W = 186;

    // Tab 0 Always-Visible Fused-Book Flip / Split Toolbar (GUI-04)
    public static final HitBox TAB0_TOOLBAR_FLIP = new HitBox(206, 23, 44, 12);
    public static final HitBox TAB0_TOOLBAR_SPLIT = new HitBox(253, 23, 45, 12);

    // Tab 1 Explicit Single-Enchantment Mode Toggle + Primary Execute Button (GUI-05)
    public static final HitBox SINGLE_MODE_BTN = new HitBox(237, 84, 61, 12);
    public static final HitBox EXEC_BTN = new HitBox(237, 97, 61, 24);

    // Tab 0 live preview plaque
    public static final HitBox TAB0_PREVIEW_BOX = new HitBox(112, 89, 122, 36);

    // Tab 0 prominent Fused-Book Flip / Split buttons inside manage card when idle
    public static final HitBox BOOK_FLIP_BTN = new HitBox(140, 98, 44, 20);
    public static final HitBox BOOK_SPLIT_BTN = new HitBox(187, 98, 47, 20);

    private static final int PICKER_ROW_STEP = 17;
    private static final int PICKER_ROW_HEIGHT = 16;

    /** Shared candidate picker layout descriptor deduplicated across render, scroll, click, and hover (GUI-2). */
    private record PickerGeometry(int rx, int startY, int rw, int visibleRows) {
        HitBox listBounds() {
            return new HitBox(rx, startY, rw, visibleRows * PICKER_ROW_STEP);
        }

        HitBox rowBox(int visibleOffset) {
            return new HitBox(rx, startY + visibleOffset * PICKER_ROW_STEP, rw, PICKER_ROW_HEIGHT);
        }
    }

    private int candidateScroll = 0;
    private @Nullable FusionGrimoireScreen grimoireOverlay = null;
    private @Nullable StationPreview cachedFramePreview = null;
    private @Nullable Component disabledFeedbackText = null;
    private long disabledFeedbackUntilMs = 0L;

    public EnchantmentFusionScreen(EnchantmentFusionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, EnchantmentFusionMenu.GUI_WIDTH, EnchantmentFusionMenu.GUI_HEIGHT);
        this.titleLabelX = 14;
        this.titleLabelY = 26;
        this.inventoryLabelX = EnchantmentFusionMenu.PLAYER_INV_X;
        this.inventoryLabelY = 122;
    }

    public Rect2i guiBounds() {
        if (this.grimoireOverlay != null) {
            int bx = (this.width - FusionGrimoireScreen.BOOK_W) / 2;
            int by = (this.height - FusionGrimoireScreen.BOOK_H) / 2;
            return new Rect2i(bx, by, FusionGrimoireScreen.BOOK_W, FusionGrimoireScreen.BOOK_H);
        }
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.init(this.width, this.height);
        }
    }

    private void openEmbeddedGrimoire() {
        if (this.minecraft == null) {
            return;
        }
        int initCh = this.menu.activeTab() == FusionStationOperations.TAB_SOCKET ? 2 : 1;
        FusionGrimoireScreen overlay = new FusionGrimoireScreen(() -> this.grimoireOverlay = null, initCh);
        overlay.init(this.width, this.height);
        this.grimoireOverlay = overlay;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    private static PickerGeometry pickerGeometryForTab(int tab) {
        if (tab == FusionStationOperations.TAB_SOCKET) {
            return new PickerGeometry(RIGHT_X, 86, 122, 2);
        }
        return new PickerGeometry(RIGHT_X, 36, RIGHT_W, 3);
    }

    private boolean canSwapPhysicalSlots() {
        return !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_PRIMARY).isEmpty()
            || !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty();
    }

    private boolean canSwapSelectionOrder(StationPreview preview) {
        return !this.menu.singleOnlyMode() && preview.selection().candidates().size() >= 2;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Bounded labels rendered cleanly inside extractBackground.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int left = this.leftPos + FusionGuiMicroEffects.currentShakeOffsetX();
        int top = this.topPos + FusionGuiMicroEffects.currentShakeOffsetY();
        FusionGuiMicroEffects.updateStationOrigin(this.leftPos, this.topPos);
        int tab = this.menu.activeTab();

        // GUI-03: Evaluate preview once per frame and share with tooltip pass
        StationPreview preview = this.menu.computePreview();
        this.cachedFramePreview = preview;

        // 1. Render 2 Workshop Tabs + Top-Right Embedded Grimoire Button
        for (int t = 0; t < 2; t++) {
            if (t != tab) {
                drawTabHeader(ctx, t, false, mouseX, mouseY);
            }
        }
        drawGrimoireTopRightButton(ctx, mouseX, mouseY);

        FusionGuiSkin.drawNineSlice(
            ctx, FusionGuiSkin.SLICE_PANEL_MAIN,
            left, top + 18, this.imageWidth, this.imageHeight - 18
        );
        drawTabHeader(ctx, tab, true, mouseX, mouseY);

        boolean highRisk = preview.economyQuote() != null && preview.economyQuote().highRiskTier();
        FusionGuiMicroEffects.renderPedestalResonanceBackdrop(ctx, left, top, preview.executable(), highRisk);

        // 2. Left Workstation Pedestal Header & 4 Role-Specific Slot Bezels
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, this.title.getString(),
            left + 10, 96, top + 30.0F, FusionGuiSkin.SCALE_LABEL, FusionGuiSkin.COLOR_GOLD
        );

        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_PRIMARY, left + 17, top + 51, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_SECONDARY, left + 77, top + 51, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_CATALYST, left + 47, top + 79, 18, 18);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_DEFAULT, left + 47, top + 107, 18, 18);

        String slot0Label = Component.translatable(
            tab == FusionStationOperations.TAB_SOCKET ? "gui.enchantment_fusion.slot.inlay_book" : "gui.enchantment_fusion.slot.primary_short"
        ).getString();
        String slot1Label = Component.translatable(
            tab == FusionStationOperations.TAB_SOCKET ? "gui.enchantment_fusion.slot.aux_book" : "gui.enchantment_fusion.slot.secondary_short"
        ).getString();
        String slot3Label = Component.translatable(
            tab == FusionStationOperations.TAB_SOCKET ? "gui.enchantment_fusion.slot.host_gear" : "gui.enchantment_fusion.slot.output_book"
        ).getString();

        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, slot0Label, left + 8, 36, top + 45.0F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD);
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, slot1Label, left + 68, 36, top + 45.0F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CYAN);
        FusionGuiSkin.drawLeftBoundedText(ctx, this.font, slot3Label, left + 10, top + 112, 35, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_CREAM);

        // GUI-1: Split [⇄] Physical Slot Swap vs [⇅] Selection Order Swap buttons
        drawSwapActionButton(
            ctx, left, top, SWAP_SLOTS_BTN,
            canSwapPhysicalSlots(), SWAP_SLOTS_BTN.containsAt(left, top, mouseX, mouseY),
            "\u21C4", FusionGuiSkin.COLOR_GOLD
        );
        drawSwapActionButton(
            ctx, left, top, SWAP_ORDER_BTN,
            canSwapSelectionOrder(preview), SWAP_ORDER_BTN.containsAt(left, top, mouseX, mouseY),
            "\u21C5", FusionGuiSkin.COLOR_CYAN
        );

        renderVesselDiamonds(ctx, left + 69, top + 110);

        // 3. Player Inventory 36 Slot Bezels
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                FusionGuiSkin.drawSprite(
                    ctx, FusionGuiSkin.SLOT_DEFAULT,
                    left + EnchantmentFusionMenu.PLAYER_INV_X - 1 + col * 18,
                    top + EnchantmentFusionMenu.PLAYER_INV_Y - 1 + row * 18,
                    18, 18
                );
            }
        }
        for (int col = 0; col < 9; col++) {
            FusionGuiSkin.drawSprite(
                ctx, FusionGuiSkin.SLOT_DEFAULT,
                left + EnchantmentFusionMenu.PLAYER_INV_X - 1 + col * 18,
                top + EnchantmentFusionMenu.PLAYER_INV_Y + 57,
                18, 18
            );
        }

        // 4. Right Interactive Workspace (2 Tabs)
        HolderLookup.Provider registries = currentRegistries();
        if (tab == FusionStationOperations.TAB_SOCKET) {
            renderTab1GearSocketsAndInPlace(ctx, registries, preview, mouseX, mouseY);
        } else {
            renderTab0BookFusionAndSplit(ctx, registries, preview, mouseX, mouseY);
        }
    }

    private void drawSwapActionButton(
        GuiGraphicsExtractor ctx,
        int left,
        int top,
        HitBox box,
        boolean enabled,
        boolean hovered,
        String glyph,
        int activeColor
    ) {
        var sprite = !enabled
            ? FusionGuiSkin.BTN_SWAP_DISABLED
            : (hovered ? FusionGuiSkin.BTN_SWAP_HOVER : FusionGuiSkin.BTN_SWAP_NORMAL);
        FusionGuiSkin.drawSprite(ctx, sprite, left + box.x(), top + box.y(), box.w(), box.h());
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, glyph,
            left + box.x() + 2, box.w() - 4, top + box.y() + box.h() * 0.5F,
            FusionGuiSkin.SCALE_BODY, enabled ? activeColor : FusionGuiSkin.COLOR_MUTED
        );
    }

    private void drawTabHeader(GuiGraphicsExtractor ctx, int tabIndex, boolean active, int mouseX, int mouseY) {
        HitBox tabBox = tabHitBox(tabIndex);
        boolean hovered = tabBox.containsAt(this.leftPos, this.topPos, mouseX, mouseY);
        var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hovered ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
        FusionGuiSkin.drawSprite(ctx, sprite, this.leftPos + tabBox.x(), this.topPos + tabBox.y(), tabBox.w(), tabBox.h() + (active ? 2 : 0));

        String key = tabIndex == 0 ? "gui.enchantment_fusion.tab.fuse" : "gui.enchantment_fusion.tab.socket";
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable(key).getString(),
            this.leftPos + tabBox.x() + 6, tabBox.w() - 12,
            this.topPos + 11.5F,
            FusionGuiSkin.SCALE_BODY,
            active ? FusionGuiSkin.COLOR_GOLD : (hovered ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
        );
    }

    private void drawGrimoireTopRightButton(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int gx = this.leftPos + GRIMOIRE_BTN.x();
        int gy = this.topPos + GRIMOIRE_BTN.y();
        boolean hov = GRIMOIRE_BTN.containsAt(this.leftPos, this.topPos, mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx,
            hov ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL,
            gx, gy, GRIMOIRE_BTN.w(), GRIMOIRE_BTN.h()
        );
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), gx + 2, gy + 1);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable("gui.enchantment_fusion.btn.grimoire").getString(),
            gx + 19, gy + 5, GRIMOIRE_BTN.w() - 22, FusionGuiSkin.SCALE_SMALL,
            hov ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );
    }

    // =========================================================================
    // TAB 0: Book Synthesis + Always-Visible Fused-Book Flip / Split (GUI-04)
    // =========================================================================

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

    private BookManageState evaluateTab0BookManage(StationPreview preview) {
        ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
        FusedEnchantmentList mList = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean hasBook = manageable.is(FusionItems.FUSED_ENCHANTED_BOOK) && !mList.isEmpty();
        boolean aberrant = hasBook && mList.pairs().getFirst().isAberrant();
        String fingerprint = hasBook ? mList.pairs().getFirst().fingerprint() : "";
        boolean secBusy = !this.menu.stationContainer().getItem(FusionStationOperations.SLOT_SECONDARY).isEmpty() && preview.executable();
        boolean xpOk = canAffordXp(preview.manageXpCost());
        return new BookManageState(hasBook, aberrant, secBusy, xpOk, preview.manageXpCost(), fingerprint);
    }

    private void renderTab0BookFusionAndSplit(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        StationPreview preview,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;

        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable("gui.enchantment_fusion.tab0.header_hint").getString(),
            left + RIGHT_X, top + 25, 90, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
        );

        BookManageState mState = evaluateTab0BookManage(preview);
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, left, top, TAB0_TOOLBAR_FLIP,
            mState.canExecute(), TAB0_TOOLBAR_FLIP.containsAt(left, top, mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
            FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_CYAN
        );
        FusionGuiSkin.drawButtonBox(
            ctx, this.font, left, top, TAB0_TOOLBAR_SPLIT,
            mState.canExecute(), TAB0_TOOLBAR_SPLIT.containsAt(left, top, mouseX, mouseY),
            Component.translatable("gui.enchantment_fusion.btn.split").getString(),
            FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GOLD
        );

        renderCandidatePickerRows(ctx, registries, preview.selection(), pickerGeometryForTab(FusionStationOperations.TAB_FUSE), mouseX, mouseY);

        ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
        FusedEnchantmentList mList = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean showBookManage = !preview.executable() && mState.hasBook() && !mState.isAberrant();

        if (showBookManage) {
            FusedPair topPair = mList.pairs().getFirst();
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.SLICE_PANEL_INSET,
                left + TAB0_PREVIEW_BOX.x(), top + TAB0_PREVIEW_BOX.y(), RIGHT_W, TAB0_PREVIEW_BOX.h()
            );
            String title = FusionTooltipRenderer.buildPairDisplayName(registries, topPair).getString();
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, title,
                left + TAB0_PREVIEW_BOX.x() + 6, top + TAB0_PREVIEW_BOX.y() + 6, RIGHT_W - 12, FusionGuiSkin.SCALE_MICRO,
                topPair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
            );

            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_FLIP_BTN,
                mState.canExecute(), BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
                FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CYAN
            );
            FusionGuiSkin.drawButtonBox(
                ctx, this.font, left, top, BOOK_SPLIT_BTN,
                mState.canExecute(), BOOK_SPLIT_BTN.containsAt(left, top, mouseX, mouseY),
                Component.translatable("gui.enchantment_fusion.btn.split").getString(),
                FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
            );
            return;
        }

        FusionGuiSkin.drawNineSlice(
            ctx, FusionGuiSkin.SLICE_PANEL_INSET,
            left + TAB0_PREVIEW_BOX.x(), top + TAB0_PREVIEW_BOX.y(), TAB0_PREVIEW_BOX.w(), TAB0_PREVIEW_BOX.h()
        );
        renderCompactPreviewBox(
            ctx, registries, preview,
            left + TAB0_PREVIEW_BOX.x(), top + TAB0_PREVIEW_BOX.y(), TAB0_PREVIEW_BOX.w(), TAB0_PREVIEW_BOX.h()
        );
        renderPrimaryExecuteButton(ctx, preview, mouseX, mouseY);
    }

    // =========================================================================
    // TAB 1: Gear Vessel Slots + Explicit Single-Enchantment Toggle (GUI-05)
    // =========================================================================

    private void renderTab1GearSocketsAndInPlace(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        StationPreview preview,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;
        ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        boolean hasGear = VesselCapacityRules.isPotentialEquipmentHost(host);
        boolean hasSpecialCross = !hasGear && CrossDomainTransmutations.isEligibleCrossDomainHost(host);

        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable("gui.enchantment_fusion.tab1.header_hint").getString(),
            left + RIGHT_X, top + 25, RIGHT_W, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
        );

        int maxSlots = hasGear ? VesselCapacityRules.getMaxVesselSlots(host) : 1;
        FusedEnchantmentList socketed = hasGear
            ? host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            : FusedEnchantmentList.EMPTY;

        for (int idx = 0; idx < 2; idx++) {
            int cardY = 34 + idx * 25;
            boolean unlocked = hasGear && idx < maxSlots;
            FusedPair pair = (unlocked && idx < socketed.size()) ? socketed.pairs().get(idx) : null;

            var cardSlice = pair == null
                ? FusionGuiSkin.SLICE_SOCKET_EMPTY
                : (pair.isAberrant() ? FusionGuiSkin.SLICE_SOCKET_ABERRANT : FusionGuiSkin.SLICE_SOCKET_FILLED);
            FusionGuiSkin.drawNineSlice(ctx, cardSlice, left + RIGHT_X, top + cardY, RIGHT_W, 23);

            if (!hasGear && hasSpecialCross) {
                if (idx == 0) {
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.special_cross_host").getString(),
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CYAN
                    );
                } else if (preview.previewTransmute() != null && preview.singleCandidate() != null) {
                    String tName = FusionTooltipRenderer.buildTransmutedDisplayName(
                        preview.previewTransmute(),
                        Math.min(preview.singleCandidate().maxLevel(), preview.singleCandidate().level()),
                        preview.singleCandidate().maxLevel(),
                        preview.singleCandidate().id()
                    ).getString();
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font, tName,
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
                    );
                }
            } else if (!hasGear) {
                if (idx == 0) {
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.place_gear").getString(),
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_MUTED
                    );
                }
            } else if (!unlocked) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.locked_expand_hint").getString(),
                    left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
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
                        ctx, this.font, "\u2726 " + tName,
                        left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
                    );
                } else {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.empty_ready", idx + 1).getString(),
                        left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_CREAM
                    );
                }
            } else {
                String label = FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, label,
                    left + RIGHT_X + 7, top + cardY + 8, 124, FusionGuiSkin.SCALE_SMALL,
                    pair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
                );

                boolean manageable = !pair.isAberrant() && canAffordXp(preview.manageXpCost());
                HitBox flipBox = new HitBox(RIGHT_X + 134, cardY + 3, 22, 17);
                HitBox unsockBox = new HitBox(RIGHT_X + 158, cardY + 3, 24, 17);

                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, flipBox,
                    manageable, flipBox.containsAt(left, top, mouseX, mouseY),
                    "\u21C4", FusionGuiSkin.SCALE_BODY, FusionGuiSkin.COLOR_CYAN
                );
                FusionGuiSkin.drawButtonBox(
                    ctx, this.font, left, top, unsockBox,
                    manageable, unsockBox.containsAt(left, top, mouseX, mouseY),
                    Component.translatable("gui.enchantment_fusion.btn.unsocket").getString(),
                    FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GOLD
                );
            }
        }

        renderCandidatePickerRows(ctx, registries, preview.selection(), pickerGeometryForTab(FusionStationOperations.TAB_SOCKET), mouseX, mouseY);

        // GUI-05: Explicit [仅单附魔] / [Single] mode toggle button above primary Execute button
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
            left + SINGLE_MODE_BTN.x() + 3, SINGLE_MODE_BTN.w() - 6, top + SINGLE_MODE_BTN.y() + 6.0F,
            FusionGuiSkin.SCALE_MICRO, singleMode ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );

        renderPrimaryExecuteButton(ctx, preview, mouseX, mouseY);
    }

    // =========================================================================
    // Shared Mode C Enchantment Picker, Economy Badges & Preview Helpers
    // =========================================================================

    private void renderCandidatePickerRows(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        ResolvedSelection sel,
        PickerGeometry geom,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;
        List<EnchantCandidate> list = sel.candidates();

        if (list.isEmpty()) {
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.SLICE_ROW_UNSELECTED,
                left + geom.rx(), top + geom.startY(), geom.rw(), geom.visibleRows() * PICKER_ROW_STEP - 1
            );
            String emptyKey = this.menu.activeTab() == FusionStationOperations.TAB_SOCKET
                ? "gui.enchantment_fusion.picker.empty_gear"
                : "gui.enchantment_fusion.picker.empty_books";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, Component.translatable(emptyKey).getString(),
                left + geom.rx() + 6, geom.rw() - 12, top + geom.startY() + (geom.visibleRows() * PICKER_ROW_STEP - 1) * 0.5F,
                FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        int maxScroll = Math.max(0, list.size() - geom.visibleRows());
        int scroll = Math.max(0, Math.min(candidateScroll, maxScroll));

        for (int r = 0; r < geom.visibleRows(); r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            EnchantCandidate cand = list.get(idx);
            HitBox rowBox = geom.rowBox(r);
            boolean isA = (idx == sel.primaryIndex());
            boolean isB = (idx == sel.secondaryIndex());
            boolean hov = rowBox.containsAt(left, top, mouseX, mouseY);

            var rowSlice = isA ? FusionGuiSkin.SLICE_ROW_SELECTED_A
                : (isB ? FusionGuiSkin.SLICE_ROW_SELECTED_B : (hov ? FusionGuiSkin.SLICE_ROW_HOVER : FusionGuiSkin.SLICE_ROW_UNSELECTED));
            FusionGuiSkin.drawNineSlice(ctx, rowSlice, left + rowBox.x(), top + rowBox.y(), rowBox.w(), rowBox.h());

            String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
            String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u2694 " : "";
            String enchName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level()).getString();
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, rolePrefix + srcBadge + enchName,
                left + rowBox.x() + 6, top + rowBox.y() + 5, rowBox.w() - 12, FusionGuiSkin.SCALE_SMALL,
                isA ? FusionGuiSkin.COLOR_GOLD : (isB ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_CREAM)
            );
        }
    }

    private void renderCompactPreviewBox(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        StationPreview preview,
        int px,
        int py,
        int pw,
        int ph
    ) {
        ctx.enableScissor(px + 4, py + 4, px + pw - 4, py + ph - 4);
        try {
            int ix = px + 6;
            int iw = pw - 12;
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));

            if (preview.kind() == OperationKind.INVALID) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable(rejectKey(preview.rejectReason())).getString(),
                    ix, py + 7, iw, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_RED
                );
                String secondHintKey = preview.rejectReason() == RejectReason.NEED_PURIFY_STABILIZERS
                    ? "gui.enchantment_fusion.preview.purify_hint"
                    : "gui.enchantment_fusion.preview.try_swap_hint";
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable(secondHintKey).getString(),
                    ix, py + 20, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
                );
                return;
            }

            if (preview.kind() == OperationKind.PURIFY_ABERRANT && preview.previewPair() != null) {
                FusedPair purePair = preview.previewPair();
                String title = FusionTooltipRenderer.buildPairDisplayName(registries, purePair).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, title, ix, py + 6, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_GREEN);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.purify_ready").getString(),
                    ix, py + 15, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_CYAN
                );
                String econLine = formatCompactEconomyLine(preview);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, econLine, ix, py + 25, iw, FusionGuiSkin.SCALE_MICRO,
                    canAffordPreview(preview) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
                );
                return;
            }

            FusedPair pair = preview.previewPair();
            if (pair != null) {
                String title = FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, title, ix, py + 6, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_AMETHYST);

                String desc = FusionTooltipRenderer.buildEffectSummary(pair, form).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, desc, ix, py + 15, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_CYAN);

                String econLine = formatCompactEconomyLine(preview);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, econLine, ix, py + 25, iw, FusionGuiSkin.SCALE_MICRO,
                    canAffordPreview(preview) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
                );
            } else {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.idle_title").getString(),
                    ix, py + 8, iw, FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_GOLD
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").getString(),
                    ix, py + 20, iw, FusionGuiSkin.SCALE_MICRO, FusionGuiSkin.COLOR_MUTED
                );
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private void renderPrimaryExecuteButton(
        GuiGraphicsExtractor ctx,
        StationPreview preview,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;
        boolean ready = preview.executable() && canAffordPreview(preview);
        boolean hovered = EXEC_BTN.containsAt(left, top, mouseX, mouseY);

        FusionGuiSkin.drawNineSlice(
            ctx,
            !ready ? FusionGuiSkin.SLICE_BTN_DISABLED : (hovered ? FusionGuiSkin.SLICE_BTN_HOVER : FusionGuiSkin.SLICE_BTN_NORMAL),
            left + EXEC_BTN.x(), top + EXEC_BTN.y(), EXEC_BTN.w(), EXEC_BTN.h()
        );
        String label = Component.translatable(primaryActionLabelKey(preview.kind())).getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, label,
            left + EXEC_BTN.x() + 4, EXEC_BTN.w() - 8, top + EXEC_BTN.y() + 8.0F,
            FusionGuiSkin.SCALE_SMALL, ready ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );
        if (preview.executable()) {
            EconomyQuote q = preview.economyQuote();
            int succPct = q != null ? (int) Math.round((1.0 - q.failureChance()) * 100.0) : 100;
            String badge = preview.xpCost() + "Lv \u00B7 " + succPct + "%";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, badge,
                left + EXEC_BTN.x() + 3, EXEC_BTN.w() - 6, top + EXEC_BTN.y() + 17.5F,
                FusionGuiSkin.SCALE_MICRO, ready ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
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
        for (int i = 0; i < maxSlots; i++) {
            var sprite = (i < list.size()) ? FusionGuiSkin.VESSEL_FILLED : FusionGuiSkin.VESSEL_EMPTY;
            FusionGuiSkin.drawSprite(ctx, sprite, startX + i * 13, y, 12, 12);
        }
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
        renderDisabledFeedbackBanner(ctx);
        this.extractTooltip(ctx, mouseX, mouseY);
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) {
            renderHoverTooltips(ctx, mouseX, mouseY);
        }
    }

    private void renderDisabledFeedbackBanner(GuiGraphicsExtractor ctx) {
        if (this.disabledFeedbackText == null || System.currentTimeMillis() > this.disabledFeedbackUntilMs) {
            this.disabledFeedbackText = null;
            return;
        }
        int pillW = Math.min(this.imageWidth - 20, 260);
        int pillX = this.leftPos + (this.imageWidth - pillW) / 2;
        int pillY = Math.min(ctx.guiHeight() - 18, this.topPos + this.imageHeight + 3);
        FusionGuiSkin.drawNineSlice(ctx, FusionGuiSkin.SLICE_ROW_UNSELECTED, pillX, pillY, pillW, 14);
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, this.disabledFeedbackText.getString(),
            pillX + 6, pillW - 12, pillY + 7.0F,
            FusionGuiSkin.SCALE_SMALL, FusionGuiSkin.COLOR_RED
        );
    }

    private void renderHoverTooltips(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        StationPreview preview = this.cachedFramePreview != null ? this.cachedFramePreview : this.menu.computePreview();
        HolderLookup.Provider registries = currentRegistries();
        int tab = this.menu.activeTab();
        int left = this.leftPos;
        int top = this.topPos;

        if (GRIMOIRE_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("item.enchantment_fusion.fusion_grimoire").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.grimoire_btn").withStyle(ChatFormatting.GRAY));
        } else if (SWAP_SLOTS_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap_slots.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap_slots.desc").withStyle(ChatFormatting.GRAY));
        } else if (SWAP_ORDER_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.desc").withStyle(ChatFormatting.GRAY));
        } else if (tab == FusionStationOperations.TAB_SOCKET && SINGLE_MODE_BTN.containsAt(left, top, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.single_mode.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.single_mode.desc").withStyle(ChatFormatting.GRAY));
        } else if (tab == FusionStationOperations.TAB_FUSE
            && (TAB0_TOOLBAR_FLIP.containsAt(left, top, mouseX, mouseY)
                || (!preview.executable() && BOOK_FLIP_BTN.containsAt(left, top, mouseX, mouseY)))) {
            appendFlipOrSplitHoverLines(lines, evaluateTab0BookManage(preview), true);
        } else if (tab == FusionStationOperations.TAB_FUSE
            && (TAB0_TOOLBAR_SPLIT.containsAt(left, top, mouseX, mouseY)
                || (!preview.executable() && BOOK_SPLIT_BTN.containsAt(left, top, mouseX, mouseY)))) {
            appendFlipOrSplitHoverLines(lines, evaluateTab0BookManage(preview), false);
        } else if (appendHoveredPickerRowTooltip(lines, registries, preview.selection(), pickerGeometryForTab(tab), mouseX, mouseY)) {
            // Handled by candidate row hover (shows full untruncated enchantment name & role hint)
        } else if (EXEC_BTN.containsAt(left, top, mouseX, mouseY)
            && preview.kind() == OperationKind.INSCRIBE_SINGLE_CROSS
            && preview.previewTransmute() != null
            && preview.singleCandidate() != null) {
            EnchantCandidate cand = preview.singleCandidate();
            int lv = Math.min(cand.maxLevel(), cand.level());
            lines.add(FusionTooltipRenderer.buildTransmutedDisplayName(
                preview.previewTransmute(), lv, cand.maxLevel(), cand.id()
            ));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.cross_zero_slot")
                .withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable(
                "tooltip.enchantment_fusion.transmute_origin_desc",
                FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), lv),
                Component.translatable(preview.previewTransmute().descKey())
            ).withStyle(ChatFormatting.DARK_AQUA));
            appendEconomyTooltipLines(lines, preview);
        } else if ((EXEC_BTN.containsAt(left, top, mouseX, mouseY)
            || (tab == FusionStationOperations.TAB_FUSE && TAB0_PREVIEW_BOX.containsAt(left, top, mouseX, mouseY)))
            && preview.kind() == OperationKind.PURIFY_ABERRANT
            && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));
            lines.add(Component.translatable("gui.enchantment_fusion.btn.purify").withStyle(ChatFormatting.GOLD));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy().withStyle(ChatFormatting.GREEN));
            lines.add(FusionTooltipRenderer.buildEffectSummary(p, form).copy().withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.enchantment_fusion.preview.purify_ready").withStyle(ChatFormatting.GRAY));
            appendEconomyTooltipLines(lines, preview);
        } else if ((EXEC_BTN.containsAt(left, top, mouseX, mouseY)
            || (tab == FusionStationOperations.TAB_FUSE && TAB0_PREVIEW_BOX.containsAt(left, top, mouseX, mouseY)))
            && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(FusionTooltipRenderer.buildEffectSummary(p, form).copy().withStyle(ChatFormatting.AQUA));
            appendEconomyTooltipLines(lines, preview);
            FusedPair rev = p.flipped();
            lines.add(Component.translatable("gui.enchantment_fusion.tip.preview.reverse_hint").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, rev).copy().withStyle(ChatFormatting.GOLD));
            lines.add(FusionTooltipRenderer.buildEffectSummary(rev, form).copy().withStyle(ChatFormatting.GRAY));
        } else if (EXEC_BTN.containsAt(left, top, mouseX, mouseY) && !preview.executable()) {
            lines.add(Component.translatable(primaryActionLabelKey(preview.kind())).withStyle(ChatFormatting.GOLD));
            lines.add(explainPrimaryDisabledReason(preview).copy().withStyle(ChatFormatting.RED));
        } else if (tab == FusionStationOperations.TAB_SOCKET) {
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int idx = 0; idx < socketed.size() && idx < 2; idx++) {
                int cardY = 34 + idx * 25;
                FusedPair p = socketed.pairs().get(idx);
                HitBox flipBox = new HitBox(RIGHT_X + 134, cardY + 3, 22, 17);
                HitBox unsockBox = new HitBox(RIGHT_X + 158, cardY + 3, 24, 17);
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
                if (new HitBox(RIGHT_X, cardY, 130, 23).containsAt(left, top, mouseX, mouseY)) {
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
        PickerGeometry geom,
        int mouseX,
        int mouseY
    ) {
        List<EnchantCandidate> list = sel.candidates();
        if (list.isEmpty()) {
            return false;
        }
        int maxScroll = Math.max(0, list.size() - geom.visibleRows());
        int scroll = Math.max(0, Math.min(candidateScroll, maxScroll));
        for (int r = 0; r < geom.visibleRows(); r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            HitBox rowBox = geom.rowBox(r);
            if (rowBox.containsAt(this.leftPos, this.topPos, mouseX, mouseY)) {
                EnchantCandidate cand = list.get(idx);
                boolean isA = (idx == sel.primaryIndex());
                boolean isB = (idx == sel.secondaryIndex());
                String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
                String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u2694 " : "";
                Component fullLabel = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level());
                FittedText fitted = FusionGuiSkin.fitBoundedText(
                    this.font, rolePrefix + srcBadge + fullLabel.getString(),
                    rowBox.w() - 12, FusionGuiSkin.SCALE_SMALL
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
        if (q == null) return;
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

    // =========================================================================
    // Disabled Click Feedback & Reason Explanations (TXT-8)
    // =========================================================================

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
    // Mouse & Keyboard Input (with Full Grimoire Overlay Forwarding & TXT-9 Shortcuts)
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
        PickerGeometry geom = pickerGeometryForTab(this.menu.activeTab());
        if (geom.listBounds().containsAt(this.leftPos, this.topPos, mouseX, mouseY)) {
            int max = Math.max(0, this.menu.computePreview().selection().candidates().size() - geom.visibleRows());
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
        int tab = this.menu.activeTab();
        int left = this.leftPos;
        int top = this.topPos;
        StationPreview preview = this.menu.computePreview();

        // 0. Top-right Embedded Grimoire Button
        if (button == 0 && GRIMOIRE_BTN.containsAt(left, top, mx, my)) {
            openEmbeddedGrimoire();
            return true;
        }

        // 1. 2 Workshop Tabs
        if (button == 0) {
            for (int t = 0; t < 2; t++) {
                if (tabHitBox(t).containsAt(left, top, mx, my)) {
                    candidateScroll = 0;
                    sendAction(StationAction.SELECT_TAB, t, 0, "");
                    playClickSound(1.08F);
                    return true;
                }
            }
        }

        // 2a. [⇄] Swap Physical Input Slots (Slot 0 <-> Slot 1)
        if (button == 0 && SWAP_SLOTS_BTN.containsAt(left, top, mx, my)) {
            if (canSwapPhysicalSlots()) {
                sendAction(StationAction.SWAP_SLOTS, 0, 0, "");
                playClickSound(1.15F);
                FusionGuiMicroEffects.triggerSwapPulse(left + SWAP_SLOTS_BTN.x() + 9, top + SWAP_SLOTS_BTN.y() + 9);
            } else {
                notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_slots.empty"));
            }
            return true;
        }

        // 2b. [⇅] Swap [A ▷ B] Selection Assignment
        if (button == 0 && SWAP_ORDER_BTN.containsAt(left, top, mx, my)) {
            if (canSwapSelectionOrder(preview)) {
                sendAction(StationAction.SWAP_ORDER, 0, 0, "");
                playClickSound(1.15F);
                FusionGuiMicroEffects.triggerSwapPulse(left + SWAP_ORDER_BTN.x() + 9, top + SWAP_ORDER_BTN.y() + 9);
            } else {
                notifyDisabledClick(Component.translatable("gui.enchantment_fusion.tip.swap_order.disabled"));
            }
            return true;
        }

        // 2c. Tab 1 [仅单附魔] Toggle Button (GUI-05)
        if (button == 0 && tab == FusionStationOperations.TAB_SOCKET && SINGLE_MODE_BTN.containsAt(left, top, mx, my)) {
            sendAction(StationAction.TOGGLE_SINGLE_MODE, this.menu.singleOnlyMode() ? 0 : 1, 0, "");
            playClickSound(1.06F);
            return true;
        }

        // 3. Mode C Enchantment Picker Row Clicks (NET-03 token + GUI-05 Shift+Click single shortcut)
        if (button == 0 || button == 1) {
            PickerGeometry geom = pickerGeometryForTab(tab);
            List<EnchantCandidate> cands = preview.selection().candidates();
            int scroll = Math.max(0, Math.min(candidateScroll, Math.max(0, cands.size() - geom.visibleRows())));

            for (int r = 0; r < geom.visibleRows(); r++) {
                int idx = scroll + r;
                if (idx >= cands.size()) break;
                HitBox rowBox = geom.rowBox(r);
                if (rowBox.containsAt(left, top, mx, my)) {
                    EnchantCandidate cand = cands.get(idx);
                    if (button == 0 && tab == FusionStationOperations.TAB_SOCKET && FusionGuiSkin.isShiftHeld()) {
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

        // 4. Tab 0 Always-Visible Toolbar + Idle Card [Flip] & [Split] Buttons (GUI-04 + NET-1 fingerprint)
        if (button == 0 && tab == FusionStationOperations.TAB_FUSE) {
            BookManageState mState = evaluateTab0BookManage(preview);
            boolean idleManageVisible = !preview.executable() && mState.hasBook() && !mState.isAberrant();
            boolean clickedFlip = TAB0_TOOLBAR_FLIP.containsAt(left, top, mx, my)
                || (idleManageVisible && BOOK_FLIP_BTN.containsAt(left, top, mx, my));
            boolean clickedSplit = TAB0_TOOLBAR_SPLIT.containsAt(left, top, mx, my)
                || (idleManageVisible && BOOK_SPLIT_BTN.containsAt(left, top, mx, my));
            if (clickedFlip) {
                if (mState.canExecute()) {
                    sendAction(StationAction.FLIP_SOCKET, 0, 0, mState.pairFingerprint());
                    playClickSound(1.1F);
                } else {
                    notifyDisabledClick(explainBookManageDisabledReason(mState));
                }
                return true;
            }
            if (clickedSplit) {
                if (mState.canExecute()) {
                    sendAction(StationAction.UNSOCKET_OR_SPLIT, 0, 0, mState.pairFingerprint());
                    playClickSound(0.95F);
                } else {
                    notifyDisabledClick(explainBookManageDisabledReason(mState));
                }
                return true;
            }
        }

        // 5. Primary Execute Button (with TXT-8 disabled click feedback & NET-02 execute token)
        if (button == 0 && EXEC_BTN.containsAt(left, top, mx, my)) {
            triggerPrimaryExecute(preview);
            return true;
        }

        // 6. Tab 1 Socket Card Inline [⇄] Flip & [卸除] Unsocket Buttons (NET-1 pair fingerprint + TXT-8 feedback)
        if (button == 0 && tab == FusionStationOperations.TAB_SOCKET) {
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int idx = 0; idx < socketed.size() && idx < 2; idx++) {
                FusedPair p = socketed.pairs().get(idx);
                int btnY = 34 + idx * 25 + 3;
                boolean hitFlip = new HitBox(RIGHT_X + 134, btnY, 22, 17).containsAt(left, top, mx, my);
                boolean hitRemove = new HitBox(RIGHT_X + 158, btnY, 24, 17).containsAt(left, top, mx, my);
                if (hitFlip || hitRemove) {
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
                        sendAction(StationAction.FLIP_SOCKET, idx, 0, p.fingerprint());
                        playClickSound(1.12F);
                    } else {
                        sendAction(StationAction.UNSOCKET_OR_SPLIT, idx, 0, p.fingerprint());
                        playClickSound(0.95F);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    private void triggerPrimaryExecute(StationPreview preview) {
        if (preview.executable() && canAffordPreview(preview)) {
            sendAction(StationAction.EXECUTE_PRIMARY, 0, 0, preview.expectedExecuteToken());
            playClickSound(1.0F);
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
        boolean ctrl = FusionGuiSkin.isCtrlOrCmdHeld();
        if (ctrl && key == InputConstants.KEY_TAB) {
            int nextTab = this.menu.activeTab() == FusionStationOperations.TAB_FUSE
                ? FusionStationOperations.TAB_SOCKET
                : FusionStationOperations.TAB_FUSE;
            candidateScroll = 0;
            sendAction(StationAction.SELECT_TAB, nextTab, 0, "");
            playClickSound(1.08F);
            return true;
        }
        if (ctrl && key == InputConstants.KEY_G) {
            openEmbeddedGrimoire();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            StationPreview preview = this.menu.computePreview();
            triggerPrimaryExecute(preview);
            return true;
        }
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            PickerGeometry geom = pickerGeometryForTab(this.menu.activeTab());
            int total = this.menu.computePreview().selection().candidates().size();
            if (total > geom.visibleRows()) {
                int max = Math.max(0, total - geom.visibleRows());
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

    private static HitBox tabHitBox(int tabIndex) {
        return new HitBox(tabIndex == 0 ? TAB0_X : TAB1_X, TAB_Y, TAB_W, TAB_H);
    }

    private HolderLookup.Provider currentRegistries() {
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
        if (reason == null || reason.langKey().isEmpty()) {
            return "gui.enchantment_fusion.reject.invalid_combination";
        }
        return reason.langKey();
    }
}
