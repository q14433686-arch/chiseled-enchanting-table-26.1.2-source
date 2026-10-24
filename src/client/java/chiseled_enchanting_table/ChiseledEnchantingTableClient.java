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
import net.minecraft.ChatFormatting;
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
    public static final net.minecraft.client.KeyMapping.Category KEY_CATEGORY =
        net.minecraft.client.KeyMapping.Category.register(ChiseledEnchantingTable.identifier("keybindings"));
    public static net.minecraft.client.KeyMapping KEY_GRIMOIRE_RECIPE;
    public static net.minecraft.client.KeyMapping KEY_GRIMOIRE_USES;

    private static boolean serverHasAnyApplyChannel() {
        if (ClientPlayNetworking.canSend(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID
        )) {
            return true;
        }
        for (var probe : chiseled_enchanting_table.menu.EnchantingProtocol.LEGACY_APPLY_PROBES) {
            if (ClientPlayNetworking.canSend(probe)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInitializeClient() {
        KEY_GRIMOIRE_RECIPE = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping(
                "key.chiseled_enchanting_table.grimoire_recipe",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                com.mojang.blaze3d.platform.InputConstants.KEY_R,
                KEY_CATEGORY
            )
        );
        KEY_GRIMOIRE_USES = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping(
                "key.chiseled_enchanting_table.grimoire_uses",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                com.mojang.blaze3d.platform.InputConstants.KEY_U,
                KEY_CATEGORY
            )
        );
        chiseled_enchanting_table.compat.IrisGlintPreload.register();
        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.EnchantingProtocol.Capability.TYPE,
            (payload, ctx) -> ctx.client().execute(() -> {
                int localStack = ChiseledEnchantingTable.bootEnchantedBookStackSize();
                if (chiseled_enchanting_table.menu.EnchantingProtocol.stackSizeIncompatible(
                    localStack, payload.enchantedBookStackSize()
                )) {
                    var conn = ctx.client().getConnection();
                    if (conn != null) {
                        conn.getConnection().disconnect(
                            chiseled_enchanting_table.menu.EnchantingProtocol.stackSizeMismatchComponent(
                                localStack, payload.enchantedBookStackSize()
                            )
                        );
                    }
                }
            })
        );
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            if (chiseled_enchanting_table.menu.EnchantingProtocol.incompatible(
                serverHasAnyApplyChannel(),
                ClientPlayNetworking.canSend(chiseled_enchanting_table.menu.EnchantingProtocol.Capability.TYPE))) {
                listener.getConnection().disconnect(
                    chiseled_enchanting_table.menu.EnchantingProtocol.protocolMismatchComponent()
                );
                return;
            }
            if (ClientPlayNetworking.canSend(chiseled_enchanting_table.menu.EnchantingProtocol.Capability.TYPE)) {
                ClientPlayNetworking.send(new chiseled_enchanting_table.menu.EnchantingProtocol.Capability());
            }
        });
        ArcaneGrimoireItem.CLIENT_OPEN_ACTION = player -> {
            GrimoireSoundHelper.playOpen();
            Minecraft.getInstance().setScreen(new ArcaneGrimoireScreen(null));
        };
        MenuScreens.register(ScreenHandlerRegistry.CHISELED_ENCHANTING_TABLE_SCREEN_HANDLER, ChiseledEnchantingTableScreen::new);
        BlockEntityRendererRegistry.register(EntityRegistry.CHISELED_ENCHANTING_TABLE_ENTITY_TYPE, ChiseledEnchantingTableBlockEntityRenderer::new);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommands.literal("cet")
                    .then(ClientCommands.literal("config")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.setScreen(new ChiseledEnchantingConfigScreen(null)));
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
            if (!stack.is(Items.ENCHANTED_BOOK) || ModRuntimeConfig.effective().classic103Mode()) {
                return;
            }
            var cfg = SyncedEnchantmentCosts.libraryResonanceConfig();
            if (cfg.tomeConsumptionMode() == MasterTomeWear.ConsumptionMode.KEEP) {
                return;
            }
            boolean rawImmuneTag = chiseled_enchanting_table.mechanics.resonance.TomeWearData.hasWearImmunity(stack);
            boolean effectiveImmune = MasterTomeWear.hasEffectiveWearImmunity(cfg, stack);
            int wearUsed = MasterTomeWear.getWearUsed(stack);
            int generation = chiseled_enchanting_table.mechanics.resonance.TomeWearData.getGeneration(stack);
            if (effectiveImmune) {
                lines.add(Component.translatable(
                    "item.chiseled_enchanting_table.master_tome_infinite"
                ).withStyle(ChatFormatting.LIGHT_PURPLE));
                int nonCurseCount = chiseled_enchanting_table.mechanics.resonance.TomeWearData
                    .countNonCurseStoredEnchantments(stack);
                int waiverPct = (int) Math.round(
                    chiseled_enchanting_table.mechanics.resonance.TomeWearData
                        .computeWearImmuneWaiverChance(cfg, nonCurseCount) * 100.0
                );
                if (nonCurseCount > cfg.wearImmuneFullWaiverMaxEnchants()) {
                    lines.add(Component.translatable(
                        "item.chiseled_enchanting_table.master_tome_overload_waiver",
                        waiverPct,
                        nonCurseCount
                    ).withStyle(ChatFormatting.GOLD));
                }
                var rCfg = SyncedEnchantmentCosts.ritualConfig();
                double loadL = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
                    .computeInfinityTomeQualificationLoad(rCfg, cfg, stack);
                if (loadL >= rCfg.crosslineSlip().minLoadThreshold()) {
                    lines.add(Component.translatable(
                        "item.chiseled_enchanting_table.master_tome_overloaded_hint",
                        String.format(java.util.Locale.ROOT, "%.1f", loadL)
                    ).withStyle(ChatFormatting.GOLD));
                }
                if (wearUsed <= 0 && nonCurseCount <= cfg.wearImmuneFullWaiverMaxEnchants()) {
                    if (generation > 0) {
                        lines.add(Component.translatable(
                            "item.chiseled_enchanting_table.master_tome_generation",
                            generation,
                            0
                        ).withStyle(ChatFormatting.DARK_PURPLE));
                    }
                    return;
                }
            } else if (rawImmuneTag && generation > cfg.wearImmuneMaxGeneration()) {
                lines.add(Component.translatable(
                    "item.chiseled_enchanting_table.master_tome_generation_lost_immunity",
                    generation,
                    cfg.wearImmuneMaxGeneration()
                ).withStyle(ChatFormatting.GOLD));
            }
            int maxUses = chiseled_enchanting_table.mechanics.resonance.TomeWearData.effectiveMaxUses(
                cfg.maxTomeUses(),
                generation,
                rawImmuneTag,
                cfg.wearImmuneMaxGeneration()
            );
            int remaining = Math.max(1, maxUses - wearUsed);
            float ratio = (float) remaining / (float) maxUses;
            ChatFormatting color = wearUsed <= 0
                ? ChatFormatting.DARK_AQUA
                : (ratio > 0.5f ? ChatFormatting.AQUA : (ratio > 0.25f ? ChatFormatting.GOLD : ChatFormatting.RED));
            lines.add(Component.translatable(
                "item.chiseled_enchanting_table.master_tome_charges",
                remaining,
                maxUses
            ).withStyle(color));
            if (generation > 0) {
                lines.add(Component.translatable(
                    "item.chiseled_enchanting_table.master_tome_generation",
                    generation,
                    Math.max(0, cfg.maxTomeUses() - maxUses)
                ).withStyle(ChatFormatting.DARK_PURPLE));
            }
            if (!effectiveImmune) {
                int waiverPct = (int) Math.round(MasterTomeWear.computeUnbreakingWaiverChance(stack, cfg) * 100.0);
                if (waiverPct > 0) {
                    lines.add(Component.translatable(
                        "item.chiseled_enchanting_table.master_tome_unbreaking_hint",
                        waiverPct
                    ).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            if (wearUsed > 0 && cfg.tomeRegenEnabled()) {
                lines.add(Component.translatable(
                    "item.chiseled_enchanting_table.master_tome_regen_hint",
                    cfg.tomeRegenTier1Shelves(),
                    cfg.tomeRegenTier2Shelves()
                ).withStyle(ChatFormatting.DARK_GRAY));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ModConfigNetworking.SyncRuntimeConfigPayload.TYPE, (payload, ctx) ->
            ctx.client().execute(() -> {
                ModRuntimeConfig synced = ModRuntimeConfig.fromJsonString(payload.effectiveJson());
                ModRuntimeConfig.setRemotePreferences(ModRuntimeConfig.fromJsonString(payload.json()));
                ModRuntimeConfig.PackPolicy policy = ModRuntimeConfig.PackPolicy.fromJsonString(payload.policyJson());
                ModRuntimeConfig.applyRemoteSync(synced, policy, payload.canEdit());
                // In an integrated server the shared tables were already committed on the server thread.
                if (!ctx.client().hasSingleplayerServer()) ChiseledEnchantingTableData.applyRuntimeConfig(synced, ModRuntimeConfig.PackPolicy.NONE, () -> {});
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
        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.TYPE,
            (payload, ctx) -> ctx.client().execute(() -> {
                var player = ctx.client().player;
                if (player != null && player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                    menu.applySyncedRitualState(payload);
                }
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload.TYPE,
            (payload, ctx) -> ctx.client().execute(() -> {
                SyncedEnchantmentCosts.updateTransformations(payload.transformations());
                var player = ctx.client().player;
                if (player != null
                    && player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                    && payload.targets(menu.containerId)) {
                    menu.applyTransformationSnapshot(payload.transformations());
                }
            })
        );
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            SyncedEnchantmentCosts.clear();
            ModRuntimeConfig.clearRemoteSync();
            // Audit COUP-04: mirror the SYNC path's guard. In singleplayer the integrated
            // server may still be saving final chunks that read the shared data tables;
            // the tables it committed already reflect the local config, so only re-apply
            // on remote connections where the client alone owns the shared statics.
            if (!client.hasSingleplayerServer()) {
                ChiseledEnchantingTableData.applyRuntimeConfig(ModRuntimeConfig.get());
            }
        });
    }
}
