package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the cost sync: registers the S2C payload and pushes a freshly resolved
 * {@link EnchantCostTablePayload} to every client that understands it.
 *
 * <p>Two triggers, both needed:</p>
 * <ul>
 *   <li><b>player join</b> — before the client world exists, so JEI/REI already see the server's
 *       numbers when they build their display lists;</li>
 *   <li><b>end of a data pack reload</b> — {@code /reload} can change {@code enchantment_costs.json},
 *       which would otherwise leave every connected client showing stale costs.</li>
 * </ul>
 *
 * <p>{@code canSend} keeps vanilla clients (the mod is optional on the server side of a Fabric
 * handshake) from receiving payloads they cannot read.</p>
 */
public final class EnchantCostSync {
    private EnchantCostSync() {}
    private static final java.util.Map<ServerPlayer, java.util.List<ChiseledEnchantingMenuPayloads.TransformationDisplay>> LAST_DISPLAYS = new java.util.WeakHashMap<>();

    /**
     * "Peer has some build of this mod" signal for the JOIN handshake: the current cost
     * channel or any query-only legacy probe (see the retirement policy in
     * {@link EnchantingProtocol}).
     */
    private static boolean peerHasAnyCostChannel(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, EnchantCostTablePayload.TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.LEGACY_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.PREVIOUS_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.REVISION_5_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.REVISION_6_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.REVISION_7_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.REVISION_8_COST_TYPE)
            || ServerPlayNetworking.canSend(player, EnchantingProtocol.REVISION_9_COST_TYPE);
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(EnchantingProtocol.Capability.TYPE, EnchantingProtocol.Capability.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EnchantingProtocol.Capability.TYPE, EnchantingProtocol.Capability.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EnchantingProtocol.Capability.TYPE, (payload, ctx) -> {});
        PayloadTypeRegistry.clientboundPlay().register(EnchantCostTablePayload.TYPE, EnchantCostTablePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE,
            ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.TYPE,
            ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.CODEC
        );

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.player;
            if (player != null) {
                if (EnchantingProtocol.incompatible(
                    peerHasAnyCostChannel(player),
                    ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE))) {
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(EnchantingProtocol.MISMATCH_MESSAGE));
                    return;
                }
                chiseled_enchanting_table.config.ModConfigNetworking.sendToPlayer(player);
                sendTo(player);
            }
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE)) continue;
                var displays = ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload.buildTransformationDisplays(player);
                if (!displays.equals(LAST_DISPLAYS.get(player))) sendTo(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_DISPLAYS.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> LAST_DISPLAYS.remove(listener.player));

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // An open menu caches transformation IDs/inputs/thresholds from its opening
                // packet. Reloading only the cost table leaves that snapshot stale. Close just
                // our menu (vanilla returns its input stacks), then the next open gets fresh data.
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler) {
                    player.closeContainer();
                }
                chiseled_enchanting_table.config.ModConfigNetworking.sendToPlayer(player);
                sendTo(player);
            }
        });
    }

    /** Pushes the player's resolved cost table, if their client registered the payload. */
    public static void sendTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, EnchantCostTablePayload.TYPE)
            || !ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE)) {
            return;
        }
        try {
            var payload = EnchantCostTablePayload.build(player);
            ServerPlayNetworking.send(player, payload);
            LAST_DISPLAYS.put(player, payload.transformations());
            if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                menu.applyTransformationSnapshot(payload.transformations());
            }
        } catch (RuntimeException e) {
            // Never let a display-only sync break a join or a reload.
            ChiseledEnchantingTable.LOGGER.warn("Failed to send the enchanting cost table to {}", player.getName().getString(), e);
        }
    }
}
