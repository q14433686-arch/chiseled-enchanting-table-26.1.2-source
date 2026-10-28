package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.config.SearchInputSession;
import chiseled_enchanting_table.config.SearchTextBuffer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import static chiseled_enchanting_table.gui.TableSkin.*;

/**
 * Compact 15px Jade-Teal Cleanse-page search/mode/filter bar + bag-aware cost-bundle selector.
 */
public final class CleanseFilterBar {
    public enum Mode {
        ALL("gui.chiseled_enchanting_table.cleanse_filter.mode_all"),
        REFUND("gui.chiseled_enchanting_table.cleanse_filter.mode_refund"),
        STRIP_EXTRACT("gui.chiseled_enchanting_table.cleanse_filter.mode_strip"),
        CURSE("gui.chiseled_enchanting_table.cleanse_filter.mode_curse");

        private final String translationKey;
        Mode(String translationKey) { this.translationKey = translationKey; }
        public String translationKey() { return this.translationKey; }

        public Mode next() {
            Mode[] all = values();
            return all[(this.ordinal() + 1) % all.length];
        }

        public boolean accepts(OptionType type) {
            return switch (this) {
                case ALL -> true;
                case REFUND -> type == OptionType.DISENCHANT_ALL || type == OptionType.DOWNGRADE_ENCHANTMENT;
                case STRIP_EXTRACT -> type == OptionType.STRIP_SINGLE_ENCHANTMENT
                    || type == OptionType.EXTRACT_ENCHANTMENT_BOOK;
                case CURSE -> type == OptionType.PURGE_CURSE || type == OptionType.PURGE_ALL_CURSES;
            };
        }
    }

    private final SearchInputSession searchInput = new SearchInputSession(focused -> {});
    private final SearchTextBuffer searchBuffer = searchInput.buffer();
    private Mode mode = Mode.ALL;
    private boolean affordableOnly = false;
    private final Map<AvailableOption, Integer> pinnedCostIndices = new HashMap<>();

    public String query() { return this.searchBuffer.text().trim(); }
    public Mode mode() { return this.mode; }
    public boolean affordableOnly() { return this.affordableOnly; }
    public boolean searchFocused() { return this.searchInput.focused(); }
    public boolean hasActiveFilter() {
        return !query().isEmpty() || this.mode != Mode.ALL || this.affordableOnly;
    }

    public void unfocus() {
        if (this.searchInput.focused()) {
            this.searchInput.setFocused(false);
        }
    }

    public int resolveCostOptionIndex(
        AvailableOption option,
        List<List<ItemStack>> options,
        ItemStack costSlot,
        ChiseledEnchantingTableScreenHandler handler
    ) {
        if (options == null || options.isEmpty()) {
            return -1;
        }
        Integer pinned = option == null ? null : this.pinnedCostIndices.get(option);
        if (pinned != null && pinned >= 0) {
            return pinned % options.size();
        }
        if (costSlot != null && !costSlot.isEmpty()) {
            for (int i = 0; i < options.size(); i++) {
                List<ItemStack> b = options.get(i);
                if (b != null && b.stream().anyMatch(s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, costSlot))
                    && (handler == null || handler.isCleanseBundleAffordableFromBagOrSlot(b))) {
                    return i;
                }
            }
        }
        if (handler != null) {
            for (int i = 0; i < options.size(); i++) {
                List<ItemStack> b = options.get(i);
                if (b != null && !b.isEmpty() && handler.isCleanseBundleAffordableFromBagOrSlot(b)) {
                    return i;
                }
            }
        }
        return 0;
    }

    public int cycleCostOption(AvailableOption option, int optionCount, int currentIndex) {
        if (option == null || optionCount <= 1) {
            return Math.max(0, currentIndex);
        }
        int next = (Math.max(0, currentIndex) + 1) % optionCount;
        this.pinnedCostIndices.put(option, next);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.15F));
        return next;
    }

    public boolean mouseClicked(
        double mx,
        double my,
        int button,
        int barX,
        int barY,
        Runnable onFilterChanged,
        Runnable onToggleExpandAll
    ) {
        double rx = mx - barX;
        double ry = my - barY;
        var searchRect = TomeDirectoryLayout.cleanseSearchBox();
        var modeRect = TomeDirectoryLayout.cleanseModeButton();
        var affordRect = TomeDirectoryLayout.cleanseAffordableButton();
        var expandRect = TomeDirectoryLayout.cleanseExpandButton();

        if (searchRect.contains(rx, ry)) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (!this.searchBuffer.isEmpty()) {
                    this.searchBuffer.clear();
                    onFilterChanged.run();
                }
                this.searchInput.setFocused(true);
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                if (!this.searchBuffer.isEmpty() && rx >= searchRect.right() - 12) {
                    this.searchBuffer.clear();
                    onFilterChanged.run();
                }
                this.searchInput.setFocused(true);
                return true;
            }
        } else {
            this.searchInput.setFocused(false);
        }

        if (modeRect.contains(rx, ry)) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                this.mode = Mode.ALL;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F));
                onFilterChanged.run();
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                this.mode = this.mode.next();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.08F));
                onFilterChanged.run();
                return true;
            }
        }

        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (affordRect.contains(rx, ry)) {
            this.affordableOnly = !this.affordableOnly;
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, this.affordableOnly ? 1.1F : 0.92F)
            );
            onFilterChanged.run();
            return true;
        }
        if (expandRect.contains(rx, ry)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.04F));
            onToggleExpandAll.run();
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event, Runnable onFilterChanged) {
        if (!this.searchInput.focused()) return false;
        if (this.searchBuffer.insertCodePoint(event.codepoint())) onFilterChanged.run();
        return true;
    }

    public boolean keyPressed(KeyEvent event, Runnable onFilterChanged) {
        if (!this.searchInput.focused()) return false;
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (this.searchBuffer.hasSelection()) {
                this.searchBuffer.collapseSelection();
            } else if (!this.searchBuffer.isEmpty()) {
                this.searchBuffer.clear();
                onFilterChanged.run();
            } else {
                this.searchInput.setFocused(false);
            }
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            this.searchInput.setFocused(false);
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            if (this.searchBuffer.deleteBackward()) onFilterChanged.run();
            return true;
        }
        if (key == InputConstants.KEY_DELETE) {
            if (this.searchBuffer.deleteForward()) onFilterChanged.run();
            return true;
        }
        if (key == InputConstants.KEY_LEFT) {
            this.searchBuffer.moveByCodePoints(-1, event.hasShiftDown());
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            this.searchBuffer.moveByCodePoints(1, event.hasShiftDown());
            return true;
        }
        if (key == InputConstants.KEY_HOME) {
            this.searchBuffer.moveHome(event.hasShiftDown());
            return true;
        }
        if (key == InputConstants.KEY_END) {
            this.searchBuffer.moveEnd(event.hasShiftDown());
            return true;
        }
        if (event.hasControlDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (key == InputConstants.KEY_A) {
                this.searchBuffer.selectAll();
                return true;
            }
            if (key == InputConstants.KEY_C && mc.keyboardHandler != null) {
                if (this.searchBuffer.hasSelection()) mc.keyboardHandler.setClipboard(this.searchBuffer.selectedText());
                return true;
            }
            if (key == InputConstants.KEY_V && mc.keyboardHandler != null) {
                if (this.searchBuffer.insertText(mc.keyboardHandler.getClipboard())) onFilterChanged.run();
                return true;
            }
        }
        return true;
    }

    public boolean render(
        GuiGraphicsExtractor ctx,
        Font font,
        int barX,
        int barY,
        int mx,
        int my,
        boolean allExpanded
    ) {
        var searchRect = TomeDirectoryLayout.cleanseSearchBox();
        var modeRect = TomeDirectoryLayout.cleanseModeButton();
        var affordRect = TomeDirectoryLayout.cleanseAffordableButton();
        var expandRect = TomeDirectoryLayout.cleanseExpandButton();

        int sx = barX + searchRect.x();
        int sy = barY + searchRect.y();
        Identifier searchSprite = this.searchInput.focused() ? CLEANSE_SEARCH_BOX_FOCUSED : CLEANSE_SEARCH_BOX;
        ctx.blit(searchSprite, sx, sy, sx + searchRect.width(), sy + searchRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);

        String text = this.searchBuffer.text();
        ctx.enableScissor(sx + 5, sy + 3, sx + searchRect.width() - 5, sy + searchRect.height() - 3);
        try {
            if (text.isEmpty() && !this.searchInput.focused()) {
                String ph = Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.placeholder").getString();
                RowRenderSupport.renderCenteredYText(ctx, font, ph, sx + 6, sy + 7.5f, 0.56f, searchRect.width() - 12, 0xFF8DBAB0);
            } else {
                String shown = text;
                if (this.searchInput.focused() && ((net.minecraft.util.Util.getMillis() / 500L) & 1L) == 0L) {
                    shown = text + "|";
                }
                RowRenderSupport.renderCenteredYText(ctx, font, shown, sx + 6, sy + 7.5f, 0.60f, searchRect.width() - 18, 0xFFE8FFF8);
                if (!text.isEmpty()) {
                    RowRenderSupport.renderCenteredYText(ctx, font, "\u00d7", sx + searchRect.width() - 10, sy + 7.5f, 0.64f, 6, 0xFF9EFFDB);
                }
            }
        } finally {
            ctx.disableScissor();
        }

        int mdx = barX + modeRect.x();
        int mdy = barY + modeRect.y();
        ctx.blit(BTN_CLEANSE_MODE, mdx, mdy, mdx + modeRect.width(), mdy + modeRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);
        boolean overMode = modeRect.contains(mx - barX, my - barY);
        if (this.mode != Mode.ALL) {
            int rim = this.mode == Mode.CURSE ? 0xFFFF7A96 : 0xFF66FFD2;
            drawActiveOutline(ctx, mdx, mdy, modeRect.width(), modeRect.height(), rim);
        } else if (overMode) {
            ctx.fill(mdx + 1, mdy + 1, mdx + modeRect.width() - 1, mdy + modeRect.height() - 1, 0x2E8CFFD2);
        }

        int ax = barX + affordRect.x();
        int ay = barY + affordRect.y();
        ctx.blit(BTN_CLEANSE_AFFORDABLE, ax, ay, ax + affordRect.width(), ay + affordRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);
        boolean overAfford = affordRect.contains(mx - barX, my - barY);
        if (this.affordableOnly) {
            drawActiveOutline(ctx, ax, ay, affordRect.width(), affordRect.height(), 0xFF7CFFA6);
        } else if (overAfford) {
            ctx.fill(ax + 1, ay + 1, ax + affordRect.width() - 1, ay + affordRect.height() - 1, 0x2E8CFFD2);
        }

        int ex = barX + expandRect.x();
        int ey = barY + expandRect.y();
        ctx.blit(BTN_CLEANSE_EXPAND, ex, ey, ex + expandRect.width(), ey + expandRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);
        boolean overExpand = expandRect.contains(mx - barX, my - barY);
        if (allExpanded) {
            drawActiveOutline(ctx, ex, ey, expandRect.width(), expandRect.height(), 0xFF9EFFDB);
        } else if (overExpand) {
            ctx.fill(ex + 1, ey + 1, ex + expandRect.width() - 1, ey + expandRect.height() - 1, 0x2E8CFFD2);
        }

        if (overMode) {
            TooltipPresentation.show(ctx, font, List.of(
                Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.mode_title",
                    Component.translatable(this.mode.translationKey()))
                    .withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.mode_hint")
                    .withStyle(ChatFormatting.DARK_GRAY)
            ), Optional.empty(), mx, my, null);
            return true;
        }
        if (overAfford) {
            TooltipPresentation.show(ctx, font, List.of(
                Component.translatable(this.affordableOnly
                    ? "gui.chiseled_enchanting_table.cleanse_filter.affordable_on"
                    : "gui.chiseled_enchanting_table.cleanse_filter.affordable_off")
                    .withStyle(this.affordableOnly ? ChatFormatting.GREEN : ChatFormatting.AQUA),
                Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.affordable_hint")
                    .withStyle(ChatFormatting.GRAY)
            ), Optional.empty(), mx, my, null);
            return true;
        }
        if (overExpand) {
            TooltipPresentation.show(ctx, font, List.of(
                Component.translatable(allExpanded
                    ? "gui.chiseled_enchanting_table.cleanse_filter.collapse_all"
                    : "gui.chiseled_enchanting_table.cleanse_filter.expand_all").withStyle(ChatFormatting.AQUA),
                Component.translatable("gui.chiseled_enchanting_table.cleanse_filter.expand_hint").withStyle(ChatFormatting.GRAY)
            ), Optional.empty(), mx, my, null);
            return true;
        }
        return false;
    }

    private static void drawActiveOutline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
