package chiseled_enchanting_table;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.render.ChiseledEnchantingTableBlockEntityRenderer;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ScreenHandlerRegistry.CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER, ChiseledEnchantingTableScreen::new);
        BlockEntityRendererRegistry.register(EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, ChiseledEnchantingTableBlockEntityRenderer::new);

        // Server-authoritative costs for the recipe viewers (and the tooltips they show). Sent on join
        // and after every data pack reload; empty means "fall back to the canonical cost".
        ClientPlayNetworking.registerGlobalReceiver(EnchantCostTablePayload.TYPE, (payload, ctx) ->
            ctx.client().execute(() -> SyncedEnchantmentCosts.apply(payload.costs())));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> SyncedEnchantmentCosts.clear());
    }
}
