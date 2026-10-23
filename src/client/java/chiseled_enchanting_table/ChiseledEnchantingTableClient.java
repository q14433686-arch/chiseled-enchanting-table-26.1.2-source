package chiseled_enchanting_table;

import chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.config.ModConfigNetworking;
import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.gui.ChiseledEnchantingTableScreen;
import chiseled_enchanting_table.gui.config.ChiseledEnchantingConfigScreen;
import chiseled_enchanting_table.gui.grimoire.ArcaneGrimoireScreen;
import chiseled_enchanting_table.gui.grimoire.GrimoireSoundHelper;
import chiseled_enchanting_table.item.ArcaneGrimoireItem;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.registry.EntityRegistry;
import chiseled_enchanting_table.registry.ScreenHandlerRegistry;
import chiseled_enchanting_table.render.ChiseledEnchantingTableBlockEntityRenderer;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommands.literal("cet")
                    .then(ClientCommands.literal("config")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.gui.setScreen(new ChiseledEnchantingConfigScreen(null)));
                            return 1;
                        }))
            )
        );

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

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            if (!stack.is(Items.ENCHANTED_BOOK)) {
                return;
            }
            int wearUsed = MasterTomeWear.getWearUsed(stack);
            if (wearUsed <= 0) {
                return;
            }
            var cfg = SyncedEnchantmentCosts.libraryResonanceConfig();
            if (cfg.tomeConsumptionMode() == MasterTomeWear.ConsumptionMode.KEEP) {
                return;
            }
            int maxUses = Math.max(1, cfg.maxTomeUses());
            int remaining = Math.max(1, maxUses - wearUsed);
            float ratio = (float) remaining / (float) maxUses;
            int color = ratio > 0.5f
                ? 0xFF55FFFF
                : (ratio > 0.25f ? 0xFFFFAA00 : 0xFFFF5555);
            lines.add(Component.translatable(
                "item.chiseled_enchanting_table.master_tome_charges",
                remaining,
                maxUses
            ).withStyle(style -> style.withColor(color)));
        });

        ClientPlayNetworking.registerGlobalReceiver(ModConfigNetworking.SyncRuntimeConfigPayload.TYPE, (payload, ctx) ->
            ctx.client().execute(() -> {
                ModRuntimeConfig synced = ModRuntimeConfig.fromJsonString(payload.json());
                ModRuntimeConfig.applyRemoteSync(synced, payload.canEdit());
                // In an integrated server the shared tables were already committed on the server thread.
                if (!ctx.client().hasSingleplayerServer()) ChiseledEnchantingTableData.applyRuntimeConfig(synced);
                if (payload.requestId() > 0) ModRuntimeConfig.acknowledgeSave(payload.requestId(), payload.accepted());
                var player = ctx.client().player;
                if (player != null && player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                    if (menu.onContentChangedCallback != null) {
                        menu.onContentChangedCallback.run();
                    }
                }
            }));

        // Server-authoritative costs for the menu and recipe viewers. Sent on join
        // and after every data pack reload; server catalogs (even empty) are authoritative.
        ClientPlayNetworking.registerGlobalReceiver(EnchantCostTablePayload.TYPE, (payload, ctx) ->
            ctx.client().execute(() -> {
                SyncedEnchantmentCosts.apply(payload);
                var player = ctx.client().player;
                if (player != null && player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                    menu.applyTransformationSnapshot(payload.transformations());
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE,
            (payload, ctx) -> ctx.client().execute(() -> {
                var player = ctx.client().player;
                if (player != null && player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                    menu.applyLiveShelfState(payload);
                }
            })
        );
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            SyncedEnchantmentCosts.clear();
            ModRuntimeConfig.clearRemoteSync();
            ChiseledEnchantingTableData.applyRuntimeConfig(ModRuntimeConfig.get());
        });
    }
}
