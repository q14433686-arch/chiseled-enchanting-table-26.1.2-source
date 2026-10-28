package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.config.SearchInputSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Binds filter focus to the 26.3 SDL text-input owner, including release after screen changes. */
final class NativeFilterInput {
    private Screen owner;
    final SearchInputSession session = new SearchInputSession(this::focusChanged);

    private void focusChanged(boolean focused) {
        var client = Minecraft.getInstance();
        if (focused) this.owner = client.gui.screen();
        if (this.owner != null) client.onTextInputFocusChange(this.owner, focused);
        if (!focused) this.owner = null;
    }

    static boolean captures(EnchantementListWidget list) {
        return list != null && (list.inscribeFilter.searchFocused() || list.cleanseFilter.searchFocused());
    }

    static void release(EnchantementListWidget list) {
        if (list != null) {
            list.inscribeFilter.unfocus();
            list.cleanseFilter.unfocus();
        }
    }

    void updateArea(int x, int y, int width, int height) {
        if (this.session.focused()) {
            Minecraft.getInstance().textInputManager().setTextInputArea(x, y, x + width, y + height);
        }
    }
}
