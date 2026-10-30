package enchantment_fusion.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import enchantment_fusion.gui.FusionConfigScreen;

/** Loaded only by Mod Menu, which supplies the real API at runtime. */
public final class EnchantmentFusionModMenuPlugin implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FusionConfigScreen::new;
    }
}
