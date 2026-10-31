package enchantment_fusion.menu;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.data.FusionMatrixRegistry.EconomyConfig;
import enchantment_fusion.data.FusionMatrixRegistry.MatrixProfile;
import enchantment_fusion.mechanics.FusionStationOperations.ActionResult;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Explicit-wire-ID network protocol for the 2-Tab Enchantment Fusion Table:
 * <ul>
 *   <li>Stable explicit wire IDs instead of {@code enum.ordinal()} + per-tick server rate guard (NET-02 / EF-008).</li>
 *   <li>Expected candidate / pair token verification on {@code EXECUTE_PRIMARY}, {@code FLIP_SOCKET}, and {@code UNSOCKET_OR_SPLIT}
 *       with localized rejection feedback (NET-03 / NET-1 / NET-5).</li>
 *   <li>Versioned {@link MatrixSyncPayload} syncing full-curve {@link EconomyConfig} + trigger cooldowns (NET-01 / NUM-1 / NUM-2).</li>
 *   <li>Bounded {@link CompendiumSyncPayload} + incremental {@link CompendiumDeltaPayload} (NET-05 / NET-3 / NET-4).</li>
 * </ul>
 */
public final class FusionNetworking {
    public static final int MATRIX_SYNC_PROTOCOL_VERSION = 3;
    // A directional pair may contain two 128-character enchantment IDs plus separators.
    public static final int MAX_COMPENDIUM_KEY_CHARS = 512;

    public enum StationAction {
        SELECT_TAB(10),
        PICK_PRIMARY_A(11),
        PICK_SECONDARY_B(12),
        SWAP_ORDER(13),
        EXECUTE_PRIMARY(14),
        FLIP_SOCKET(15),
        UNSOCKET_OR_SPLIT(16),
        TOGGLE_SINGLE_MODE(17),
        SWAP_SLOTS(18),
        SKIP_RITUAL(19),
        CANCEL_RITUAL(20),
        PICK_SINGLE_A(21),
        EXTRACT_RETAINED(22);

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

    /** Optional clientbound revision of the shared target slot; zero is never sent. */
    public record StationTargetRevisionPayload(int containerId, long revision) implements CustomPacketPayload {
        public static final Type<StationTargetRevisionPayload> TYPE = new Type<>(EnchantmentFusion.id("station_target_revision"));
        public static final StreamCodec<ByteBuf, StationTargetRevisionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StationTargetRevisionPayload::containerId,
            ByteBufCodecs.VAR_LONG, StationTargetRevisionPayload::revision,
            StationTargetRevisionPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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
        public static final int MAX_SYNC_ENTRIES = 4096;
        public static final Type<CompendiumSyncPayload> TYPE =
            new Type<>(EnchantmentFusion.id("compendium_sync"));
        public static final StreamCodec<ByteBuf, CompendiumSyncPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(MAX_COMPENDIUM_KEY_CHARS).apply(ByteBufCodecs.list(MAX_SYNC_ENTRIES))
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
            ByteBufCodecs.stringUtf8(MAX_COMPENDIUM_KEY_CHARS), CompendiumDeltaPayload::entryKey,
            ByteBufCodecs.VAR_INT, CompendiumDeltaPayload::stage,
            CompendiumDeltaPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MatrixSyncPayload(@Nullable FusionMatrixRegistry.Snapshot snapshot) implements CustomPacketPayload {
        public static final int MAX_PROFILES = 2048;
        public static final int MAX_GROUPS = 256;
        public static final int MAX_GROUP_MEMBERS = 64;
        public static final int MAX_COOLDOWNS = 32;
        public static final Type<MatrixSyncPayload> TYPE =
            new Type<>(EnchantmentFusion.id("matrix_sync"));
        public static final StreamCodec<FriendlyByteBuf, MatrixSyncPayload> STREAM_CODEC = StreamCodec.of(
            MatrixSyncPayload::write,
            MatrixSyncPayload::read
        );

        /** Reject an unsynchronizable datapack before publishing it or writing any bytes. */
        public static void validateSnapshot(FusionMatrixRegistry.Snapshot snapshot) {
            if (snapshot.profiles().size() > MAX_PROFILES || snapshot.exclusiveGroups().size() > MAX_GROUPS
                || TriggerType.values().length > MAX_COOLDOWNS) {
                throw new IllegalArgumentException("matrix snapshot exceeds protocol count limits");
            }
            for (Map.Entry<String, MatrixProfile> entry : snapshot.profiles().entrySet()) {
                validateWireId(entry.getKey());
                MatrixProfile profile = entry.getValue();
                if (profile == null || profile.trigger() == null || profile.payload() == null
                    || profile.fusionHosts().size() > FusionCarrier.values().length) {
                    throw new IllegalArgumentException("invalid matrix profile");
                }
            }
            for (Set<String> group : snapshot.exclusiveGroups()) {
                if (group.size() > MAX_GROUP_MEMBERS) throw new IllegalArgumentException("exclusive group too large");
                for (String id : group) validateWireId(id);
            }
        }

        private static void validateWireId(String id) {
            if (id == null || id.length() > 128) throw new IllegalArgumentException("matrix identifier exceeds protocol limit");
        }

        private static int readCount(FriendlyByteBuf buf, int max, String field) {
            int count = buf.readVarInt();
            if (count < 0 || count > max) throw new DecoderException("Invalid " + field + " count: " + count);
            return count;
        }

        private static void write(FriendlyByteBuf buf, MatrixSyncPayload payload) {
            FusionMatrixRegistry.Snapshot snap = payload.snapshot() != null
                ? payload.snapshot()
                : FusionMatrixRegistry.serverSnapshot();
            try {
                validateSnapshot(snap);
            } catch (IllegalArgumentException ex) {
                throw new EncoderException(ex.getMessage(), ex);
            }
            buf.writeVarInt(MATRIX_SYNC_PROTOCOL_VERSION);
            Map<String, MatrixProfile> profiles = snap.profiles();
            buf.writeVarInt(profiles.size());
            for (Map.Entry<String, MatrixProfile> e : profiles.entrySet()) {
                buf.writeUtf(e.getKey(), 128);
                buf.writeUtf(e.getValue().trigger().name(), 64);
                buf.writeUtf(e.getValue().payload().name(), 64);
                buf.writeBoolean(e.getValue().explicitFusionMeaning());
                List<FusionCarrier> hosts = e.getValue().fusionHosts().stream().sorted().toList();
                buf.writeVarInt(hosts.size());
                for (FusionCarrier host : hosts) buf.writeUtf(host.name(), 64);
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
            buf.writeDouble(eco.aberrantSocketFailChance());
            buf.writeDouble(eco.failCeilingLow());
            buf.writeDouble(eco.failCeilingMid());
            buf.writeDouble(eco.failCeilingHigh());
            buf.writeDouble(eco.failCeilingRepulsive());
            buf.writeDouble(eco.failCeilingConversion());
            buf.writeDouble(eco.failCeilingSocket());
            buf.writeDouble(eco.stabilizerReductionRepulsive());
            buf.writeDouble(eco.stabilizerReductionNormal());
            buf.writeVarInt(eco.maxRepulsiveStabilizers());
            buf.writeVarInt(eco.maxNormalStabilizers());
            buf.writeVarInt(eco.maxLowStabilizers());
            buf.writeDouble(eco.normalFailFeeRatio());
            buf.writeVarInt(eco.repulsiveBaseXp());
            buf.writeVarInt(eco.repulsiveMaxBonusXp());
            buf.writeVarInt(eco.repulsiveMaxPairReagents());
            buf.writeVarInt(eco.repulsiveDefaultReagents());
            buf.writeVarInt(eco.highBaseXp());
            buf.writeVarInt(eco.highMaxXp());
            buf.writeVarInt(eco.highReagents());
            buf.writeVarInt(eco.midBaseXp());
            buf.writeVarInt(eco.midMinXp());
            buf.writeVarInt(eco.midMaxXp());
            buf.writeVarInt(eco.midMaxReagents());
            buf.writeVarInt(eco.lowBaseXp());
            buf.writeVarInt(eco.lowMinXp());
            buf.writeVarInt(eco.lowMaxReagents());
            buf.writeVarInt(eco.socketBookXp());
            buf.writeVarInt(eco.topUpXp());
            buf.writeVarInt(eco.expandVesselXp());
            buf.writeVarInt(eco.manageFlipSplitXp());
            buf.writeVarInt(eco.purifyAberrantXp());
            buf.writeVarInt(eco.purifyAberrantStabilizers());
            buf.writeDouble(eco.meleeMinCharge());
            buf.writeDouble(eco.meleeFullCharge());

            TriggerType[] allTriggers = TriggerType.values();
            buf.writeVarInt(allTriggers.length);
            for (TriggerType t : allTriggers) {
                buf.writeUtf(t.name(), 64);
                buf.writeVarInt(eco.cooldownTicksFor(t));
            }
        }

        private static MatrixSyncPayload read(FriendlyByteBuf buf) {
            int wireVersion = buf.readVarInt();
            if (wireVersion != MATRIX_SYNC_PROTOCOL_VERSION && wireVersion != 2) {
                int skipped = buf.readableBytes();
                buf.skipBytes(skipped);
                EnchantmentFusion.LOGGER.warn(
                    "Ignored MatrixSyncPayload with incompatible wire version {} (expected {}, skipped {} bytes)",
                    wireVersion,
                    MATRIX_SYNC_PROTOCOL_VERSION,
                    skipped
                );
                return new MatrixSyncPayload(null);
            }
            int pCount = readCount(buf, MAX_PROFILES, "profile");
            Map<String, MatrixProfile> profiles = new HashMap<>(pCount);
            for (int i = 0; i < pCount; i++) {
                String key = buf.readUtf(128);
                TriggerType trig = TriggerType.fromString(buf.readUtf(64), TriggerType.ON_MELEE_STRIKE);
                PayloadType pay = PayloadType.fromString(buf.readUtf(64), PayloadType.EDGE);
                boolean explicitMeaning = false;
                Set<FusionCarrier> hosts = java.util.EnumSet.noneOf(FusionCarrier.class);
                if (wireVersion >= 3) {
                    explicitMeaning = buf.readBoolean();
                    int count = readCount(buf, FusionCarrier.values().length, "fusion host");
                    for (int h = 0; h < count; h++) {
                        String name = buf.readUtf(64);
                        try {
                            if (!hosts.add(FusionCarrier.valueOf(name))) throw new DecoderException("Duplicate fusion host: " + name);
                        } catch (IllegalArgumentException ex) {
                            throw new DecoderException("Unknown fusion host: " + name, ex);
                        }
                    }
                }
                if (profiles.put(key, new MatrixProfile(trig, pay, explicitMeaning, hosts)) != null) {
                    throw new DecoderException("Duplicate matrix profile: " + key);
                }
            }
            int gCount = readCount(buf, MAX_GROUPS, "group");
            List<Set<String>> groups = new ArrayList<>(gCount);
            for (int i = 0; i < gCount; i++) {
                int sz = readCount(buf, MAX_GROUP_MEMBERS, "group member");
                Set<String> g = new HashSet<>(sz);
                for (int j = 0; j < sz; j++) {
                    g.add(buf.readUtf(128));
                }
                if (g.size() >= 2) {
                    groups.add(Collections.unmodifiableSet(g));
                }
            }

            double lowFail = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double midFail = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double highFail = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double repFail = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double repFloor = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double abSockFail = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilLow = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilMid = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilHigh = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilRep = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilConv = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double ceilSock = Math.clamp(buf.readDouble(), 0.0, 1.0);
            double stabRep = Math.clamp(buf.readDouble(), 0.01, 1.0);
            double stabNorm = Math.clamp(buf.readDouble(), 0.01, 1.0);
            int maxRepStab = Math.clamp(buf.readVarInt(), 0, 16);
            int maxNormStab = Math.clamp(buf.readVarInt(), 0, 16);
            int maxLowStab = Math.clamp(buf.readVarInt(), 0, 16);
            double failFeeRatio = Math.clamp(buf.readDouble(), 0.0, 1.0);
            int repBaseXp = Math.clamp(buf.readVarInt(), 0, 100);
            int repBonusXp = Math.clamp(buf.readVarInt(), 0, 50);
            int repMaxReag = Math.clamp(buf.readVarInt(), 0, 64);
            int repDefReag = Math.clamp(buf.readVarInt(), 0, 64);
            int highBaseXp = Math.clamp(buf.readVarInt(), 0, 100);
            int highMaxXp = Math.clamp(buf.readVarInt(), 0, 100);
            int highReag = Math.clamp(buf.readVarInt(), 0, 64);
            int midBaseXp = Math.clamp(buf.readVarInt(), 0, 100);
            int midMinXp = Math.clamp(buf.readVarInt(), 0, 100);
            int midMaxXp = Math.clamp(buf.readVarInt(), 0, 100);
            int midMaxReag = Math.clamp(buf.readVarInt(), 0, 64);
            int lowBaseXp = Math.clamp(buf.readVarInt(), 0, 100);
            int lowMinXp = Math.clamp(buf.readVarInt(), 0, 100);
            int lowMaxReag = Math.clamp(buf.readVarInt(), 0, 64);
            int sockXp = Math.clamp(buf.readVarInt(), 0, 100);
            int topUpXp = Math.clamp(buf.readVarInt(), 0, 100);
            int expandXp = Math.clamp(buf.readVarInt(), 0, 100);
            int manageXp = Math.clamp(buf.readVarInt(), 0, 100);
            int purifyXp = Math.clamp(buf.readVarInt(), 0, 200);
            int purifyStab = Math.clamp(buf.readVarInt(), 1, 64);
            double meleeMin = Math.clamp(buf.readDouble(), 0.05, 1.0);
            double meleeFull = Math.clamp(buf.readDouble(), 0.10, 1.0);

            int cdCount = readCount(buf, MAX_COOLDOWNS, "cooldown");
            Map<TriggerType, Integer> cooldowns = new EnumMap<>(EconomyConfig.DEFAULT_TRIGGER_COOLDOWNS);
            for (int i = 0; i < cdCount; i++) {
                TriggerType t = TriggerType.fromString(buf.readUtf(64), null);
                int ticks = Math.clamp(buf.readVarInt(), 4, 1200);
                if (t != null) {
                    cooldowns.put(t, ticks);
                }
            }

            EconomyConfig eco = new EconomyConfig(
                lowFail, midFail, highFail, repFail, repFloor, abSockFail,
                ceilLow, ceilMid, ceilHigh, ceilRep, ceilConv, ceilSock,
                stabRep, stabNorm, maxRepStab, maxNormStab, maxLowStab, failFeeRatio,
                repBaseXp, repBonusXp, repMaxReag, repDefReag,
                highBaseXp, highMaxXp, highReag,
                midBaseXp, midMinXp, midMaxXp, midMaxReag,
                lowBaseXp, lowMinXp, lowMaxReag,
                sockXp, topUpXp, expandXp, manageXp,
                purifyXp, purifyStab,
                meleeMin, meleeFull,
                cooldowns
            );
            return new MatrixSyncPayload(new FusionMatrixRegistry.Snapshot(
                Collections.unmodifiableMap(profiles),
                Collections.unmodifiableList(groups),
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

    public static final int SURGE_TICKS_NORMAL = 28;
    public static final int SURGE_TICKS_ABERRANT = 36;

    /** Shared surge duration source of truth used by both BlockEntity and Renderer (CPL-4 / DEAD-3). */
    public static int surgeTicksFor(int eventKind) {
        return eventKind == VISUAL_FUSE_ABERRANT ? SURGE_TICKS_ABERRANT : SURGE_TICKS_NORMAL;
    }

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

    /** A snapshot, rather than event 35, makes the actual equipped item appear in the activation animation. */
    public record LoyaltyGuardActivationPayload(ItemStack item) implements CustomPacketPayload {
        public static final Type<LoyaltyGuardActivationPayload> TYPE = new Type<>(EnchantmentFusion.id("loyalty_guard_activation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoyaltyGuardActivationPayload> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(LoyaltyGuardActivationPayload::new, LoyaltyGuardActivationPayload::item);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, long[]> ACTION_RATE_GUARD = new ConcurrentHashMap<>();

    private FusionNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(StationActionPayload.TYPE, StationActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StationTargetRevisionPayload.TYPE, StationTargetRevisionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumSyncPayload.TYPE, CompendiumSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumDeltaPayload.TYPE, CompendiumDeltaPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MatrixSyncPayload.TYPE, MatrixSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenGrimoirePayload.TYPE, OpenGrimoirePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FusionVisualEventPayload.TYPE, FusionVisualEventPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LoyaltyGuardActivationPayload.TYPE, LoyaltyGuardActivationPayload.STREAM_CODEC);

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
                boolean mutatesItem = action == StationAction.EXECUTE_PRIMARY
                    || action == StationAction.FLIP_SOCKET
                    || action == StationAction.UNSOCKET_OR_SPLIT
                    || action == StationAction.EXTRACT_RETAINED
                    || action == StationAction.SKIP_RITUAL
                    || action == StationAction.CANCEL_RITUAL;
                if ((action == StationAction.EXECUTE_PRIMARY || action == StationAction.FLIP_SOCKET
                    || action == StationAction.UNSOCKET_OR_SPLIT || action == StationAction.EXTRACT_RETAINED)
                    && payload.expectedToken().isEmpty()) {
                    player.sendSystemMessage(
                        Component.translatable("message.enchantment_fusion.action_rejected").withStyle(ChatFormatting.RED),
                        true
                    );
                    return;
                }

                ActionResult res = menu.performActionWithResult(
                    player, action, payload.paramA(), payload.paramB(), payload.expectedToken()
                );
                if (!res.success() && mutatesItem) {
                    String key = !res.reason().langKey().isEmpty()
                        ? res.reason().langKey()
                        : "message.enchantment_fusion.action_rejected";
                    player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
                }
            });
        });
    }

    public static void sendCompendiumSync(ServerPlayer player, Collection<String> encodedStates) {
        if (ServerPlayNetworking.canSend(player, CompendiumSyncPayload.TYPE)) {
            List<String> copy = new ArrayList<>();
            for (String state : encodedStates) {
                if (state != null && state.length() <= MAX_COMPENDIUM_KEY_CHARS) copy.add(state);
                else EnchantmentFusion.LOGGER.warn("Skipping an oversized compendium entry for {}", player.getScoreboardName());
            }
            if (copy.size() > CompendiumSyncPayload.MAX_SYNC_ENTRIES) {
                EnchantmentFusion.LOGGER.warn(
                    "Truncating compendium sync for {} from {} entries to protocol cap {}",
                    player.getScoreboardName(),
                    copy.size(),
                    CompendiumSyncPayload.MAX_SYNC_ENTRIES
                );
                copy = copy.subList(0, CompendiumSyncPayload.MAX_SYNC_ENTRIES);
            }
            ServerPlayNetworking.send(player, new CompendiumSyncPayload(copy));
        }
    }

    public static void sendCompendiumDelta(ServerPlayer player, String entryKey, int stage) {
        if (entryKey == null || entryKey.length() > MAX_COMPENDIUM_KEY_CHARS) {
            EnchantmentFusion.LOGGER.warn("Skipping an oversized compendium delta for {}", player.getScoreboardName());
            return;
        }
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
        if (payload != null && payload.snapshot() != null) {
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
        Vec3 center = Vec3.atCenterOf(pos);
        double maxDistSqr = 48.0D * 48.0D;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(center) <= maxDistSqr
                && ServerPlayNetworking.canSend(sp, FusionVisualEventPayload.TYPE)) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
    }
}
