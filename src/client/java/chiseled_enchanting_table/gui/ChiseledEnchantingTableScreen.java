package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ctx, mouseX, mouseY, partialTick);
        if (this.menu.getCarried().isEmpty() && this.scrollContainer != null) {
            ItemStack hoveredCost = this.scrollContainer.hoveredCostItem(mouseX, mouseY);
            if (hoveredCost != null && !hoveredCost.isEmpty()) {
                java.util.List<Component> lines = new java.util.ArrayList<>(this.getTooltipFromContainerItem(hoveredCost));
                java.util.List<java.util.List<ItemStack>> options = this.scrollContainer.hoveredCostOptions(mouseX, mouseY);
                if (options != null && options.size() > 1) {
                    lines.add(Component.translatable("gui.chiseled_enchanting_table.cost_alternatives.header")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                    for (java.util.List<ItemStack> bundle : options) {
                        if (bundle.isEmpty()) continue;
                        ItemStack primary = bundle.getFirst();
                        lines.add(Component.literal(" \u2022 " + primary.getCount() + "\u00d7 ")
                            .append(primary.getHoverName())
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
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
            if (isOverResonanceBadge(mouseX, mouseY)) {
                int woodenBonus = Math.min(10, this.menu.regularBookshelves);
                int chiseledBonus = this.menu.chiseledBookCount * 2;
                java.util.List<Component> resonanceTooltip = java.util.List.of(
                    Component.translatable("gui.chiseled_enchanting_table.resonance.title", this.menu.xpDiscountPercent)
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                    Component.translatable("gui.chiseled_enchanting_table.resonance.wooden", this.menu.regularBookshelves, woodenBonus)
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.chiseled_enchanting_table.resonance.chiseled", this.menu.chiseledBookCount, chiseledBonus)
                        .withStyle(net.minecraft.ChatFormatting.AQUA),
                    Component.translatable("gui.chiseled_enchanting_table.resonance.cap")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                );
                ctx.setTooltipForNextFrame(
                    this.font,
                    resonanceTooltip,
                    java.util.Optional.empty(),
                    mouseX,
                    mouseY,
                    (Identifier) null
                );
            }
        }
    }

    private boolean isOverResonanceBadge(double mouseX, double mouseY) {
        if (this.menu.xpDiscountPercent <= 0 && this.menu.regularBookshelves <= 0 && this.menu.chiseledBookCount <= 0) {
            return false;
        }
        Component badge = resonanceBadgeComponent();
        int badgeWidth = this.font.width(badge);
        int badgeX = this.leftPos + this.imageWidth - 8 - badgeWidth;
        int badgeY = this.topPos + this.titleLabelY;
        return mouseX >= badgeX - 2 && mouseX <= badgeX + badgeWidth + 2
            && mouseY >= badgeY - 2 && mouseY <= badgeY + this.font.lineHeight + 2;
    }

    private Component resonanceBadgeComponent() {
        return Component.translatable("gui.chiseled_enchanting_table.resonance.badge", this.menu.xpDiscountPercent);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752);
        if (this.menu.xpDiscountPercent > 0 || this.menu.regularBookshelves > 0 || this.menu.chiseledBookCount > 0) {
            Component badge = resonanceBadgeComponent();
            int badgeX = this.imageWidth - 8 - this.font.width(badge);
            int color = this.menu.xpDiscountPercent > 0 ? 0x1E6B3A : 0x555555;
            context.text(this.font, badge, badgeX, this.titleLabelY, color, false);
        }
    }
}
