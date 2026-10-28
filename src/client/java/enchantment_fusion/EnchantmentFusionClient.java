package enchantment_fusion;

import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.gui.FusionTooltipRenderer;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.menu.FusionNetworking.CompendiumSyncPayload;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionMenus;
import enchantment_fusion.render.EnchantmentFusionTableRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

@Environment(EnvType.CLIENT)
public final class EnchantmentFusionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(FusionMenus.ENCHANTMENT_FUSION_MENU, EnchantmentFusionScreen::new);
        BlockEntityRendererRegistry.register(
            FusionBlockEntities.ENCHANTMENT_FUSION_TABLE_BE,
            EnchantmentFusionTableRenderer::new
        );
        FusionTooltipRenderer.register();

        ClientPlayNetworking.registerGlobalReceiver(CompendiumSyncPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> CompendiumDiscoveryTracker.applyClientSync(payload.unlockedPairs()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CompendiumDiscoveryTracker.applyClientSync(java.util.List.of());
        });
    }
}
