package chiseled_enchanting_table.config;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import chiseled_enchanting_table.menu.EnchantCostSync;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Bidirectional server-authoritative synchronization &amp; management commands for
 * {@link ModRuntimeConfig}.
 */
public final class ModConfigNetworking {
    private ModConfigNetworking() {}

    public static final Identifier CONFIG_MANAGE_PERMISSION =
        ChiseledEnchantingTable.identifier("config/manage");

    public record SyncRuntimeConfigPayload(String json, String policyJson, String effectiveJson, boolean canEdit, long requestId, boolean accepted) implements CustomPacketPayload {
        public SyncRuntimeConfigPayload(String json, boolean edit, long request, boolean accepted) {
            this(json, "", json, edit, request, accepted);
        }
        public static final CustomPacketPayload.Type<SyncRuntimeConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("sync_runtime_config_" + chiseled_enchanting_table.menu.EnchantingProtocol.REVISION));

        public static final StreamCodec<FriendlyByteBuf, SyncRuntimeConfigPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.json(), 262_144);
                buf.writeUtf(payload.policyJson() == null ? "" : payload.policyJson(), 65_536);
                buf.writeUtf(payload.effectiveJson(), 262_144);
                buf.writeBoolean(payload.canEdit());
                buf.writeLong(payload.requestId());
                buf.writeBoolean(payload.accepted());
            },
            buf -> new SyncRuntimeConfigPayload(buf.readUtf(262_144), buf.readUtf(65_536), buf.readUtf(262_144), buf.readBoolean(), buf.readLong(), buf.readBoolean())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateRuntimeConfigPayload(String json, long requestId, boolean closeContainer) implements CustomPacketPayload {
        public UpdateRuntimeConfigPayload(String json, long requestId) {
            this(json, requestId, true);
        }

        public static final CustomPacketPayload.Type<UpdateRuntimeConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("update_runtime_config_" + chiseled_enchanting_table.menu.EnchantingProtocol.REVISION));

        public static final StreamCodec<FriendlyByteBuf, UpdateRuntimeConfigPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.json(), 262_144);
                buf.writeLong(payload.requestId());
                buf.writeBoolean(payload.closeContainer());
            },
            buf -> new UpdateRuntimeConfigPayload(buf.readUtf(262_144), buf.readLong(), buf.readBoolean())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static boolean mayManage(boolean dedicated, boolean owner, boolean permitted) {
        return (!dedicated && owner) || permitted;
    }

    public static boolean canPlayerManageConfig(MinecraftServer server, ServerPlayer player) {
        if (ModRuntimeConfig.serverPackPolicy().lockInGameConfig()) return false;
        return mayManage(server.isDedicatedServer(), server.isSingleplayerOwner(player.nameAndId()),
            player.checkPermission(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS));
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(
            SyncRuntimeConfigPayload.TYPE,
            SyncRuntimeConfigPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            UpdateRuntimeConfigPayload.TYPE,
            UpdateRuntimeConfigPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(UpdateRuntimeConfigPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = player.level().getServer();
            if (server == null || !canPlayerManageConfig(server, player)) {
                sendToPlayer(player, payload.requestId(), false);
                return;
            }
            try {
                if (payload.requestId() <= 0 || payload.json().isBlank()) throw new IllegalArgumentException("Invalid save request");
                ModRuntimeConfig parsed = ModRuntimeConfig.fromJsonString(payload.json());
                applyAndBroadcast(server, parsed, payload.closeContainer() ? null : player);
                sendToPlayer(player, payload.requestId(), true);
            } catch (RuntimeException error) {
                ChiseledEnchantingTable.LOGGER.warn("Rejected config update from {}", player.getName().getString(), error);
                sendToPlayer(player, payload.requestId(), false);
                player.sendSystemMessage(Component.translatable("config.chiseled_enchanting_table.msg.save_failed"));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(
                Commands.literal("cet")
                    .then(Commands.literal("cancel-ritual").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                            && menu.tableBlockPos() != null
                            && player.level().getBlockEntity(menu.tableBlockPos()) instanceof
                                chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity table
                            && table.cancelRitual(player.getUUID())) {
                            player.sendSystemMessage(Component.translatable("command.chiseled_enchanting_table.ritual_cancelled"));
                            table.syncOpenMenusWatchingTable(player.level(), menu.tableBlockPos());
                            return 1;
                        }
                        return 0;
                    }))
                    .then(Commands.literal("reload-config")
                        .requires(PermissionPredicates.require(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS))
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            ModRuntimeConfig reloaded = ModRuntimeConfig.reloadFromDisk();
                            applyAndBroadcast(server, reloaded);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("config.chiseled_enchanting_table.msg.reloaded"),
                                true
                            );
                            return 1;
                        }))
                    .then(Commands.literal("reset-config")
                        .requires(PermissionPredicates.require(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS))
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            if (ModRuntimeConfig.serverPackPolicy().lockInGameConfig()) {
                                ctx.getSource().sendFailure(
                                    Component.translatable("config.chiseled_enchanting_table.reason.pack_locked")
                                );
                                return 0;
                            }
                            applyAndBroadcast(server, ModRuntimeConfig.DEFAULT);
                            ctx.getSource().sendSuccess(
                                () -> Component.translatable("config.chiseled_enchanting_table.msg.reset_all"),
                                true
                            );
                            return 1;
                        }))
            );
        });
    }

    public static void applyAndBroadcast(MinecraftServer server, ModRuntimeConfig updated) {
        applyAndBroadcast(server, updated, null);
    }

    public static void applyAndBroadcast(
        MinecraftServer server,
        ModRuntimeConfig updated,
        @org.jetbrains.annotations.Nullable ServerPlayer keepOpenCaller
    ) {
        ModRuntimeConfig sanitized = updated.withEnforcedPolicy(ModRuntimeConfig.serverPackPolicy());
        ChiseledEnchantingTableData.applyRuntimeConfig(sanitized, ModRuntimeConfig.serverPackPolicy(),
            () -> ModRuntimeConfig.setLocalAndSave(sanitized));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // Rule changes can invalidate opening-time unlock/scan snapshots. Vanilla returns inputs.
            if (p.containerMenu instanceof ChiseledEnchantingTableScreenHandler openMenu) {
                if (keepOpenCaller != null && p == keepOpenCaller) {
                    sendToPlayer(p);
                    EnchantCostSync.sendTo(p);
                    openMenu.refreshAfterRuntimeConfigChange();
                    continue;
                }
                p.closeContainer();
            }
            sendToPlayer(p);
            EnchantCostSync.sendTo(p);
        }
    }

    public static void sendToPlayer(ServerPlayer player) {
        sendToPlayer(player, 0L, true);
    }

    private static void sendToPlayer(ServerPlayer player, long requestId, boolean accepted) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !ServerPlayNetworking.canSend(player, SyncRuntimeConfigPayload.TYPE)) {
            return;
        }
        try {
            ServerPlayNetworking.send(
                player,
                new SyncRuntimeConfigPayload(
                    ModRuntimeConfig.get().toJsonString(),
                    ModRuntimeConfig.serverPackPolicy().toJsonString(),
                    ChiseledEnchantingTableData.runtimeConfiguration().toJsonString(),
                    canPlayerManageConfig(server, player), requestId, accepted
                )
            );
        } catch (RuntimeException e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to sync runtime config to {}", player.getName().getString(), e);
        }
    }
}
