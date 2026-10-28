package enchantment_fusion.gui;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.FusionStationOperations.EnchantCandidate;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.FusionStationOperations.ResolvedSelection;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.mechanics.SignaturePairs;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.VesselCapacityRules;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.StationAction;
import enchantment_fusion.menu.FusionNetworking.StationActionPayload;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.registry.FusionDataComponents;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 3-Tab + Mode C Enchantment-Picker + Vessel-Socket pixel-art GUI for the Enchantment Fusion Table (308 x 214).
 * Uses exclusively genuine pixel-art PNG sprites (zero solid rectangle placeholders).
 */
public final class EnchantmentFusionScreen extends AbstractContainerScreen<EnchantmentFusionMenu> {
    public static final int TAB_W = 84;
    public static final int TAB_H = 20;
    public static final int TAB0_X = 12;
    public static final int TAB1_X = 100;
    public static final int TAB2_X = 188;
    public static final int TAB_Y = 0;

    public static final int SWAP_BTN_X = 43;
    public static final int SWAP_BTN_Y = 48;
    public static final int SWAP_BTN_W = 24;
    public static final int SWAP_BTN_H = 24;

    public static final int RIGHT_X = 112;
    public static final int RIGHT_W = 186;

    // Tab 0 & Tab 1 primary execute button
    public static final int EXEC_BTN_X = 237;
    public static final int EXEC_BTN_Y = 95;
    public static final int EXEC_BTN_W = 61;
    public static final int EXEC_BTN_H = 24;

    // Tab 0 live preview plaque
    public static final int TAB0_PREVIEW_X = 112;
    public static final int TAB0_PREVIEW_Y = 89;
    public static final int TAB0_PREVIEW_W = 122;
    public static final int TAB0_PREVIEW_H = 36;

    // Tab 2 book flip / split buttons
    public static final int TAB2_FLIP_X = 200;
    public static final int TAB2_FLIP_Y = 35;
    public static final int TAB2_FLIP_W = 46;
    public static final int TAB2_FLIP_H = 18;

    public static final int TAB2_SPLIT_X = 249;
    public static final int TAB2_SPLIT_Y = 35;
    public static final int TAB2_SPLIT_W = 49;
    public static final int TAB2_SPLIT_H = 18;

    private int candidateScroll = 0;
    private int compendiumScroll = 0;

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
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Bounded labels rendered cleanly inside extractBackground.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int left = this.leftPos;
        int top = this.topPos;
        int tab = this.menu.activeTab();

        // 1. Render inactive tabs behind main panel, then main panel (y+18..y+214), then active tab flush on top!
        for (int t = 0; t < 3; t++) {
            if (t != tab) {
                drawTabHeader(ctx, t, false, mouseX, mouseY);
            }
        }
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

        // Dynamic contextual mini-labels above Pedestal slots
        String slot0Label = Component.translatable(
            tab == FusionStationOperations.TAB_SOCKET ? "gui.enchantment_fusion.slot.inlay_book" : "gui.enchantment_fusion.slot.primary_short"
        ).getString();
        String slot1Label = Component.translatable(
            tab == FusionStationOperations.TAB_SOCKET ? "gui.enchantment_fusion.slot.aux_book" : "gui.enchantment_fusion.slot.secondary_short"
        ).getString();
        String slot3Label = Component.translatable(switch (tab) {
            case FusionStationOperations.TAB_SOCKET -> "gui.enchantment_fusion.slot.host_gear";
            case FusionStationOperations.TAB_COMPENDIUM -> "gui.enchantment_fusion.slot.split_target";
            default -> "gui.enchantment_fusion.slot.output_book";
        }).getString();

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

        // Vessel Spirit Gem indicators next to Slot 3
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

        // 4. Right Interactive Workspace by Active Tab
        HolderLookup.Provider registries = currentRegistries();
        if (tab == FusionStationOperations.TAB_FUSE) {
            renderTab0BookFusion(ctx, registries, preview, mouseX, mouseY);
        } else if (tab == FusionStationOperations.TAB_SOCKET) {
            renderTab1GearSocketsAndInPlace(ctx, registries, preview, mouseX, mouseY);
        } else {
            renderTab2SplitAndCompendium(ctx, registries, preview, mouseX, mouseY);
        }
    }

    private void drawTabHeader(GuiGraphicsExtractor ctx, int tabIndex, boolean active, int mouseX, int mouseY) {
        int tx = tabX(tabIndex);
        boolean hovered = isHoveringRect(tx, TAB_Y, TAB_W, TAB_H, mouseX, mouseY);
        var sprite = active ? FusionGuiSkin.TAB_ACTIVE : (hovered ? FusionGuiSkin.TAB_HOVER : FusionGuiSkin.TAB_INACTIVE);
        FusionGuiSkin.drawSprite(ctx, sprite, this.leftPos + tx, this.topPos + TAB_Y, TAB_W, TAB_H + (active ? 2 : 0));

        String key = switch (tabIndex) {
            case 0 -> "gui.enchantment_fusion.tab.fuse";
            case 1 -> "gui.enchantment_fusion.tab.socket";
            default -> "gui.enchantment_fusion.tab.compendium";
        };
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable(key).getString(),
            this.leftPos + tx + 6, TAB_W - 12,
            this.topPos + 11.5F,
            0.64F,
            active ? FusionGuiSkin.COLOR_GOLD : (hovered ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED)
        );
    }

    // =========================================================================
    // TAB 0: Book & Multi-Enchantment List Picker -> Fused Enchanted Book
    // =========================================================================

    private void renderTab0BookFusion(
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

        // Live Preview Plaque + Primary Execute Button
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
    // TAB 1: Gear Vessel Gem Sockets + In-Place Gear 2-in-1 Fusion
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

        // Render 2 Vessel Socket Relic Cards (y=34 and y=60)
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
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.socket.empty_ready", idx + 1).getString(),
                    left + RIGHT_X + 8, top + cardY + 8, RIGHT_W - 16, 0.56F, FusionGuiSkin.COLOR_CREAM
                );
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

        // Lower In-Place Enchantment Picker (2 rows at y=86, 103, width 122) + Execute Button on Right
        renderCandidatePickerRows(ctx, registries, preview.selection(), RIGHT_X, 86, 122, 2, mouseX, mouseY);
        renderPrimaryExecuteButton(ctx, preview, mouseX, mouseY);
    }

    // =========================================================================
    // TAB 2: Fused Book Flip / Clean Split + Handcrafted Resonance Compendium
    // =========================================================================

    private void renderTab2SplitAndCompendium(
        GuiGraphicsExtractor ctx,
        HolderLookup.Provider registries,
        StationPreview preview,
        int mouseX,
        int mouseY
    ) {
        int left = this.leftPos;
        int top = this.topPos;

        ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
        FusedEnchantmentList list = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        FusedPair topPair = list.isEmpty() ? null : list.pairs().get(0);
        boolean canAct = topPair != null && !topPair.isAberrant() && canAffordXp(preview.manageXpCost());

        String status = topPair != null
            ? FusionTooltipRenderer.buildPairDisplayName(registries, topPair).getString()
            : Component.translatable("gui.enchantment_fusion.tab2.place_book_hint").getString();
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, status,
            left + RIGHT_X, top + 40, 85, 0.56F,
            topPair != null ? FusionGuiSkin.COLOR_AMETHYST : FusionGuiSkin.COLOR_MUTED
        );

        boolean overFlip = isHoveringRect(TAB2_FLIP_X, TAB2_FLIP_Y, TAB2_FLIP_W, TAB2_FLIP_H, mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx,
            !canAct ? FusionGuiSkin.BTN_DISABLED : (overFlip ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
            left + TAB2_FLIP_X, top + TAB2_FLIP_Y, TAB2_FLIP_W, TAB2_FLIP_H, 32, 32, 4
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
            left + TAB2_FLIP_X + 3, TAB2_FLIP_W - 6, top + TAB2_FLIP_Y + 9.0F,
            0.56F, canAct ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
        );

        boolean overSplit = isHoveringRect(TAB2_SPLIT_X, TAB2_SPLIT_Y, TAB2_SPLIT_W, TAB2_SPLIT_H, mouseX, mouseY);
        FusionGuiSkin.drawNineSlice(
            ctx,
            !canAct ? FusionGuiSkin.BTN_DISABLED : (overSplit ? FusionGuiSkin.BTN_HOVER : FusionGuiSkin.BTN_NORMAL),
            left + TAB2_SPLIT_X, top + TAB2_SPLIT_Y, TAB2_SPLIT_W, TAB2_SPLIT_H, 32, 32, 4
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.split").getString(),
            left + TAB2_SPLIT_X + 3, TAB2_SPLIT_W - 6, top + TAB2_SPLIT_Y + 9.0F,
            0.56F, canAct ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );

        // Scrollable Handcrafted Signature Compendium (4 rows at y = 57, 74, 91, 108)
        List<SignatureEntry> allSigs = SignaturePairs.allUniqueSignatures();
        int maxScroll = Math.max(0, allSigs.size() - 4);
        int start = Math.max(0, Math.min(compendiumScroll, maxScroll));

        for (int r = 0; r < 4; r++) {
            int idx = start + r;
            if (idx >= allSigs.size()) break;
            SignatureEntry sig = allSigs.get(idx);
            int ry = 57 + r * 17;
            boolean hov = isHoveringRect(RIGHT_X, ry, RIGHT_W, 16, mouseX, mouseY);
            boolean unlocked = "*".equals(sig.primaryId())
                || CompendiumDiscoveryTracker.isClientDiscovered(sig.primaryId(), sig.secondaryId());

            FusionGuiSkin.drawNineSlice(
                ctx, hov ? FusionGuiSkin.ROW_HOVER : FusionGuiSkin.ROW_UNSELECTED,
                left + RIGHT_X, top + ry, RIGHT_W, 16, 64, 18, 4
            );
            String pName = "*".equals(sig.primaryId())
                ? "*"
                : FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.primaryId()).getString();
            String sName = FusionTooltipRenderer.formatEnchantmentBaseName(registries, sig.secondaryId()).getString();
            String titleStr = Component.translatable(sig.titleKey()).getString();
            String line = (unlocked ? "\u2605 " : "\u25C7 ") + titleStr + " [" + pName + " \u25B7 " + sName + "]";
            FusionGuiSkin.drawLeftBoundedText(
                ctx, this.font, line,
                left + RIGHT_X + 6, top + ry + 5, RIGHT_W - 12, 0.56F,
                unlocked ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_CREAM
            );
        }
    }

    // =========================================================================
    // Shared Mode C Enchantment Picker & Preview Helpers
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
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, title, ix, py + 6, iw, 0.56F, FusionGuiSkin.COLOR_AMETHYST);

                String desc = FusionTooltipRenderer.buildEffectSummary(pair, form).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, desc, ix, py + 16, iw, 0.52F, FusionGuiSkin.COLOR_CYAN);

                int stabPct = (int) Math.round((1.0 - preview.instabilityChance()) * 100.0);
                String costAndStab = Component.translatable("gui.enchantment_fusion.preview.xp_cost", preview.xpCost()).getString()
                    + (preview.repulsive() ? ("  " + String.format(Locale.ROOT, "%d%%", stabPct)) : "");
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, costAndStab, ix, py + 25, iw, 0.52F,
                    canAffordXp(preview.xpCost()) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
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
        boolean ready = preview.executable() && canAffordXp(preview.xpCost());
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
            0.60F, ready ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );
        if (preview.executable()) {
            String xpLine = Component.translatable("gui.enchantment_fusion.btn.xp_badge", preview.xpCost()).getString();
            FusionGuiSkin.drawCenteredBoundedText(
                ctx, this.font, xpLine,
                left + EXEC_BTN_X + 4, EXEC_BTN_W - 8, top + EXEC_BTN_Y + 17.5F,
                0.52F, canAffordXp(preview.xpCost()) ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
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
    // Hover Tooltips (Live A▷B vs B▷A Preview + Compendium Entries + Controls)
    // =========================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
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

        if (isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mouseX, mouseY)) {
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
        } else if ((isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mouseX, mouseY)
            || (tab == FusionStationOperations.TAB_FUSE && isHoveringRect(TAB0_PREVIEW_X, TAB0_PREVIEW_Y, TAB0_PREVIEW_W, TAB0_PREVIEW_H, mouseX, mouseY)))
            && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(FusionStationOperations.SLOT_TARGET));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, p).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(FusionTooltipRenderer.buildEffectSummary(p, form).copy().withStyle(ChatFormatting.AQUA));
            FusedPair rev = p.flipped();
            lines.add(Component.translatable("gui.enchantment_fusion.tip.preview.reverse_hint").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(FusionTooltipRenderer.buildPairDisplayName(registries, rev).copy().withStyle(ChatFormatting.GOLD));
            lines.add(FusionTooltipRenderer.buildEffectSummary(rev, form).copy().withStyle(ChatFormatting.GRAY));
        } else if (tab == FusionStationOperations.TAB_COMPENDIUM) {
            List<SignatureEntry> allSigs = SignaturePairs.allUniqueSignatures();
            int start = Math.max(0, Math.min(compendiumScroll, Math.max(0, allSigs.size() - 4)));
            for (int r = 0; r < 4; r++) {
                int idx = start + r;
                if (idx >= allSigs.size()) break;
                int ry = 57 + r * 17;
                if (isHoveringRect(RIGHT_X, ry, RIGHT_W, 16, mouseX, mouseY)) {
                    SignatureEntry sig = allSigs.get(idx);
                    lines.add(Component.translatable(sig.titleKey()).withStyle(ChatFormatting.GOLD));
                    lines.add(Component.translatable(sig.descKey()).withStyle(ChatFormatting.AQUA));
                    break;
                }
            }
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

    // =========================================================================
    // Mouse Clicks & Scroll Wheel
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int tab = this.menu.activeTab();
        if (tab == FusionStationOperations.TAB_COMPENDIUM && isHoveringRect(RIGHT_X, 56, RIGHT_W, 70, mouseX, mouseY)) {
            int max = Math.max(0, SignaturePairs.allUniqueSignatures().size() - 4);
            compendiumScroll = Math.max(0, Math.min(max, compendiumScroll + delta));
            return true;
        }
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
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        int tab = this.menu.activeTab();
        StationPreview preview = this.menu.computePreview();

        // 1. Top Folder Tab Clicks
        if (button == 0) {
            for (int t = 0; t < 3; t++) {
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

        // 3. Mode C Enchantment Picker Row Clicks (Left-Click = Primary A, Right-Click = Secondary B!)
        if ((tab == FusionStationOperations.TAB_FUSE || tab == FusionStationOperations.TAB_SOCKET)
            && (button == 0 || button == 1)) {
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

        // 4. Primary Execute Button (Tab 0 & Tab 1)
        if (button == 0 && (tab == FusionStationOperations.TAB_FUSE || tab == FusionStationOperations.TAB_SOCKET)
            && isHoveringRect(EXEC_BTN_X, EXEC_BTN_Y, EXEC_BTN_W, EXEC_BTN_H, mx, my)
            && preview.executable() && canAffordXp(preview.xpCost())) {
            sendAction(StationAction.EXECUTE_PRIMARY, 0, 0);
            playClickSound(1.0F);
            return true;
        }

        // 5. Tab 1 Socket Card Inline [⇄] Flip & [卸] Unsocket Buttons
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

        // 6. Tab 2 Fused Book [Flip] & [Split] Buttons
        if (button == 0 && tab == FusionStationOperations.TAB_COMPENDIUM) {
            ItemStack manageable = FusionStationOperations.resolveManageableStack(this.menu.stationContainer());
            FusedEnchantmentList list = manageable.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
            if (!list.isEmpty() && !list.pairs().get(0).isAberrant() && canAffordXp(preview.manageXpCost())) {
                if (isHoveringRect(TAB2_FLIP_X, TAB2_FLIP_Y, TAB2_FLIP_W, TAB2_FLIP_H, mx, my)) {
                    sendAction(StationAction.FLIP_SOCKET, 0, 0);
                    playClickSound(1.1F);
                    return true;
                }
                if (isHoveringRect(TAB2_SPLIT_X, TAB2_SPLIT_Y, TAB2_SPLIT_W, TAB2_SPLIT_H, mx, my)) {
                    sendAction(StationAction.UNSOCKET_OR_SPLIT, 0, 0);
                    playClickSound(0.95F);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    private static int tabX(int tabIndex) {
        return switch (tabIndex) {
            case 0 -> TAB0_X;
            case 1 -> TAB1_X;
            default -> TAB2_X;
        };
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
