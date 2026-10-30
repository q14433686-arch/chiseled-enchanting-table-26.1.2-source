package enchantment_fusion.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.menu.EnchantmentFusionMenu;
import enchantment_fusion.menu.FusionNetworking;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 * Bidirectional server-authoritative runtime config synchronization and management commands
 * ({@code /enchantment_fusion} &amp; {@code /ef}), modeled after {@code Chiseled Enchanting Table}.
 */
public final class ModConfigNetworking {
    private static final Gson COMPACT_GSON = new Gson();
    public static final Identifier CONFIG_MANAGE_PERMISSION = EnchantmentFusion.id("config/manage");
    private static final Map<UUID, Long> LAST_UPDATE_NANO = new ConcurrentHashMap<>();
    private static final long MIN_UPDATE_INTERVAL_NANOS = 250_000_000L;
    private static volatile boolean clientCanEdit = true;

    private ModConfigNetworking() {}

    public record SyncRuntimeConfigPayload(
        String json,
        boolean canEdit,
        long requestId,
        boolean accepted
    ) implements CustomPacketPayload {
        public static final Type<SyncRuntimeConfigPayload> TYPE =
            new Type<>(EnchantmentFusion.id("sync_runtime_config"));
        public static final StreamCodec<FriendlyByteBuf, SyncRuntimeConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.json(), 262_144);
                buf.writeBoolean(p.canEdit());
                buf.writeLong(p.requestId());
                buf.writeBoolean(p.accepted());
            },
            buf -> new SyncRuntimeConfigPayload(
                buf.readUtf(262_144),
                buf.readBoolean(),
                buf.readLong(),
                buf.readBoolean()
            )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateRuntimeConfigPayload(
        String json,
        long requestId
    ) implements CustomPacketPayload {
        public static final Type<UpdateRuntimeConfigPayload> TYPE =
            new Type<>(EnchantmentFusion.id("update_runtime_config"));
        public static final StreamCodec<FriendlyByteBuf, UpdateRuntimeConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.json(), 262_144);
                buf.writeLong(p.requestId());
            },
            buf -> new UpdateRuntimeConfigPayload(buf.readUtf(262_144), buf.readLong())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenConfigPayload() implements CustomPacketPayload {
        public static final Type<OpenConfigPayload> TYPE =
            new Type<>(EnchantmentFusion.id("open_config_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenConfigPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenConfigPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static boolean clientCanEdit() {
        return clientCanEdit;
    }

    public static void setClientCanEdit(boolean allowed) {
        clientCanEdit = allowed;
    }

    public static boolean canPlayerManageConfig(MinecraftServer server, ServerPlayer player) {
        return (!server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId()))
            || player.checkPermission(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS);
    }

    public static String snapshotToJsonString(ModRuntimeConfig.Snapshot snapshot) {
        return COMPACT_GSON.toJson(ConfigFileStore.toJson(snapshot));
    }

    public static ModRuntimeConfig.Snapshot snapshotFromJsonString(String json) {
        var el = JsonParser.parseString(json);
        if (el == null || !el.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return ConfigFileStore.fromJson(el.getAsJsonObject());
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(SyncRuntimeConfigPayload.TYPE, SyncRuntimeConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenConfigPayload.TYPE, OpenConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateRuntimeConfigPayload.TYPE, UpdateRuntimeConfigPayload.STREAM_CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendToPlayer(handler.player, 0L, true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_UPDATE_NANO.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_UPDATE_NANO.clear());

        ServerPlayNetworking.registerGlobalReceiver(UpdateRuntimeConfigPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = ctx.server();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!canPlayerManageConfig(server, player)) {
                    sendToPlayer(player, payload.requestId(), false);
                    return;
                }
                long now = System.nanoTime();
                Long prev = LAST_UPDATE_NANO.get(player.getUUID());
                if (prev != null && now - prev < MIN_UPDATE_INTERVAL_NANOS) {
                    sendToPlayer(player, payload.requestId(), false);
                    return;
                }
                LAST_UPDATE_NANO.put(player.getUUID(), now);
                try {
                    if (payload.json() == null || payload.json().isBlank()) {
                        throw new IllegalArgumentException("Empty config payload");
                    }
                    ModRuntimeConfig.Snapshot parsed = snapshotFromJsonString(payload.json());
                    applyAndBroadcast(server, parsed);
                    sendToPlayer(player, payload.requestId(), true);
                } catch (RuntimeException ex) {
                    EnchantmentFusion.LOGGER.warn("Rejected config update from {}", player.getName().getString(), ex);
                    sendToPlayer(player, payload.requestId(), false);
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            registerCommandRoot(dispatcher, "enchantment_fusion");
            registerCommandRoot(dispatcher, "ef");
        });
    }

    private static void registerCommandRoot(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(
            Commands.literal(literal)
                .then(Commands.literal("config").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    sendToPlayer(player, 0L, true);
                    if (ServerPlayNetworking.canSend(player, OpenConfigPayload.TYPE)) {
                        ServerPlayNetworking.send(player, new OpenConfigPayload());
                    }
                    return 1;
                }))
                .then(Commands.literal("reload-config")
                    .requires(PermissionPredicates.require(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS))
                    .executes(ctx -> {
                        ModRuntimeConfig.Snapshot reloaded = ConfigFileStore.loadOrCreate();
                        applyAndBroadcast(ctx.getSource().getServer(), reloaded);
                        ctx.getSource().sendSuccess(() -> Component.translatable("gui.enchantment_fusion.config.msg.reloaded"), true);
                        return 1;
                    }))
                .then(Commands.literal("reset-config")
                    .requires(PermissionPredicates.require(CONFIG_MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS))
                    .executes(ctx -> {
                        applyAndBroadcast(ctx.getSource().getServer(), ModRuntimeConfig.Snapshot.DEFAULT);
                        ctx.getSource().sendSuccess(() -> Component.translatable("gui.enchantment_fusion.config.msg.reset_done"), true);
                        return 1;
                    }))
        );
    }

    public static void applyAndBroadcast(MinecraftServer server, ModRuntimeConfig.Snapshot updated) {
        ModRuntimeConfig.applyAndSave(updated);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendToPlayer(p, 0L, true);
            FusionNetworking.sendMatrixSync(p);
            if (p.containerMenu instanceof EnchantmentFusionMenu openMenu) {
                openMenu.slotsChanged(null);
            }
        }
    }

    public static void sendToPlayer(ServerPlayer player, long requestId, boolean accepted) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !ServerPlayNetworking.canSend(player, SyncRuntimeConfigPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(
            player,
            new SyncRuntimeConfigPayload(
                snapshotToJsonString(ModRuntimeConfig.localSnapshot()),
                canPlayerManageConfig(server, player),
                requestId,
                accepted
            )
        );
    }
}
