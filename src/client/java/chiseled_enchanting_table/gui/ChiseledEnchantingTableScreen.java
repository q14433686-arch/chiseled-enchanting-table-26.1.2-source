package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.EnchantementListWidget.UiTabMode;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

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

    private static final Identifier TAB_INSCRIBE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_active.png");
    private static final Identifier TAB_INSCRIBE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_inscribe_inactive.png");
    private static final Identifier TAB_CLEANSE_ACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_active.png");
    private static final Identifier TAB_CLEANSE_INACTIVE = ChiseledEnchantingTable.identifier("gui/tab_cleanse_inactive.png");

    // Top mode switcher tabs aligned pixel-for-pixel with the purple header cloth [x = 14 .. 167].
    private static final int TAB_W = 75;
    private static final int TAB_H = 18;
    private static final int TAB_Y_OFFSET = -15;
    private static final int TAB_INSCRIBE_X = 14;
    private static final int TAB_CLEANSE_X = 92;

    // Integrated Sanctum / Cleanse Console coordinates inside the cohesive 340x166 window chassis.
    private static final int PANEL_X = 214;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 119;
    private static final int PANEL_H = 61;

    private static final int BTN_SIZE = 14;
    private static final int FREEZE_BTN_X = PANEL_X + 79;
    private static final int FREEZE_BTN_Y = PANEL_Y + 35;
    private static final int REROLL_BTN_X = PANEL_X + 95;
    private static final int REROLL_BTN_Y = PANEL_Y + 35;
    private static final int CLEANSE_ALL_BTN_X = PANEL_X + 95;
    private static final int CLEANSE_ALL_BTN_Y = PANEL_Y + 35;

    private EnchantementListWidget scrollContainer;

    public int inventory_size;
    public int hotbar_size;

    public ChiseledEnchantingTableScreen(ChiseledEnchantingTableScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 340, 166);
        this.hotbar_size = Inventory.getSelectionSize();
        this.inventory_size = inventory.getContainerSize();

        handler.onContentChangedCallback = () -> {
            this.scrollContainer.updateEntry(handler.get_enchantable_item(), handler.get_cost_item());
        };
        this.scrollContainer = new EnchantementListWidget(
            Minecraft.getInstance(),
            handler,
            151,
            140,
            150,
            25
        );
    }

    private boolean isCleanseTab() {
        return this.scrollContainer != null && this.scrollContainer.tabMode() == UiTabMode.CLEANSE;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(this.scrollContainer);
        this.scrollContainer.updateSizeAndPosition(151, 141, this.leftPos + 7, this.topPos + 17);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.scrollContainer.updateSizeAndPosition(151, 141, this.leftPos + 7, this.topPos + 17);
    }

    private boolean isOverInscribeTab(double mouseX, double mouseY) {
        int x0 = this.leftPos + TAB_INSCRIBE_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
    }

    private boolean isOverCleanseTab(double mouseX, double mouseY) {
        if (!this.menu.disenchantEnabled()) return false;
        int x0 = this.leftPos + TAB_CLEANSE_X;
        int y0 = this.topPos + TAB_Y_OFFSET;
        return mouseX >= x0 && mouseX < x0 + TAB_W && mouseY >= y0 && mouseY < y0 + TAB_H - 2;
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
        if (isCleanseTab()) return false;
        int x0 = this.leftPos + FREEZE_BTN_X;
        int y0 = this.topPos + FREEZE_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        if (isCleanseTab()) return false;
        int x0 = this.leftPos + REROLL_BTN_X;
        int y0 = this.topPos + REROLL_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverCleanseAllButton(double mouseX, double mouseY) {
        if (!isCleanseTab()) return false;
        int x0 = this.leftPos + CLEANSE_ALL_BTN_X;
        int y0 = this.topPos + CLEANSE_ALL_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean canCleanseAllNow() {
        return this.menu.disenchantEnabled()
            && DisenchantmentApplication.countNonCurseEnchantments(this.menu.get_enchantable_item()) > 0;
    }

    private boolean isOverSanctumPanel(double mouseX, double mouseY) {
        int x0 = this.leftPos + PANEL_X;
        int y0 = this.topPos + PANEL_Y;
        return mouseX >= x0 && mouseX < x0 + PANEL_W && mouseY >= y0 && mouseY < y0 + PANEL_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverEnchantList(mouseX, mouseY) || isOverEnchantListScrollbar(mouseX, mouseY)) {
            return this.scrollContainer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (isOverInscribeTab(event.x(), event.y())) {
            if (isCleanseTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.scrollContainer.setTabMode(UiTabMode.INSCRIBE);
            }
            return true;
        }
        if (isOverCleanseTab(event.x(), event.y())) {
            if (!isCleanseTab()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                this.scrollContainer.setTabMode(UiTabMode.CLEANSE);
            }
            return true;
        }
        if (isOverCleanseAllButton(event.x(), event.y())) {
            if (canCleanseAllNow()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                ClientPlayNetworking.send(new ApplyEnchantmentPayload(OptionType.DISENCHANT_ALL, DisenchantmentApplication.DISENCHANT_ALL_ID, 0));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverFreezeButton(event.x(), event.y())) {
            if (this.menu.canAffordFreezeEpiphany()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.05F));
                ClientPlayNetworking.send(new FreezeEpiphanyPayload());
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            }
            return true;
        }
        if (isOverRerollButton(event.x(), event.y())) {
            if (this.menu.canAffordRerollEpiphany()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ClientPlayNetworking.send(new RerollEpiphanyPayload());
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
        if (this.scrollContainer != null && (this.scrollContainer.isDragging() || isOverEnchantListScrollbar(event.x(), event.y()))) {
            return this.scrollContainer.mouseDragged(event, dragX, dragY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scrollContainer != null && this.scrollContainer.isDragging()) {
            this.scrollContainer.setDragging(false);
            this.scrollContainer.onRelease(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.scrollContainer.keyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        this.scrollContainer.keyReleased(event);
        return super.keyReleased(event);
    }

    private static String formatClock(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int mins = clamped / 60;
        int secs = clamped % 60;
        return String.format("%02d:%02d", mins, secs);
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
        boolean cleanse = isCleanseTab();
        int tabY = j + TAB_Y_OFFSET;

        int inscribeX = i + TAB_INSCRIBE_X;
        Identifier inscribeSprite = cleanse ? TAB_INSCRIBE_INACTIVE : TAB_INSCRIBE_ACTIVE;
        ctx.blit(inscribeSprite, inscribeX, tabY, inscribeX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
        if (cleanse && isOverInscribeTab(mouseX, mouseY)) {
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
            cleanse ? 0xFFC2B2DC : 0xFFFFF0C8
        );

        if (this.menu.disenchantEnabled()) {
            int cleanseX = i + TAB_CLEANSE_X;
            Identifier cleanseSprite = cleanse ? TAB_CLEANSE_ACTIVE : TAB_CLEANSE_INACTIVE;
            ctx.blit(cleanseSprite, cleanseX, tabY, cleanseX + TAB_W, tabY + TAB_H, 0.0f, 1.0f, 0.0f, 1.0f);
            if (!cleanse && isOverCleanseTab(mouseX, mouseY)) {
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
                cleanse ? 0xFF9EFFDB : (traitCount > 0 ? 0xFF8BE8C8 : 0xFFA2C4BC)
            );
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int i = this.leftPos;
        int j = this.topPos;

        // Draw inactive tab under main frame, main frame, then active tab flush on top so its open bottom blends seamlessly!
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);
        renderModeTabs(ctx, i, j, mouseX, mouseY);

        int px = i + PANEL_X;
        int py = j + PANEL_Y;

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
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
            if (isOverInscribeTab(mouseX, mouseY)) {
                List<Component> tip = List.of(
                    Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.chiseled_enchanting_table.tab.inscribe.desc").withStyle(ChatFormatting.GRAY)
                );
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
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.hint")
                        .withStyle(ChatFormatting.GRAY));
                } else if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
                    String freeKey = ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(hoveredXpEntry.enchantment_id)
                        ? "gui.chiseled_enchanting_table.xp_mode.free_tome"
                        : "gui.chiseled_enchanting_table.xp_mode.free";
                    xpTip.add(Component.translatable(freeKey).withStyle(ChatFormatting.GREEN));
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
                if (specialEntry.shelf_locked && specialEntry.enchantment != null) {
                    String lockedName = net.minecraft.world.item.enchantment.Enchantment
                        .getFullname(specialEntry.enchantment, specialEntry.enchant_level)
                        .getString();
                    List<Component> lockTip = new ArrayList<>();
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_title",
                        lockedName
                    ).withStyle(ChatFormatting.GOLD));
                    lockTip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.shelf_locked.tip_req",
                        this.menu.regularBookshelves,
                        specialEntry.required_bookshelves
                    ).withStyle(ChatFormatting.RED));
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
                    int directCap = this.menu.maxUnlockedDirectTier(specialEntry.enchantment_id, maxLvl);
                    int effectiveCap = Math.min(maxLvl, Math.max(storedLvl, directCap));
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
                    if (directCap < maxLvl) {
                        int nextTier = directCap + 1;
                        int reqShelves = this.menu.requiredBookshelvesForDirectTier(
                            specialEntry.enchantment_id,
                            nextTier,
                            maxLvl
                        );
                        catTip.add(Component.translatable(
                            "gui.chiseled_enchanting_table.category.tip_next",
                            nextTier,
                            reqShelves,
                            sg.maxDirectBookshelves()
                        ).withStyle(ChatFormatting.YELLOW));
                    }
                    catTip.add(Component.translatable("gui.chiseled_enchanting_table.category.tip_master")
                        .withStyle(ChatFormatting.DARK_GRAY));
                    ctx.setTooltipForNextFrame(this.font, catTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
            }
            if (isOverCleanseAllButton(mouseX, mouseY)) {
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(
                    this.menu.get_enchantable_item(),
                    this.menu.disenchantRefundScale()
                );
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.btn_all_title")
                    .withStyle(ChatFormatting.GREEN));
                if (canCleanseAllNow()) {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.refund.amount", refund)
                        .withStyle(ChatFormatting.AQUA));
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
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.auto_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                return;
            }
            if (isOverSanctumPanel(mouseX, mouseY)) {
                if (isCleanseTab()) {
                    ItemStack target = this.menu.get_enchantable_item();
                    int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                    int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                    int refund = DisenchantmentApplication.totalFullDisenchantRefund(target, this.menu.disenchantRefundScale());
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
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.cleanse.panel_resonance")
                        .withStyle(ChatFormatting.DARK_GRAY));
                    ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
                    return;
                }
                int woodenBonus = this.menu.woodenXpDiscountPercent();
                int chiseledBonus = this.menu.globalMasteryDiscountPercent();
                double totalUnits = this.menu.totalMasteryUnits();
                double affinityUnits = this.menu.affinityMasteryUnits(this.menu.get_enchantable_item());
                int minShelves = this.menu.epiphanyMinBookshelves();
                var sg = this.menu.shelfGatesConfig();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.title", this.menu.xpDiscountPercent)
                    .withStyle(ChatFormatting.GOLD));
                if (this.menu.regularBookshelves < chiseled_enchanting_table.mechanics.resonance.LibraryResonance.MIN_DISCOUNT_BOOKSHELVES) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.resonance.wooden_locked",
                        this.menu.regularBookshelves,
                        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.MIN_DISCOUNT_BOOKSHELVES
                    ).withStyle(ChatFormatting.GRAY));
                } else {
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", this.menu.regularBookshelves, woodenBonus)
                        .withStyle(ChatFormatting.GRAY));
                }
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.resonance.chiseled_mastery",
                    String.format("%.1f", totalUnits),
                    chiseledBonus
                ).withStyle(ChatFormatting.AQUA));
                tip.add(Component.translatable(
                    "gui.chiseled_enchanting_table.resonance.high_tier_books",
                    this.menu.qualifyingChiseledEnchantments(),
                    sg.tier3QualifyingEnchantments()
                ).withStyle(this.menu.qualifyingChiseledEnchantments() >= sg.tier3QualifyingEnchantments()
                    ? ChatFormatting.GREEN
                    : ChatFormatting.GRAY));
                if (affinityUnits > 0.005) {
                    tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.resonance.affinity_line",
                        String.format("%.1f", affinityUnits)
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
                ctx.setTooltipForNextFrame(this.font, tip, Optional.empty(), mouseX, mouseY, (Identifier) null);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        // Purple stitched header cloth spans y = [1.0 .. 15.0] (center = 8.0); place visual ink center at 7.6f!
        RowRenderSupport.renderCenteredYText(context, this.minecraft, this.title.getString(), 18, 7.6f, 0.94f, 300, 0xFFFFF0C8);

        int lx = PANEL_X + 10;
        int ly = PANEL_Y;

        context.enableScissor(PANEL_X + 9, PANEL_Y + 9, PANEL_X + 110, PANEL_Y + 52);
        try {
            if (isCleanseTab()) {
                ItemStack target = this.menu.get_enchantable_item();
                int totalTraits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
                int nonCurse = DisenchantmentApplication.countNonCurseEnchantments(target);
                int refund = DisenchantmentApplication.totalFullDisenchantRefund(target, this.menu.disenchantRefundScale());

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
