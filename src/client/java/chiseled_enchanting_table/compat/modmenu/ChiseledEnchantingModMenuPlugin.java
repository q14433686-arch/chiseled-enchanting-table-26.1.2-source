package chiseled_enchanting_table.compat.modmenu;

import chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ChiseledEnchantingModMenuPlugin implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ChiseledEnchantingConfigScreen::new;
    }
}
