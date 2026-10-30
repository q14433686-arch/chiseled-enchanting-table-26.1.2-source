package enchantment_fusion.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import enchantment_fusion.gui.EnchantmentFusionConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Optional ModMenu integration opening {@link EnchantmentFusionConfigScreen} directly from ModMenu.
 */
@Environment(EnvType.CLIENT)
public final class EnchantmentFusionModMenuPlugin implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return EnchantmentFusionConfigScreen::new;
    }
}
