package chiseled_enchanting_table.config;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-adaptive bridge between the config search field and the game's native (SDL) text-input
 * manager. On some 26.x lines the vanilla focus path calls {@code Minecraft#onTextInputFocusChange};
 * a hand-drawn search field that only flips a private boolean never starts native text capture, so
 * no {@code CharacterEvent} reaches {@code charTyped(...)} and the box looks dead. Other 26.x lines
 * deliver character events directly with no SDL layer, so a static call would not compile there.
 *
 * <p>All game-specific hooks are resolved reflectively by name (never by exact descriptor), so this
 * single source compiles on every 26.x line and fails open (no-op) where the hook is absent. This
 * closes the 26.2-only coupling gap left by the 26.1.2 backport, which hard-coded the "no SDL"
 * assumption. Cross-version compile is verified by the real 26.2 CI (compile-check workflow).
 */
public final class NativeTextInputFocus {
    private static final Method FOCUS_CHANGE = resolveFocusChange();
    private static volatile boolean reportedFailure;

    private NativeTextInputFocus() {}

    private static Method resolveFocusChange() {
        try {
            for (Method m : Minecraft.class.getMethods()) {
                if (m.getName().equals("onTextInputFocusChange") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == boolean.class) {
                    return m;
                }
            }
        } catch (Throwable ignored) {
            // No reflection access to the client class (headless unit test environment).
        }
        return null;
    }

    /** True when the running game exposes an SDL-like native text-input focus hook. */
    public static boolean hasNativeFocusHook() {
        return FOCUS_CHANGE != null;
    }

    /** Start or stop native text capture; no-op where the hook is absent. */
    public static void apply(Minecraft minecraft, Screen screen, boolean focused) {
        if (FOCUS_CHANGE == null || minecraft == null) {
            return;
        }
        try {
            FOCUS_CHANGE.invoke(minecraft, screen, focused);
        } catch (Throwable error) {
            if (!reportedFailure) {
                reportedFailure = true;
                chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.warn(
                    "Native text-input focus hook failed; config search may not receive typed text", error);
            }
        }
    }
}
