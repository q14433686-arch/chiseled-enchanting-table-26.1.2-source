package chiseled_enchanting_table.gui;

import static chiseled_enchanting_table.gui.TableSkin.*;

import static chiseled_enchanting_table.gui.ResonanceTooltip.formatClock;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.EnchantementListWidget.UiTabMode;
import chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen;
import chiseled_enchanting_table.gui.grimoire.ArcaneGrimoireScreen;
import chiseled_enchanting_table.gui.grimoire.GrimoireSoundHelper;
import chiseled_enchanting_table.gui.grimoire.GrimoireViewerBridge.HoveredSlot;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ReopenTablePayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableScreen extends AbstractContainerScreen<ChiseledEnchantingTableScreenHandler> {

    public static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/container/slot.png");
    private EnchantementListWidget scrollContainer;
    @Nullable
    private ArcaneGrimoireScreen grimoireOverlay;
    @Nullable
    private ChiseledEnchantingConfigScreen configOverlay;
    @Nullable
    private final BlockPos openedTablePos;
    private int lastBatchSignature = Integer.MIN_VALUE;
    private boolean lastRitualActive = false;
    private final chiseled_enchanting_table.config.ConfirmationWindow<ItemStack> disenchantConfirmation =
        new chiseled_enchanting_table.config.ConfirmationWindow<>(DISENCHANT_CONFIRM_WINDOW_MS,
            ItemStack::copy, (current, snapshot) -> !current.isEmpty() && ItemStack.isSameItemSameComponents(current, snapshot));
    private boolean lastConfirmPending = false;
    private boolean returningFromExternalScreen = false;
    private double savedScrollAmount = 0.0;
    private UiTabMode savedTabMode = UiTabMode.INSCRIBE;

    public int inventory_size;
    public int hotbar_size;

    public ChiseledEnchantingTableScreen(ChiseledEnchantingTableScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 340, 166);
        this.hotbar_size = Inventory.getSelectionSize();
        this.inventory_size = inventory.getContainerSize();
        Minecraft mc = Minecraft.getInstance();
        BlockPos menuPos = handler.tableBlockPos();
        this.openedTablePos = menuPos != null
            ? menuPos.immutable()
            : ((mc.hitResult instanceof BlockHitResult bhr) ? bhr.getBlockPos().immutable() : null);

        handler.onContentChangedCallback = () -> {
            if (this.scrollContainer != null) {
                if ((this.scrollContainer.tabMode() == UiTabMode.CLEANSE && !handler.disenchantEnabled())
                    || (this.scrollContainer.tabMode() == UiTabMode.TOMES && !handler.isTomePageEnabled())) {
                    this.scrollContainer.setTabMode(UiTabMode.INSCRIBE);
                }
                this.scrollContainer.updateEntry(handler.getEffectiveEnchantableItem(), handler.get_cost_item());
            }
        };
        this.scrollContainer = new EnchantmentListWidget(
            mc,
            handler,
            151,
            140,
            150,
            25
        );
    }

    @Nullable
    private BlockPos effectiveTablePos() {
        BlockPos menuPos = this.menu.tableBlockPos();
        return menuPos != null ? menuPos : this.openedTablePos;
    }

    @Override
    public void removed() {
        if (this.configOverlay != null) this.configOverlay.removed();
        if (this.scrollContainer != null) {
            this.savedScrollAmount = this.scrollContainer.scrollAmount();
            this.savedTabMode = this.scrollContainer.tabMode();
        }
        if (this.minecraft != null
            && this.minecraft.player != null
            && this.minecraft.player.containerMenu == this.menu) {
            // Container menu stayed open while Screen#removed() ran (e.g. JEI / REI viewer screen opened).
            this.returningFromExternalScreen = true;
        }
        super.removed();
    }

    private void restoreAfterExternalScreenReturn(boolean requestServerSync) {
        if (this.width > 0 && this.height > 0) {
            this.leftPos = (this.width - this.imageWidth) / 2;
            this.topPos = (this.height - this.imageHeight) / 2;
        }
        if (this.scrollContainer != null) {
            if (!this.children().contains(this.scrollContainer)) {
                this.addRenderableWidget(this.scrollContainer);
            }
            boolean wasReturning = this.returningFromExternalScreen;
            double restoreScroll = wasReturning ? this.savedScrollAmount : this.scrollContainer.scrollAmount();
            UiTabMode rawTab = wasReturning ? this.savedTabMode : this.scrollContainer.tabMode();
            UiTabMode targetTab = (rawTab == UiTabMode.CLEANSE && !this.menu.disenchantEnabled())
                || (rawTab == UiTabMode.TOMES && !this.menu.isTomePageEnabled())
                ? UiTabMode.INSCRIBE
                : rawTab;
            if (targetTab != null && this.scrollContainer.tabMode() != targetTab) {
                this.scrollContainer.setTabMode(targetTab);
            }
            layoutDirectory();
            this.scrollContainer.updateEntry(this.menu.getEffectiveEnchantableItem(), this.menu.get_cost_item());
            this.scrollContainer.setScrollAmount(Math.min(restoreScroll, this.scrollContainer.maxScrollAmount()));
        }
        BlockPos pos = effectiveTablePos();
        boolean menuDetached = this.minecraft != null
            && this.minecraft.player != null
            && this.minecraft.player.containerMenu != this.menu;
        if ((requestServerSync || menuDetached) && pos != null && ClientPlayNetworking.canSend(ReopenTablePayload.TYPE)) {
            ClientPlayNetworking.send(new ReopenTablePayload(pos));
        }
        this.returningFromExternalScreen = false;
    }

    @Override
    public boolean isPauseScreen() {
        return this.configOverlay != null && this.configOverlay.isPauseScreen();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        layoutDirectory();
        if (isTomeTab() && this.configOverlay == null && this.grimoireOverlay == null) tomeActions.tick(this.menu.tomeBooks, this::sendTomeAction);
        else { tomeActions.cancel(); tomeDirectory.close(); }
        if (this.configOverlay != null) {
            this.configOverlay.tick();
        }
        if (this.returningFromExternalScreen
            || (this.scrollContainer != null
                && isInscribeTab()
                && !this.scrollContainer.inscribeFilter.hasActiveFilter()
                && !this.menu.getEffectiveEnchantableItem().isEmpty()
                && this.scrollContainer.children().isEmpty())) {
            restoreAfterExternalScreenReturn(this.returningFromExternalScreen);
        }
        int sig = this.menu.transformationBatchSignature();
        boolean activeNow = this.menu.isRitualActive();
        boolean confirmNow = isDisenchantAllConfirmPending();
        if (sig != this.lastBatchSignature || activeNow != this.lastRitualActive || confirmNow != this.lastConfirmPending) {
            this.lastBatchSignature = sig;
            this.lastRitualActive = activeNow;
            this.lastConfirmPending = confirmNow;
            if (this.scrollContainer != null) {
                this.scrollContainer.updateEntry(this.menu.getEffectiveEnchantableItem(), this.menu.get_cost_item());
            }
        }
    }

    public static void notifyChannelUnavailableClient() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
        if (mc.gui != null) {
            mc.gui.hud.setOverlayMessage(
                Component.translatable("gui.chiseled_enchanting_table.network.channel_unavailable")
                    .withStyle(ChatFormatting.RED),
                false
            );
        }
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendServerboundOrWarn(
        T payload,
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type
    ) {
        if (ClientPlayNetworking.canSend(type)) {
            ClientPlayNetworking.send(payload);
        } else {
            notifyChannelUnavailableClient();
        }
    }

    public static boolean requiresDisenchantAllConfirm(ItemStack target) { return TableCleansePresentation.requiresDisenchantAllConfirm(target); }

    public boolean isDisenchantAllConfirmPending() {
        return this.disenchantConfirmation.pending(this.menu.get_enchantable_item(), net.minecraft.util.Util.getMillis());
    }

    public void requestDisenchantAll(boolean shiftBypass) {
        ItemStack target = this.menu.get_enchantable_item();
        if (!shiftBypass && requiresDisenchantAllConfirm(target) && !isDisenchantAllConfirmPending()) {
            this.disenchantConfirmation.arm(target, net.minecraft.util.Util.getMillis());
            this.lastConfirmPending = true;
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.85F));
            if (mc.gui != null) {
                mc.gui.hud.setOverlayMessage(
                    Component.translatable("gui.chiseled_enchanting_table.cleanse.confirm_prompt")
                        .withStyle(ChatFormatting.GOLD),
                    false
                );
            }
            if (this.scrollContainer != null) {
                this.scrollContainer.updateEntry(this.menu.getEffectiveEnchantableItem(), this.menu.get_cost_item());
            }
            return;
        }
        this.disenchantConfirmation.clear();
        this.lastConfirmPending = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
        sendServerboundOrWarn(new ApplyEnchantmentPayload(this.menu.containerId, OptionType.DISENCHANT_ALL,
            DisenchantmentApplication.DISENCHANT_ALL_ID, 0), ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID);
    }

    private boolean inGuiRect(double mx, double my, int rx, int ry, int rw, int rh) {
        int x0 = this.leftPos + rx, y0 = this.topPos + ry;
        return mx >= x0 && mx < x0 + rw && my >= y0 && my < y0 + rh;
    }

    private boolean shouldShowShelfHudBadge() {
        return !this.menu.usesClassicShelfBoost() || this.menu.isLibraryDiscountEnabled();
    }

    private boolean shouldShowSanctumPanel() {
        return this.menu.isRitualActive() || !isInscribeTab() || this.menu.isEpiphanyEnabled() || this.menu.isLibraryDiscountEnabled();
    }

    private boolean isOverShelfHudBadge(double mouseX, double mouseY) {
        return shouldShowShelfHudBadge() && inGuiRect(mouseX, mouseY, SHELF_HUD_X, SHELF_HUD_Y, SHELF_HUD_W, SHELF_HUD_H);
    }

    private boolean isCleanseTab() { return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.CLEANSE; }
    private boolean isInscribeTab() { return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.INSCRIBE; }
    private boolean isTomeTab() { return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.TOMES; }

    private void syncAndInitOverlays() {
        boolean shouldSync = this.returningFromExternalScreen
            || (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.containerMenu != this.menu);
        restoreAfterExternalScreenReturn(shouldSync);
        if (this.grimoireOverlay != null) this.grimoireOverlay.initOverlay(this.minecraft, this.width, this.height);
        if (this.configOverlay != null) this.configOverlay.initOverlay(this.minecraft, this.width, this.height);
    }

    @Override
    protected void init() { super.init(); GuiMicroEffects.resetModeTracking(); syncAndInitOverlays(); }

    @Override
    protected void repositionElements() { super.repositionElements(); syncAndInitOverlays(); }

    private boolean isOverInscribeTab(double mouseX, double mouseY) {
        return this.menu.hasSecondaryTabs() && inGuiRect(mouseX, mouseY, TAB_INSCRIBE_X, TAB_Y_OFFSET, TAB_W, TAB_H - 2);
    }

    private boolean isOverTomeTab(double mouseX, double mouseY) {
        return this.menu.isTomePageEnabled() && inGuiRect(mouseX, mouseY, TAB_TOMES_X, TAB_Y_OFFSET, TAB_W, TAB_H - 2);
    }

    private boolean isOverSacrificeButton(double mouseX, double mouseY) {
        if ((!this.menu.disenchantEnabled() && !this.menu.isTomePageEnabled())
            || (this.menu.disenchantEnabled() && this.menu.isCleansePageUnlocked())) return false;
        return inGuiRect(mouseX, mouseY, SACRIFICE_BTN_X, TAB_Y_OFFSET, SACRIFICE_BTN_W, TAB_H - 2);
    }

    private int[] tomeButtonRect(int index) {
        var r=TomeDirectoryLayout.lockButton(index);
        return new int[]{r.x(),r.y(),r.width(),r.height()};
    }

    private int hoveredTomeButton(double mouseX, double mouseY) {
        if (!isTomeTab() || !this.menu.isTomePageUnlocked()) return -1;
        for (int i = 0; i < TomeDirectoryLayout.TOME_BUTTON_COUNT; i++) {
            int[] r = tomeButtonRect(i);
            int x0 = this.leftPos + r[0], y0 = this.topPos + r[1];
            if (mouseX >= x0 && mouseX < x0 + r[2] && mouseY >= y0 && mouseY < y0 + r[3]) return i;
        }
        return -1;
    }

    private int hoveredRitualButton(double mouseX, double mouseY) {
        if (!isInscribeTab() || !this.menu.isRitualActive()) return -1;
        for (int i = 0; i < TomeDirectoryLayout.RITUAL_BUTTON_COUNT; i++) {
            var r = TomeDirectoryLayout.ritualButton(i);
            int x0 = this.leftPos + r.x(), y0 = this.topPos + r.y();
            if (mouseX >= x0 && mouseX < x0 + r.width() && mouseY >= y0 && mouseY < y0 + r.height()) return i;
        }
        return -1;
    }

    private final TomeDirectoryControls tomeDirectory = new TomeDirectoryControls();
    private final chiseled_enchanting_table.catalog.TomeActionQueue tomeActions = new chiseled_enchanting_table.catalog.TomeActionQueue();
    void cancelPendingTomeActions() { tomeActions.cancel(); }
    private void layoutDirectory() {
        int offset = isTomeTab() ? TomeDirectoryLayout.listOffset()
            : (isInscribeTab() ? TomeDirectoryLayout.inscribeListOffset()
                : (isCleanseTab() ? TomeDirectoryLayout.cleanseListOffset() : 0));
        if (this.scrollContainer != null) this.scrollContainer.updateSizeAndPosition(TomeDirectoryLayout.WIDTH, TomeDirectoryLayout.CONTENT_HEIGHT-offset,
            this.leftPos + TomeDirectoryLayout.CONTENT_X, this.topPos + TomeDirectoryLayout.CONTENT_Y+offset);
    }

    private void sendTomeAction(
        chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action action,
        chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry entry
    ) {
        sendServerboundOrWarn(new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload(
            this.menu.containerId, action, entry == null ? net.minecraft.core.BlockPos.ZERO : entry.shelf_pos(), entry == null ? -1 : entry.slot()),
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.TYPE);
    }

    private void sendLocateBatch(List<chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry> batch) {
        var first = batch.getFirst();
        var extras = batch.stream().skip(1).map(b -> new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeSlotRef(b.shelf_pos(), b.slot())).toList();
        sendServerboundOrWarn(new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload(
            this.menu.containerId, chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCATE_PING, first.shelf_pos(), first.slot(), extras),
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.TYPE);
    }

    private boolean isOverCleanseTab(double mouseX, double mouseY) {
        if (!this.menu.disenchantEnabled()) return false;
        int x0 = this.leftPos + TAB_CLEANSE_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    public void openConfigFromGrimoire() {
        this.grimoireOverlay = null;
        this.configOverlay = new ChiseledEnchantingConfigScreen(this, () -> {
            this.configOverlay = null;
            if (this.scrollContainer != null) {
                this.scrollContainer.updateEntry(this.menu.getEffectiveEnchantableItem(), this.menu.get_cost_item());
            }
        });
        this.configOverlay.initOverlay(this.minecraft, this.width, this.height);
    }

    private boolean isOverConfigButton(double mouseX, double mouseY) {
        int x0 = this.leftPos + CONFIG_BTN_X;
        int y0 = this.topPos + CONFIG_BTN_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + CONFIG_BTN_SIZE && mouseY >= y0 && mouseY < y0 + CONFIG_BTN_SIZE;
    }

    private boolean isOverGrimoireButton(double mouseX, double mouseY) {
        int x0 = this.leftPos + GRIMOIRE_BTN_X;
        int y0 = this.topPos + GRIMOIRE_BTN_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + GRIMOIRE_BTN_SIZE && mouseY >= y0 && mouseY < y0 + GRIMOIRE_BTN_SIZE;
    }

    public boolean hasActiveConfigOverlay() {
        return this.configOverlay != null;
    }

    public net.minecraft.client.renderer.Rect2i viewerMainGuiBounds() {
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.fullGuiBounds();
        }
        return new net.minecraft.client.renderer.Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    public List<net.minecraft.client.renderer.Rect2i> extraViewerExclusionAreas() {
        if (this.configOverlay != null) {
            return List.of(new net.minecraft.client.renderer.Rect2i(
                0,
                0,
                Math.max(1, this.width),
                Math.max(1, this.height)
            ));
        }
        List<net.minecraft.client.renderer.Rect2i> areas = new ArrayList<>(5);
        if (this.menu.hasSecondaryTabs()) {
            int topBarRight = (this.menu.disenchantEnabled() && !this.menu.isCleansePageUnlocked())
                ? (SACRIFICE_BTN_X + SACRIFICE_BTN_W)
                : (TAB_TOMES_X + TAB_W);
            areas.add(new net.minecraft.client.renderer.Rect2i(
                this.leftPos + TAB_INSCRIBE_X, this.topPos + TAB_Y_OFFSET, topBarRight - TAB_INSCRIBE_X, TAB_H));
        }
        areas.add(new net.minecraft.client.renderer.Rect2i(
            this.leftPos + CONFIG_BTN_X,
            this.topPos + CONFIG_BTN_Y_OFFSET,
            CONFIG_BTN_SIZE,
            CONFIG_BTN_SIZE
        ));
        areas.add(new net.minecraft.client.renderer.Rect2i(
            this.leftPos + GRIMOIRE_BTN_X,
            this.topPos + GRIMOIRE_BTN_Y_OFFSET,
            GRIMOIRE_BTN_SIZE,
            GRIMOIRE_BTN_SIZE
        ));
        if (this.configOverlay != null) {
            areas.add(this.configOverlay.fullGuiBounds());
        }
        if (this.grimoireOverlay != null) {
            areas.add(this.grimoireOverlay.fullGuiBounds());
        }
        return areas;
    }

    @Nullable
    public HoveredSlot hoveredCostIngredient(double mouseX, double mouseY) {
        if (this.configOverlay != null) {
            return null;
        }
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.hoveredIngredient(mouseX, mouseY);
        }
        if (this.scrollContainer == null) return null;
        ItemStack stack = this.scrollContainer.hoveredCostItem(mouseX, mouseY);
        if (stack == null || stack.isEmpty()) return null;
        return new HoveredSlot(
            stack,
            new net.minecraft.client.renderer.Rect2i((int) mouseX - 6, (int) mouseY - 6, 12, 12),
            false
        );
    }

    private boolean isOverEnchantList(double mouseX, double mouseY) {
        return this.scrollContainer != null
            && mouseX >= this.scrollContainer.getX()
            && mouseX <= this.scrollContainer.getX() + this.scrollContainer.getWidth()
            && mouseY >= this.scrollContainer.getY()
            && mouseY <= this.scrollContainer.getY() + this.scrollContainer.getHeight();
    }

    private boolean isOverEnchantListScrollbar(double mouseX, double mouseY) {
        if (this.scrollContainer == null) return false;
        int scrollbarLeft = this.scrollContainer.getX() + this.scrollContainer.getWidth() - 2;
        int scrollbarRight = this.scrollContainer.getX() + this.scrollContainer.getWidth() + 9;
        return mouseX >= scrollbarLeft
            && mouseX <= scrollbarRight
            && mouseY >= this.scrollContainer.getY()
            && mouseY <= this.scrollContainer.getY() + this.scrollContainer.getHeight();
    }

    private boolean isOverFreezeButton(double mouseX, double mouseY) {
        return this.menu.isEpiphanyEnabled() && isInscribeTab() && !this.menu.isRitualActive()
            && inGuiRect(mouseX, mouseY, FREEZE_BTN_X, FREEZE_BTN_Y, BTN_SIZE, BTN_SIZE);
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        return this.menu.isEpiphanyEnabled() && isInscribeTab() && !this.menu.isRitualActive()
            && inGuiRect(mouseX, mouseY, REROLL_BTN_X, REROLL_BTN_Y, BTN_SIZE, BTN_SIZE);
    }

    private boolean isOverCleanseAllButton(double mouseX, double mouseY) {
        return isCleanseTab() && inGuiRect(mouseX, mouseY, CLEANSE_ALL_BTN_X, CLEANSE_ALL_BTN_Y, BTN_SIZE, BTN_SIZE);
    }

    private boolean isOverPurgeCursesButton(double mouseX, double mouseY) {
        return isCleanseTab() && this.menu.disenchantAllowCursePurge()
            && inGuiRect(mouseX, mouseY, PURGE_CURSES_BTN_X, PURGE_CURSES_BTN_Y, BTN_SIZE, BTN_SIZE);
    }

    private boolean canCleanseAllNow() {
        return this.menu.disenchantEnabled()
            && !this.menu.isRitualActive()
            && DisenchantmentApplication.countNonCurseEnchantments(this.menu.get_enchantable_item()) > 0;
    }

    private boolean canPurgeCursesNow() {
        return chiseled_enchanting_table.mechanics.enchanting.CleanseMechanics.canPurgeAnyCurseNow(this.menu);
    }

    private boolean isOverSanctumPanel(double mouseX, double mouseY) {
        if (!shouldShowSanctumPanel()) return false;
        int x0 = this.leftPos + PANEL_X;
        int y0 = this.topPos + PANEL_Y;
        return mouseX >= x0 && mouseX < x0 + PANEL_W && mouseY >= y0 && mouseY < y0 + PANEL_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.configOverlay != null) {
            return this.configOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (isTomeTab() && tomeDirectory.scroll(scrollY)) return true;
        if (isTomeTab() && TooltipPresentation.detailsRequested()) {
            var book = this.scrollContainer.hoveredTomeEntry(mouseX, mouseY);
            if (book != null) { TomeEnchantmentDetails.scroll(book, scrollY); return true; }
        }
        if (isOverEnchantList(mouseX, mouseY) || isOverEnchantListScrollbar(mouseX, mouseY)) {
            return this.scrollContainer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (this.configOverlay != null) {
            this.configOverlay.mouseClicked(event, isDoubleClick);
            return true;
        }
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.mouseClicked(event, isDoubleClick);
            return true;
        }
        if (isInscribeTab() && this.scrollContainer != null && this.scrollContainer.inscribeFilter.mouseClicked(
            event.x(), event.y(), event.button(), this.leftPos + TomeDirectoryLayout.CONTENT_X,
            this.topPos + TomeDirectoryLayout.CONTENT_Y, this.scrollContainer::refreshInscribeFilter,
            this.scrollContainer::toggleExpandAllInscribeCategories)) {
            return true;
        }
        if (isCleanseTab() && this.scrollContainer != null && this.scrollContainer.cleanseFilter.mouseClicked(
            event.x(), event.y(), event.button(), this.leftPos + TomeDirectoryLayout.CONTENT_X,
            this.topPos + TomeDirectoryLayout.CONTENT_Y, this.scrollContainer::refreshInscribeFilter,
            this.scrollContainer::toggleExpandAllCleanseCategories)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && isInscribeTab() && this.menu.isTomePageEnabled() && this.scrollContainer != null) {
            Identifier jumpId = this.scrollContainer.hoveredMasterBackedInscribeId(event.x(), event.y());
            if (jumpId != null) {
                if (!this.menu.isTomePageUnlocked()) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                    if (this.minecraft != null && this.minecraft.gui != null) {
                        this.minecraft.gui.hud.setOverlayMessage(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(ChatFormatting.YELLOW), false);
                    }
                } else {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.08F));
                    tomeActions.cancel(); tomeDirectory.close();
                    this.scrollContainer.jumpToTomesForEnchantment(jumpId); layoutDirectory();
                }
                return true;
            }
        }
        // Restrict custom actions to the platform left button, but retain vanilla
        // inventory right-click handling. Overlay events above remain fully forwarded.
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, isDoubleClick);
        }
        if (isOverConfigButton(event.x(), event.y())) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.configOverlay = new ChiseledEnchantingConfigScreen(this, () -> {
                this.configOverlay = null;
                if (this.scrollContainer != null) this.scrollContainer.updateEntry(this.menu.getEffectiveEnchantableItem(), this.menu.get_cost_item());
            });
            this.configOverlay.initOverlay(this.minecraft, this.width, this.height);
            return true;
        }

        if (isOverGrimoireButton(event.x(), event.y())) {
            GrimoireSoundHelper.playOpen();
            this.grimoireOverlay = new ArcaneGrimoireScreen(
                this,
                0,
                () -> this.grimoireOverlay = null,
                true,
                this.menu.regularBookshelves
            );
            this.grimoireOverlay.initOverlay(this.minecraft, this.width, this.height);
            return true;
        }
        if (isOverSacrificeButton(event.x(), event.y())) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
            sendTomeAction(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.SACRIFICE_DEPLETED, null);
            return true;
        }
        if (isOverInscribeTab(event.x(), event.y())) {
            if (!isInscribeTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.scrollContainer.setTabMode(UiTabMode.INSCRIBE); layoutDirectory();
            }
            return true;
        }
        if (isOverCleanseTab(event.x(), event.y())) {
            if (!this.menu.isCleansePageUnlocked()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            } else if (!isCleanseTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                this.scrollContainer.setTabMode(UiTabMode.CLEANSE); layoutDirectory();
            }
            return true;
        }
        if (isOverTomeTab(event.x(), event.y())) {
            if (!this.menu.isTomePageUnlocked()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            } else if (!isTomeTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.95F));
                this.scrollContainer.setTabMode(UiTabMode.TOMES); layoutDirectory();
            }
            return true;
        }
        if (isTomeTab() && event.button() == InputConstants.MOUSE_BUTTON_LEFT
            && tomeDirectory.click(event.x(), event.y(), this.leftPos+TomeDirectoryLayout.CONTENT_X, this.topPos+TomeDirectoryLayout.CONTENT_Y,
                this.scrollContainer.catalogue, this.menu.player, this.scrollContainer::refreshDirectory)) {
            tomeActions.cancel(); return true;
        }
        int tomeButton = hoveredTomeButton(event.x(), event.y());
        if (tomeButton >= 0) {
            var explicit = this.scrollContainer.selectedTomes();
            if (tomeButton == 4 && explicit.isEmpty()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                sendTomeAction(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL, null);
                return true;
            }
            var action = switch (tomeButton) {
                case 0 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_NEXT;
                case 1, 4 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_FULL;
                case 2 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_PERMANENT;
                case 3 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK;
                case 5 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.MEND_ONE;
                case 6 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.SANCTIFY_CATALYST;
                default -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCATE_PING;
            };
            var targets = explicit;
            if (targets.isEmpty() && this.scrollContainer.selectedTomeEntry() != null) {
                targets = java.util.List.of(this.scrollContainer.selectedTomeEntry());
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (tomeActions.submit(action, targets) && action == chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCATE_PING) {
                var batch = tomeActions.drainPendingForImmediateBatch(action);
                if (!batch.isEmpty()) sendLocateBatch(batch);
            }
            return true;
        }
        int ritualBtn = hoveredRitualButton(event.x(), event.y());
        if (ritualBtn >= 0) {
            var skipAction = switch (ritualBtn) {
                case 0 -> this.menu.canSacrificeSkipNow() ? chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.SACRIFICE_XP : null;
                case 1 -> {
                    boolean preferCatalyst = event.hasShiftDown()
                        || this.menu.get_cost_item().is(chiseled_enchanting_table.registry.BlockRegistry.ARCANE_CATALYST)
                        || !this.menu.canFuelBoostNow();
                    if (preferCatalyst && this.menu.canCatalystInstantNow())
                        yield chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.CATALYST_INSTANT;
                    yield this.menu.canFuelBoostNow() ? chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.FUEL_BOOST : null;
                }
                case 2 -> this.menu.canRushSkipNow() ? chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.FORCE_RUSH : null;
                default -> this.menu.canSafeAbortNow() ? chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.Action.SAFE_ABORT : null;
            };
            if (skipAction != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                sendServerboundOrWarn(new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload(
                    this.menu.containerId, this.menu.ritualSession(), skipAction),
                    chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE);
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverPurgeCursesButton(event.x(), event.y())) {
            if (canPurgeCursesNow()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                sendServerboundOrWarn(new ApplyEnchantmentPayload(this.menu.containerId,
                    OptionType.PURGE_ALL_CURSES, DisenchantmentApplication.DISENCHANT_ALL_ID, 0),
                    ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID);
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverCleanseAllButton(event.x(), event.y())) {
            if (canCleanseAllNow()) {
                requestDisenchantAll(event.hasShiftDown());
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverFreezeButton(event.x(), event.y())) {
            if (this.menu.canAffordFreezeEpiphany()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                sendServerboundOrWarn(new FreezeEpiphanyPayload(this.menu.containerId), FreezeEpiphanyPayload.TYPE);
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverRerollButton(event.x(), event.y())) {
            if (this.menu.canAffordRerollEpiphany()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                sendServerboundOrWarn(new RerollEpiphanyPayload(this.menu.containerId), RerollEpiphanyPayload.TYPE);
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverEnchantList(event.x(), event.y()) || isOverEnchantListScrollbar(event.x(), event.y())) {
            this.setFocused(this.scrollContainer);
            this.scrollContainer.setFocused(true);
            if (isOverEnchantListScrollbar(event.x(), event.y())) {
                this.scrollContainer.jumpScrollbarToMouseY(event.y());
                this.scrollContainer.setDragging(true);
                this.scrollContainer.updateScrolling(event);
                return true;
            }
            return this.scrollContainer.mouseClicked(event, isDoubleClick);
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.configOverlay != null) {
            return this.configOverlay.mouseDragged(event, dragX, dragY);
        }
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseDragged(event, dragX, dragY);
        }
        if (this.scrollContainer != null && (this.scrollContainer.isDragging() || isOverEnchantListScrollbar(event.x(), event.y()))) {
            return this.scrollContainer.mouseDragged(event, dragX, dragY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.configOverlay != null) {
            return this.configOverlay.mouseReleased(event);
        }
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.mouseReleased(event);
        }
        if (this.scrollContainer != null && this.scrollContainer.isDragging()) {
            this.scrollContainer.setDragging(false);
            this.scrollContainer.onRelease(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    // Modal overlay contract (GUI-04 / GUI-05): while configOverlay/grimoireOverlay is open,
    // EVERY input callback (mouseScrolled, mouseClicked, mouseDragged, mouseReleased,
    // keyPressed, charTyped, keyReleased) is forwarded to it and consumed here so input never
    // leaks to the container beneath. If an overlay fails to handle ESC itself (returns false),
    // the parent screen enforces closing via overlay.onClose() so no future overlay can soft-lock.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.configOverlay != null) {
            boolean handled = this.configOverlay.keyPressed(event);
            if (!handled && event.key() == InputConstants.KEY_ESCAPE && this.configOverlay != null) {
                this.configOverlay.onClose();
            }
            return true;
        }
        if (this.grimoireOverlay != null) {
            boolean handled = this.grimoireOverlay.keyPressed(event);
            if (!handled && event.key() == InputConstants.KEY_ESCAPE && this.grimoireOverlay != null) {
                this.grimoireOverlay.onClose();
            }
            return true;
        }
        if (isInscribeTab() && this.scrollContainer != null
            && this.scrollContainer.inscribeFilter.keyPressed(event, this.scrollContainer::refreshInscribeFilter)) {
            return true;
        }
        if (isCleanseTab() && this.scrollContainer != null
            && this.scrollContainer.cleanseFilter.keyPressed(event, this.scrollContainer::refreshInscribeFilter)) {
            return true;
        }
        if (this.scrollContainer.keyPressed(event)) {
            if (isTomeTab()) tomeActions.cancel();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.configOverlay != null) { this.configOverlay.charTyped(event); return true; }
        if (this.grimoireOverlay != null) { this.grimoireOverlay.charTyped(event); return true; }
        if (isInscribeTab() && this.scrollContainer != null
            && this.scrollContainer.inscribeFilter.charTyped(event, this.scrollContainer::refreshInscribeFilter)) {
            return true;
        }
        if (isCleanseTab() && this.scrollContainer != null
            && this.scrollContainer.cleanseFilter.charTyped(event, this.scrollContainer::refreshInscribeFilter)) {
            return true;
        }
        if (this.scrollContainer.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.configOverlay != null) return this.configOverlay.keyReleased(event);
        if (this.grimoireOverlay != null) return this.grimoireOverlay.keyReleased(event);
        this.scrollContainer.keyReleased(event);
        return super.keyReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        if (this.returningFromExternalScreen) restoreAfterExternalScreenReturn(true);
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        TablePanelBackground.extractBackground(panelInputs(mouseX, mouseY), ctx, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        if (this.configOverlay != null) {
            super.extractRenderState(ctx, -1000, -1000, partialTick);
            this.configOverlay.extractRenderState(ctx, mouseX, mouseY, partialTick);
            return;
        }
        if (this.grimoireOverlay != null) {
            super.extractRenderState(ctx, -1000, -1000, partialTick);
            this.grimoireOverlay.extractRenderState(ctx, mouseX, mouseY, partialTick);
            return;
        }
        layoutDirectory();
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        int modeSig = ((this.scrollContainer != null ? this.scrollContainer.tabMode().ordinal() : 0) << 2)
            | (this.menu.isRitualActive() ? 1 : 0);
        GuiMicroEffects.observeModeSignature(modeSig);
        GuiMicroEffects.renderOverlays(ctx, this.leftPos, this.topPos, effectiveTablePos(),
            this.menu, shouldShowSanctumPanel(), isInscribeTab());
        if (isInscribeTab() && this.scrollContainer != null) {
            if (this.scrollContainer.inscribeFilter.render(ctx, this.font,
                this.leftPos + TomeDirectoryLayout.CONTENT_X, this.topPos + TomeDirectoryLayout.CONTENT_Y,
                mouseX, mouseY, this.scrollContainer.areAllInscribeCategoriesExpanded())) return;
        }
        if (isCleanseTab() && this.scrollContainer != null) {
            if (this.scrollContainer.cleanseFilter.render(ctx, this.font,
                this.leftPos + TomeDirectoryLayout.CONTENT_X, this.topPos + TomeDirectoryLayout.CONTENT_Y,
                mouseX, mouseY, this.scrollContainer.areAllCleanseCategoriesExpanded())) return;
        }
        if (isTomeTab()) {
            var status = tomeActions.status();
            if (status != null) {
                ctx.fill(this.leftPos+PANEL_X+9, this.topPos+PANEL_Y+8, this.leftPos+PANEL_X+110, this.topPos+PANEL_Y+16, 0xFF102722);
                RowRenderSupport.renderCenteredBoxText(ctx, this.font, TomeDirectoryControls.text(status.key(), status.count()),
                    this.leftPos+PANEL_X+10, 99, this.topPos+PANEL_Y+12, 0.58f, 0xFFFFD280);
            }
            if (tomeDirectory.render(ctx, this.font, this.leftPos+TomeDirectoryLayout.CONTENT_X, this.topPos+TomeDirectoryLayout.CONTENT_Y, mouseX, mouseY,
                this.scrollContainer.catalogue, this.menu.player)) return;
        }
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
            var state = tooltipInputs(mouseX, mouseY);
            if (TableNavigationTooltips.render(state, ctx, mouseX, mouseY)) return;
            if (TableCostTooltips.render(state, ctx, mouseX, mouseY)) return;
            if (TableOptionTooltips.render(state, ctx, mouseX, mouseY)) return;
            TablePanelTooltips.render(state, ctx, mouseX, mouseY);
        }
    }

    private List<Component> buildResonanceBreakdownTooltip() {
        BlockPos pos = effectiveTablePos();
        var scan = TooltipPresentation.detailsRequested() && this.minecraft != null && this.minecraft.level != null && pos != null
            ? chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.scanLibraryRitualContext(
                this.minecraft.level, pos, this.menu.shelfGatesConfig()) : null;
        return ResonanceTooltip.build(this.menu, scan);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TablePanelLabels.extractLabels(panelInputs(mouseX, mouseY), context, mouseX, mouseY);
    }

    private TableTooltipContext tooltipInputs(int mouseX, int mouseY) {
        var regions = java.util.EnumSet.noneOf(TableTooltipContext.Region.class);
        if (isOverCleanseAllButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverCleanseAllButton);
        if (isOverPurgeCursesButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverPurgeCursesButton);
        if (isOverCleanseTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverCleanseTab);
        if (isOverConfigButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverConfigButton);
        if (isOverFreezeButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverFreezeButton);
        if (isOverGrimoireButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverGrimoireButton);
        if (isOverInscribeTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverInscribeTab);
        if (isOverRerollButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverRerollButton);
        if (isOverSacrificeButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverSacrificeButton);
        if (isOverSanctumPanel(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverSanctumPanel);
        if (isOverShelfHudBadge(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverShelfHudBadge);
        if (isOverTomeTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverTomeTab);
        return new TableTooltipContext(this.menu, this.scrollContainer == null ? null : this.scrollContainer.tableRead(), this.font, this.minecraft.level,
            regions, hoveredTomeButton(mouseX, mouseY), hoveredRitualButton(mouseX, mouseY), isCleanseTab(), isTomeTab(),
            isDisenchantAllConfirmPending(), canCleanseAllNow(), canPurgeCursesNow(), this::getTooltipFromContainerItem,
            this::buildResonanceBreakdownTooltip);
    }

    private TablePanelContext panelInputs(int mouseX, int mouseY) {
        return new TablePanelContext(tooltipInputs(mouseX, mouseY), this.leftPos, this.topPos, this.title.getString(),
            shouldShowSanctumPanel(), shouldShowShelfHudBadge(), isInscribeTab(),
            this::tomeButtonRect);
    }
}
