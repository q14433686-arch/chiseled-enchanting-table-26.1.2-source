package enchantment_fusion.registry;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking.OpenStationPayload;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FusionMenus {
    public static final ExtendedMenuType<EnchantmentFusionMenu, OpenStationPayload> ENCHANTMENT_FUSION_MENU =
        Registry.register(
            BuiltInRegistries.MENU,
            EnchantmentFusion.id("enchantment_fusion_table"),
            new ExtendedMenuType<>(EnchantmentFusionMenu::new, OpenStationPayload.STREAM_CODEC)
        );

    private FusionMenus() {}

    public static void init() {
        // Explicit bootstrap trigger
    }
}
