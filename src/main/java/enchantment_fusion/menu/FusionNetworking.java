package enchantment_fusion.menu;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.data.FusionMatrixRegistry.MatrixProfile;
import enchantment_fusion.mechanics.FusionStationOperations.StationPreview;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Explicit-wire-ID network protocol for the 2-Tab Enchantment Fusion Table:
 * <ul>
 *   <li>Stable explicit wire IDs instead of {@code enum.ordinal()} + per-tick server rate guard (NET-02 / EF-008).</li>
 *   <li>Expected candidate token verification on {@code EXECUTE_PRIMARY} against shared-table slot races (NET-03).</li>
 *   <li>Server-to-client {@link MatrixSyncPayload} for datapack profiles, exclusive groups & economy (NET-01).</li>
 *   <li>Bounded {@link CompendiumSyncPayload} + incremental {@link CompendiumDeltaPayload} (NET-05).</li>
 * </ul>
 */
public final class FusionNetworking {
    public enum StationAction {
        SELECT_TAB(10),
        PICK_PRIMARY_A(11),
        PICK_SECONDARY_B(12),
        SWAP_ORDER(13),
        EXECUTE_PRIMARY(14),
        FLIP_SOCKET(15),
        UNSOCKET_OR_SPLIT(16),
        TOGGLE_SINGLE_MODE(17);

        private final int wireId;

        StationAction(int wireId) {
            this.wireId = wireId;
        }

        public int wireId() {
            return wireId;
        }

        public static @Nullable StationAction fromWireId(int id) {
            for (StationAction a : values()) {
                if (a.wireId == id) {
                    return a;
                }
            }
            return null;
        }
    }

    public record OpenStationPayload(BlockPos blockPos) implements CustomPacketPayload {
        public static final Type<OpenStationPayload> TYPE =
            new Type<>(EnchantmentFusion.id("open_station"));
        public static final StreamCodec<ByteBuf, OpenStationPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(OpenStationPayload::new, OpenStationPayload::blockPos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StationActionPayload(
        int containerId,
        int actionWireId,
        int paramA,
        int paramB,
        String expectedToken
    ) implements CustomPacketPayload {
        public static final Type<StationActionPayload> TYPE =
            new Type<>(EnchantmentFusion.id("station_action"));
        public static final StreamCodec<ByteBuf, StationActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StationActionPayload::containerId,
            ByteBufCodecs.VAR_INT, StationActionPayload::actionWireId,
            ByteBufCodecs.VAR_INT, StationActionPayload::paramA,
            ByteBufCodecs.VAR_INT, StationActionPayload::paramB,
            ByteBufCodecs.stringUtf8(512), StationActionPayload::expectedToken,
            StationActionPayload::new
        );

        public StationActionPayload(int containerId, StationAction action, int paramA, int paramB, String expectedToken) {
            this(containerId, action.wireId(), paramA, paramB, expectedToken == null ? "" : expectedToken);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CompendiumSyncPayload(List<String> unlockedPairs) implements CustomPacketPayload {
        public static final Type<CompendiumSyncPayload> TYPE =
            new Type<>(EnchantmentFusion.id("compendium_sync"));
        public static final StreamCodec<ByteBuf, CompendiumSyncPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(4096))
                .map(CompendiumSyncPayload::new, CompendiumSyncPayload::unlockedPairs);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CompendiumDeltaPayload(String entryKey, int stage) implements CustomPacketPayload {
        public static final Type<CompendiumDeltaPayload> TYPE =
            new Type<>(EnchantmentFusion.id("compendium_delta"));
        public static final StreamCodec<ByteBuf, CompendiumDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), CompendiumDeltaPayload::entryKey,
            ByteBufCodecs.VAR_INT, CompendiumDeltaPayload::stage,
            CompendiumDeltaPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MatrixSyncPayload(FusionMatrixRegistry.Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<MatrixSyncPayload> TYPE =
            new Type<>(EnchantmentFusion.id("matrix_sync"));
        public static final StreamCodec<FriendlyByteBuf, MatrixSyncPayload> STREAM_CODEC = StreamCodec.of(
            MatrixSyncPayload::write,
            MatrixSyncPayload::read
        );

        private static void write(FriendlyByteBuf buf, MatrixSyncPayload payload) {
            FusionMatrixRegistry.Snapshot snap = payload.snapshot();
            Map<String, MatrixProfile> profiles = snap.profiles();
            buf.writeVarInt(profiles.size());
            for (Map.Entry<String, MatrixProfile> e : profiles.entrySet()) {
                buf.writeUtf(e.getKey(), 128);
                buf.writeUtf(e.getValue().trigger().name(), 64);
                buf.writeUtf(e.getValue().payload().name(), 64);
            }
            List<Set<String>> groups = snap.exclusiveGroups();
            buf.writeVarInt(groups.size());
            for (Set<String> g : groups) {
                buf.writeVarInt(g.size());
                for (String id : g) {
                    buf.writeUtf(id, 128);
                }
            }
            EconomyConfig eco = snap.economy();
            buf.writeDouble(eco.lowFailChance());
            buf.writeDouble(eco.midFailChance());
            buf.writeDouble(eco.highFailChance());
            buf.writeDouble(eco.repulsiveFailChance());
            buf.writeDouble(eco.repulsiveMinFloor());
            buf.writeDouble(eco.stabilizerReductionRepulsive());
            buf.writeDouble(eco.stabilizerReductionNormal());
            buf.writeVarInt(eco.maxRepulsiveStabilizers());
        }

        private static MatrixSyncPayload read(FriendlyByteBuf buf) {
            int pCount = Math.clamp(buf.readVarInt(), 0, 2048);
            Map<String, MatrixProfile> profiles = new HashMap<>(pCount);
            for (int i = 0; i < pCount; i++) {
                String key = buf.readUtf(128);
                TriggerType trig = TriggerType.fromString(buf.readUtf(64), TriggerType.ON_MELEE_STRIKE);
                PayloadType pay = PayloadType.fromString(buf.readUtf(64), PayloadType.EDGE);
                profiles.put(key, new MatrixProfile(trig, pay));
            }
            int gCount = Math.clamp(buf.readVarInt(), 0, 256);
            List<Set<String>> groups = new ArrayList<>(gCount);
            for (int i = 0; i < gCount; i++) {
                int sz = Math.clamp(buf.readVarInt(), 0, 64);
                Set<String> g = new HashSet<>(sz);
                for (int j = 0; j < sz; j++) {
                    g.add(buf.readUtf(128));
                }
                if (g.size() >= 2) {
                    groups.add(Collections.unmodifiableSet(g));
                }
            }
            EconomyConfig eco = new EconomyConfig(
                Math.clamp(buf.readDouble(), 0.0, 1.0),
                Math.clamp(buf.readDouble(), 0.0, 1.0),
                Math.clamp(buf.readDouble(), 0.0, 1.0),
                Math.clamp(buf.readDouble(), 0.0, 1.0),
                Math.clamp(buf.readDouble(), 0.0, 1.0),
                Math.clamp(buf.readDouble(), 0.01, 1.0),
                Math.clamp(buf.readDouble(), 0.01, 1.0),
                Math.clamp(buf.readVarInt(), 0, 16)
            );
            return new MatrixSyncPayload(new FusionMatrixRegistry.Snapshot(
                Collections.unmodifiableMap(profiles),
                Collections.unmodifiableList(groups),
                eco.repulsiveFailChance(),
                eco
            ));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenGrimoirePayload(int initialChapter) implements CustomPacketPayload {
        public static final Type<OpenGrimoirePayload> TYPE =
            new Type<>(EnchantmentFusion.id("open_grimoire"));
        public static final StreamCodec<ByteBuf, OpenGrimoirePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(OpenGrimoirePayload::new, OpenGrimoirePayload::initialChapter);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static final int VISUAL_FUSE_SUCCESS = 1;
    public static final int VISUAL_FUSE_ABERRANT = 2;
    public static final int VISUAL_FUSE_FAIL = 3;
    public static final int VISUAL_FLIP_ORDER = 4;
    public static final int VISUAL_SPLIT_EXTRACT = 5;

    public record FusionVisualEventPayload(BlockPos blockPos, int eventKind) implements CustomPacketPayload {
        public static final Type<FusionVisualEventPayload> TYPE =
            new Type<>(EnchantmentFusion.id("visual_event"));
        public static final StreamCodec<ByteBuf, FusionVisualEventPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FusionVisualEventPayload::blockPos,
            ByteBufCodecs.VAR_INT, FusionVisualEventPayload::eventKind,
            FusionVisualEventPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final Map<UUID, long[]> ACTION_RATE_GUARD = new ConcurrentHashMap<>();

    private FusionNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(StationActionPayload.TYPE, StationActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumSyncPayload.TYPE, CompendiumSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumDeltaPayload.TYPE, CompendiumDeltaPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MatrixSyncPayload.TYPE, MatrixSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenGrimoirePayload.TYPE, OpenGrimoirePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FusionVisualEventPayload.TYPE, FusionVisualEventPayload.STREAM_CODEC);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ACTION_RATE_GUARD.remove(handler.getPlayer().getUUID());
        });

        ServerPlayNetworking.registerGlobalReceiver(StationActionPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.containerMenu instanceof EnchantmentFusionMenu menu)) {
                    return;
                }
                if (menu.containerId != payload.containerId() || !menu.stillValid(player)) {
                    return;
                }
                long tick = player.level().getGameTime();
                long[] bucket = ACTION_RATE_GUARD.computeIfAbsent(player.getUUID(), k -> new long[]{tick, 0});
                if (bucket[0] != tick) {
                    bucket[0] = tick;
                    bucket[1] = 0;
                }
                if (++bucket[1] > 10) {
                    return;
                }

                StationAction action = StationAction.fromWireId(payload.actionWireId());
                if (action == null) {
                    return;
                }
                // Executions must bind to the candidate selection the player saw.
                // Other inventory changes are still revalidated by the station operation.
                if (action == StationAction.EXECUTE_PRIMARY && payload.expectedToken().isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.enchantment_fusion.action_rejected").withStyle(ChatFormatting.RED), true);
                    return;
                }
                boolean ok = menu.performAction(player, action, payload.paramA(), payload.paramB(), payload.expectedToken());
                if (!ok && action == StationAction.EXECUTE_PRIMARY) {
                    StationPreview now = menu.computePreview();
                    String key = !now.rejectReason().langKey().isEmpty()
                        ? now.rejectReason().langKey()
                        : "message.enchantment_fusion.action_rejected";
                    player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
                }
            });
        });
    }

    public static void sendCompendiumSync(ServerPlayer player, Collection<String> encodedStates) {
        if (ServerPlayNetworking.canSend(player, CompendiumSyncPayload.TYPE)) {
            List<String> copy = new ArrayList<>(encodedStates);
            if (copy.size() > 4096) {
                copy = copy.subList(0, 4096);
            }
            ServerPlayNetworking.send(player, new CompendiumSyncPayload(copy));
        }
    }

    public static void sendCompendiumDelta(ServerPlayer player, String entryKey, int stage) {
        if (ServerPlayNetworking.canSend(player, CompendiumDeltaPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CompendiumDeltaPayload(entryKey, stage));
        }
    }

    public static void sendMatrixSync(ServerPlayer player) {
        sendMatrixSync(player, FusionMatrixRegistry.serverSnapshot());
    }

    public static void sendMatrixSync(ServerPlayer player, FusionMatrixRegistry.Snapshot snapshot) {
        if (ServerPlayNetworking.canSend(player, MatrixSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new MatrixSyncPayload(snapshot));
        }
    }

    public static void applyClientMatrixSync(MatrixSyncPayload payload) {
        if (payload != null) {
            FusionMatrixRegistry.setClientSnapshot(payload.snapshot());
        }
    }

    public static void sendOpenGrimoire(ServerPlayer player, int initialChapter) {
        if (ServerPlayNetworking.canSend(player, OpenGrimoirePayload.TYPE)) {
            ServerPlayNetworking.send(player, new OpenGrimoirePayload(initialChapter));
        }
    }

    public static void broadcastVisualEvent(
        net.minecraft.server.level.ServerLevel level,
        BlockPos pos,
        int eventKind
    ) {
        if (level == null || pos == null) {
            return;
        }
        FusionVisualEventPayload payload = new FusionVisualEventPayload(pos, eventKind);
        double maxDistSqr = 48.0D * 48.0D;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= maxDistSqr
                && ServerPlayNetworking.canSend(sp, FusionVisualEventPayload.TYPE)) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
    }
}
