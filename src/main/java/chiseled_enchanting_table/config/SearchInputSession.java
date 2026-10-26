package chiseled_enchanting_table.config;

import java.util.function.Consumer;

/** Text editing plus native-input ownership; the client adapter supplies SDL start/stop. */
public final class SearchInputSession {
    private final SearchTextBuffer buffer = new SearchTextBuffer();
    private final Consumer<Boolean> nativeFocus;
    private boolean focused;
    public SearchInputSession(Consumer<Boolean> nativeFocus) {
        this.nativeFocus = java.util.Objects.requireNonNull(nativeFocus);
    }
    public SearchTextBuffer buffer() { return buffer; }
    public boolean focused() { return focused; }
    public void setFocused(boolean focused) {
        this.focused = focused;
        nativeFocus.accept(focused);
    }
}
