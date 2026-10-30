package enchantment_fusion.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.menu.FusionNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/** Network protocol for the configuration screen. Editing is always checked on the server. */
public final class FusionConfigNetworking {
    public static final Identifier MANAGE_PERMISSION = EnchantmentFusion.id("config/manage");
    private static final long UPDATE_INTERVAL_NANOS = 250_000_000L;
    private static final Map<UUID, Long> LAST_UPDATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private FusionConfigNetworking() {}

    public record RequestPayload(int unused) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(EnchantmentFusion.id("config_request"));
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.unused()), buf -> new RequestPayload(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePayload(String file, String pathJson, String valueJson, boolean reset,
                                long revision, long requestId) implements CustomPacketPayload {
        public static final Type<UpdatePayload> TYPE = new Type<>(EnchantmentFusion.id("config_update"));
        public static final StreamCodec<FriendlyByteBuf, UpdatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.file(), 64); buf.writeUtf(p.pathJson(), 4096);
                buf.writeUtf(p.valueJson(), FusionConfigStore.MAX_VALUE_CHARS);
                buf.writeBoolean(p.reset()); buf.writeLong(p.revision()); buf.writeLong(p.requestId());
            },
            buf -> new UpdatePayload(buf.readUtf(64), buf.readUtf(4096),
                buf.readUtf(FusionConfigStore.MAX_VALUE_CHARS), buf.readBoolean(), buf.readLong(), buf.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncPayload(String effectiveJson, String overridesJson, long revision,
                              boolean canEdit, long requestId, String result) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(EnchantmentFusion.id("config_sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.effectiveJson(), FusionConfigStore.MAX_DOCUMENT_CHARS);
                buf.writeUtf(p.overridesJson(), FusionConfigStore.MAX_DOCUMENT_CHARS);
                buf.writeLong(p.revision()); buf.writeBoolean(p.canEdit());
                buf.writeLong(p.requestId()); buf.writeUtf(p.result(), 32);
            },
            buf -> new SyncPayload(buf.readUtf(FusionConfigStore.MAX_DOCUMENT_CHARS),
                buf.readUtf(FusionConfigStore.MAX_DOCUMENT_CHARS), buf.readLong(), buf.readBoolean(),
                buf.readLong(), buf.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean canEdit(MinecraftServer server, ServerPlayer player) {
        return (!server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId()))
            || player.checkPermission(MANAGE_PERMISSION, PermissionLevel.GAMEMASTERS);
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdatePayload.TYPE, UpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), 0, "sync"));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_UPDATE.remove(handler.getPlayer().getUUID()); LAST_REQUEST.remove(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LAST_UPDATE.clear(); LAST_REQUEST.clear(); });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, 0, "sync");
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                long now = System.nanoTime();
                Long last = LAST_REQUEST.get(player.getUUID());
                if (last == null || now - last >= UPDATE_INTERVAL_NANOS) {
                    LAST_REQUEST.put(player.getUUID(), now);
                    send(player, 0, "sync");
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(UpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            server.execute(() -> {
                if (!canEdit(server, player)) { send(player, payload.requestId(), "denied"); return; }
                long now = System.nanoTime();
                Long last = LAST_UPDATE.get(player.getUUID());
                if (last != null && now - last < UPDATE_INTERVAL_NANOS) { send(player, payload.requestId(), "throttled"); return; }
                LAST_UPDATE.put(player.getUUID(), now);
                if (payload.requestId() <= 0) { send(player, payload.requestId(), "invalid"); return; }
                try {
                    JsonElement pathElement = JsonParser.parseString(payload.pathJson());
                    if (!pathElement.isJsonArray()) throw new IllegalArgumentException("path must be an array");
                    JsonArray pathArray = pathElement.getAsJsonArray();
                    if (pathArray.isEmpty() || pathArray.size() > 8) throw new IllegalArgumentException("invalid path");
                    List<String> path = new ArrayList<>();
                    for (JsonElement part : pathArray) {
                        if (!part.isJsonPrimitive() || !part.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("invalid path segment");
                        path.add(part.getAsString());
                    }
                    JsonElement value = payload.reset() ? null : JsonParser.parseString(payload.valueJson());
                    FusionConfigStore.UpdateResult result = FusionConfigStore.update(
                        payload.file(), path, value, payload.reset(), payload.revision());
                    if (result.success()) {
                        for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
                            FusionNetworking.sendMatrixSync(observer);
                            send(observer, observer == player ? payload.requestId() : 0, observer == player ? "saved" : "sync");
                        }
                    } else send(player, payload.requestId(), result.reason());
                } catch (RuntimeException ex) {
                    EnchantmentFusion.LOGGER.debug("Rejected malformed config update from {}", player.getScoreboardName(), ex);
                    send(player, payload.requestId(), "invalid");
                }
            });
        });
    }

    private static void send(ServerPlayer player, long requestId, String result) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !ServerPlayNetworking.canSend(player, SyncPayload.TYPE)) return;
        FusionConfigStore.View view = FusionConfigStore.view();
        String effective = view.effective().toString(), overrides = view.overrides().toString();
        if (effective.length() > FusionConfigStore.MAX_DOCUMENT_CHARS || overrides.length() > FusionConfigStore.MAX_DOCUMENT_CHARS) {
            EnchantmentFusion.LOGGER.error("Enchantment Fusion config exceeds network limit; not sending to {}", player.getScoreboardName());
            return;
        }
        ServerPlayNetworking.send(player, new SyncPayload(effective, overrides, view.revision(), canEdit(server, player), requestId, result));
    }
}
