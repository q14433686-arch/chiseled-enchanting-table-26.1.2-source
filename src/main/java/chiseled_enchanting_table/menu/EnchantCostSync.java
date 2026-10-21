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

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(EnchantCostTablePayload.TYPE, EnchantCostTablePayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.player;
            if (player != null) {
                sendTo(player);
            }
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendTo(player);
            }
        });
    }

    /** Pushes the player's resolved cost table, if their client registered the payload. */
    public static void sendTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, EnchantCostTablePayload.TYPE)) {
            return;
        }
        try {
            ServerPlayNetworking.send(player, EnchantCostTablePayload.build(player));
        } catch (RuntimeException e) {
            // Never let a display-only sync break a join or a reload.
            ChiseledEnchantingTable.LOGGER.warn("Failed to send the enchanting cost table to {}", player.getName().getString(), e);
        }
    }
}
