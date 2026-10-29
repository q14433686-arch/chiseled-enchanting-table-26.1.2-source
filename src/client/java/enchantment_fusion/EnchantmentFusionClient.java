package enchantment_fusion;

import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.gui.FusionGrimoireScreen;
import enchantment_fusion.gui.FusionGuiMicroEffects;
import enchantment_fusion.gui.FusionTooltipRenderer;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.menu.FusionNetworking.CompendiumDeltaPayload;
import enchantment_fusion.menu.FusionNetworking.CompendiumSyncPayload;
import enchantment_fusion.menu.FusionNetworking.FusionVisualEventPayload;
import enchantment_fusion.menu.FusionNetworking.MatrixSyncPayload;
import enchantment_fusion.menu.FusionNetworking.OpenGrimoirePayload;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionMenus;
import enchantment_fusion.render.EnchantmentFusionTableRenderer;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client entrypoint registering the pixel-art Fusion Table screen, floating BlockEntity renderer,
 * standalone Fusion Grimoire screen, Matrix & Compendium sync receivers (NET-01 / NET-05),
 * and dynamic Shift-expandable tooltips.
 */
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
        ClientPlayNetworking.registerGlobalReceiver(CompendiumDeltaPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                CompendiumDiscoveryTracker.applyClientDelta(payload.entryKey(), payload.stage());
                FusionGuiMicroEffects.triggerDiscoveryGlow();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(MatrixSyncPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> FusionNetworking.applyClientMatrixSync(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenGrimoirePayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> ctx.client().setScreen(new FusionGrimoireScreen(null, payload.initialChapter())));
        });
        ClientPlayNetworking.registerGlobalReceiver(FusionVisualEventPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                if (ctx.client().level != null
                    && ctx.client().level.getBlockEntity(payload.blockPos()) instanceof EnchantmentFusionTableBlockEntity be) {
                    be.triggerClientSurge(payload.eventKind());
                }
                FusionGuiMicroEffects.onServerVisualEvent(payload.eventKind());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CompendiumDiscoveryTracker.applyClientSync(List.of());
            FusionMatrixRegistry.setClientSnapshot(null);
        });
    }
}
