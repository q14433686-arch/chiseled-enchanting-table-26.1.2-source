package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload;
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

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableScreen extends AbstractContainerScreen<ChiseledEnchantingTableScreenHandler> {

    public static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/container/slot.png");
    private static final Identifier BACKGROUND = ChiseledEnchantingTable.identifier("gui/chiseled_enchanting_table.png");
    private static final Identifier EPIPHANY_PANEL = ChiseledEnchantingTable.identifier("gui/epiphany_panel.png");
    private static final Identifier EPIPHANY_PANEL_FROZEN = ChiseledEnchantingTable.identifier("gui/epiphany_panel_frozen.png");
    private static final Identifier BTN_FREEZE = ChiseledEnchantingTable.identifier("gui/btn_freeze.png");
    private static final Identifier BTN_REROLL = ChiseledEnchantingTable.identifier("gui/btn_reroll.png");

    // Explicit painted Sanctum & Stasis Panel geometry in the upper-right empty region above player inventory.
    private static final int PANEL_X = 216;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 118;
    private static final int PANEL_H = 62;

    private static final int BTN_SIZE = 16;
    private static final int FREEZE_BTN_X = PANEL_X + 72;
    private static final int FREEZE_BTN_Y = PANEL_Y + 34;
    private static final int REROLL_BTN_X = PANEL_X + 90;
    private static final int REROLL_BTN_Y = PANEL_Y + 34;

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
            153,
            140,
            150,
            25
        );
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(this.scrollContainer);
        this.scrollContainer.updateSizeAndPosition(153, 140, this.leftPos + 7, this.topPos + 18);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.scrollContainer.updateSizeAndPosition(153, 140, this.leftPos + 7, this.topPos + 18);
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
        int scrollbarLeft = this.scrollContainer.getX() + this.scrollContainer.getWidth() - 8;
        int scrollbarRight = this.scrollContainer.getX() + this.scrollContainer.getWidth() + 8;
        return mouseX >= scrollbarLeft
            && mouseX <= scrollbarRight
            && mouseY >= this.scrollContainer.getY()
            && mouseY <= this.scrollContainer.getY() + this.scrollContainer.getHeight();
    }

    private boolean isOverFreezeButton(double mouseX, double mouseY) {
        int x0 = this.leftPos + FREEZE_BTN_X;
        int y0 = this.topPos + FREEZE_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        int x0 = this.leftPos + REROLL_BTN_X;
        int y0 = this.topPos + REROLL_BTN_Y;
        return mouseX >= x0 && mouseX < x0 + BTN_SIZE && mouseY >= y0 && mouseY < y0 + BTN_SIZE;
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
            if (isOverEnchantListScrollbar(event.x(), event.y()) && this.scrollContainer.updateScrolling(event)) {
                this.scrollContainer.setDragging(true);
                return true;
            }
            return this.scrollContainer.mouseClicked(event, isDoubleClick);
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scrollContainer != null && (this.scrollContainer.isDragging() || isOverEnchantList(event.x(), event.y()) || isOverEnchantListScrollbar(event.x(), event.y()))) {
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

    private void drawFittedText(GuiGraphicsExtractor ctx, Component text, int x, int y, int maxWidth, int argbColor) {
        int w = this.font.width(text);
        if (w <= maxWidth || w <= 0) {
            ctx.text(this.font, text, x, y, argbColor, true);
        } else {
            float scale = (float) maxWidth / (float) w;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y + (1.0f - scale) * 4.0f);
            ctx.pose().scale(scale, scale);
            ctx.text(this.font, text, 0, 0, argbColor, true);
            ctx.pose().popMatrix();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int i = this.leftPos;
        int j = this.topPos;
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);

        // Render imported painted Sanctum & Stasis Panel sprite in the upper-right region (216, 18, 118, 62).
        boolean frozen = this.menu.isEpiphanyFrozen();
        Identifier panelSprite = frozen ? EPIPHANY_PANEL_FROZEN : EPIPHANY_PANEL;
        int px = i + PANEL_X;
        int py = j + PANEL_Y;
        ctx.blit(panelSprite, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);

        // Countdown / Awakening progress bar inside the dark recessed panel screen.
        int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
        boolean unlocked = this.menu.regularBookshelves >= minShelves;
        int barX = px + 9;
        int barY = py + 45;
        int barW = 58;
        int barH = 5;
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

        // Imported painted square icon buttons: [Freeze] and [Reroll].
        int fx = i + FREEZE_BTN_X;
        int fy = j + FREEZE_BTN_Y;
        boolean canFreeze = this.menu.canAffordFreezeEpiphany();
        int freezeTint = canFreeze
            ? (isOverFreezeButton(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFEAFBFF)
            : (unlocked ? 0x99FFFFFF : 0x55FFFFFF);
        ctx.blit(BTN_FREEZE, fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f, freezeTint);
        if (isOverFreezeButton(mouseX, mouseY) && canFreeze) {
            ctx.fill(fx, fy, fx + BTN_SIZE, fy + BTN_SIZE, 0x2890F5FF);
        }

        int rx = i + REROLL_BTN_X;
        int ry = j + REROLL_BTN_Y;
        boolean canReroll = this.menu.canAffordRerollEpiphany();
        int rerollTint = canReroll
            ? (isOverRerollButton(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFFFF4DC)
            : (unlocked ? 0x99FFFFFF : 0x55FFFFFF);
        ctx.blit(BTN_REROLL, rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0.0f, 1.0f, 0.0f, 1.0f, rerollTint);
        if (isOverRerollButton(mouseX, mouseY) && canReroll) {
            ctx.fill(rx, ry, rx + BTN_SIZE, ry + BTN_SIZE, 0x28FFE082);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
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
                    tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.slots_line", maxSlots)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
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
                int woodenBonus = Math.min(10, this.menu.regularBookshelves);
                int chiseledBonus = this.menu.chiseledBookCount * 2;
                int minShelves = this.menu.epiphanyMinBookshelves();
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.title", this.menu.xpDiscountPercent)
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", this.menu.regularBookshelves, woodenBonus)
                    .withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.chiseled", this.menu.chiseledBookCount, chiseledBonus)
                    .withStyle(ChatFormatting.AQUA));
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
        context.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);

        int lx = PANEL_X + 9;
        int ly = PANEL_Y;
        int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
        boolean unlocked = this.menu.regularBookshelves >= minShelves;
        boolean frozen = this.menu.isEpiphanyFrozen();

        Component resLine = Component.translatable("gui.chiseled_enchanting_table.resonance.badge", this.menu.xpDiscountPercent);
        drawFittedText(context, resLine, lx, ly + 9, 99, this.menu.xpDiscountPercent > 0 ? 0xFF7FFFB2 : 0xFFE2D8F5);

        if (unlocked) {
            int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
            int maxSlots = Math.max(1, this.menu.epiphanyMaxSlots());
            Component statLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_stats", pct, maxSlots);
            drawFittedText(context, statLine, lx, ly + 20, 99, 0xFFFFDF7E);

            String clock = formatClock(this.menu.countdownSeconds());
            Component timerLine = frozen
                ? Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_frozen", clock)
                : Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_cycle", clock);
            drawFittedText(context, timerLine, lx, ly + 33, 59, frozen ? 0xFF74F3FF : 0xFFEEDDFF);
        } else {
            Component lockedLine = Component.translatable(
                "gui.chiseled_enchanting_table.epiphany.panel_locked",
                this.menu.regularBookshelves,
                minShelves
            );
            drawFittedText(context, lockedLine, lx, ly + 20, 99, 0xFFD4BEFF);

            int wakePct = Math.min(100, Math.round(this.menu.regularBookshelves * 100.0f / minShelves));
            Component wakeLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_wake", wakePct);
            drawFittedText(context, wakeLine, lx, ly + 33, 59, 0xFFB8A2E0);
        }
    }
}
