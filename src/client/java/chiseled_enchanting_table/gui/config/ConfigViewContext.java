package chiseled_enchanting_table.gui.config;
import chiseled_enchanting_table.config.ConfigCatalog.*;
import chiseled_enchanting_table.config.ModRuntimeConfig.Draft;
import net.minecraft.client.gui.Font;
import static chiseled_enchanting_table.gui.config.ConfigInteractions.*;
/** Explicit capabilities: no Screen, networking, persistence, or arbitrary layout mutation. */
interface ConfigViewContext {
    Font font();
    Draft draft();
    Draft defaults();
    Draft classicReference();
    Draft displayed(OptionEntry entry);
    SessionMode mode();
    int focusedIndex();
    void registerHit(HitBox hit);
    void registerSlider(SliderTrack slider);
    void changed();
    void sound(float pitch);
    void navigate(CategoryId category);
}
