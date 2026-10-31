package enchantment_fusion.menu;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.signatures.UnderwaterMiningSkills;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** One rebindable active-skill input; the server decides what can be activated. */
public final class FusionSkillNetworking {
    public record ActivatePayload() implements CustomPacketPayload {
        public static final Type<ActivatePayload> TYPE = new Type<>(EnchantmentFusion.id("activate_skill"));
        public static final ActivatePayload INSTANCE = new ActivatePayload();
        public static final StreamCodec<ByteBuf, ActivatePayload> CODEC = StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private FusionSkillNetworking() {}

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(ActivatePayload.TYPE, ActivatePayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REQUEST.clear());
        ServerPlayNetworking.registerGlobalReceiver(ActivatePayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                long tick = player.level().getGameTime();
                Long last = LAST_REQUEST.get(player.getUUID());
                if (last != null && tick >= last && tick - last < 5) return;
                LAST_REQUEST.put(player.getUUID(), tick);
                // The client cannot select a target, supply a multiplier, or choose which item pays.
                UnderwaterMiningSkills.activate(player);
            });
        });
    }
}
