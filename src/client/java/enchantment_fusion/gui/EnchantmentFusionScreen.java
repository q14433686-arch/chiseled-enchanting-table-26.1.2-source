package enchantment_fusion.gui;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionEconomyRules;
import enchantment_fusion.mechanics.FusionEconomyRules.EconomyQuote;
import enchantment_fusion.mechanics.FusionEconomyRules.ReagentKind;
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
import java.util.Locale;
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

    // Top-right embedded Grimoire button (opens FusionGrimoireScreen in-place without closing container!)
    public static final int GRIMOIRE_BTN_X = 232;
    public static final int GRIMOIRE_BTN_Y = 0;
    public static final int GRIMOIRE_BTN_W = 68;
    public static final int GRIMOIRE_BTN_H = 18;

    public static final int SWAP_BTN_X = 43;
    public static final int SWAP_BTN_Y = 48;
    public static final int SWAP_BTN_W = 24;
    public static final int SWAP_BTN_H = 24;

    public static final int RIGHT_X = 112;
    public static final int RIGHT_W = 186;

    // Primary execute button (Synthesis / Conversion)
    public static final int EXEC_BTN_X = 237;
    public static final int EXEC_BTN_Y = 95;
    public static final int EXEC_BTN_W = 61;
    public static final int EXEC_BTN_H = 26;

    // Tab 0 live preview plaque
    public static final int TAB0_PREVIEW_X = 112;
    public static final int TAB0_PREVIEW_Y = 89;
    public static final int TAB0_PREVIEW_W = 122;
    public static final int TAB0_PREVIEW_H = 36;

    // Tab 0 integrated Fused-Book Flip / Split bar when a Fused Book sits in Slot 3 or Slot 0
    public static final int BOOK_FLIP_X = 140;
    public static final int BOOK_FLIP_Y = 98;
    public static final int BOOK_FLIP_W = 44;
    public static final int BOOK_FLIP_H = 20;

    public static final int BOOK_SPLIT_X = 187;
    public static final int BOOK_SPLIT_Y = 98;
    public static final int BOOK_SPLIT_W = 47;
    public static final int BOOK_SPLIT_H = 20;

    private int candidateScroll = 0;
    private @Nullable FusionGrimoireScreen grimoireOverlay = null;

    public EnchantmentFusionScreen(EnchantmentFusionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, EnchantmentFusionMenu.GUI_WIDTH, EnchantmentFusionMenu.GUI_HEIGHT);
        this.titleLabelX = 14;
        this.titleLabelY = 26;
        this.inventoryLabelX = EnchantmentFusionMenu.PLAYER_INV_X;
        this.inventoryLabelY = 122;
    }

    public Rect2i guiBounds() {
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        if (this.grimoireOverlay != null && this.minecraft != null) {
            this.grimoireOverlay.init(this.minecraft, this.width, this.height);
        }
    }

    private void openEmbeddedGrimoire() {
        if (this.minecraft == null) {
            return;
        }
        int initCh = this.menu.activeTab() == FusionStationOperations.TAB_SOCKET ? 2 : 1;
        FusionGrimoireScreen overlay = new FusionGrimoireScreen(() -> this.grimoireOverlay = null, initCh);
        overlay.init(this.minecraft, this.width, this.height);
        this.grimoireOverlay = overlay;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Bounded labels rendered cleanly inside extractBackground.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int left = this.leftPos;
        int top = this.topPos;
        int tab = this.menu.activeTab();

        // 1. Render 2 Workshop Tabs + Top-Right Embedded Grimoire Button
        for (int t = 0; t < 2; t++) {
            if (t != tab) {
                drawTabHeader(ctx, t, false, mouseX, mouseY);
            }
        }
        drawGrimoireTopRightButton(ctx, mouseX, mouseY);

        FusionGuiSkin.drawNineSlice(
            ctx, FusionGuiSkin.PANEL_FRAME,
            left, top + 18, this.imageWidth, this.imageHeight - 18, 48, 48, 6
        );
        drawTabHeader(ctx, tab, true, mouseX, mouseY);

        // 2. Left Workstation Pedestal Header & 4 Role-Specific Slot Bezels
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, this.title.getString(),
            left + 10, 96, top + 30.0F, 0.72F, FusionGuiSkin.COLOR_GOLD
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

        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, slot0Label, left + 8, 36, top + 45.0F, 0.56F, FusionGuiSkin.COLOR_GOLD);
        FusionGuiSkin.drawCenteredBoundedText(ctx, this.font, slot1Label, left + 68, 36, top + 45.0F, 0.56F, FusionGuiSkin.COLOR_CYAN);
        FusionGuiSkin.drawLeftBoundedText(ctx, this.font, slot3Label, left + 10, top + 112, 35, 0.54F, FusionGuiSkin.COLOR_CREAM);

        // [⇄] Swap-Order pixel-art button
        StationPreview preview = this.menu.computePreview();
        boolean canSwap = preview.selection().candidates().size() >= 2
            || !this.menu.stationContainer().getItem(0).isEmpty()
            || !this.menu.stationContainer().getItem(1).isEmpty();
        boolean overSwap = isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawSprite(
            ctx,
            !canSwap ? FusionGuiSkin.BTN_SWAP_DISABLED : (overSwap ? FusionGuiSkin.BTN_SWAP_HOVER : FusionGuiSkin.BTN_SWAP_NORMAL),
            left + SWAP_BTN_X, top + SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H
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

        // 4. Right Interactive Workspace (2 Tabs!)
        HolderLookup.Provider registries = currentRegistries();
        if (tab == FusionStationOperations.TAB_SOCKET) {
            renderTab1GearSocketsAndInPlace(ctx, registries, preview, mouseX, mouseY);
        } else {
            renderTab0BookFusionAndSplit(ctx, registries, preview, mouseX, mouseY);
        }
    }

    private void drawTabHeader(GuiGraphicsExtractor ctx, int tabIndex, boolean active, int mouseX, int mouseY) {
        int tx = tabX(tabIndex);
        boolean hovered = isHoveringRect(tx, TAB_Y, TAB_W, TAB_H, mouseX, mouseY);
        var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hovered ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
        FusionGuiSkin.drawSprite(ctx, sprite, this.leftPos + tx, this.topPos + TAB_Y, TAB_W, TAB_H + (active ? 2 : 0));

        String key = tabIndex == 0 ? "gui.enchantment_fusion.tab.fuse" : "gui.enchantment_fusion.tab.socket";
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable(key).getString(),
            this.leftPos + tx + 6, TAB_W - 12,
            this.topPos + 11.5F,
            0.62F,
            active ? FusionGuiSkin.COLOR_GOLD : (hovered ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
        );
    }

    private void drawGrimoireTopRightButton(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int gx = this.leftPos + GRIMOIRE_BTN_X;
        int gy = this.topPos + GRIMOIRE_BTN_Y;
        boolean hov = isHoveringRect(GRIMOIRE_BTN_X, GRIMOIRE_BTN_Y, GRIMOIRE_BTN_W, GRIMOIRE_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx,
            hov ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL,
            gx, gy, GRIMOIRE_BTN_W, GRIMOIRE_BTN_H, 32, 32, 4
        );
        ctx.item(new ItemStack(FusionItems.FUSION_GRIMOIRE), gx + 2, gy + 1);
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font,
            Component.translatable("gui.enchantment_fusion.btn.grimoire").getString(),
            gx + 19, gy + 5, GRIMOIRE_BTN_W - 22, 0.56F,
            hov ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
        );
    }

    // =========================================================================
    // TAB 0: Book Synthesis + Integrated Fused-Book Flip / Split
    // =========================================================================

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
            left + RIGHT_X, top + 27, RIGHT_W, 0.56F, FusionGuiSkin.COLOR_MUTED
        );

        renderCandidatePickerRows(ctx, registries, preview.selection(), RIGHT_X, 36, RIGHT_W, 3, mouseX, mouseY);

        // If a manageable Fused Enchanted Book sits in Slot 3 / Slot 0 and no new fusion is queued, show Flip & Split controls directly!
        ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
        FusedEnchantmentList mList = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        boolean showBookManage = !preview.executable() && manageable.is(FusionItems.FUSED_ENCHANTED_BOOK) && !mList.isEmpty();

        if (showBookManage) {
            FusedPair topPair = mList.pairs().getFirst();
            boolean canAct = !topPair.isAberrant() && canAffordXp(preview.manageXpCost());
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.PANEL_FRAME,
                left + TAB0_PREVIEW_X, top + TAB0_PREVIEW_Y, RIGHT_W, TAB0_PREVIEW_H, 48, 48, 5
            );
            String title = FusionTooltipRenderer.buildPairDisplayName(registries, topPair).getString();
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, title,
                left + TAB0_PREVIEW_X + 6, top + TAB0_PREVIEW_Y + 6, RIGHT_W - 12, 0.54F,
                topPair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
            );

            boolean overFlip = isHoveringRect(BOOK_FLIP_X, BOOK_FLIP_Y, BOOK_FLIP_W, BOOK_FLIP_H, mouseX, mouseY);
            FusionGuiSkin.drawNineSlice(
                ctx, !canAct ? FusionGuiSkin.BTN_DISABLED : (overFlip ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
                left + BOOK_FLIP_X, top + BOOK_FLIP_Y, BOOK_FLIP_W, BOOK_FLIP_H, 32, 32, 4
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
                left + BOOK_FLIP_X + 3, BOOK_FLIP_W - 6, top + BOOK_FLIP_Y + 10.0F,
                0.56F, canAct ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
            );

            boolean overSplit = isHoveringRect(BOOK_SPLIT_X, BOOK_SPLIT_Y, BOOK_SPLIT_W, BOOK_SPLIT_H, mouseX, mouseY);
            FusionGuiSkin.drawNineSlice(
                ctx, !canAct ? FusionGuiSkin.BTN_DISABLED : (overSplit ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
                left + BOOK_SPLIT_X, top + BOOK_SPLIT_Y, BOOK_SPLIT_W, BOOK_SPLIT_H, 32, 32, 4
            );
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.split").getString(),
                left + BOOK_SPLIT_X + 3, BOOK_SPLIT_W - 6, top + BOOK_SPLIT_Y + 10.0F,
                0.56F, canAct ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        FusionGuiSkin.drawNineSlice(
            ctx, FusionGuiSkin.PANEL_FRAME,
            left + TAB0_PREVIEW_X, top + TAB0_PREVIEW_Y, TAB0_PREVIEW_W, TAB0_PREVIEW_H, 48, 48, 5
        );
        renderCompactPreviewBox(
            ctx, registries, preview,
            left + TAB0_PREVIEW_X, top + TAB0_PREVIEW_Y, TAB0_PREVIEW_W, TAB0_PREVIEW_H
        );
        renderPrimaryExecuteButton(ctx, preview, mouseX, mouseY);
    }

    // =========================================================================
    // TAB 1: Gear Vessel Gem Sockets + Cross-Domain Conversion
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
            left + RIGHT_X, top + 26, RIGHT_W, 0.55F, FusionGuiSkin.COLOR_MUTED
        );

        int maxSlots = hasGear ? VesselCapacityRules.getMaxVesselSlots(host) : 1;
        FusedEnchantmentList socketed = hasGear
            ? host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            : FusedEnchantmentList.EMPTY;

        for (int idx = 0; idx < 2; idx++) {
            int cardY = 34 + idx * 25;
            boolean unlocked = hasGear && idx < maxSlots;
            FusedPair pair = (unlocked && idx < socketed.size()) ? socketed.pairs().get(idx) : null;

            var cardSprite = pair == null
                ? FusionGuiSkin.SOCKET_EMPTY
                : (pair.isAberrant() ? FusionGuiSkin.SOCKET_ABERRANT : FusionGuiSkin.SOCKET_FILLED);
            FusionGuiSkin.drawNineSlice(ctx, cardSprite, left + RIGHT_X, top + cardY, RIGHT_W, 23, 96, 40, 7);

            if (!hasGear && hasSpecialCross) {
                if (idx == 0) {
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.special_cross_host").getString(),
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, 0.56F, FusionGuiSkin.COLOR_CYAN
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
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, 0.58F, FusionGuiSkin.COLOR_GOLD
                    );
                }
            } else if (!hasGear) {
                if (idx == 0) {
                    FusionGuiSkin.drawCenteredBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.place_gear").getString(),
                        left + RIGHT_X + 8, RIGHT_W - 16, top + cardY + 11.5F, 0.58F, FusionGuiSkin.COLOR_MUTED
                    );
                }
            } else if (!unlocked) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.locked_expand_hint").getString(),
                    left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, 0.54F, FusionGuiSkin.COLOR_MUTED
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
                        left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, 0.56F, FusionGuiSkin.COLOR_GOLD
                    );
                } else {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.socket.empty_ready", idx + 1).getString(),
                        left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, 0.56F, FusionGuiSkin.COLOR_CREAM
                    );
                }
            } else {
                String label = FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, label,
                    left + RIGHT_X + 7, top + cardY + 8, 124, 0.56F,
                    pair.isAberrant() ? FusionGuiSkin.COLOR_RED : FusionGuiSkin.COLOR_AMETHYST
                );

                boolean manageable = !pair.isAberrant() && canAffordXp(preview.manageXpCost());
                int flipX = RIGHT_X + 134;
                int unsockX = RIGHT_X + 158;
                int btnY = cardY + 3;

                boolean overFlip = isHoveringRect(flipX, btnY, 22, 17, mouseX, mouseY);
                FusionGuiSkin.drawNineSlice(
                    ctx,
                    !manageable ? FusionGuiSkin.BTN_DISABLED : (overFlip ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
                    left + flipX, top + btnY, 22, 17, 32, 32, 4
                );
                FusionGuiSkin.drawCenteredBoundedText(
                    ctx, this.font, "\u21C4",
                    left + flipX + 2, 18, top + btnY + 8.5F, 0.65F,
                    manageable ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
                );

                boolean overUnsock = isHoveringRect(unsockX, btnY, 24, 17, mouseX, mouseY);
                FusionGuiSkin.drawNineSlice(
                    ctx,
                    !manageable ? FusionGuiSkin.BTN_DISABLED : (overUnsock ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
                    left + unsockX, top + btnY, 24, 17, 32, 32, 4
                );
                FusionGuiSkin.drawCenteredBoundedText(
                    ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.unsocket").getString(),
                    left + unsockX + 2, 20, top + btnY + 8.5F, 0.54F,
                    manageable ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
                );
            }
        }

        renderCandidatePickerRows(ctx, registries, preview.selection(), RIGHT_X, 86, 122, 2, mouseX, mouseY);
        renderPrimaryExecuteButton(ctx, preview, mouseX, mouseY);
    }

    // =========================================================================
    // Shared Mode C Enchantment Picker, Economy Badges & Preview Helpers
    // =========================================================================

    private void renderCandidatePickerRows(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        ResolvedSelection sel,
        int rx,
        int startY,
        int rw,
        int visibleRows,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;
        List<EnchantCandidate> list = sel.candidates();

        if (list.isEmpty()) {
            FusionGuiSkin.drawNineSlice(
                ctx, FusionGuiSkin.ROW_UNSELECTED,
                left + rx, top + startY, rw, visibleRows * 17 - 1, 64, 18, 4
            );
            String emptyKey = this.menu.activeTab() == FusionStationOperations.TAB_SOCKET
                ? "gui.enchantment_fusion.picker.empty_gear"
                : "gui.enchantment_fusion.picker.empty_books";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, Component.translatable(emptyKey).getString(),
                left + rx + 6, rw - 12, top + startY + (visibleRows * 17 - 1) * 0.5F,
                0.56F, FusionGuiSkin.COLOR_MUTED
            );
            return;
        }

        int maxScroll = Math.max(0, list.size() - visibleRows);
        int scroll = Math.max(0, Math.min(candidateScroll, maxScroll));

        for (int r = 0; r < visibleRows; r++) {
            int idx = scroll + r;
            if (idx >= list.size()) break;
            EnchantCandidate cand = list.get(idx);
            int ry = startY + r * 17;
            boolean isA = (idx == sel.primaryIndex());
            boolean isB = (idx == sel.secondaryIndex());
            boolean hov = isHoveringRect(rx, ry, rw, 16, mouseX, mouseY);

            var rowSprite = isA ? FusionGuiSkin.ROW_SELECTED_A
                : (isB ? FusionGuiSkin.ROW_SELECTED_B : (hov ? FusionGuiSkin.ROW_HOVER : FusionGuiSkin.ROW_UNSELECTED));
            FusionGuiSkin.drawNineSlice(ctx, rowSprite, left + rx, top + ry, rw, 16, 64, 18, 4);

            String rolePrefix = isA ? "[A] " : (isB ? "[B] " : "\u00B7 ");
            String srcBadge = cand.sourceSlot() == FusionStationOperations.SLOT_TARGET ? "\u2694 " : "";
            String enchName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, cand.id(), cand.level()).getString();
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, rolePrefix + srcBadge + enchName,
                left + rx + 6, top + ry + 5, rw - 12, 0.58F,
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
                    ix, py + 7, iw, 0.56F, FusionGuiSkin.COLOR_RED
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.try_swap_hint").getString(),
                    ix, py + 20, iw, 0.52F, FusionGuiSkin.COLOR_MUTED
                );
                return;
            }

            FusedPair pair = preview.previewPair();
            if (pair != null) {
                String title = FusionTooltipRenderer.buildPairDisplayName(registries, pair).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, title, ix, py + 6, iw, 0.54F, FusionGuiSkin.COLOR_AMETHYST);

                String desc = FusionTooltipRenderer.buildEffectSummary(pair, form).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, desc, ix, py + 15, iw, 0.50F, FusionGuiSkin.COLOR_CYAN);

                String econLine = formatCompactEconomyLine(preview);
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, econLine, ix, py + 25, iw, 0.50F,
                    canAffordPreview(preview) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
                );
            } else {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.idle_title").getString(),
                    ix, py + 8, iw, 0.58F, FusionGuiSkin.COLOR_GOLD
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.picker.click_ab_tip").getString(),
                    ix, py + 20, iw, 0.52F, FusionGuiSkin.COLOR_MUTED
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
        boolean hovered = isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mouseX, mouseY);

        FusionGuiSkin.drawNineSlice(
            ctx,
            !ready ? FusionGuiSkin.BTN_DISABLED : (hovered ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
            left + EXEC_BTN_X, top + EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, 32, 32, 4
        );
        String label = Component.translatable(primaryActionLabelKey(preview.kind())).getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, label,
            left + EXEC_BTN_X + 4, EXEC_BTN_W - 8, top + EXEC_BTN_Y + 9.0F,
            0.58F, ready ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );
        if (preview.executable()) {
            EconomyQuote q = preview.economyQuote();
            int succPct = q != null ? (int) Math.round((1.0 - q.failureChance()) * 100.0) : 100;
            String badge = preview.xpCost() + "Lv \u00B7 " + succPct + "%";
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, badge,
                left + EXEC_BTN_X + 3, EXEC_BTN_W - 6, top + EXEC_BTN_Y + 18.5F,
                0.48F, ready ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
            );
        }
    }

    private void renderVesselDiamonds(GuiGraphicsExtractor ctx, int startX, int y) {
        ItemStack target = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
        if (!VesselCapacityRules.isPotentialEquipmentHost(target)) {
            return;
        }
        int maxSlots = VesselCapacityRules.maxVesselSlots(target);
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
            // Render table cleanly behind overlay with off-screen mouse so slot hovers don't bleed through!
            super.extractRenderState(ctx, -1000, -1000, partialTick);
            this.grimoireOverlay.extractRenderState(ctx, mouseX, mouseY, partialTick);
            return;
        }

        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        this.extractTooltip(ctx, mouseX, mouseY);
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) {
            renderHoverTooltips(ctx, mouseX, mouseY);
        }
    }

    private void renderHoverTooltips(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        StationPreview preview = this.menu.computePreview();
        HolderLookup.Provider registries = currentRegistries();
        int tab = this.menu.activeTab();

        if (isHoveringRect(GRIMOIRE_BTN_X, GRIMOIRE_BTN_Y, GRIMOIRE_BTN_W, GRIMOIRE_BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("item.enchantment_fusion.fusion_grimoire").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.grimoire_btn").withStyle(ChatFormatting.GRAY));
        } else if (isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.desc").withStyle(ChatFormatting.GRAY));
        } else if (isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mouseX, mouseY)
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
        } else if ((isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mouseX, mouseY)
            || (tab == FusionStationOperations.TAB_FUSE && isHoveringRect(TAB0_PREVIEW_X, TAB0_PREVIEW_Y, TAB0_PREVIEW_W, TAB0_PREVIEW_H, mouseX, mouseY)))
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
        } else if (tab == FusionStationOperations.TAB_SOCKET) {
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int idx = 0; idx < socketed.size() && idx < 2; idx++) {
                int cardY = 34 + idx * 25;
                if (isHoveringRect(RIGHT_X, cardY, 130, 23, mouseX, mouseY)) {
                    FusedPair p = socketed.pairs().get(idx);
                    lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy()
                        .withStyle(p.isAberrant() ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
                    lines.add(FusionTooltipRenderer.buildEffectSummary(p, HostForm.resolve(host)).copy()
                        .withStyle(ChatFormatting.AQUA));
                    if (p.isAberrant()) {
                        lines.add(Component.translatable("tooltip.enchantment_fusion.aberrant_cet_required")
                            .withStyle(ChatFormatting.DARK_RED));
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

    private void appendEconomyTooltipLines(List<Component> lines, StationPreview preview) {
        EconomyQuote q = preview.economyQuote();
        if (q == null) return;
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
        }
        int failPct = (int) Math.round(q.failureChance() * 100.0);
        if (failPct > 0) {
            String riskKey = q.highRiskTier()
                ? "gui.enchantment_fusion.tip.risk_high"
                : "gui.enchantment_fusion.tip.risk_normal";
            lines.add(Component.translatable(riskKey, failPct)
                .withStyle(q.highRiskTier() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW));
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
    // Mouse & Keyboard Input (with Full Grimoire Overlay Forwarding!)
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
        int tab = this.menu.activeTab();
        int visibleRows = (tab == FusionStationOperations.TAB_SOCKET) ? 2 : 3;
        int listY = (tab == FusionStationOperations.TAB_SOCKET) ? 86 : 36;
        if (isHoveringRect(RIGHT_X, listY, RIGHT_W, visibleRows * 17, mouseX, mouseY)) {
            int max = Math.max(0, this.menu.computePreview().selection().candidates().size() - visibleRows);
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
        StationPreview preview = this.menu.computePreview();

        // 0. Top-right Embedded Grimoire Button
        if (button == 0 && isHoveringRect(GRIMOIRE_BTN_X, GRIMOIRE_BTN_Y, GRIMOIRE_BTN_W, GRIMOIRE_BTN_H, mx, my)) {
            openEmbeddedGrimoire();
            return true;
        }

        // 1. 2 Workshop Tabs
        if (button == 0) {
            for (int t = 0; t < 2; t++) {
                if (isHoveringRect(tabX(t), TAB_Y, TAB_W, TAB_H, mx, my)) {
                    candidateScroll = 0;
                    sendAction(StationAction.SELECT_TAB, t, 0);
                    playClickSound(1.08F);
                    return true;
                }
            }
        }

        // 2. [⇄] Swap-Order Button
        if (button == 0 && isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mx, my)) {
            sendAction(StationAction.SWAP_ORDER, 0, 0);
            playClickSound(1.15F);
            return true;
        }

        // 3. Mode C Enchantment Picker Row Clicks
        if (button == 0 || button == 1) {
            int visibleRows = (tab == FusionStationOperations.TAB_SOCKET) ? 2 : 3;
            int startY = (tab == FusionStationOperations.TAB_SOCKET) ? 86 : 36;
            int rowW = (tab == FusionStationOperations.TAB_SOCKET) ? 122 : RIGHT_W;
            List<EnchantCandidate> cands = preview.selection().candidates();
            int scroll = Math.max(0, Math.min(candidateScroll, Math.max(0, cands.size() - visibleRows)));

            for (int r = 0; r < visibleRows; r++) {
                int idx = scroll + r;
                if (idx >= cands.size()) break;
                int ry = startY + r * 17;
                if (isHoveringRect(RIGHT_X, ry, rowW, 16, mx, my)) {
                    sendAction(button == 0 ? StationAction.PICK_PRIMARY_A : StationAction.PICK_SECONDARY_B, idx, 0);
                    playClickSound(button == 0 ? 1.12F : 0.96F);
                    return true;
                }
            }
        }

        // 4. Tab 0 Integrated Fused-Book [Flip] & [Split] Buttons
        if (button == 0 && tab == FusionStationOperations.TAB_FUSE && !preview.executable()) {
            ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
            FusedEnchantmentList list = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (manageable.is(FusionItems.FUSED_ENCHANTED_BOOK) && !list.isEmpty()
                && !list.pairs().getFirst().isAberrant() && canAffordXp(preview.manageXpCost())) {
                if (isHoveringRect(BOOK_FLIP_X, BOOK_FLIP_Y, BOOK_FLIP_W, BOOK_FLIP_H, mx, my)) {
                    sendAction(StationAction.FLIP_SOCKET, 0, 0);
                    playClickSound(1.1F);
                    return true;
                }
                if (isHoveringRect(BOOK_SPLIT_X, BOOK_SPLIT_Y, BOOK_SPLIT_W, BOOK_SPLIT_H, mx, my)) {
                    sendAction(StationAction.UNSOCKET_OR_SPLIT, 0, 0);
                    playClickSound(0.95F);
                    return true;
                }
            }
        }

        // 5. Primary Execute Button
        if (button == 0
            && isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mx, my)
            && preview.executable() && canAffordPreview(preview)) {
            sendAction(StationAction.EXECUTE_PRIMARY, 0, 0);
            playClickSound(1.0F);
            return true;
        }

        // 6. Tab 1 Socket Card Inline [⇄] Flip & [卸] Unsocket Buttons
        if (button == 0 && tab == FusionStationOperations.TAB_SOCKET) {
            ItemStack host = this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET);
            FusedEnchantmentList socketed = host.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            for (int idx = 0; idx < socketed.size() && idx < 2; idx++) {
                FusedPair p = socketed.pairs().get(idx);
                if (p.isAberrant() || !canAffordXp(preview.manageXpCost())) continue;
                int btnY = 34 + idx * 25 + 3;
                if (isHoveringRect(RIGHT_X + 134, btnY, 22, 17, mx, my)) {
                    sendAction(StationAction.FLIP_SOCKET, idx, 0);
                    playClickSound(1.12F);
                    return true;
                }
                if (isHoveringRect(RIGHT_X + 158, btnY, 24, 17, mx, my)) {
                    sendAction(StationAction.UNSOCKET_OR_SPLIT, idx, 0);
                    playClickSound(0.95F);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
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

    private static int tabX(int tabIndex) {
        return tabIndex == 0 ? TAB0_X : TAB1_X;
    }

    private HolderLookup.Provider currentRegistries() {
        return this.minecraft != null && this.minecraft.level != null
            ? this.minecraft.level.registryAccess()
            : null;
    }

    private void sendAction(StationAction action, int paramA, int paramB) {
        if (ClientPlayNetworking.canSend(StationActionPayload.TYPE)) {
            ClientPlayNetworking.send(new StationActionPayload(this.menu.containerId, action.ordinal(), paramA, paramB));
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

    private boolean isHoveringRect(int rx, int ry, int rw, int rh, double mouseX, double mouseY) {
        int gx = this.leftPos + rx;
        int gy = this.topPos + ry;
        return mouseX >= gx && mouseX < gx + rw && mouseY >= gy && mouseY < gy + rh;
    }

    private static String primaryActionLabelKey(OperationKind kind) {
        return switch (kind) {
            case EXPAND_VESSEL -> "gui.enchantment_fusion.btn.expand_vessel";
            case TOP_UP_LEVEL -> "gui.enchantment_fusion.btn.top_up";
            case SOCKET_FUSED_BOOK -> "gui.enchantment_fusion.btn.inscribe";
            case INSCRIBE_SINGLE_CROSS -> "gui.enchantment_fusion.btn.inscribe_cross";
            case FUSE_ONTO_GEAR -> "gui.enchantment_fusion.btn.fuse_gear";
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
