package chiseled_enchanting_table.gui;

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
    private long disenchantConfirmUntilMs = 0L;
    private ItemStack disenchantConfirmSnapshot = ItemStack.EMPTY;
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
                if (handler.isClassic103Mode() && this.scrollContainer.tabMode() != UiTabMode.INSCRIBE) {
                    this.scrollContainer.setTabMode(UiTabMode.INSCRIBE);
                }
                this.scrollContainer.updateEntry(handler.getEffectiveEnchantableItem(), handler.get_cost_item());
            }
        };
        this.scrollContainer = new EnchantementListWidget(
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
            UiTabMode targetTab = this.menu.isClassic103Mode()
                ? UiTabMode.INSCRIBE
                : (wasReturning ? this.savedTabMode : this.scrollContainer.tabMode());
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
            mc.gui.setOverlayMessage(
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
        if (this.disenchantConfirmUntilMs <= 0L) {
            return false;
        }
        long now = net.minecraft.util.Util.getMillis();
        ItemStack current = this.menu.get_enchantable_item();
        if (now > this.disenchantConfirmUntilMs
            || current.isEmpty()
            || !ItemStack.isSameItemSameComponents(current, this.disenchantConfirmSnapshot)) {
            this.disenchantConfirmUntilMs = 0L;
            this.disenchantConfirmSnapshot = ItemStack.EMPTY;
            return false;
        }
        return true;
    }

    public void requestDisenchantAll(boolean shiftBypass) {
        ItemStack target = this.menu.get_enchantable_item();
        if (!shiftBypass && requiresDisenchantAllConfirm(target) && !isDisenchantAllConfirmPending()) {
            this.disenchantConfirmUntilMs = net.minecraft.util.Util.getMillis() + DISENCHANT_CONFIRM_WINDOW_MS;
            this.disenchantConfirmSnapshot = target.copy();
            this.lastConfirmPending = true;
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.85F));
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(
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
        this.disenchantConfirmUntilMs = 0L;
        this.disenchantConfirmSnapshot = ItemStack.EMPTY;
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

    private boolean isOverShelfHudBadge(double mouseX, double mouseY) {
        if (this.menu.isClassic103Mode()) return false;
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
        if (this.menu.isClassic103Mode()) return false;
        int x0 = this.leftPos + TAB_INSCRIBE_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private boolean isOverTomeTab(double mouseX, double mouseY) {
        if (this.menu.isClassic103Mode()) return false;
        int x0 = this.leftPos + TAB_TOMES_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private boolean isOverSacrificeButton(double mouseX, double mouseY) {
        if (this.menu.isClassic103Mode() || this.menu.isCleansePageUnlocked()) return false;
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

    public List<net.minecraft.client.renderer.Rect2i> extraViewerExclusionAreas() {
        List<net.minecraft.client.renderer.Rect2i> areas = new ArrayList<>(5);
        if (!this.menu.isClassic103Mode()) {
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
        if (this.menu.isClassic103Mode() || !isInscribeTab() || this.menu.isRitualActive()) return false;
        int x0 = this.leftPos + FREEZE_BTN_X;
        int y0 = this.topPos + FREEZE_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        if (this.menu.isClassic103Mode() || !isInscribeTab() || this.menu.isRitualActive()) return false;
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
        if (this.menu.isClassic103Mode() && !this.menu.isRitualActive()) return false;
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

    // Modal overlay contract (audit GUI-01): while configOverlay/grimoireOverlay is open,
    // EVERY input callback is forwarded to it and the event is consumed here, regardless of
    // the overlay's own return value — input must never fall through to the container
    // beneath. Scroll/drag/release return the overlay result because those handshakes care
    // about it, but the event still cannot reach the parent screen. ESC-to-close is each
    // overlay's own responsibility (its onClose clears the field via callback).
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.configOverlay != null) {
            this.configOverlay.keyPressed(event);
            return true;
        }
        if (this.grimoireOverlay != null) {
            this.grimoireOverlay.keyPressed(event);
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

    private static String formatClock(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int mins = clamped / 60;
        int secs = clamped % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
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
        if (this.menu.isClassic103Mode()) {
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

        if (!this.menu.isCleansePageUnlocked()) {
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
        if (!this.menu.isClassic103Mode()) {
            renderHeaderShelfHudBadge(ctx, i, j, mouseX, mouseY);
        }

        int px = i + PANEL_X;
        int py = j + PANEL_Y;
        if (this.menu.isClassic103Mode() && !this.menu.isRitualActive()) {
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
            if (isOverConfigButton(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("config.chiseled_enchanting_table.open_tip.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("config.chiseled_enchanting_table.open_tip.sub").withStyle(ChatFormatting.GRAY)
                );
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverGrimoireButton(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chiseled_enchanting_table.grimoire.open_btn.desc").withStyle(ChatFormatting.GRAY)
                );
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverShelfHudBadge(mouseX, mouseY)) {
                ctx.setTooltipForNextFrame(
                    this.font,
                    buildResonanceBreakdownTooltip(),
                    Optional.empty(),
                    mouseX,
                    mouseY,
                    (Identifier) null
                );
                return;
            }
            if (isOverInscribeTab(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.desc").withStyle(ChatFormatting.GRAY)
                );
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverCleanseTab(mouseX, mouseY) && !this.menu.isCleansePageUnlocked()) {
                ctx.setTooltipForNextFrame(this.font, List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(ChatFormatting.RED)), Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverTomeTab(mouseX, mouseY)) {
                List<Component> tip = this.menu.isTomePageUnlocked()
                    ? List.of(Component.translatable("gui.chiseled_enchanting_table.tab.tomes.title").withStyle(ChatFormatting.AQUA), Component.translatable("gui.chiseled_enchanting_table.tab.tomes.desc").withStyle(ChatFormatting.GRAY))
                    : List.of(Component.translatable("gui.chiseled_enchanting_table.tab.locked_tooltip").withStyle(ChatFormatting.RED));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverSacrificeButton(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chiseled_enchanting_table.tomes.sacrifice_desc", this.menu.sacrificeProgress, this.menu.sacrificeRequired).withStyle(ChatFormatting.GRAY)
                );
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            int tomeBtn = hoveredTomeButton(mouseX, mouseY);
            if (tomeBtn >= 0) {
                String key = switch (tomeBtn) {
                    case 0 -> "gui.chiseled_enchanting_table.tomes.tip_next";
                    case 1 -> "gui.chiseled_enchanting_table.tomes.tip_full";
                    case 2 -> "gui.chiseled_enchanting_table.tomes.tip_permanent";
                    case 3 -> "gui.chiseled_enchanting_table.tomes.tip_unlock";
                    default -> "gui.chiseled_enchanting_table.tomes.tip_lock_all";
                };
                var lrCfg = this.menu.libraryResonanceConfig();
                var tipText = tomeBtn == 3
                    ? Component.translatable(key,
                        lrCfg.earlyUnlockXpLevels(),
                        lrCfg.earlyUnlockGritCount())
                    : Component.translatable(key);
                ctx.setTooltipForNextFrame(this.font, List.of(tipText.withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            var hoveredTome = this.scrollContainer.hoveredTomeEntry(mouseX, mouseY);
            if (hoveredTome != null) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(EnchantementListWidget.tomeTitle(hoveredTome, this.menu.player)).withStyle(ChatFormatting.GOLD));
                var wearImmuneEntry = hoveredTome.enchantments().stream()
                    .filter(e -> EnchantementListWidget.isWearImmuneEnchantmentId(e.enchantment_id(), this.menu.player))
                    .findFirst();
                boolean purelyImmune = hoveredTome.wear_immune()
                    && hoveredTome.wear_used() <= 0
                    && hoveredTome.remaining_charges() >= hoveredTome.effective_max_uses();
                if (wearImmuneEntry.isPresent() && hoveredTome.wear_immune()) {
                    var immuneId = wearImmuneEntry.get().enchantment_id();
                    EnchantementListWidget.findEnchantment(immuneId, this.menu.player).ifPresent(h -> {
                        tip.add(net.minecraft.world.item.enchantment.Enchantment.getFullname(h, wearImmuneEntry.get().enchantment_level())
                            .copy().withStyle(ChatFormatting.GRAY));
                        String descKey = "enchantment." + immuneId.getNamespace() + "." + immuneId.getPath() + ".desc";
                        if (net.minecraft.locale.Language.getInstance().has(descKey)) {
                            tip.add(Component.translatable(descKey).withStyle(ChatFormatting.DARK_GRAY));
                        }
                    });
                    tip.add(Component.translatable("item.chiseled_enchanting_table.master_tome_infinite")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (!purelyImmune) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.tomes.charges",
                        hoveredTome.remaining_charges(),
                        hoveredTome.effective_max_uses()
                    ).withStyle(ChatFormatting.AQUA));
                }
                if (!hoveredTome.last_used_enchant().isBlank() && hoveredTome.last_used_level() > 0) {
                    var lastId = Identifier.tryParse(hoveredTome.last_used_enchant());
                    String lastName = lastId == null ? hoveredTome.last_used_enchant()
                        : EnchantementListWidget.findEnchantment(lastId, this.menu.player)
                            .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, hoveredTome.last_used_level()).getString())
                            .orElse(hoveredTome.last_used_enchant());
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.last_used", lastName).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (hoveredTome.deplete_outcome() > 0 && !hoveredTome.deplete_enchant().isBlank()) {
                    var depId = Identifier.tryParse(hoveredTome.deplete_enchant());
                    java.util.function.IntFunction<String> depName = lvl -> depId == null
                        ? hoveredTome.deplete_enchant()
                        : EnchantementListWidget.findEnchantment(depId, this.menu.player)
                            .map(h -> net.minecraft.world.item.enchantment.Enchantment.getFullname(h, lvl).getString())
                            .orElse(hoveredTome.deplete_enchant());
                    int depLevel = hoveredTome.deplete_level();
                    var depleteLine = switch (hoveredTome.deplete_outcome()) {
                        case 1 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_step_down", depName.apply(Math.max(1, depLevel - 1)));
                        case 2 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_depleted", depName.apply(depLevel));
                        case 3 -> Component.translatable("gui.chiseled_enchanting_table.tomes.deplete_lose", depName.apply(depLevel));
                        case 4 -> Component.translatable("gui.chiseled_enchanting_table.tomes.consume_lose", depName.apply(depLevel));
                        case 5 -> Component.translatable("gui.chiseled_enchanting_table.tomes.consume_depleted", depName.apply(depLevel));
                        default -> null;
                    };
                    if (depleteLine != null) {
                        tip.add(depleteLine.withStyle(ChatFormatting.GOLD));
                    }
                }
                if (hoveredTome.locked()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.locked", Component.translatable("gui.chiseled_enchanting_table.tomes.lock_mode." + hoveredTome.lock_mode()).getString(), hoveredTome.lock_owner_name()).withStyle(ChatFormatting.RED));
                    int lockEta = hoveredTome.lock_mode() == 0 ? hoveredTome.eta_next_ticks()
                        : hoveredTome.lock_mode() == 1 ? hoveredTome.eta_full_ticks() : -1;
                    if (lockEta >= 0) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_lock_remaining", EnchantementListWidget.formatTomeEta(lockEta)).withStyle(ChatFormatting.GOLD));
                    }
                }
                if (hoveredTome.charging()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_next", EnchantementListWidget.formatTomeEta(hoveredTome.eta_next_ticks())).withStyle(ChatFormatting.GREEN));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.tip_eta_full", EnchantementListWidget.formatTomeEta(hoveredTome.eta_full_ticks())).withStyle(ChatFormatting.GREEN));
                }
                tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.select_hint").withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverSacrificeSkipButton(mouseX, mouseY)) {
                int lvls = this.menu.currentSacrificeSkipLevels();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_title")
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_sacrifice_cost",
                    lvls,
                    this.menu.player.experienceLevel
                ).withStyle(this.menu.player.experienceLevel >= lvls ? ChatFormatting.GREEN : ChatFormatting.RED));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_safe")
                    .withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_sacrifice_currency")
                    .withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverRushSkipButton(mouseX, mouseY)) {
                double riskPct = this.menu.currentRushSkipDegradeChance() * 100.0;
                var rCfg = this.menu.ritualConfig();
                int capPct = (int) Math.round(100.0 * (this.menu.ritualUsedInfinityTome()
                    ? rCfg.maxRushDegradeInfinity()
                    : rCfg.maxRushDegradeNormal()));
                int infCapPct = (int) Math.round(100.0 * rCfg.maxRushDegradeInfinity());
                boolean hasTome = this.menu.hasAnyMasterTomeInRange();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_title")
                    .withStyle(ChatFormatting.RED));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_rush_risk",
                    String.format(java.util.Locale.ROOT, "%.1f", riskPct),
                    capPct
                ).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.tip_rush_priority")
                    .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.ritual.tip_rush_currency",
                    infCapPct
                ).withStyle(ChatFormatting.DARK_GRAY));
                if (!hasTome) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.ritual.no_tome_cannot_skip")
                        .withStyle(ChatFormatting.RED));
                }
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverCleanseTab(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.title").withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.chiseled_enchanting_table.tab.cleanse.desc").withStyle(ChatFormatting.GRAY)
                );
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }

            ItemStack hoveredCost = this.scrollContainer.hoveredCostItem(mouseX, mouseY);
            if (hoveredCost != null && !hoveredCost.isEmpty()) {
                List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(hoveredCost));
                List<List<ItemStack>> options = this.scrollContainer.hoveredCostOptions(mouseX, mouseY);
                if (options != null && !options.isEmpty()) {
                    lines.add(Component.translatable("gui.chiseled_enchanting_table.cost_alternatives.header")
                        .withStyle(ChatFormatting.GOLD));
                    for (List<ItemStack> bundle : options) {
                        if (bundle.isEmpty()) continue;
                        MutableComponent bullet = Component.literal(" \u2022 ").withStyle(ChatFormatting.GRAY);
                        for (int idx = 0; idx < bundle.size(); idx++) {
                            if (idx > 0) {
                                bullet.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
                            }
                            ItemStack st = bundle.get(idx);
                            bullet.append(Component.literal(st.getCount() + "\u00d7 ")
                                .append(st.getHoverName())
                                .withStyle(bundle.size() == 1 && options.size() > 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
                        }
                        lines.add(bullet);
                    }
                }
                Identifier hoveredEnchId = this.scrollContainer.hoveredEnchantmentId(mouseX, mouseY);
                var hoveredOptType = this.scrollContainer.hoveredOptionType(mouseX, mouseY);
                if (hoveredEnchId != null && hoveredOptType != null) {
                    int storedLvl = this.menu.storedMasterLevel(hoveredEnchId);
                    int specBonus = this.menu.specificEnchantDiscountBonus(hoveredEnchId);
                    int totalDisc = this.menu.effectiveXpDiscountPercent(hoveredEnchId, hoveredOptType);
                    if (storedLvl > 0 && specBonus > 0) {
                        int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
                            this.minecraft.level,
                            hoveredEnchId
                        );
                        lines.add(Component.translatable(
                            "gui.chiseled_enchanting_table.resonance.specific_line",
                            storedLvl,
                            maxLvl,
                            specBonus,
                            totalDisc
                        ).withStyle(ChatFormatting.AQUA));
                    }
                }
                ctx.setTooltipForNextFrame(
                    this.font,
                    lines,
                    hoveredCost.getTooltipImage(),
                    mouseX,
                    mouseY,
                    hoveredCost.get(DataComponents.TOOLTIP_STYLE)
                );
                return;
            }
            var hoveredXpEntry = this.scrollContainer.hoveredXpBadgeEntry(mouseX, mouseY);
            if (hoveredXpEntry != null) {
                var req = hoveredXpEntry.xp_requirement;
                int playerLvl = this.menu.player.experienceLevel;
                boolean met = hoveredXpEntry.xpConditionMet();
                List<Component> xpTip = new ArrayList<>();
                if (req.isRefund()) {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.title")
                        .withStyle(ChatFormatting.GREEN));
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.amount", req.refundPoints())
                        .withStyle(ChatFormatting.AQUA));
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.refund.hint",
                        (int) Math.round(this.menu.disenchantExternalRefundScale() * 100.0)
                    ).withStyle(ChatFormatting.GRAY));
                } else if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
                    if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(hoveredXpEntry.enchantment_id)) {
                        xpTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.xp_mode.free_tome",
                            this.menu.shelfGatesConfig().tier1Bookshelves()
                        ).withStyle(ChatFormatting.GREEN));
                    } else {
                        xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.free")
                            .withStyle(ChatFormatting.GREEN));
                    }
                } else if (req.vanillaTierMode()) {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.vanilla.title")
                        .withStyle(ChatFormatting.GREEN));
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.vanilla.threshold",
                        req.requiredLevel(),
                        playerLvl
                    ).withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.vanilla.consume",
                        req.deductedLevels()
                    ).withStyle(ChatFormatting.GOLD));
                    if (req.discountPercent() > 0) {
                        xpTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.xp_mode.discount",
                            req.discountPercent(),
                            req.rawRequiredLevel()
                        ).withStyle(ChatFormatting.AQUA));
                    }
                } else {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.full.title")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                    xpTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.xp_mode.full.consume",
                        req.deductedLevels(),
                        playerLvl
                    ).withStyle(met ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED));
                    if (req.discountPercent() > 0) {
                        xpTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.xp_mode.discount",
                            req.discountPercent(),
                            req.rawRequiredLevel()
                        ).withStyle(ChatFormatting.AQUA));
                    }
                    if (hoveredXpEntry.option_type == OptionType.ENCHANTMENT) {
                        xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.full.master_hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                ctx.setTooltipForNextFrame(this.font, xpTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            var specialEntry = this.scrollContainer.hoveredSpecialRowEntry(mouseX, mouseY);
            if (specialEntry != null) {
                if (specialEntry.discovery_locked) {
                    String lockedOut = this.menu.syncedTransformationOutputName(specialEntry.enchantment_id);
                    List<Component> discTip = new ArrayList<>();
                    discTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.discovery_locked.tip_title",
                        lockedOut
                    ).withStyle(ChatFormatting.GOLD));
                    discTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.discovery_locked.tip_desc",
                        lockedOut
                    ).withStyle(ChatFormatting.YELLOW));
                    ctx.setTooltipForNextFrame(this.font, discTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
                if (specialEntry.shelf_locked && specialEntry.enchantment != null) {
                    String lockedName = net.minecraft.world.item.enchantment.Enchantment
                        .getFullname(specialEntry.enchantment, specialEntry.enchant_level)
                        .getString();
                    List<Component> lockTip = new ArrayList<>();
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_title",
                        lockedName
                    ).withStyle(ChatFormatting.GOLD));
                    if (this.menu.regularBookshelves < specialEntry.required_bookshelves) {
                        lockTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.shelf_locked.tip_req",
                            this.menu.regularBookshelves,
                            specialEntry.required_bookshelves
                        ).withStyle(ChatFormatting.RED));
                    } else {
                        lockTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.shelf_locked.tip_blank_cap"
                        ).withStyle(ChatFormatting.YELLOW));
                    }
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_bypass"
                    ).withStyle(ChatFormatting.GRAY));
                    ctx.setTooltipForNextFrame(this.font, lockTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
                if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(specialEntry.enchantment_id)) {
                    var dtTier = this.menu.depletedTomeTier();
                    var sg = this.menu.shelfGatesConfig();
                    int shelves = this.menu.regularBookshelves;
                    int qualifyingEnchants = this.menu.qualifyingChiseledEnchantments();
                    List<Component> dtTip = new ArrayList<>();
                    dtTip.add(Component.translatable("gui.chiseled_enchanting_table.depleted_tome.tip_title")
                        .withStyle(ChatFormatting.GOLD));
                    dtTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.depleted_tome.tip_g1",
                        shelves,
                        sg.tier1Bookshelves()
                    ).withStyle(dtTier.ordinal() >= 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    dtTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.depleted_tome.tip_g2",
                        shelves,
                        sg.tier2Bookshelves()
                    ).withStyle(dtTier.ordinal() >= 2 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                    dtTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.depleted_tome.tip_g3",
                        shelves,
                        sg.tier2Bookshelves(),
                        qualifyingEnchants,
                        sg.tier3QualifyingEnchantments()
                    ).withStyle(dtTier == chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.DepletedTomeTier.TIER_3_FREE
                        ? ChatFormatting.LIGHT_PURPLE
                        : ChatFormatting.GRAY));
                    ctx.setTooltipForNextFrame(this.font, dtTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
                if (specialEntry.is_category_header && !specialEntry.cleanse_theme) {
                    int maxLvl = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.resolveMaxLevel(
                        this.minecraft.level,
                        specialEntry.enchantment_id
                    );
                    var sg = this.menu.shelfGatesConfig();
                    int shelves = this.menu.regularBookshelves;
                    int storedLvl = this.menu.storedMasterLevel(specialEntry.enchantment_id);
                    int existingLvl = this.menu.currentTargetEnchantLevel(specialEntry.enchantment_id);
                    int directCap = this.menu.maxUnlockedDirectTier(specialEntry.enchantment_id, maxLvl);
                    int baseCap = existingLvl >= 1
                        ? directCap
                        : this.menu.maxBlankWoodenTier(specialEntry.enchantment_id, maxLvl);
                    int effectiveCap = Math.min(maxLvl, Math.max(storedLvl, baseCap));
                    List<Component> catTip = new ArrayList<>();
                    catTip.add(Component.literal(specialEntry.category_title + "  " + specialEntry.category_badge)
                        .withStyle(ChatFormatting.GOLD));
                    catTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.category.tip_cap",
                        effectiveCap,
                        maxLvl,
                        shelves,
                        sg.maxDirectBookshelves()
                    ).withStyle(effectiveCap >= maxLvl ? ChatFormatting.GREEN : ChatFormatting.AQUA));
                    if (effectiveCap < maxLvl) {
                        int nextTier = effectiveCap + 1;
                        int reqShelves = this.menu.requiredBookshelvesForDirectTier(
                            specialEntry.enchantment_id,
                            nextTier,
                            maxLvl
                        );
                        if (shelves < reqShelves) {
                            catTip.add(Component.translatable(
                                "gui.chiseled_enchanting_table.category.tip_next",
                                nextTier,
                                reqShelves,
                                sg.maxDirectBookshelves()
                            ).withStyle(ChatFormatting.YELLOW));
                        } else {
                            catTip.add(Component.translatable(
                                "gui.chiseled_enchanting_table.shelf_locked.tip_blank_cap"
                            ).withStyle(ChatFormatting.YELLOW));
                        }
                    }
                    catTip.add(Component.translatable("gui.chiseled_enchanting_table.category.tip_master")
                        .withStyle(ChatFormatting.DARK_GRAY));
                    ctx.setTooltipForNextFrame(this.font, catTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
            }
            var hoveredRow = this.scrollContainer.hoveredActionRowEntry(mouseX, mouseY);
            if (hoveredRow != null) {
                List<Component> rowTip = new ArrayList<>();
                AvailableOption hoverOpt = new AvailableOption(
                    hoveredRow.option_type,
                    hoveredRow.enchantment_id,
                    hoveredRow.enchant_level
                );
                rowTip.add(Component.literal(this.scrollContainer.displayName(hoverOpt)).withStyle(ChatFormatting.GOLD));
                boolean fromBook = this.menu.get_cost_item().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                    && hoveredRow.option_type == OptionType.ENCHANTMENT;
                var lrCfg = this.menu.libraryResonanceConfig();
                if (fromBook) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.book_transfer")
                        .withStyle(ChatFormatting.GREEN));
                } else if (hoveredRow.is_transformation) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.transformation")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (hoveredRow.option_type == OptionType.TREASURE_EPIPHANY) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.epiphany")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (hoveredRow.option_type == OptionType.DISENCHANT_ALL
                    || hoveredRow.option_type == OptionType.DOWNGRADE_ENCHANTMENT) {
                    int extPct = (int) Math.round(this.menu.disenchantExternalRefundScale() * 100.0);
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.cleanse_provenance", extPct)
                        .withStyle(ChatFormatting.AQUA));
                } else if (hoveredRow.option_type == OptionType.STRIP_SINGLE_ENCHANTMENT
                    || hoveredRow.option_type == OptionType.PURGE_CURSE) {
                    rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.cleanse_targeted")
                        .withStyle(ChatFormatting.AQUA));
                } else {
                    boolean masterBacked = this.menu.storedMasterLevel(hoveredRow.enchantment_id) >= hoveredRow.enchant_level;
                    if (masterBacked) {
                        rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.master_tome")
                            .withStyle(ChatFormatting.GREEN));
                        boolean targetIsBook = hoveredRow.enchantable_item.is(net.minecraft.world.item.Items.BOOK)
                            || hoveredRow.enchantable_item.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                        int wearCost = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.wearCostForTarget(lrCfg, targetIsBook);
                        if (wearCost > 0) {
                            int liveRemaining = this.menu.masterTomeRemainingCharges(hoveredRow.enchantment_id, hoveredRow.enchant_level);
                            if (liveRemaining == chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.IMMORTAL_CHARGES) {
                                rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.tome_wear_infinite")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                            } else {
                                int maxUses = Math.max(1, lrCfg.maxTomeUses());
                                float ratio = (float) liveRemaining / (float) maxUses;
                                ChatFormatting chargeColor = ratio > 0.5f
                                    ? ChatFormatting.AQUA
                                    : (ratio > 0.25f ? ChatFormatting.GOLD : ChatFormatting.RED);
                                rowTip.add(Component.translatable(
                                    "gui.chiseled_enchanting_table.pricing.tome_wear_live",
                                    liveRemaining,
                                    maxUses,
                                    wearCost
                                ).withStyle(chargeColor));
                                if (lrCfg.tomeRegenEnabled()) {
                                    int nearShelves = this.menu.totalLibraryBookshelves();
                                    double regenSec = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                                        .computeRegenSeconds(lrCfg, nearShelves);
                                    int regenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                                        .regenTierIndex(lrCfg, nearShelves);
                                    Component tierBadge = switch (regenTier) {
                                        case 2 -> Component.translatable(
                                            "gui.chiseled_enchanting_table.pricing.tome_regen_tier2",
                                            lrCfg.tomeRegenTier2Shelves()
                                        );
                                        case 1 -> Component.translatable(
                                            "gui.chiseled_enchanting_table.pricing.tome_regen_tier1",
                                            lrCfg.tomeRegenTier1Shelves()
                                        );
                                        default -> Component.translatable(
                                            "gui.chiseled_enchanting_table.pricing.tome_regen_tier0",
                                            lrCfg.tomeRegenTier1Shelves()
                                        );
                                    };
                                    rowTip.add(Component.translatable(
                                        "gui.chiseled_enchanting_table.pricing.tome_regen_rate",
                                        String.format(java.util.Locale.ROOT, "%.1f", regenSec / 1200.0),
                                        nearShelves,
                                        tierBadge
                                    ).withStyle(regenTier >= 2
                                        ? ChatFormatting.GOLD
                                        : (regenTier == 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
                                }
                            }
                        }
                    } else if (hoveredRow.existingSameLevel.isPresent() && hoveredRow.existingSameLevel.getAsInt() > 0) {
                        rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.incremental")
                            .withStyle(ChatFormatting.AQUA));
                    } else {
                        rowTip.add(Component.translatable("gui.chiseled_enchanting_table.pricing.full_ritual")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                }
                if (hoveredRow.conflict_refund_points > 0) {
                    rowTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.pricing.conflict_refund",
                        hoveredRow.conflict_refund_points
                    ).withStyle(ChatFormatting.GREEN));
                }
                int rowDisc = this.menu.effectiveXpDiscountPercent(hoveredRow.enchantment_id, hoveredRow.option_type);
                if (rowDisc > 0 && !hoveredRow.xp_requirement.isRefund() && !fromBook) {
                    rowTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.pricing.resonance_discount",
                        rowDisc
                    ).withStyle(ChatFormatting.DARK_AQUA));
                }
                ctx.setTooltipForNextFrame(this.font, rowTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverCleanseAllButton(mouseX, mouseY)) {
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                    this.menu.libraryResonanceConfig(),
                    this.menu.get_enchantable_item(),
                    this.menu.disenchantRefundScale(),
                    this.menu.disenchantExternalRefundScale()
                );
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_all_title")
                    .withStyle(ChatFormatting.GREEN));
                if (canCleanseAllNow()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.amount", refund)
                        .withStyle(ChatFormatting.AQUA));
                    if (isDisenchantAllConfirmPending()) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.confirm_prompt")
                            .withStyle(ChatFormatting.GOLD));
                    } else if (requiresDisenchantAllConfirm(this.menu.get_enchantable_item())) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.confirm_hint_shift")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                } else {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.empty_line1")
                        .withStyle(ChatFormatting.RED));
                }
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_all_hint")
                    .withStyle(ChatFormatting.GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverFreezeButton(mouseX, mouseY)) {
                int minShelves = this.menu.epiphanyMinBookshelves();
                int addMins = Math.max(1, this.menu.epiphanyFreezeDurationTicks() / 1200);
                int maxMins = Math.max(1, this.menu.epiphanyMaxFreezeTicks() / 1200);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.freeze_title", addMins)
                    .withStyle(ChatFormatting.AQUA));
                if (this.menu.regularBookshelves < minShelves) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.progress",
                        this.menu.regularBookshelves,
                        minShelves
                    ).withStyle(ChatFormatting.RED));
                } else {
                    if (this.menu.isEpiphanyFrozen()) {
                        tip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.epiphany.frozen_remaining",
                            formatClock(this.menu.countdownSeconds()),
                            maxMins
                        ).withStyle(ChatFormatting.AQUA));
                    }
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.freeze_cost",
                        this.menu.epiphanyFreezeXpLevels()
                    ).withStyle(this.menu.canAffordFreezeEpiphany() ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                for (ItemStack req : this.menu.epiphanyFreezeCosts()) {
                    tip.add(Component.literal(" \u2022 " + req.getCount() + "\u00d7 ")
                        .append(req.getHoverName())
                        .withStyle(ChatFormatting.GRAY));
                }
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.freeze_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverRerollButton(mouseX, mouseY)) {
                int minShelves = this.menu.epiphanyMinBookshelves();
                int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
                int maxSlots = Math.max(1, this.menu.epiphanyMaxSlots());
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.reroll_title", pct)
                    .withStyle(ChatFormatting.GOLD));
                if (this.menu.regularBookshelves < minShelves) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.progress",
                        this.menu.regularBookshelves,
                        minShelves
                    ).withStyle(ChatFormatting.RED));
                } else {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.active", pct)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.slots_line", maxSlots)
                        .withStyle(ChatFormatting.GOLD));
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.reroll_cost",
                        this.menu.epiphanyRerollXpLevels()
                    ).withStyle(this.menu.canAffordRerollEpiphany() ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                for (ItemStack req : this.menu.epiphanyRerollCosts()) {
                    tip.add(Component.literal(" \u2022 " + req.getCount() + "\u00d7 ")
                        .append(req.getHoverName())
                        .withStyle(ChatFormatting.GRAY));
                }
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.auto_hint",
                    Math.max(1, Math.round(this.menu.epiphanyCycleTicks() / 1200.0f))
                ).withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverSanctumPanel(mouseX, mouseY)) {
                if (isTomeTab()) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.panel_title").withStyle(ChatFormatting.AQUA));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.panel_shelves", this.menu.qualifyingEnchantedBookshelves, this.menu.liveTotalLibraryBookshelves).withStyle(ChatFormatting.GRAY));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.tomes.unlock_progress", this.menu.qualifyingEnchantedBookshelves, this.menu.tomeRequiredShelves).withStyle(this.menu.isTomePageUnlocked() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                    var sel = this.scrollContainer.selectedTomeEntry();
                    if (sel != null) tip.add(Component.literal(EnchantementListWidget.tomeTitle(sel, this.menu.player)).withStyle(ChatFormatting.GOLD));
                    ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
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
                    List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_title")
                        .withStyle(ChatFormatting.AQUA));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_free")
                        .withStyle(ChatFormatting.GREEN));
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_strip")
                        .withStyle(ChatFormatting.GRAY));
                    if (this.menu.disenchantAllowCursePurge()) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_rule_curse")
                            .withStyle(ChatFormatting.GOLD));
                    }
                    if (totalTraits > 0) {
                        tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_stats", nonCurse, refund)
                            .withStyle(ChatFormatting.AQUA));
                    }
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.cleanse.panel_resonance",
                        this.menu.libraryResonanceConfig().maxSpecificEnchantBonusPercent()
                    ).withStyle(ChatFormatting.DARK_GRAY));
                    ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
                ctx.setTooltipForNextFrame(
                    this.font,
                    buildResonanceBreakdownTooltip(),
                    Optional.empty(),
                    mouseX,
                    mouseY,
                    (Identifier) null
                );
            }
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
        int woodenBonus = this.menu.woodenXpDiscountPercent();
        int chiseledBonus = this.menu.globalMasteryDiscountPercent();
        double totalUnits = this.menu.totalMasteryUnits();
        double affinityUnits = this.menu.affinityMasteryUnits(this.menu.get_enchantable_item());
        int minShelves = this.menu.epiphanyMinBookshelves();
        var sg = this.menu.shelfGatesConfig();
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.title",
            this.menu.xpDiscountPercent,
            this.menu.libraryResonanceConfig().maxTotalXpDiscountPercent()
        ).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.broad_breakdown",
            this.menu.totalLibraryBookshelves(),
            this.menu.regularBookshelves,
            this.menu.emptyChiseledBookshelves,
            this.menu.chiseledBookshelves
        ).withStyle(ChatFormatting.YELLOW));
        if (this.menu.crampedLibrary) {
            tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.cramped")
                .withStyle(ChatFormatting.RED));
        }
        int minDiscShelves = this.menu.libraryResonanceConfig().minDiscountBookshelves();
        if (this.menu.regularBookshelves < minDiscShelves) {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.wooden_locked",
                this.menu.regularBookshelves,
                minDiscShelves,
                minDiscShelves
            ).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", this.menu.regularBookshelves, woodenBonus)
                .withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.chiseled_mastery",
            String.format(java.util.Locale.ROOT, "%.1f", totalUnits),
            chiseledBonus
        ).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.slot_cap_hint",
            String.format(java.util.Locale.ROOT, "%.1f", this.menu.libraryResonanceConfig().maxMasteryUnitsPerSlot())
        ).withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable(
            "gui.chiseled_enchanting_table.resonance.high_tier_books",
            this.menu.qualifyingChiseledEnchantments(),
            sg.tier3QualifyingEnchantments()
        ).withStyle(this.menu.qualifyingChiseledEnchantments() >= sg.tier3QualifyingEnchantments()
            ? ChatFormatting.GREEN
            : ChatFormatting.GRAY));
        var lrPanelCfg = this.menu.libraryResonanceConfig();
        if (lrPanelCfg.tomeRegenEnabled()
            && lrPanelCfg.tomeConsumptionMode() != chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.KEEP) {
            int nearShelves = this.menu.totalLibraryBookshelves();
            double regenSec = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                .computeRegenSeconds(lrPanelCfg, nearShelves);
            int regenTier = chiseled_enchanting_table.mechanics.resonance.MasterTomeWear
                .regenTierIndex(lrPanelCfg, nearShelves);
            Component tierBadge = switch (regenTier) {
                case 2 -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier2",
                    lrPanelCfg.tomeRegenTier2Shelves()
                );
                case 1 -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier1",
                    lrPanelCfg.tomeRegenTier1Shelves()
                );
                default -> Component.translatable(
                    "gui.chiseled_enchanting_table.pricing.tome_regen_tier0",
                    lrPanelCfg.tomeRegenTier1Shelves()
                );
            };
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.pricing.tome_regen_rate",
                String.format(java.util.Locale.ROOT, "%.1f", regenSec / 1200.0),
                nearShelves,
                tierBadge
            ).withStyle(regenTier >= 2
                ? ChatFormatting.GOLD
                : (regenTier == 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
        if (affinityUnits > 0.005) {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.affinity_line",
                String.format(java.util.Locale.ROOT, "%.1f", affinityUnits)
            ).withStyle(ChatFormatting.GREEN));
        }
        if (this.menu.regularBookshelves >= minShelves) {
            int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
            int maxSlots = Math.max(1, this.menu.epiphanyMaxSlots());
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.active", pct)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.slots_line", maxSlots)
                .withStyle(ChatFormatting.GOLD));
            if (this.menu.isEpiphanyFrozen()) {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.frozen_remaining",
                    formatClock(this.menu.countdownSeconds()),
                    Math.max(1, this.menu.epiphanyMaxFreezeTicks() / 1200)
                ).withStyle(ChatFormatting.AQUA));
            } else {
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.epiphany.cycle_remaining",
                    formatClock(this.menu.countdownSeconds())
                ).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.epiphany.progress",
                this.menu.regularBookshelves,
                minShelves
            ).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.treasure_rule")
            .withStyle(ChatFormatting.DARK_GRAY));
        BlockPos scanPos = effectiveTablePos();
        if (this.minecraft != null && this.minecraft.level != null && scanPos != null) {
            var ritScan = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
                .scanLibraryRitualContext(this.minecraft.level, scanPos, sg);
            var rCfg = this.menu.ritualConfig();
            double sigmaL = ritScan.totalWearImmuneLoad();
            double slipPct = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
                .computeLoadSlipProbability(rCfg, sigmaL, ritScan.totalDetectedBookshelves()) * 100.0;
            tip.add(Component.translatable(
                "gui.chiseled_enchanting_table.resonance.overload_sigma_line",
                String.format(java.util.Locale.ROOT, "%.1f", sigmaL),
                String.format(java.util.Locale.ROOT, "%.1f", slipPct),
                String.format(java.util.Locale.ROOT, "%.1f", rCfg.crosslineSlip().minLoadThreshold())
            ).withStyle(slipPct > 0.005 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.scapegoat_order_line")
            .withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Purple stitched header cloth spans y = [1.0 .. 15.0] (center = 8.0); place visual ink center at 7.6f!
        RowRenderSupport.renderCenteredYText(context, this.minecraft, this.title.getString(), 18, 7.6f, 0.94f, 190, 0xFFFFF0C8);

        if (this.menu.isClassic103Mode()) {
            if (!this.menu.isRitualActive()) {
                return;
            }
        } else {
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
