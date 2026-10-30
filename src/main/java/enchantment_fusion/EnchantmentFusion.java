package enchantment_fusion;

import enchantment_fusion.compat.CetSoftBridge;
import enchantment_fusion.config.FusionConfigNetworking;
import enchantment_fusion.data.FusionDataLoader;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionWorldLoot;
import enchantment_fusion.mechanics.MatrixRuntimeHooks;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.mixin.FusionMixinConfigPlugin;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import enchantment_fusion.registry.FusionMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for Enchantment Fusion (融合附魔).
 */
public final class EnchantmentFusion implements ModInitializer {
    public static final String MOD_ID = "enchantment_fusion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        FusionDataComponents.init();
        FusionBlocks.init();
        FusionItems.init();
        FusionBlockEntities.init();
        FusionMenus.init();
        FusionNetworking.init();
        FusionConfigNetworking.init();
        FusionDataLoader.init();
        FusionWorldLoot.init();
        CompendiumDiscoveryTracker.initServerHooks();
        MatrixRuntimeHooks.init();
        if (CetSoftBridge.isCetLoaded()) {
            int applied = FusionMixinConfigPlugin.appliedCetMixinCount();
            if (applied == 0) {
                LOGGER.warn(
                    "Sister mod Chiseled Enchanting Table detected, yet 0 CET @Pseudo bridge mixins have applied so far; check sister-mod version compatibility."
                );
            } else {
                LOGGER.info("Chiseled Enchanting Table soft-synergy bridge active ({} server/common mixins applied).", applied);
            }
        }
        LOGGER.info("Enchantment Fusion initialized (26.1.2 full 43-enchantment & Grimoire system ready).");
    }
}
