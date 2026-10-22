package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
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

    // Integrated Sanctum & Stasis Console coordinates inside the cohesive 340x166 window chassis.
    private static final int PANEL_X = 214;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 119;
    private static final int PANEL_H = 61;

    private static final int BTN_SIZE = 15;
    private static final int FREEZE_BTN_X = PANEL_X + 79;
    private static final int FREEZE_BTN_Y = PANEL_Y + 34;
    private static final int REROLL_BTN_X = PANEL_X + 96;
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
            151,
            140,
            150,
            25
        );
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(this.scrollContainer);
        this.scrollContainer.updateSizeAndPosition(151, 140, this.leftPos + 7, this.topPos + 18);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.scrollContainer.updateSizeAndPosition(151, 140, this.leftPos + 7, this.topPos + 18);
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

    /**
     * Renders text cleanly within {@code maxWidth}: fits natively at 1.0x when short enough,
     * and smoothly auto-scales down via {@link RowRenderSupport#renderScaledText} when wider,
     * NEVER truncating digits (such as the ones digit of MM:SS seconds!).
     */
    private void drawAutoScaledText(GuiGraphicsExtractor ctx, Component text, int x, int y, int maxWidth, int argbColor) {
        String raw = text.getString();
        int w = this.font.width(raw);
        if (w <= maxWidth || w <= 0) {
            ctx.text(this.font, text, x, y, argbColor, true);
        } else {
            float scale = (float) maxWidth / (float) w;
            RowRenderSupport.renderScaledText(ctx, this.minecraft, raw, x, y + Math.round((1.0f - scale) * 4.0f), scale, argbColor);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int i = this.leftPos;
        int j = this.topPos;
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);

        boolean frozen = this.menu.isEpiphanyFrozen();
        Identifier panelSprite = frozen ? EPIPHANY_PANEL_FROZEN : EPIPHANY_PANEL;
        int px = i + PANEL_X;
        int py = j + PANEL_Y;
        ctx.blit(panelSprite, px, py, px + PANEL_W, py + PANEL_H, 0.0f, 1.0f, 0.0f, 1.0f);

        // Countdown / Awakening progress bar inside the recessed Sanctum readout.
        int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
        boolean unlocked = this.menu.regularBookshelves >= minShelves;
        int barX = px + 8;
        int barY = py + 46;
        int barW = 67;
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

        // Imported painted square icon buttons: [Freeze] and [Reroll].
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
                if (req.requiredLevel() <= 0 && req.deductedLevels() <= 0) {
                    xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.free")
                        .withStyle(ChatFormatting.GREEN));
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
                    if (hoveredXpEntry.option_type == chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType.ENCHANTMENT) {
                        xpTip.add(Component.translatable("gui.chiseled_enchanting_table.xp_mode.full.master_hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
                ctx.setTooltipForNextFrame(this.font, xpTip, Optional.empty(), mouseX, mouseY, (Identifier) null);
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
                int woodenBonus = this.menu.woodenXpDiscountPercent();
                int chiseledBonus = this.menu.globalMasteryDiscountPercent();
                double totalUnits = this.menu.totalMasteryUnits();
                double affinityUnits = this.menu.affinityMasteryUnits(this.menu.get_enchantable_item());
                int minShelves = this.menu.epiphanyMinBookshelves();
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
        // Warm gilded-ivory title rendered right atop the stitched royal-purple header cloth.
        context.text(this.font, this.title, 18, 6, 0xFFFFF0C8, true);

        int lx = PANEL_X + 8;
        int ly = PANEL_Y;
        int minShelves = Math.max(1, this.menu.epiphanyMinBookshelves());
        boolean unlocked = this.menu.regularBookshelves >= minShelves;
        boolean frozen = this.menu.isEpiphanyFrozen();

        Component resLine = Component.translatable("gui.chiseled_enchanting_table.resonance.badge", this.menu.xpDiscountPercent);
        drawAutoScaledText(context, resLine, lx, ly + 8, 102, this.menu.xpDiscountPercent > 0 ? 0xFF7FFFB2 : 0xFFE2D8F5);

        if (unlocked) {
            int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
            int maxSlots = Math.max(1, this.menu.epiphanyMaxSlots());
            Component statLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_stats", pct, maxSlots);
            drawAutoScaledText(context, statLine, lx, ly + 20, 102, 0xFFFFDF7E);

            String clock = formatClock(this.menu.countdownSeconds());
            Component timerLine = frozen
                ? Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_frozen", clock)
                : Component.translatable("gui.chiseled_enchanting_table.epiphany.timer_cycle", clock);
            drawAutoScaledText(context, timerLine, lx, ly + 34, 68, frozen ? 0xFF74F3FF : 0xFFFFF2FF);
        } else {
            Component lockedLine = Component.translatable(
                "gui.chiseled_enchanting_table.epiphany.panel_locked",
                this.menu.regularBookshelves,
                minShelves
            );
            drawAutoScaledText(context, lockedLine, lx, ly + 20, 102, 0xFFD4BEFF);

            int wakePct = Math.min(100, Math.round(this.menu.regularBookshelves * 100.0f / minShelves));
            Component wakeLine = Component.translatable("gui.chiseled_enchanting_table.epiphany.panel_wake", wakePct);
            drawAutoScaledText(context, wakeLine, lx, ly + 34, 68, 0xFFB8A2E0);
        }
    }
}
