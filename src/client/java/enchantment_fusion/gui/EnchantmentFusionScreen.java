package enchantment_fusion.gui;

import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.FusionStationOperations.OperationKind;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Custom pixel-art GUI screen for the Enchantment Fusion Table (276 x 198).
 * Features the interactive {@code [⇄]} Swap-Order button with instant live matrix preview,
 * Vessel Spirit diamond indicators, and Fuse / Flip / Split workstation controls.
 */
public final class EnchantmentFusionScreen extends AbstractContainerScreen<EnchantmentFusionMenu> {
    private static final int SWAP_BTN_X = 48;
    private static final int SWAP_BTN_Y = 31;
    private static final int SWAP_BTN_W = 22;
    private static final int SWAP_BTN_H = 22;

    private static final int FUSE_BTN_X = 122;
    private static final int FUSE_BTN_Y = 88;
    private static final int FUSE_BTN_W = 76;
    private static final int FUSE_BTN_H = 20;

    private static final int FLIP_BTN_X = 201;
    private static final int FLIP_BTN_Y = 89;
    private static final int FLIP_BTN_W = 30;
    private static final int FLIP_BTN_H = 18;

    private static final int SPLIT_BTN_X = 233;
    private static final int SPLIT_BTN_Y = 89;
    private static final int SPLIT_BTN_W = 30;
    private static final int SPLIT_BTN_H = 18;

    private static final int PREVIEW_X = 122;
    private static final int PREVIEW_Y = 24;
    private static final int PREVIEW_W = 141;
    private static final int PREVIEW_H = 60;

    public EnchantmentFusionScreen(EnchantmentFusionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, EnchantmentFusionMenu.GUI_WIDTH, EnchantmentFusionMenu.GUI_HEIGHT);
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.inventoryLabelX = 58;
        this.inventoryLabelY = 106;
    }

    public Rect2i guiBounds() {
        return new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // Rendered with bounded scaling in extractBackground so long localized titles never overflow.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int left = this.leftPos;
        int top = this.topPos;

        // 1. Main ornate stone & amethyst panel (276 x 198)
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.PANEL_BG, left, top, this.imageWidth, this.imageHeight);

        // 2. Title header
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, this.title.getString(),
            left + 16, this.imageWidth - 32, top + 12.0F, 0.85F, FusionGuiSkin.COLOR_GOLD
        );

        // 3. Workstation 4 slot frames (20x20 centered around 16x16 item slots)
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_FRAME, left + 20, top + 32, 20, 20);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_FRAME, left + 84, top + 32, 20, 20);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_FRAME, left + 52, top + 60, 20, 20);
        FusionGuiSkin.drawSprite(ctx, FusionGuiSkin.SLOT_FRAME, left + 52, top + 90, 20, 20);

        // Slot role badges ("A", "B")
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.slot.primary_short").getString(),
            left + 16, 28, top + 26.5F, 0.65F, FusionGuiSkin.COLOR_GOLD
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.slot.secondary_short").getString(),
            left + 80, 28, top + 26.5F, 0.65F, FusionGuiSkin.COLOR_CYAN
        );

        // 4. Player inventory 36 slot frames (18x18)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                FusionGuiSkin.drawSprite(
                    ctx, FusionGuiSkin.SLOT_FRAME,
                    left + EnchantmentFusionMenu.PLAYER_INV_X - 1 + col * 18,
                    top + EnchantmentFusionMenu.PLAYER_INV_Y - 1 + row * 18,
                    18, 18
                );
            }
        }
        for (int col = 0; col < 9; col++) {
            FusionGuiSkin.drawSprite(
                ctx, FusionGuiSkin.SLOT_FRAME,
                left + EnchantmentFusionMenu.PLAYER_INV_X - 1 + col * 18,
                top + EnchantmentFusionMenu.PLAYER_INV_Y + 57,
                18, 18
            );
        }

        // 5. Vessel Spirit diamonds next to Target Slot (Slot 3)
        renderVesselDiamonds(ctx, left + 77, top + 94);

        // 6. Interactive buttons (Swap [⇄], Fuse, Flip, Split)
        StationPreview preview = this.menu.computePreview();
        boolean overSwap = isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawSprite(
            ctx,
            overSwap ? FusionGuiSkin.BTN_SWAP_HOVER : FusionGuiSkin.BTN_SWAP_NORMAL,
            left + SWAP_BTN_X, top + SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, "\u21C4",
            left + SWAP_BTN_X + 2, SWAP_BTN_W - 4, top + SWAP_BTN_Y + 11.0F, 0.85F, FusionGuiSkin.COLOR_GOLD
        );

        boolean canAffordPrimary = canAffordXp(preview.xpCost());
        boolean primaryReady = preview.executable() && canAffordPrimary;
        boolean overFuse = isHoveringRect(FUSE_BTN_X, FUSE_BTN_Y, FUSE_BTN_W, FUSE_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawSprite(
            ctx,
            !primaryReady ? FusionGuiSkin.BTN_FUSE_DISABLED : (overFuse ? FusionGuiSkin.BTN_FUSE_HOVER : FusionGuiSkin.BTN_FUSE_NORMAL),
            left + FUSE_BTN_X, top + FUSE_BTN_Y, FUSE_BTN_W, FUSE_BTN_H
        );
        String fuseBtnLabel = Component.translatable(primaryActionLabelKey(preview.kind())).getString();
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, fuseBtnLabel,
            left + FUSE_BTN_X + 5, FUSE_BTN_W - 10, top + FUSE_BTN_Y + 10.0F,
            0.72F, primaryReady ? FusionGuiSkin.COLOR_CREAM : FusionGuiSkin.COLOR_MUTED
        );

        boolean flipReady = preview.canFlipTarget() && canAffordXp(preview.flipOrSplitXpCost());
        boolean overFlip = isHoveringRect(FLIP_BTN_X, FLIP_BTN_Y, FLIP_BTN_W, FLIP_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawSprite(
            ctx,
            !flipReady ? FusionGuiSkin.BTN_SMALL_DISABLED : (overFlip ? FusionGuiSkin.BTN_SMALL_HOVER : FusionGuiSkin.BTN_SMALL_NORMAL),
            left + FLIP_BTN_X, top + FLIP_BTN_Y, FLIP_BTN_W, FLIP_BTN_H
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.flip").getString(),
            left + FLIP_BTN_X + 3, FLIP_BTN_W - 6, top + FLIP_BTN_Y + 9.0F,
            0.64F, flipReady ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_MUTED
        );

        boolean splitReady = preview.canSplitTarget() && canAffordXp(preview.flipOrSplitXpCost());
        boolean overSplit = isHoveringRect(SPLIT_BTN_X, SPLIT_BTN_Y, SPLIT_BTN_W, SPLIT_BTN_H, mouseX, mouseY);
        FusionGuiSkin.drawSprite(
            ctx,
            !splitReady ? FusionGuiSkin.BTN_SMALL_DISABLED : (overSplit ? FusionGuiSkin.BTN_SMALL_HOVER : FusionGuiSkin.BTN_SMALL_NORMAL),
            left + SPLIT_BTN_X, top + SPLIT_BTN_Y, SPLIT_BTN_W, SPLIT_BTN_H
        );
        FusionGuiSkin.drawCenteredBoundedText(
            ctx, this.font, Component.translatable("gui.enchantment_fusion.btn.split").getString(),
            left + SPLIT_BTN_X + 3, SPLIT_BTN_W - 6, top + SPLIT_BTN_Y + 9.0F,
            0.64F, splitReady ? FusionGuiSkin.COLOR_GOLD : FusionGuiSkin.COLOR_MUTED
        );

        // 7. Scissor-clipped Live Resonance Preview Readout
        renderPreviewPanel(ctx, preview, left + PREVIEW_X, top + PREVIEW_Y, PREVIEW_W, PREVIEW_H);
    }

    private void renderVesselDiamonds(GuiGraphicsExtractor ctx, int startX, int y) {
        ItemStack target = this.menu.stationContainer().getItem(3);
        if (!VesselCapacityRules.isPotentialEquipmentHost(target)) {
            return;
        }
        int maxSlots = VesselCapacityRules.maxVesselSlots(target);
        FusedEnchantmentList list = target.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (int i = 0; i < maxSlots; i++) {
            var sprite = FusionGuiSkin.VESSEL_EMPTY;
            if (i < list.size()) {
                sprite = list.pairs().get(i).isAberrant()
                    ? FusionGuiSkin.VESSEL_ABERRANT
                    : FusionGuiSkin.VESSEL_FILLED;
            }
            FusionGuiSkin.drawSprite(ctx, sprite, startX + i * 13, y, 12, 12);
        }
    }

    private void renderPreviewPanel(
        GuiGraphicsExtractor ctx,
        StationPreview preview,
        int px,
        int py,
        int pw,
        int ph
    ) {
        ctx.enableScissor(px + 2, py + 2, px + pw - 2, py + ph - 2);
        try {
            int innerX = px + 5;
            int innerW = pw - 10;
            var registries = this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.registryAccess()
                : null;
            ItemStack hostStack = this.menu.stationContainer().getItem(3);
            HostForm hostForm = HostForm.resolve(hostStack);

            if (preview.kind() == OperationKind.NONE) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.idle_title").getString(),
                    innerX, py + 6, innerW, 0.72F, FusionGuiSkin.COLOR_GOLD
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.idle_line1").getString(),
                    innerX, py + 20, innerW, 0.62F, FusionGuiSkin.COLOR_CREAM
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.idle_line2").getString(),
                    innerX, py + 32, innerW, 0.60F, FusionGuiSkin.COLOR_MUTED
                );
                if (preview.canFlipTarget() || preview.canSplitTarget()) {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.preview.flip_split_ready", preview.flipOrSplitXpCost()).getString(),
                        innerX, py + 45, innerW, 0.62F, FusionGuiSkin.COLOR_CYAN
                    );
                }
                return;
            }

            if (preview.kind() == OperationKind.INVALID) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.blocked").getString(),
                    innerX, py + 7, innerW, 0.72F, FusionGuiSkin.COLOR_RED
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable(rejectKey(preview.rejectReason())).getString(),
                    innerX, py + 22, innerW, 0.62F, FusionGuiSkin.COLOR_WARN
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.try_swap_hint").getString(),
                    innerX, py + 38, innerW, 0.58F, FusionGuiSkin.COLOR_MUTED
                );
                return;
            }

            if (preview.kind() == OperationKind.EXPAND_VESSEL) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.expand_title").getString(),
                    innerX, py + 7, innerW, 0.72F, FusionGuiSkin.COLOR_GOLD
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.expand_desc").getString(),
                    innerX, py + 22, innerW, 0.62F, FusionGuiSkin.COLOR_CYAN
                );
                drawXpLine(ctx, preview.xpCost(), innerX, py + 44, innerW);
                return;
            }

            if (preview.kind() == OperationKind.TOP_UP_LEVEL) {
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.topup_title").getString(),
                    innerX, py + 7, innerW, 0.72F, FusionGuiSkin.COLOR_GREEN
                );
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font,
                    Component.translatable("gui.enchantment_fusion.preview.topup_desc").getString(),
                    innerX, py + 22, innerW, 0.62F, FusionGuiSkin.COLOR_CREAM
                );
                drawXpLine(ctx, preview.xpCost(), innerX, py + 44, innerW);
                return;
            }

            FusedPair pair = preview.previewPair();
            if (pair != null) {
                String pName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, pair.primaryId(), pair.primaryLevel()).getString();
                String sName = FusionTooltipRenderer.formatEnchantmentWithLevel(registries, pair.secondaryId(), pair.secondaryLevel()).getString();
                String header = Component.translatable("tooltip.enchantment_fusion.pair_header", pName, sName).getString();
                FusionGuiSkin.drawLeftBoundedText(ctx, this.font, header, innerX, py + 5, innerW, 0.68F, FusionGuiSkin.COLOR_AMETHYST);

                boolean discovered = FusionTooltipRenderer.shouldShowUnlockedPreview(pair);
                String effectLine = discovered
                    ? FusionTooltipRenderer.buildMatrixSentence(pair, hostForm).getString()
                    : FusionTooltipRenderer.buildClueSentence(pair).getString();
                FusionGuiSkin.drawLeftBoundedText(
                    ctx, this.font, effectLine,
                    innerX, py + 19, innerW, 0.60F,
                    discovered ? FusionGuiSkin.COLOR_CYAN : FusionGuiSkin.COLOR_GOLD
                );

                if (preview.repulsive()) {
                    int pct = (int) Math.round((1.0 - preview.instabilityChance()) * 100.0);
                    String stabStr = Component.translatable(
                        "gui.enchantment_fusion.preview.repulsive_stability",
                        String.format(Locale.ROOT, "%d%%", pct)
                    ).getString();
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font, stabStr,
                        innerX, py + 32, innerW, 0.58F,
                        preview.instabilityChance() > 0.01 ? FusionGuiSkin.COLOR_WARN : FusionGuiSkin.COLOR_GREEN
                    );
                } else {
                    FusionGuiSkin.drawLeftBoundedText(
                        ctx, this.font,
                        Component.translatable("gui.enchantment_fusion.preview.harmonic_stable").getString(),
                        innerX, py + 32, innerW, 0.58F, FusionGuiSkin.COLOR_GREEN
                    );
                }
                drawXpLine(ctx, preview.xpCost(), innerX, py + 45, innerW);
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private void drawXpLine(GuiGraphicsExtractor ctx, int xpCost, int x, int y, int maxW) {
        boolean affordable = canAffordXp(xpCost);
        String xpText = Component.translatable("gui.enchantment_fusion.preview.xp_cost", xpCost).getString();
        FusionGuiSkin.drawLeftBoundedText(
            ctx, this.font, xpText,
            x, y, maxW, 0.62F,
            affordable ? FusionGuiSkin.COLOR_GREEN : FusionGuiSkin.COLOR_RED
        );
    }

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

        if (isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.swap.desc").withStyle(ChatFormatting.GRAY));
        } else if (isHoveringRect(FLIP_BTN_X, FLIP_BTN_Y, FLIP_BTN_W, FLIP_BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.flip.title").withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.flip.desc").withStyle(ChatFormatting.GRAY));
        } else if (isHoveringRect(SPLIT_BTN_X, SPLIT_BTN_Y, SPLIT_BTN_W, SPLIT_BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.enchantment_fusion.tip.split.title").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.split.desc").withStyle(ChatFormatting.GRAY));
        } else if (isHoveringRect(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, mouseX, mouseY) && preview.previewPair() != null) {
            FusedPair p = preview.previewPair();
            HostForm form = HostForm.resolve(this.menu.stationContainer().getItem(3));
            lines.add(Component.translatable("gui.enchantment_fusion.tip.preview.title").withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(FusionTooltipRenderer.shouldShowUnlockedPreview(p)
                ? FusionTooltipRenderer.buildMatrixSentence(p, form).copy().withStyle(ChatFormatting.AQUA)
                : FusionTooltipRenderer.buildClueSentence(p).copy().withStyle(ChatFormatting.GOLD));
            FusedPair rev = p.flipped();
            lines.add(Component.translatable("gui.enchantment_fusion.tip.preview.reverse_hint").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(FusionTooltipRenderer.shouldShowUnlockedPreview(rev)
                ? FusionTooltipRenderer.buildMatrixSentence(rev, form).copy().withStyle(ChatFormatting.GRAY)
                : FusionTooltipRenderer.buildClueSentence(rev).copy().withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!lines.isEmpty()) {
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            int maxTipWidth = Math.max(150, Math.min(240, ctx.guiWidth() - 32));
            for (Component c : lines) {
                wrapped.addAll(this.font.split(c, maxTipWidth));
            }
            ctx.setTooltipForNextFrame(
                this.font, wrapped, Optional.empty(),
                DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, false, null
            );
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            StationPreview preview = this.menu.computePreview();

            if (isHoveringRect(SWAP_BTN_X, SWAP_BTN_Y, SWAP_BTN_W, SWAP_BTN_H, mx, my)) {
                sendStationAction(StationAction.SWAP_ORDER);
                playClickSound(1.15F);
                return true;
            }
            if (isHoveringRect(FUSE_BTN_X, FUSE_BTN_Y, FUSE_BTN_W, FUSE_BTN_H, mx, my)
                && preview.executable() && canAffordXp(preview.xpCost())) {
                sendStationAction(StationAction.EXECUTE_PRIMARY);
                playClickSound(1.0F);
                return true;
            }
            if (isHoveringRect(FLIP_BTN_X, FLIP_BTN_Y, FLIP_BTN_W, FLIP_BTN_H, mx, my)
                && preview.canFlipTarget() && canAffordXp(preview.flipOrSplitXpCost())) {
                sendStationAction(StationAction.EXECUTE_FLIP);
                playClickSound(1.1F);
                return true;
            }
            if (isHoveringRect(SPLIT_BTN_X, SPLIT_BTN_Y, SPLIT_BTN_W, SPLIT_BTN_H, mx, my)
                && preview.canSplitTarget() && canAffordXp(preview.flipOrSplitXpCost())) {
                sendStationAction(StationAction.EXECUTE_SPLIT);
                playClickSound(0.95F);
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void sendStationAction(StationAction action) {
        if (ClientPlayNetworking.canSend(StationActionPayload.TYPE)) {
            ClientPlayNetworking.send(new StationActionPayload(this.menu.containerId, action.ordinal()));
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
            case INSCRIBE_FUSED_BOOK -> "gui.enchantment_fusion.btn.inscribe";
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
