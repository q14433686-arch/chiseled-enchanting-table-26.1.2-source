package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.config.SearchInputSession;
import chiseled_enchanting_table.config.SearchTextBuffer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.menu.AvailableOption;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import static chiseled_enchanting_table.gui.TableSkin.*;

/**
 * Compact 15px Inscribe-page search/filter bar + deterministic multi-option cost-bundle selector.
 *
 * <p>Searches both enchantment/transformation names and material names across all cost bundles,
 * toggles affordable-only filtering and expand/collapse-all, and pins player-cycled cost bundles
 * so the server strictly deducts the displayed bundle without timer drift.</p>
 */
public final class InscribeFilterBar {
    private final NativeFilterInput nativeInput = new NativeFilterInput();
    private final SearchInputSession searchInput = this.nativeInput.session;
    private final SearchTextBuffer searchBuffer = searchInput.buffer();
    private boolean affordableOnly = false;
    private final Map<AvailableOption, Integer> pinnedCostIndices = new HashMap<>();

    public String query() {
        return this.searchBuffer.text().trim();
    }

    public boolean hasActiveFilter() {
        return !query().isEmpty() || this.affordableOnly;
    }

    public boolean affordableOnly() {
        return this.affordableOnly;
    }

    public boolean searchFocused() {
        return this.searchInput.focused();
    }

    public void unfocus() {
        if (this.searchInput.focused()) {
            this.searchInput.setFocused(false);
        }
    }

    public int resolveCostOptionIndex(AvailableOption option, List<List<ItemStack>> options, ItemStack costSlot) {
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
                if (b != null && !b.isEmpty() && ItemStack.isSameItemSameComponents(b.getFirst(), costSlot)) {
                    return i;
                }
            }
            for (int i = 0; i < options.size(); i++) {
                List<ItemStack> b = options.get(i);
                if (b == null) continue;
                for (ItemStack st : b) {
                    if (!st.isEmpty() && ItemStack.isSameItemSameComponents(st, costSlot)) {
                        return i;
                    }
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

    public static boolean matchesQuery(
        String rawQuery,
        String localizedTitle,
        Identifier id,
        List<List<ItemStack>> costOptions
    ) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }
        String needle = rawQuery.trim().toLowerCase(Locale.ROOT);
        if (localizedTitle != null && localizedTitle.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (id != null && (id.getPath().toLowerCase(Locale.ROOT).contains(needle)
            || id.toString().toLowerCase(Locale.ROOT).contains(needle))) {
            return true;
        }
        if (costOptions != null) {
            for (List<ItemStack> bundle : costOptions) {
                if (bundle == null) continue;
                for (ItemStack stack : bundle) {
                    if (stack == null || stack.isEmpty()) continue;
                    if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                    Identifier itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemKey != null && itemKey.getPath().toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        var searchRect = TomeDirectoryLayout.inscribeSearchBox();
        var affordRect = TomeDirectoryLayout.inscribeAffordableButton();
        var expandRect = TomeDirectoryLayout.inscribeExpandButton();

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
        if (!this.searchInput.focused()) {
            return false;
        }
        if (this.searchBuffer.insertCodePoint(event.codepoint())) {
            onFilterChanged.run();
        }
        return true;
    }

    public boolean keyPressed(KeyEvent event, Runnable onFilterChanged) {
        if (!this.searchInput.focused()) {
            return false;
        }
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
                if (this.searchBuffer.hasSelection()) {
                    mc.keyboardHandler.setClipboard(this.searchBuffer.selectedText());
                }
                return true;
            }
            if (key == InputConstants.KEY_V && mc.keyboardHandler != null) {
                if (this.searchBuffer.insertText(mc.keyboardHandler.getClipboard())) {
                    onFilterChanged.run();
                }
                return true;
            }
        }
        // Consume all printable keys (including 'E') while focused so inventory hotkey never closes screen.
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
        var searchRect = TomeDirectoryLayout.inscribeSearchBox();
        var affordRect = TomeDirectoryLayout.inscribeAffordableButton();
        var expandRect = TomeDirectoryLayout.inscribeExpandButton();

        int sx = barX + searchRect.x();
        int sy = barY + searchRect.y();
        this.nativeInput.updateArea(sx + 5, sy, searchRect.width() - 10, searchRect.height());
        Identifier searchSprite = this.searchInput.focused() ? SEARCH_BOX_FOCUSED : SEARCH_BOX;
        ctx.blit(searchSprite, sx, sy, sx + searchRect.width(), sy + searchRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);

        String text = this.searchBuffer.text();
        ctx.enableScissor(sx + 5, sy + 2, sx + searchRect.width() - 5, sy + searchRect.height() - 2);
        try {
            if (text.isEmpty() && !this.searchInput.focused()) {
                String ph = Component.translatable("gui.chiseled_enchanting_table.inscribe.search_placeholder").getString();
                RowRenderSupport.renderCenteredYText(ctx, font, ph, sx + 6, sy + 7.5f, 0.56f, searchRect.width() - 12, 0xFF9C90B3);
            } else {
                String shown = text;
                if (this.searchInput.focused() && ((net.minecraft.util.Util.getMillis() / 500L) & 1L) == 0L) {
                    shown = text + "|";
                }
                RowRenderSupport.renderCenteredYText(ctx, font, shown, sx + 6, sy + 7.5f, 0.60f, searchRect.width() - 18, 0xFFF7F2EA);
                if (!text.isEmpty()) {
                    RowRenderSupport.renderCenteredYText(ctx, font, "\u00d7", sx + searchRect.width() - 10, sy + 7.5f, 0.64f, 6, 0xFFD9B8FF);
                }
            }
        } finally {
            ctx.disableScissor();
        }

        int ax = barX + affordRect.x();
        int ay = barY + affordRect.y();
        ctx.blit(BTN_FILTER_AFFORDABLE, ax, ay, ax + affordRect.width(), ay + affordRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);
        boolean overAfford = affordRect.contains(mx - barX, my - barY);
        if (this.affordableOnly) {
            ctx.fill(ax, ay, ax + affordRect.width(), ay + 1, 0xFF7CFFA6);
            ctx.fill(ax, ay + affordRect.height() - 1, ax + affordRect.width(), ay + affordRect.height(), 0xFF7CFFA6);
            ctx.fill(ax, ay, ax + 1, ay + affordRect.height(), 0xFF7CFFA6);
            ctx.fill(ax + affordRect.width() - 1, ay, ax + affordRect.width(), ay + affordRect.height(), 0xFF7CFFA6);
        } else if (overAfford) {
            ctx.fill(ax + 1, ay + 1, ax + affordRect.width() - 1, ay + affordRect.height() - 1, 0x2EFFDF82);
        }

        int ex = barX + expandRect.x();
        int ey = barY + expandRect.y();
        ctx.blit(BTN_FILTER_EXPAND, ex, ey, ex + expandRect.width(), ey + expandRect.height(), 0.0f, 1.0f, 0.0f, 1.0f);
        boolean overExpand = expandRect.contains(mx - barX, my - barY);
        if (allExpanded) {
            ctx.fill(ex, ey, ex + expandRect.width(), ey + 1, 0xFFFFDF82);
            ctx.fill(ex, ey + expandRect.height() - 1, ex + expandRect.width(), ey + expandRect.height(), 0xFFFFDF82);
        } else if (overExpand) {
            ctx.fill(ex + 1, ey + 1, ex + expandRect.width() - 1, ey + expandRect.height() - 1, 0x2EFFDF82);
        }

        if (overAfford) {
            TooltipPresentation.show(ctx, font, List.of(
                Component.translatable("gui.chiseled_enchanting_table.inscribe.filter_affordable.title")
                    .withStyle(this.affordableOnly ? ChatFormatting.GREEN : ChatFormatting.GOLD),
                Component.translatable(this.affordableOnly
                    ? "gui.chiseled_enchanting_table.inscribe.filter_affordable.on"
                    : "gui.chiseled_enchanting_table.inscribe.filter_affordable.off").withStyle(ChatFormatting.GRAY)
            ), Optional.empty(), mx, my, null);
            return true;
        }
        if (overExpand) {
            TooltipPresentation.show(ctx, font, List.of(
                Component.translatable(allExpanded
                    ? "gui.chiseled_enchanting_table.inscribe.collapse_all"
                    : "gui.chiseled_enchanting_table.inscribe.expand_all").withStyle(ChatFormatting.GOLD),
                Component.translatable("gui.chiseled_enchanting_table.inscribe.expand_hint").withStyle(ChatFormatting.GRAY)
            ), Optional.empty(), mx, my, null);
            return true;
        }
        return false;
    }
}
