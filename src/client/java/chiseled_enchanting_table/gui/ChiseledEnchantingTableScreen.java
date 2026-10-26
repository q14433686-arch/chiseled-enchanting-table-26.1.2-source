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
            this.scrollContainer.updateSizeAndPosition(151, 141, this.leftPos + 7, this.topPos + 17);
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
        if (this.configOverlay != null) {
            this.configOverlay.tick();
        }
        if (this.returningFromExternalScreen
            || (this.scrollContainer != null
                && isInscribeTab()
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
        sendServerboundOrWarn(
            new ApplyEnchantmentPayload(
                this.menu.containerId,
                OptionType.DISENCHANT_ALL,
                DisenchantmentApplication.DISENCHANT_ALL_ID,
                0
            ),
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID
        );
    }

    private boolean shouldShowShelfHudBadge() {
        return !this.menu.usesClassicShelfBoost() || this.menu.isLibraryDiscountEnabled();
    }

    private boolean shouldShowSanctumPanel() {
        return this.menu.isRitualActive()
            || !isInscribeTab()
            || this.menu.isEpiphanyEnabled()
            || this.menu.isLibraryDiscountEnabled();
    }

    private boolean isOverShelfHudBadge(double mouseX, double mouseY) {
        if (!shouldShowShelfHudBadge()) return false;
        int x0 = this.leftPos + SHELF_HUD_X;
        int y0 = this.topPos + SHELF_HUD_Y;
        return mouseX >= x0 && mouseX < x0 + SHELF_HUD_W && mouseY >= y0 && mouseY < y0 + SHELF_HUD_H;
    }

    private boolean isCleanseTab() {
        return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.CLEANSE;
    }

    private boolean isInscribeTab() {
        return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.INSCRIBE;
    }

    private boolean isTomeTab() {
        return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.TOMES;
    }

    @Override
    protected void init() {
        super.init();
        boolean shouldSync = this.returningFromExternalScreen
            || (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.containerMenu != this.menu);
        restoreAfterExternalScreenReturn(shouldSync);
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.initOverlay(this.minecraft, this.width, this.height);
        }
        if (this.configOverlay != null) {
            this.configOverlay.initOverlay(this.minecraft, this.width, this.height);
        }
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        boolean shouldSync = this.returningFromExternalScreen
            || (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.containerMenu != this.menu);
        restoreAfterExternalScreenReturn(shouldSync);
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.initOverlay(this.minecraft, this.width, this.height);
        }
        if (this.configOverlay != null) {
            this.configOverlay.initOverlay(this.minecraft, this.width, this.height);
        }
    }

    private boolean isOverInscribeTab(double mouseX, double mouseY) {
        if (!this.menu.hasSecondaryTabs()) return false;
        int x0 = this.leftPos + TAB_INSCRIBE_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private boolean isOverTomeTab(double mouseX, double mouseY) {
        if (!this.menu.isTomePageEnabled()) return false;
        int x0 = this.leftPos + TAB_TOMES_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private boolean isOverSacrificeButton(double mouseX, double mouseY) {
        if ((!this.menu.disenchantEnabled() && !this.menu.isTomePageEnabled())
            || (this.menu.disenchantEnabled() && this.menu.isCleansePageUnlocked())) return false;
        int x0 = this.leftPos + SACRIFICE_BTN_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + SACRIFICE_BTN_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private int[] tomeButtonRect(int index) {
        // Panel inner decorative frame (measured on cleanse_panel.png): lines at
        // x 5-7 / 111-113 and y 5-7 / 53-55 in panel-relative GUI pixels. Both button
        // rows must end at x <= 109 and y <= 51 to keep a dark pixel clear of them.
        return switch (index) {
            case 0 -> new int[] {PANEL_X + 9, PANEL_Y + 35, 32, 8};
            case 1 -> new int[] {PANEL_X + 44, PANEL_Y + 35, 32, 8};
            case 2 -> new int[] {PANEL_X + 78, PANEL_Y + 35, 31, 8};
            case 3 -> new int[] {PANEL_X + 9, PANEL_Y + 44, 48, 8};
            default -> new int[] {PANEL_X + 59, PANEL_Y + 44, 50, 8};
        };
    }

    private int hoveredTomeButton(double mouseX, double mouseY) {
        if (!isTomeTab() || !this.menu.isTomePageUnlocked()) return -1;
        for (int i = 0; i < 5; i++) {
            int[] r = tomeButtonRect(i);
            int x0 = this.leftPos + r[0];
            int y0 = this.topPos + r[1];
            if (mouseX >= x0 && mouseX < x0 + r[2] && mouseY >= y0 && mouseY < y0 + r[3]) return i;
        }
        return -1;
    }

    private void sendTomeAction(
        chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action action,
        chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry entry
    ) {
        sendServerboundOrWarn(
            new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload(
                this.menu.containerId,
                action,
                entry == null ? net.minecraft.core.BlockPos.ZERO : entry.shelf_pos(),
                entry == null ? -1 : entry.slot()
            ),
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.TYPE
        );
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
            areas.add(new net.minecraft.client.renderer.Rect2i(
                this.leftPos + TAB_INSCRIBE_X,
                this.topPos + TAB_Y_OFFSET,
                (TAB_TOMES_X + TAB_W) - TAB_INSCRIBE_X,
                TAB_H
            ));
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
        if (!this.menu.isEpiphanyEnabled() || !isInscribeTab() || this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + FREEZE_BTN_X;
        int y0 = this.topPos + FREEZE_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        if (!this.menu.isEpiphanyEnabled() || !isInscribeTab() || this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + REROLL_BTN_X;
        int y0 = this.topPos + REROLL_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverCleanseAllButton(double mouseX, double mouseY) {
        if (!isCleanseTab() || this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + CLEANSE_ALL_BTN_X;
        int y0 = this.topPos + CLEANSE_ALL_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverSacrificeSkipButton(double mouseX, double mouseY) {
        if (!this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + SKIP_SACRIFICE_BTN_X;
        int y0 = this.topPos + SKIP_SACRIFICE_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + SKIP_BTN_W && mouseY >= y0 && mouseY < y0 + SKIP_BTN_H;
    }

    private boolean isOverRushSkipButton(double mouseX, double mouseY) {
        if (!this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + SKIP_RUSH_BTN_X;
        int y0 = this.topPos + SKIP_RUSH_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + SKIP_BTN_W && mouseY >= y0 && mouseY < y0 + SKIP_BTN_H;
    }

    private boolean canCleanseAllNow() {
        return this.menu.disenchantEnabled()
            && !this.menu.isRitualActive()
            && DisenchantmentApplication.countNonCurseEnchantments(this.menu.get_enchantable_item()) > 0;
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
                this.scrollContainer.setTabMode(UiTabMode.INSCRIBE);
            }
            return true;
        }
        if (isOverCleanseTab(event.x(), event.y())) {
            if (!this.menu.isCleansePageUnlocked()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            } else if (!isCleanseTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                this.scrollContainer.setTabMode(UiTabMode.CLEANSE);
            }
            return true;
        }
        if (isOverTomeTab(event.x(), event.y())) {
            if (!this.menu.isTomePageUnlocked()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            } else if (!isTomeTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.95F));
                this.scrollContainer.setTabMode(UiTabMode.TOMES);
            }
            return true;
        }
        int tomeButton = hoveredTomeButton(event.x(), event.y());
        if (tomeButton >= 0) {
            var selected = this.scrollContainer.selectedTomeEntry();
            var action = switch (tomeButton) {
                case 0 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_NEXT;
                case 1 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_FULL;
                case 2 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_PERMANENT;
                case 3 -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.UNLOCK;
                default -> chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action.LOCK_ALL_FULL;
            };
            boolean needsBook = tomeButton != 4;
            boolean valid = !needsBook || (selected != null && (tomeButton == 3 ? selected.locked() : !selected.locked()));
            if (!valid) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                sendTomeAction(action, tomeButton == 4 ? null : selected);
            }
            return true;
        }
        if (isOverSacrificeSkipButton(event.x(), event.y())) {
            if (this.menu.canSacrificeSkipNow()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
                sendServerboundOrWarn(
                    new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload(
                        this.menu.containerId, this.menu.ritualSession(), true
                    ),
                    chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE
                );
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverRushSkipButton(event.x(), event.y())) {
            if (this.menu.canRushSkipNow()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F));
                sendServerboundOrWarn(
                    new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload(
                        this.menu.containerId, this.menu.ritualSession(), false
                    ),
                    chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE
                );
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
                sendServerboundOrWarn(
                    new FreezeEpiphanyPayload(this.menu.containerId),
                    FreezeEpiphanyPayload.TYPE
                );
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverRerollButton(event.x(), event.y())) {
            if (this.menu.canAffordRerollEpiphany()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                sendServerboundOrWarn(
                    new RerollEpiphanyPayload(this.menu.containerId),
                    RerollEpiphanyPayload.TYPE
                );
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
        if (this.scrollContainer.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.configOverlay != null) {
            this.configOverlay.charTyped(event);
            return true;
        }
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.charTyped(event);
            return true;
        }
        if (this.scrollContainer.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.configOverlay != null) {
            return this.configOverlay.keyReleased(event);
        }
        if (this.grimoireOverlay != null) {
            return this.grimoireOverlay.keyReleased(event);
        }
        this.scrollContainer.keyReleased(event);
        return super.keyReleased(event);
    }







    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) { if (this.returningFromExternalScreen) restoreAfterExternalScreenReturn(true);
            super.extractBackground(ctx, mouseX, mouseY, partialTick);
            TablePanelBackground.extractBackground(panelInputs(mouseX, mouseY), ctx, mouseX, mouseY, partialTick); }

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
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
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
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) { TablePanelLabels.extractLabels(panelInputs(mouseX, mouseY), context, mouseX, mouseY); }



    private TableTooltipContext tooltipInputs(int mouseX, int mouseY) {
var regions = java.util.EnumSet.noneOf(TableTooltipContext.Region.class);
if (isOverCleanseAllButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverCleanseAllButton);
if (isOverCleanseTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverCleanseTab);
if (isOverConfigButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverConfigButton);
if (isOverFreezeButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverFreezeButton);
if (isOverGrimoireButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverGrimoireButton);
if (isOverInscribeTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverInscribeTab);
if (isOverRerollButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverRerollButton);
if (isOverRushSkipButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverRushSkipButton);
if (isOverSacrificeButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverSacrificeButton);
if (isOverSacrificeSkipButton(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverSacrificeSkipButton);
if (isOverSanctumPanel(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverSanctumPanel);
if (isOverShelfHudBadge(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverShelfHudBadge);
if (isOverTomeTab(mouseX, mouseY)) regions.add(TableTooltipContext.Region.isOverTomeTab);
return new TableTooltipContext(this.menu, this.scrollContainer == null ? null : this.scrollContainer.tableRead(), this.font, this.minecraft.level,
    regions, hoveredTomeButton(mouseX, mouseY), isCleanseTab(), isTomeTab(),
    isDisenchantAllConfirmPending(), canCleanseAllNow(), this::getTooltipFromContainerItem,
    this::buildResonanceBreakdownTooltip);
    }

    private TablePanelContext panelInputs(int mouseX, int mouseY) {
        return new TablePanelContext(tooltipInputs(mouseX, mouseY), this.leftPos, this.topPos, this.title.getString(),
            shouldShowSanctumPanel(), shouldShowShelfHudBadge(), isInscribeTab(),
            this::tomeButtonRect);
    }
}
