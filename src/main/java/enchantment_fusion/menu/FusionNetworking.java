package enchantment_fusion.menu;

import enchantment_fusion.EnchantmentFusion;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Typed network payloads for opening and operating the Enchantment Fusion Table
 * and syncing Compendium pair discoveries.
 */
public final class FusionNetworking {
    public enum StationAction {
        SWAP_ORDER,
        EXECUTE_PRIMARY,
        EXECUTE_FLIP,
        EXECUTE_SPLIT
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

    public record StationActionPayload(int containerId, int actionOrdinal) implements CustomPacketPayload {
        public static final Type<StationActionPayload> TYPE =
            new Type<>(EnchantmentFusion.id("station_action"));
        public static final StreamCodec<ByteBuf, StationActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StationActionPayload::containerId,
            ByteBufCodecs.VAR_INT, StationActionPayload::actionOrdinal,
            StationActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CompendiumSyncPayload(List<String> unlockedPairs) implements CustomPacketPayload {
        public static final Type<CompendiumSyncPayload> TYPE =
            new Type<>(EnchantmentFusion.id("compendium_sync"));
        public static final StreamCodec<ByteBuf, CompendiumSyncPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                .map(CompendiumSyncPayload::new, CompendiumSyncPayload::unlockedPairs);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private FusionNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(StationActionPayload.TYPE, StationActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumSyncPayload.TYPE, CompendiumSyncPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StationActionPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.containerMenu instanceof EnchantmentFusionMenu menu)) {
                    return;
                }
                if (menu.containerId != payload.containerId() || !menu.stillValid(player)) {
                    return;
                }
                StationAction[] actions = StationAction.values();
                if (payload.actionOrdinal() < 0 || payload.actionOrdinal() >= actions.length) {
                    return;
                }
                menu.performAction(player, actions[payload.actionOrdinal()]);
            });
        });
    }

    public static void sendCompendiumSync(ServerPlayer player, Collection<String> unlockedPairs) {
        if (ServerPlayNetworking.canSend(player, CompendiumSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CompendiumSyncPayload(new ArrayList<>(unlockedPairs)));
        }
    }
}
