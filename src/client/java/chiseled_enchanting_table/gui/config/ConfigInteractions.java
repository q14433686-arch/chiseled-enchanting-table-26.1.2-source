package chiseled_enchanting_table.gui.config;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import chiseled_enchanting_table.config.ConfigCatalog.OptionEntry;
import org.jetbrains.annotations.Nullable;
/** Rendered interaction registrations; the screen owns collection, dispatch and drag state. */
final class ConfigInteractions {
    private ConfigInteractions() {}
    record HitBox(Box box, Runnable onLeftClick, @Nullable Runnable onRightClick) {
        boolean contains(double mx, double my) {
            return box.contains(mx, my);
        }
    }
    record SliderTrack(OptionEntry entry, Box box) {
        boolean contains(double mx, double my) {
            return box.contains(mx, my);
        }
    }
}
