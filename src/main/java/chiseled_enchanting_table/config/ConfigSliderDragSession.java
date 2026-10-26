package chiseled_enchanting_table.config;

import chiseled_enchanting_table.config.ConfigCatalog.OptionEntry;
import chiseled_enchanting_table.config.ConfigLayoutSpec.Box;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;

/** Owns only a slider drag, independently of native mouse numbering, rendering and Screen lifecycle. */
public final class ConfigSliderDragSession {
    private OptionEntry entry;
    private Box track;

    public boolean active() { return this.entry != null; }

    public void begin(OptionEntry entry, Box track, Draft draft, double mouseX) {
        this.entry = java.util.Objects.requireNonNull(entry);
        this.track = java.util.Objects.requireNonNull(track);
        drag(draft, mouseX);
    }

    public boolean drag(Draft draft, double mouseX) {
        if (!active()) return false;
        double normalized = (mouseX - this.track.x()) / Math.max(1.0, this.track.w());
        this.entry.setFromNormalizedSlider(draft, normalized);
        return true;
    }

    public void release() {
        this.entry = null;
        this.track = null;
    }
}
