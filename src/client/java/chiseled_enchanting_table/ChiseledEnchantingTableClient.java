package chiseled_enchanting_table;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.gui.grimoire.ArcaneGrimoireScreen;
import chiseled_enchanting_table.gui.grimoire.GrimoireSoundHelper;
import chiseled_enchanting_table.item.ArcaneGrimoireItem;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.render.ChiseledEnchantingTableBlockEntityRenderer;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class ChiseledEnchantingTableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.EnchantingProtocol.Capability.TYPE, (payload, ctx) -> {});
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            if (chiseled_enchanting_table.menu.EnchantingProtocol.incompatible(
                ClientPlayNetworking.canSend(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID),
                ClientPlayNetworking.canSend(chiseled_enchanting_table.menu.EnchantingProtocol.Capability.TYPE))) {
                listener.getConnection().disconnect(net.minecraft.network.chat.Component.literal(
                    chiseled_enchanting_table.menu.EnchantingProtocol.MISMATCH_MESSAGE));
            }
        });
        ArcaneGrimoireItem.CLIENT_OPEN_ACTION = player -> {
            GrimoireSoundHelper.playOpen();
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ArcaneGrimoireScreen(null));
        };
        MenuScreens.register(ScreenHandlerRegistry.CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER, ChiseledEnchantingTableScreen::new);
        BlockEntityRendererRegistry.register(EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, ChiseledEnchantingTableBlockEntityRenderer::new);

        BlockColorRegistry.register(
            List.of(new BlockTintSource() {
                @Override
                public int color(BlockState state) {
                    return ChiseledEnchantingTableBlockEntity.DEFAULT_COLOR;
                }

                @Override
                public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    Object data = level.getBlockEntityRenderData(pos);
                    return data instanceof Integer argb ? argb : ChiseledEnchantingTableBlockEntity.DEFAULT_COLOR;
                }
            }),
            BlockRegistry.CHISELED_ENCHANTING_TABLE
        );

        // Server-authoritative costs for the menu and recipe viewers. Sent on join
        // and after every data pack reload; empty means "fall back to the canonical cost".
        ClientPlayNetworking.registerGlobalReceiver(EnchantCostTablePayload.TYPE, (payload, ctx) ->
            ctx.client().execute(() -> SyncedEnchantmentCosts.apply(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE,
            (payload, ctx) -> ctx.client().execute(() -> {
                var player = ctx.client().player;
                if (player != null && player.containerMenu instanceof chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler menu) {
                    menu.applyLiveShelfState(payload);
                }
            })
        );
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> SyncedEnchantmentCosts.clear());
    }
}
