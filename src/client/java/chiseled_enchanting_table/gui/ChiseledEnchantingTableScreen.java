package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableScreen extends AbstractContainerScreen<ChiseledEnchantingTableScreenHandler> {

    public static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/container/slot.png");
    private static final Identifier BACKGROUND = ChiseledEnchantingTable.identifier("gui/chiseled_enchanting_table.png");

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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverEnchantList(mouseX, mouseY) || isOverEnchantListScrollbar(mouseX, mouseY)) {
            return this.scrollContainer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
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

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(ctx, mouseX, mouseY, partialTick);
        int i = this.leftPos;
        int j = this.topPos;
        ctx.blit(BACKGROUND, i, j, i + 340, j + 166, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private boolean showResonanceBadge() {
        return true;
    }

    private boolean showRerollButton() {
        return this.menu.regularBookshelves >= this.menu.epiphanyMinBookshelves();
    }

    private Component rerollLabel() {
        return Component.literal("\u21bb");
    }

    private int rerollWidth() {
        return showRerollButton() ? (this.font.width(rerollLabel()) + 8) : 0;
    }

    private int rerollLocalX() {
        return this.imageWidth - 7 - rerollWidth();
    }

    private int badgeWidth() {
        return this.font.width(resonanceBadgeComponent()) + 8;
    }

    private int badgeLocalX() {
        int rightEdge = showRerollButton() ? (rerollLocalX() - 3) : (this.imageWidth - 7);
        return rightEdge - badgeWidth();
    }

    private int pillLocalY() {
        return this.titleLabelY - 2;
    }

    private int pillHeight() {
        return this.font.lineHeight + 3;
    }

    private boolean isOverResonanceBadge(double mouseX, double mouseY) {
        if (!showResonanceBadge()) return false;
        int x0 = this.leftPos + badgeLocalX();
        int y0 = this.topPos + pillLocalY();
        return mouseX >= x0 && mouseX <= x0 + badgeWidth() && mouseY >= y0 && mouseY <= y0 + pillHeight();
    }

    private boolean isOverRerollButton(double mouseX, double mouseY) {
        if (!showRerollButton()) return false;
        int x0 = this.leftPos + rerollLocalX();
        int y0 = this.topPos + pillLocalY();
        return mouseX >= x0 && mouseX <= x0 + rerollWidth() && mouseY >= y0 && mouseY <= y0 + pillHeight();
    }

    private Component resonanceBadgeComponent() {
        return Component.translatable("gui.chiseled_enchanting_table.resonance.badge", this.menu.xpDiscountPercent);
    }

    private static void drawPill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int bgColor, int borderColor) {
        ctx.fill(x, y, x + w, y + h, borderColor);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
            ItemStack hoveredCost = this.scrollContainer.hoveredCostItem(mouseX, mouseY);
            if (hoveredCost != null && !hoveredCost.isEmpty()) {
                List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(hoveredCost));
                List<List<ItemStack>> options = this.scrollContainer.hoveredCostOptions(mouseX, mouseY);
                if (options != null && options.size() > 1) {
                    lines.add(Component.translatable("gui.chiseled_enchanting_table.cost_alternatives.header")
                        .withStyle(ChatFormatting.GOLD));
                    for (List<ItemStack> bundle : options) {
                        if (bundle.isEmpty()) continue;
                        ItemStack primary = bundle.getFirst();
                        lines.add(Component.literal(" \u2022 " + primary.getCount() + "\u00d7 ")
                            .append(primary.getHoverName())
                            .withStyle(ChatFormatting.GRAY));
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
            if (isOverRerollButton(mouseX, mouseY)) {
                int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.reroll_title", pct)
                    .withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.reroll_cost",
                        this.menu.epiphanyRerollXpLevels()
                    ).withStyle(this.menu.canAffordRerollEpiphany() ? ChatFormatting.GREEN : ChatFormatting.RED));
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
            if (isOverResonanceBadge(mouseX, mouseY)) {
                int woodenBonus = Math.min(10, this.menu.regularBookshelves);
                int chiseledBonus = this.menu.chiseledBookCount * 2;
                int minShelves = this.menu.epiphanyMinBookshelves();
                List<Component> resonanceTooltip = new ArrayList<>();
                resonanceTooltip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.title", this.menu.xpDiscountPercent)
                    .withStyle(ChatFormatting.GOLD));
                resonanceTooltip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", this.menu.regularBookshelves, woodenBonus)
                    .withStyle(ChatFormatting.GRAY));
                resonanceTooltip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.chiseled", this.menu.chiseledBookCount, chiseledBonus)
                    .withStyle(ChatFormatting.AQUA));
                if (this.menu.regularBookshelves >= minShelves) {
                    int pct = (int) Math.round(this.menu.epiphanyChance() * 100.0);
                    resonanceTooltip.add(Component.translatable("gui.chiseled_enchanting_table.epiphany.active", pct)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    resonanceTooltip.add(Component.translatable(
                        "gui.chiseled_enchanting_table.epiphany.progress",
                        this.menu.regularBookshelves,
                        minShelves
                    ).withStyle(ChatFormatting.DARK_PURPLE));
                }
                resonanceTooltip.add(Component.translatable("gui.chiseled_enchanting_table.resonance.cap")
                    .withStyle(ChatFormatting.DARK_GRAY));
                ctx.setTooltipForNextFrame(
                    this.font,
                    resonanceTooltip,
                    Optional.empty(),
                    mouseX,
                    mouseY,
                    (Identifier) null
                );
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);

        if (showResonanceBadge()) {
            Component badge = resonanceBadgeComponent();
            int bx = badgeLocalX();
            int by = pillLocalY();
            boolean hovered = isOverResonanceBadge(mouseX, mouseY);
            int bg = hovered ? 0xF234224E : 0xEA221734;
            int border = this.menu.xpDiscountPercent > 0 ? 0xFF8B5CC4 : 0xFF5D4D73;
            int textColor = this.menu.xpDiscountPercent > 0 ? 0xFF7FFFB2 : 0xFFD8CEEC;
            drawPill(context, bx, by, badgeWidth(), pillHeight(), bg, border);
            context.text(this.font, badge, bx + 4, this.titleLabelY, textColor, true);
        }

        if (showRerollButton()) {
            Component reroll = rerollLabel();
            int rx = rerollLocalX();
            int ry = pillLocalY();
            boolean hovered = isOverRerollButton(mouseX, mouseY);
            boolean affordable = this.menu.canAffordRerollEpiphany();
            int bg = hovered ? 0xF44C3414 : 0xEA2E1E0B;
            int border = affordable ? 0xFFE8B84B : 0xFF7A6352;
            int textColor = affordable ? 0xFFFFE082 : 0xFFB8A394;
            drawPill(context, rx, ry, rerollWidth(), pillHeight(), bg, border);
            context.text(this.font, reroll, rx + 4, this.titleLabelY, textColor, true);
        }
    }
}
