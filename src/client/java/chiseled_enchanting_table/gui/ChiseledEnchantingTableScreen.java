package chiseled_enchanting_table.gui;

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
    private static final Identifier BACKGROUND = ChiseledEnchantingTable.identifier("gui/chiseled_enchanting_table.png");
    private static final Identifier EPIPHANY_PANEL = ChiseledEnchantingTable.identifier("gui/epiphany_panel.png");
    private static final Identifier EPIPHANY_PANEL_FROZEN = ChiseledEnchantingTable.identifier("gui/epiphany_panel_frozen.png");
    private static final Identifier CLEANSE_PANEL = ChiseledEnchantingTable.identifier("gui/cleanse_panel.png");
    private static final Identifier BTN_FREEZE = ChiseledEnchantingTable.identifier("gui/btn_freeze.png");
    private static final Identifier BTN_REROLL = ChiseledEnchantingTable.identifier("gui/btn_reroll.png");
    private static final Identifier BTN_CLEANSE_ALL = ChiseledEnchantingTable.identifier("gui/btn_cleanse_all.png");
    private static final Identifier BTN_GRIMOIRE = ChiseledEnchantingTable.identifier("gui/btn_grimoire.png");
    private static final Identifier BTN_CONFIG = ChiseledEnchantingTable.identifier("gui/btn_config.png");

    private static final int CONFIG_BTN_X = 293;
    private static final int CONFIG_BTN_Y_OFFSET = -15;
    private static final int CONFIG_BTN_SIZE = 15;
    private static final int GRIMOIRE_BTN_X = 311;
    private static final int GRIMOIRE_BTN_Y_OFFSET = -15;
    private static final int GRIMOIRE_BTN_SIZE = 15;

    private static final Identifier TAB_INSCRIBE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_active.png");
    private static final Identifier TAB_INSCRIBE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_inactive.png");
    private static final Identifier TAB_CLEANSE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_active.png");
    private static final Identifier TAB_CLEANSE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_inactive.png");
    private static final Identifier[] TOME_BUTTONS = {
        ChiseledEnchantingTable.identifier("gui/tome_button_0.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_1.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_2.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_3.png"),
        ChiseledEnchantingTable.identifier("gui/tome_button_4.png")
    };
    private static final Identifier TAB_TOME_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_active.png");
    private static final Identifier TAB_TOME_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_tome_inactive.png");

    // Top mode switcher tabs aligned pixel-for-pixel with the purple header cloth [x = 14 .. 167].
    private static final int TAB_W = 75;
    private static final int TAB_H = 18;
    private static final int TAB_Y_OFFSET = -15;
    private static final int TAB_INSCRIBE_X = 14;
    private static final int TAB_CLEANSE_X = 92;
    private static final int TAB_TOMES_X = 170;
    private static final int SACRIFICE_BTN_X = 248;
    private static final int SACRIFICE_BTN_W = 40;

    // Integrated Sanctum / Cleanse Console coordinates inside the cohesive 340x166 window chassis.
    private static final int PANEL_X = 214;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 119;
    private static final int PANEL_H = 61;

    // Compact persistent W / S bookshelf HUD badge in the stitched header bar directly above PANEL_X.
    private static final int SHELF_HUD_X = PANEL_X;
    private static final int SHELF_HUD_Y = 2;
    private static final int SHELF_HUD_W = 118;
    private static final int SHELF_HUD_H = 12;
    private static final long DISENCHANT_CONFIRM_WINDOW_MS = 5_000L;

    private static final int BTN_SIZE = 14;
    private static final int FREEZE_BTN_X = PANEL_X + 79;
    private static final int FREEZE_BTN_Y = PANEL_Y + 35;
    private static final int REROLL_BTN_X = PANEL_X + 95;
    private static final int REROLL_BTN_Y = PANEL_Y + 35;
    private static final int CLEANSE_ALL_BTN_X = PANEL_X + 95;
    private static final int CLEANSE_ALL_BTN_Y = PANEL_Y + 35;

    private static final int SKIP_SACRIFICE_BTN_X = PANEL_X + 10;
    private static final int SKIP_SACRIFICE_BTN_Y = PANEL_Y + 36;
    private static final int SKIP_BTN_W = 48;
    private static final int SKIP_BTN_H = 14;
    private static final int SKIP_RUSH_BTN_X = PANEL_X + 61;
    private static final int SKIP_RUSH_BTN_Y = PANEL_Y + 36;

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

    public static boolean requiresDisenchantAllConfirm(ItemStack target) {
        if (target == null || target.isEmpty() || !target.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            return false;
        }
        return DisenchantmentApplication.countNonCurseEnchantments(target) >= 3
            || chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(target);
    }

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

    private int computeNextShelfMilestoneTarget() {
        var sg = this.menu.shelfGatesConfig();
        var lr = this.menu.libraryResonanceConfig();
        int broad = this.menu.totalLibraryBookshelves();
        int[] milestones = {
            Math.max(1, lr.minDiscountBookshelves()),
            Math.max(1, sg.tier1Bookshelves()),
            Math.max(1, this.menu.epiphanyMinBookshelves()),
            Math.max(1, sg.tier2Bookshelves()),
            Math.max(1, sg.maxDirectBookshelves()),
            Math.max(1, lr.tomeRegenTier1Shelves()),
            Math.max(1, lr.tomeRegenTier2Shelves())
        };
        java.util.Arrays.sort(milestones);
        for (int m : milestones) {
            if (broad < m) {
                return m;
            }
        }
        return milestones[milestones.length - 1];
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



    private void drawAutoScaledText(
        GuiGraphicsExtractor ctx,
        Component text,
        int x,
        int y,
        float maxScale,
        int maxWidth,
        int argbColor
    ) {
        RowRenderSupport.renderBoundedText(ctx, this.minecraft, text.getString(), x, y, maxScale, maxWidth, argbColor);
    }

    private void renderModeTabs(GuiGraphicsExtractor ctx, int i, int j, int mouseX, int mouseY) {
        if (!this.menu.hasSecondaryTabs()) {
            int cx = i + CONFIG_BTN_X;
            int cy = j + CONFIG_BTN_Y_OFFSET;
            ctx.blit(BTN_CONFIG, cx, cy, cx + CONFIG_BTN_SIZE, cy + CONFIG_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (isOverConfigButton(mouseX, mouseY)) {
                ctx.fill(cx + 1, cy + 1, cx + CONFIG_BTN_SIZE - 1, cy + CONFIG_BTN_SIZE - 1, 0x2EFFDF82);
            }
            int gx = i + GRIMOIRE_BTN_X;
            int gy = j + GRIMOIRE_BTN_Y_OFFSET;
            ctx.blit(BTN_GRIMOIRE, gx, gy, gx + GRIMOIRE_BTN_SIZE, gy + GRIMOIRE_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (isOverGrimoireButton(mouseX, mouseY)) {
                ctx.fill(gx + 1, gy + 1, gx + GRIMOIRE_BTN_SIZE - 1, gy + GRIMOIRE_BTN_SIZE - 1, 0x2EFFDF82);
            }
            return;
        }
        boolean cleanse = isCleanseTab();
        boolean tome = isTomeTab();
        boolean inscribe = isInscribeTab();
        int tabY = j + TAB_Y_OFFSET;

        int inscribeX = i + TAB_INSCRIBE_X;
        Identifier inscribeSprite = inscribe ? TAB_INSCRIBE_ACTIVE : TAB_INSCRIBE_INACTIVE;
        ctx.blit(inscribeSprite, inscribeX, tabY, inscribeX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
        if (!inscribe && isOverInscribeTab(mouseX, mouseY)) {
            ctx.fill(inscribeX + 2, tabY + 2, inscribeX + TAB_W - 2, tabY + TAB_H - 3, 0x24FFE082);
        }
        float tabVisualMidY = tabY + 8.0f;
        String tab1Text = Component.translatable("gui.chiseled_enchanting_table.tab.inscribe").getString();
        RowRenderSupport.renderCenteredBoxText(
            ctx,
            this.minecraft,
            tab1Text,
            inscribeX + 5,
            TAB_W - 10,
            tabVisualMidY,
            0.76f,
            inscribe ? 0xFFFFF0C8 : 0xFFC2B2DC
        );

        if (this.menu.disenchantEnabled()) {
            int cleanseX = i + TAB_CLEANSE_X;
            Identifier cleanseSprite = cleanse ? TAB_CLEANSE_ACTIVE : TAB_CLEANSE_INACTIVE;
            ctx.blit(cleanseSprite, cleanseX, tabY, cleanseX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!this.menu.isCleansePageUnlocked()) {
                ctx.fill(cleanseX + 2, tabY + 2, cleanseX + TAB_W - 2, tabY + TAB_H - 3, 0xA0181024);
            } else if (!cleanse && isOverCleanseTab(mouseX, mouseY)) {
                ctx.fill(cleanseX + 2, tabY + 2, cleanseX + TAB_W - 2, tabY + TAB_H - 3, 0x247CFFA6);
            }
            int traitCount = EnchantmentHelper.getEnchantmentsForCrafting(this.menu.get_enchantable_item()).size();
            String baseLabel = Component.translatable("gui.chiseled_enchanting_table.tab.cleanse").getString();
            String tab2Text = traitCount > 0 ? (baseLabel + " (" + traitCount + ")") : baseLabel;
            RowRenderSupport.renderCenteredBoxText(
                ctx,
                this.minecraft,
                tab2Text,
                cleanseX + 5,
                TAB_W - 10,
                tabVisualMidY,
                0.76f,
                !this.menu.isCleansePageUnlocked() ? 0xFF8E829E : (cleanse ? 0xFF9EFFDB : (traitCount > 0 ? 0xFF8BE8C8 : 0xFFA2C4BC))
            );
        }

        if (this.menu.isTomePageEnabled()) {
            int tomeX = i + TAB_TOMES_X;
            Identifier tomeSprite = tome ? TAB_TOME_ACTIVE : TAB_TOME_INACTIVE;
            ctx.blit(tomeSprite, tomeX, tabY, tomeX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!this.menu.isTomePageUnlocked()) {
                ctx.fill(tomeX + 2, tabY + 2, tomeX + TAB_W - 2, tabY + TAB_H - 3, 0xA0181024);
            } else if (!tome && isOverTomeTab(mouseX, mouseY)) {
                ctx.fill(tomeX + 2, tabY + 2, tomeX + TAB_W - 2, tabY + TAB_H - 3, 0x24B9F6FF);
            }
            String tomeText = Component.translatable("gui.chiseled_enchanting_table.tab.tomes").getString();
            RowRenderSupport.renderCenteredBoxText(
                ctx,
                this.minecraft,
                tomeText,
                tomeX + 5,
                TAB_W - 10,
                tabVisualMidY,
                0.76f,
                !this.menu.isTomePageUnlocked() ? 0xFF8E829E : (tome ? 0xFFB9F6FF : 0xFFA8CBD8)
            );
        }

        if (this.menu.disenchantEnabled() && !this.menu.isCleansePageUnlocked()) {
            int sx = i + SACRIFICE_BTN_X;
            ctx.fill(sx, tabY + 1, sx + SACRIFICE_BTN_W, tabY + TAB_H - 2, isOverSacrificeButton(mouseX, mouseY) ? 0xFF6B4E2E : 0xFF3A2A20);
            ctx.fill(sx + 1, tabY + 2, sx + SACRIFICE_BTN_W - 1, tabY + TAB_H - 3, 0xFF241722);
            String sacText = Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_short", this.menu.sacrificeProgress, this.menu.sacrificeRequired).getString();
            RowRenderSupport.renderCenteredBoxText(ctx, this.minecraft, sacText, sx + 2, SACRIFICE_BTN_W - 4, tabVisualMidY, 0.62f, 0xFFFFC46B);
        }

        int cx = i + CONFIG_BTN_X;
        int cy = j + CONFIG_BTN_Y_OFFSET;
        ctx.blit(BTN_CONFIG, cx, cy, cx + CONFIG_BTN_SIZE, cy + CONFIG_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
        if (isOverConfigButton(mouseX, mouseY)) {
            ctx.fill(cx + 1, cy + 1, cx + CONFIG_BTN_SIZE - 1, cy + CONFIG_BTN_SIZE - 1, 0x2EFFDF82);
        }

        int gx = i + GRIMOIRE_BTN_X;
        int gy = j + GRIMOIRE_BTN_Y_OFFSET;
        ctx.blit(BTN_GRIMOIRE, gx, gy, gx + GRIMOIRE_BTN_SIZE, gy + GRIMOIRE_BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
        if (isOverGrimoireButton(mouseX, mouseY)) {
            ctx.fill(gx + 1, gy + 1, gx + GRIMOIRE_BTN_SIZE - 1, gy + GRIMOIRE_BTN_SIZE - 1, 0x2EFFDF82);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        if (this.returningFromExternalScreen) {
            restoreAfterExternalScreenReturn(true);
        }
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int i = this.leftPos;
        int j = this.topPos;

        // Draw inactive tab under main frame, main frame, then active tab flush on top so its open bottom blends seamlessly!
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);
        renderModeTabs(ctx, i, j, mouseX, mouseY);
        if (shouldShowShelfHudBadge()) {
            renderHeaderShelfHudBadge(ctx, i, j, mouseX, mouseY);
        }

        int px = i + PANEL_X;
        int py = j + PANEL_Y;
        if (!shouldShowSanctumPanel()) {
            return;
        }

        if (this.menu.isRitualActive()) {
            ctx.blit(EPIPHANY_PANEL_FROZEN, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                int barX = px + 10;
                int barY = py + 29;
                int barW = 99;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE181229);
                long rem = this.menu.ritualRemainingTicks();
                long tot = Math.max(1L, Math.max(rem, this.menu.ritualTotalTicks()));
                float doneRatio = Mth.clamp(1.0f - (float) rem / (float) tot, 0.04f, 1.0f);
                int filledW = Math.round(barW * doneRatio);
                ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);

                // Left button: Sacrifice XP Skip
                int sx = i + SKIP_SACRIFICE_BTN_X;
                int sy = j + SKIP_SACRIFICE_BTN_Y;
                boolean canSac = this.menu.canSacrificeSkipNow();
                ctx.fill(sx, sy, sx + SKIP_BTN_W, sy + SKIP_BTN_H, canSac ? 0xFFE0B85A : 0xFF584E68);
                ctx.fill(
                    sx + 1,
                    sy + 1,
                    sx + SKIP_BTN_W - 1,
                    sy + SKIP_BTN_H - 1,
                    canSac
                        ? (isOverSacrificeSkipButton(mouseX, mouseY) ? 0xFF2E5B48 : 0xFF1E3E33)
                        : 0xFF191522
                );

                // Right button: Non-Sacrifice Force Rush Skip
                int rx = i + SKIP_RUSH_BTN_X;
                int ry = j + SKIP_RUSH_BTN_Y;
                boolean canRush = this.menu.canRushSkipNow();
                ctx.fill(rx, ry, rx + SKIP_BTN_W, ry + SKIP_BTN_H, canRush ? 0xFFD9725A : 0xFF584E68);
                ctx.fill(
                    rx + 1,
                    ry + 1,
                    rx + SKIP_BTN_W - 1,
                    ry + SKIP_BTN_H - 1,
                    canRush
                        ? (isOverRushSkipButton(mouseX, mouseY) ? 0xFF662932 : 0xFF491D26)
                        : 0xFF191522
                );
            } finally {
                ctx.disableScissor();
            }
            return;
        }

        if (isTomeTab()) {
            ctx.blit(CLEANSE_PANEL, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            var selected = this.scrollContainer.selectedTomeEntry();
            String[] labels = {
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_next").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_full").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_permanent").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_unlock").getString(),
                Component.translatable("gui.chiseled_enchanting_table.tomes.btn_lock_all").getString()
            };
            for (int b = 0; b < labels.length; b++) {
                int[] r = tomeButtonRect(b);
                int bx = i + r[0];
                int by = j + r[1];
                boolean enabled = b == 4 || (selected != null && (b == 3 ? selected.locked() : !selected.locked()));
                if (b < 3 && selected != null && selected.wear_used() <= 0 && b != 2) enabled = false;
                if (b != 3 && !this.menu.isTomeLocksEnabled()) enabled = false;
                boolean hovered = hoveredTomeButton(mouseX, mouseY) == b;
                ctx.blit(TOME_BUTTONS[b], bx, by, bx + r[2], by + r[3], 0.0f, 1.0f, 0.0f, 1.0f);
                if (!enabled) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0xB0191522);
                } else if (hovered) {
                    ctx.fill(bx, by, bx + r[2], by + r[3], 0x332AD4EE);
                }
                RowRenderSupport.renderCenteredBoxText(ctx, this.minecraft, labels[b], bx + 1, r[2] - 2, by + r[3] * 0.5f, 0.58f,
                    enabled ? 0xFFDFF7FF : 0xFF7D748D);
            }
            if (this.scrollContainer.children().isEmpty()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint = Component.translatable(this.menu.isTomePageUnlocked()
                    ? "gui.chiseled_enchanting_table.tomes.empty"
                    : "gui.chiseled_enchanting_table.tab.locked_tooltip").getString();
                RowRenderSupport.renderBoundedText(ctx, this.minecraft, hint, lx, ly, 0.68f, 140, 0xFFAEEADF);
            }
            return;
        }

        if (isCleanseTab()) {
            ctx.blit(CLEANSE_PANEL, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);
            ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
            try {
                ItemStack target = this.menu.get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int barX = px + 10;
                int barY = py + 45;
                int barW = 80;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC071210);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE102420);
                float ratio = totalTraits <= 0 ? 0.0f : Mth.clamp((float) nonCurse / (float) Math.max(1, totalTraits), 0.15f, 1.0f);
                int filledW = Math.round(barW * ratio);
                if (filledW > 0) {
                    ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF39D99A);
                    ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFA8FFE0);
                }

                int bx = i + CLEANSE_ALL_BTN_X;
                int by = j + CLEANSE_ALL_BTN_Y;
                boolean canAll = canCleanseAllNow();
                ctx.blit(BTN_CLEANSE_ALL, bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
                if (!canAll) {
                    ctx.fill(bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0x990A1010);
                } else if (isDisenchantAllConfirmPending()) {
                    ctx.fill(bx - 1, by - 1, bx + BTN_SIZE + 1, by + BTN_SIZE + 1, 0xCCFFAA2A);
                    ctx.fill(bx + 1, by + 1, bx + BTN_SIZE - 1, by + BTN_SIZE - 1, 0x44FFD066);
                } else if (isOverCleanseAllButton(mouseX, mouseY)) {
                    ctx.fill(bx, by, bx + BTN_SIZE, by + BTN_SIZE, 0x2E8CFFD2);
                }
            } finally {
                ctx.disableScissor();
            }

            if (this.scrollContainer.children().isEmpty()) {
                int lx = i + 12;
                int ly = j + 74;
                String hint1 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line1").getString();
                String hint2 = Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line2").getString();
                RowRenderSupport.renderBoundedText(ctx, this.minecraft, hint1, lx, ly, 0.72f, 140, 0xFFAEEADF);
                RowRenderSupport.renderBoundedText(ctx, this.minecraft, hint2, lx, ly + 12, 0.64f, 140, 0xFF82B8AD);
            }
            return;
        }

        boolean frozen = this.menu.isEpiphanyFrozen();
        Identifier panelSprite = frozen ? EPIPHANY_PANEL_FROZEN : EPIPHANY_PANEL;
        ctx.blit(panelSprite, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);

        ctx.enableScissor(px + 9, py + 9, px + 110, py + 52);
        try {
            if (!this.menu.isEpiphanyEnabled()) {
                int barX = px + 10;
                int barY = py + 45;
                int barW = 98;
                int barH = 4;
                ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
                ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE1A122B);
                int maxDisc = Math.max(1, this.menu.libraryResonanceConfig().maxTotalXpDiscountPercent());
                float ratio = Mth.clamp((float) this.menu.xpDiscountPercent / (float) maxDisc, 0.0f, 1.0f);
                int filledW = Math.round(barW * ratio);
                if (filledW > 0) {
                    ctx.fill(barX, barY, barX + filledW, barY + barH, 0xFF42E39B);
                    ctx.fill(barX, barY, barX + filledW, barY + 1, 0xFFB8FFE0);
                }
                return;
            }
            int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
            boolean unlocked = this.menu.regularBookshelves >= minShelves;
            int barX = px + 10;
            int barY = py + 45;
            int barW = 65;
            int barH = 4;
            ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xCC0A0614);
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xEE1A122B);

            float ratio;
            int fillMain;
            int fillHighlight;
            if (!unlocked) {
                ratio = Mth.clamp((float) this.menu.regularBookshelves / (float) minShelves, 0.0f, 1.0f);
                fillMain = 0xFF8C52E8;
                fillHighlight = 0xFFD2B0FF;
            } else if (frozen) {
                int maxSec = Math.max(1, this.menu.epiphanyMaxFreezeTicks() / 20);
                ratio = Mth.clamp((float) this.menu.countdownSeconds() / (float) maxSec, 0.0f, 1.0f);
                fillMain = 0xFF3FD8FF;
                fillHighlight = 0xFFC2FBFF;
            } else {
                int cycleSec = Math.max(1, this.menu.epiphanyCycleTicks() / 20);
                ratio = Mth.clamp((float) this.menu.countdownSeconds() / (float) cycleSec, 0.0f, 1.0f);
                fillMain = 0xFFC86BFF;
                fillHighlight = 0xFFFFDF8E;
            }
            int filledW = Math.round(barW * ratio);
            if (filledW > 0) {
                ctx.fill(barX, barY, barX + filledW, barY + barH, fillMain);
                ctx.fill(barX, barY, barX + filledW, barY + 1, fillHighlight);
            }

            int fx = i + FREEZE_BTN_X;
            int fy = j + FREEZE_BTN_Y;
            boolean canFreeze = this.menu.canAffordFreezeEpiphany();
            ctx.blit(BTN_FREEZE, fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canFreeze) {
                ctx.fill(fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (isOverFreezeButton(mouseX, mouseY)) {
                ctx.fill(fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0x2C90F5FF);
            }

            int rx = i + REROLL_BTN_X;
            int ry = j + REROLL_BTN_Y;
            boolean canReroll = this.menu.canAffordRerollEpiphany();
            ctx.blit(BTN_REROLL, rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!canReroll) {
                ctx.fill(rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, unlocked ? 0x60100C1A : 0xA60C0814);
            } else if (isOverRerollButton(mouseX, mouseY)) {
                ctx.fill(rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0x2CFFE082);
            }
        } finally {
            ctx.disableScissor();
        }
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
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
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
            var state = new TableTooltipContext(this.menu, this.scrollContainer, this.font, this.minecraft.level,
                regions, hoveredTomeButton(mouseX, mouseY), isCleanseTab(), isTomeTab(),
                isDisenchantAllConfirmPending(), canCleanseAllNow(), this::getTooltipFromContainerItem,
                this::buildResonanceBreakdownTooltip);
            if (TableNavigationTooltips.render(state, ctx, mouseX, mouseY)) return;
            if (TableCostTooltips.render(state, ctx, mouseX, mouseY)) return;
            if (TableOptionTooltips.render(state, ctx, mouseX, mouseY)) return;
            TablePanelTooltips.render(state, ctx, mouseX, mouseY);
        }
    }

    private void renderHeaderShelfHudBadge(GuiGraphicsExtractor ctx, int left, int top, int mouseX, int mouseY) {
        int bx = left + SHELF_HUD_X;
        int by = top + SHELF_HUD_Y;
        boolean hovered = isOverShelfHudBadge(mouseX, mouseY);
        int border = this.menu.crampedLibrary ? 0xFFB83A4B : (hovered ? 0xFFE5C46B : 0xFF563D78);
        ctx.fill(bx, by, bx + SHELF_HUD_W, by + SHELF_HUD_H, border);
        ctx.fill(bx + 1, by + 1, bx + SHELF_HUD_W - 1, by + SHELF_HUD_H - 1, 0xD0160F24);
        if (hovered) {
            ctx.fill(bx + 1, by + 1, bx + SHELF_HUD_W - 1, by + SHELF_HUD_H - 3, 0x22FFE082);
        }
        int broad = this.menu.totalLibraryBookshelves();
        int target = computeNextShelfMilestoneTarget();
        float ratio = target <= 0 ? 1.0f : Mth.clamp((float) broad / (float) target, 0.0f, 1.0f);
        int barX = bx + 2;
        int barY = by + SHELF_HUD_H - 3;
        int barW = SHELF_HUD_W - 4;
        ctx.fill(barX, barY, barX + barW, barY + 2, 0xFF0B0714);
        int fillW = Math.round(barW * ratio);
        if (fillW > 0) {
            int barColor = this.menu.crampedLibrary
                ? 0xFFE05263
                : (broad >= target ? 0xFFFFD369 : 0xFF49E2B4);
            ctx.fill(barX, barY, barX + fillW, barY + 2, barColor);
        }
    }

    private List<Component> buildResonanceBreakdownTooltip() {
        BlockPos pos = effectiveTablePos();
        var scan = this.minecraft != null && this.minecraft.level != null && pos != null
            ? chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.scanLibraryRitualContext(
                this.minecraft.level, pos, this.menu.shelfGatesConfig()) : null;
        return ResonanceTooltip.build(this.menu, scan);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Purple stitched header cloth spans y = [1.0 .. 15.0] (center = 8.0); place visual ink center at 7.6f!
        RowRenderSupport.renderCenteredYText(context, this.minecraft, this.title.getString(), 18, 7.6f, 0.94f, 190, 0xFFFFF0C8);

        if (shouldShowShelfHudBadge()) {
            int broadShelves = this.menu.totalLibraryBookshelves();
            int nextTarget = computeNextShelfMilestoneTarget();
            int gap = Math.max(0, nextTarget - broadShelves);
            String hudText = this.menu.crampedLibrary
                ? Component.translatable(
                    "gui.chiseled_enchanting_table.header.shelf_hud_cramped",
                    this.menu.regularBookshelves,
                    broadShelves
                ).getString()
                : (gap > 0
                    ? Component.translatable(
                        "gui.chiseled_enchanting_table.header.shelf_hud_gap",
                        this.menu.regularBookshelves,
                        broadShelves,
                        gap
                    ).getString()
                    : Component.translatable(
                        "gui.chiseled_enchanting_table.header.shelf_hud_max",
                        this.menu.regularBookshelves,
                        broadShelves
                    ).getString());
            int hudTextColor = this.menu.crampedLibrary ? 0xFFFF8A96 : (gap == 0 ? 0xFFFFE58F : 0xFFC8F7EA);
            RowRenderSupport.renderCenteredBoxText(
                context,
                this.minecraft,
                hudText,
                SHELF_HUD_X + 3,
                SHELF_HUD_W - 6,
                SHELF_HUD_Y + 4.8f,
                0.58f,
                hudTextColor
            );
        }

        if (!shouldShowSanctumPanel()) {
            return;
        }

        int lx = PANEL_X + 10;
        int ly = PANEL_Y;

        context.enableScissor(PANEL_X + 9, PANEL_Y + 9, PANEL_X + 110, PANEL_Y + 52);
        try {
            if (this.menu.isRitualActive()) {
                int queueCount = Math.max(1, this.menu.queuedRitualSteps().size());
                int remSeconds = (int) ((this.menu.ritualRemainingTicks() + 19L) / 20L);
                Component header = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.panel_title",
                    queueCount
                );
                drawAutoScaledText(context, header, lx, ly + 9, 0.78f, 99, 0xFFFFE082);

                Component timer = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.panel_timer",
                    formatClock(remSeconds)
                );
                drawAutoScaledText(context, timer, lx, ly + 19, 0.78f, 99, 0xFF8CFFF0);

                int sacLvls = this.menu.currentSacrificeSkipLevels();
                int rushPct = (int) Math.round(this.menu.currentRushSkipDegradeChance() * 100.0);
                boolean canSac = this.menu.canSacrificeSkipNow();
                boolean canRush = this.menu.canRushSkipNow();

                String sacLabel = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.btn_sacrifice",
                    sacLvls
                ).getString();
                String rushLabel = Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.btn_rush",
                    rushPct
                ).getString();

                RowRenderSupport.renderCenteredBoxText(
                    context,
                    this.minecraft,
                    sacLabel,
                    SKIP_SACRIFICE_BTN_X + 2,
                    SKIP_BTN_W - 4,
                    SKIP_SACRIFICE_BTN_Y + 7.0f,
                    0.68f,
                    canSac ? 0xFFA8FFD8 : 0xFF7D748D
                );
                RowRenderSupport.renderCenteredBoxText(
                    context,
                    this.minecraft,
                    rushLabel,
                    SKIP_RUSH_BTN_X + 2,
                    SKIP_BTN_W - 4,
                    SKIP_RUSH_BTN_Y + 7.0f,
                    0.68f,
                    canRush ? 0xFFFFB2A6 : 0xFF7D748D
                );
                return;
            }

            if (isTomeTab()) {
                Component header = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_title");
                drawAutoScaledText(context, header, lx, ly + 9, 0.8f, 99, 0xFFB9F6FF);
                Component shelves = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_shelves",
                    this.menu.qualifyingEnchantedBookshelves, this.menu.liveTotalLibraryBookshelves);
                drawAutoScaledText(context, shelves, lx, ly + 16, 0.7f, 99, 0xFFE6F7FF);
                Component tier = this.menu.nextRegenTierGap > 0
                    ? Component.translatable("gui.chiseled_enchanting_table.tomes.panel_regen_gap", this.menu.liveRegenTier + 1, this.menu.nextRegenTierGap)
                    : Component.translatable("gui.chiseled_enchanting_table.tomes.panel_regen_max", this.menu.liveRegenTier + 1);
                drawAutoScaledText(context, tier, lx, ly + 23, 0.64f, 99, 0xFFA8FFC4);
                String interval = this.menu.regenIntervalTicks > 0 ? EnchantementListWidget.formatTomeEta(this.menu.regenIntervalTicks) : "--:--";
                Component rate = Component.translatable("gui.chiseled_enchanting_table.tomes.panel_rate", interval);
                drawAutoScaledText(context, rate, lx, ly + 30, 0.64f, 99, 0xFFA8FFC4);
                return;
            }

            if (isCleanseTab()) {
                ItemStack target = this.menu.get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                    this.menu.libraryResonanceConfig(),
                    target,
                    this.menu.disenchantRefundScale(),
                    this.menu.disenchantExternalRefundScale()
                );

                Component header = Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_title");
                drawAutoScaledText(context, header, lx, ly + 10, 0.85f, 98, 0xFF9EFFDB);

                Component countLine = totalTraits > 0
                    ? Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_traits", totalTraits, nonCurse)
                    : Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_idle");
                drawAutoScaledText(context, countLine, lx, ly + 22, 0.82f, 98, totalTraits > 0 ? 0xFFE6FFF6 : 0xFFA2C4BC);

                Component refundLine = Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_refund", refund);
                drawAutoScaledText(context, refundLine, lx, ly + 34, 0.80f, 80, refund > 0 ? 0xFF7CFFA6 : 0xFF8FB8AD);
                return;
            }

            int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
            boolean unlocked = this.menu.regularBookshelves >= minShelves;
            boolean frozen = this.menu.isEpiphanyFrozen();

            Component resLine = Component.translatable("gui.chiseled_enchanting_table.resonance.badge", this.menu.xpDiscountPercent);
            drawAutoScaledText(context, resLine, lx, ly + 10, 0.85f, 98, this.menu.xpDiscountPercent > 0 ? 0xFF7FFFB2 : 0xFFE2D8F5);

            if (!this.menu.isEpiphanyEnabled()) {
                Component shelvesLine = Component.translatable(
                    "gui.chiseled_enchanting_table.tomes.panel_shelves",
                    this.menu.chiseledBookshelves,
                    this.menu.totalLibraryBookshelves()
                );
                drawAutoScaledText(context, shelvesLine, lx, ly + 22, 0.78f, 98, 0xFFD4BEFF);
                return;
            }

            if (unlocked) {
                int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
                int maxSlots = Math.max(1, this.menu.epiphanyMaxSlots());
                Component statLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_stats", pct, maxSlots);
                drawAutoScaledText(context, statLine, lx, ly + 22, 0.85f, 98, 0xFFFFDF7E);

                String clock = formatClock(this.menu.countdownSeconds());
                Component timerLine = frozen
                    ? Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_frozen", clock)
                    : Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_cycle", clock);
                drawAutoScaledText(context, timerLine, lx, ly + 34, 0.82f, 65, frozen ? 0xFF74F3FF : 0xFFFFF2FF);
            } else {
                Component lockedLine = Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.panel_locked",
                    this.menu.regularBookshelves,
                    minShelves
                );
                drawAutoScaledText(context, lockedLine, lx, ly + 22, 0.85f, 98, 0xFFD4BEFF);

                int wakePct = Math.min(100, Math.round(this.menu.regularBookshelves * 100.0f / minShelves));
                Component wakeLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_wake", wakePct);
                drawAutoScaledText(context, wakeLine, lx, ly + 34, 0.82f, 65, 0xFFB8A2E0);
            }
        } finally {
            context.disableScissor();
        }
    }


}
