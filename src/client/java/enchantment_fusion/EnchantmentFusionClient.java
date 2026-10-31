package enchantment_fusion;

import enchantment_fusion.block.EnchantmentFusionTableBlockEntity;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.config.FusionConfigClient;
import enchantment_fusion.config.FusionConfigNetworking;
import enchantment_fusion.gui.FusionConfigScreen;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.gui.FusionGrimoireScreen;
import enchantment_fusion.gui.FusionRitualDirector;
import enchantment_fusion.gui.FusionGuiMicroEffects;
import enchantment_fusion.gui.FusionTooltipRenderer;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.menu.FusionSkillNetworking;
import enchantment_fusion.menu.FusionNetworking.CompendiumDeltaPayload;
import enchantment_fusion.menu.FusionNetworking.CompendiumSyncPayload;
import enchantment_fusion.menu.FusionNetworking.FusionVisualEventPayload;
import enchantment_fusion.menu.FusionNetworking.LoyaltyGuardActivationPayload;
import enchantment_fusion.menu.FusionNetworking.MatrixSyncPayload;
import enchantment_fusion.menu.FusionNetworking.OpenGrimoirePayload;
import enchantment_fusion.registry.FusionBlockEntities;
import enchantment_fusion.registry.FusionMenus;
import enchantment_fusion.render.EnchantmentFusionTableRenderer;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

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
        KeyMapping.Category skillCategory = KeyMapping.Category.register(EnchantmentFusion.id("abilities"));
        KeyMapping skillKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.enchantment_fusion.activate_skill", GLFW.GLFW_KEY_G, skillCategory));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (skillKey.consumeClick()) {
                if (client.player != null && client.screen == null
                    && ClientPlayNetworking.canSend(FusionSkillNetworking.ActivatePayload.TYPE)) {
                    ClientPlayNetworking.send(FusionSkillNetworking.ActivatePayload.INSTANCE);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FusionConfigNetworking.SyncPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> FusionConfigClient.apply(payload));
        });
        // Open after the chat screen has finished closing, not in the command callback itself.
        boolean[] openConfigNextTick = {false};
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommands.literal("fusion").then(ClientCommands.literal("config").executes(ctx -> {
                openConfigNextTick[0] = true;
                return 1;
            }))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigNextTick[0]) {
                openConfigNextTick[0] = false;
                client.setScreen(new FusionConfigScreen(null));
            }
        });

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
        ClientPlayNetworking.registerGlobalReceiver(LoyaltyGuardActivationPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> ctx.client().gameRenderer.displayItemActivation(payload.item()));
        });
        ClientPlayNetworking.registerGlobalReceiver(FusionVisualEventPayload.TYPE, (payload, ctx) -> {
            ctx.client().execute(() -> {
                if (ctx.client().level != null
                    && ctx.client().level.getBlockEntity(payload.blockPos()) instanceof EnchantmentFusionTableBlockEntity be) {
                    be.triggerClientSurge(payload.eventKind());
                }
                FusionRitualDirector.onServerResult(payload.blockPos(), payload.eventKind());
                FusionGuiMicroEffects.onServerVisualEvent(payload.eventKind());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CompendiumDiscoveryTracker.applyClientSync(List.of());
            FusionMatrixRegistry.setClientSnapshot(null);
            FusionConfigClient.disconnect();
        });
    }
}
