package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
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

    /** Keyed by immutable player {@link UUID} (never live {@link ServerPlayer} instances across respawns). */
    private static final Map<UUID, List<ChiseledEnchantingMenuPayloads.TransformationDisplay>> LAST_DISPLAYS =
        new ConcurrentHashMap<>();

    /**
     * "Peer has some build of this mod" signal for the JOIN handshake: the current cost
     * channel or any query-only legacy probe (see the retirement policy in
     * {@link EnchantingProtocol}).
     */
    private static boolean peerHasAnyCostChannel(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, EnchantCostTablePayload.TYPE)) {
            return true;
        }
        for (var probe : EnchantingProtocol.LEGACY_COST_PROBES) {
            if (ServerPlayNetworking.canSend(player, probe)) {
                return true;
            }
        }
        return false;
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(EnchantingProtocol.Capability.TYPE, EnchantingProtocol.Capability.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EnchantingProtocol.Capability.TYPE, EnchantingProtocol.Capability.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EnchantingProtocol.Capability.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            ctx.server().execute(() -> {
                int serverStack = ChiseledEnchantingTable.bootEnchantedBookStackSize();
                if (EnchantingProtocol.stackSizeIncompatible(serverStack, payload.enchantedBookStackSize())) {
                    player.connection.disconnect(
                        EnchantingProtocol.stackSizeMismatchComponent(serverStack, payload.enchantedBookStackSize())
                    );
                }
            });
        });
        PayloadTypeRegistry.clientboundPlay().register(EnchantCostTablePayload.TYPE, EnchantCostTablePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
            ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.TYPE,
            ChiseledEnchantingMenuPayloads.LiveShelfStatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.TYPE,
            ChiseledEnchantingMenuPayloads.SyncRitualStatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload.TYPE,
            ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload.CODEC
        );

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.player;
            if (player != null) {
                if (EnchantingProtocol.incompatible(
                    peerHasAnyCostChannel(player),
                    ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE))) {
                    player.connection.disconnect(EnchantingProtocol.protocolMismatchComponent());
                    return;
                }
                if (ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE)) {
                    ServerPlayNetworking.send(player, new EnchantingProtocol.Capability());
                }
                chiseled_enchanting_table.config.ModConfigNetworking.sendToPlayer(player);
                sendTo(player);
            }
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!ServerPlayNetworking.canSend(player, EnchantingProtocol.Capability.TYPE)) continue;
                UUID playerId = player.getUUID();
                var displays = ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload.buildTransformationDisplays(player);
                if (!displays.equals(LAST_DISPLAYS.get(playerId))) {
                    LAST_DISPLAYS.put(playerId, displays);
                    int activeContainerId = player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                        ? menu.containerId
                        : PayloadLimits.NO_CONTAINER;
                    if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                        menu.applyTransformationSnapshot(displays);
                    }
                    if (ServerPlayNetworking.canSend(player, ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload.TYPE)) {
                        ServerPlayNetworking.send(
                            player,
                            new ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload(activeContainerId, displays)
                        );
                    }
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_DISPLAYS.clear());
        ServerPlayConnectionEvents.DISCONNECT.register(
            (listener, server) -> LAST_DISPLAYS.remove(listener.player.getUUID())
        );

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (!success) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // An open menu caches transformation IDs/inputs/thresholds from its opening
                // packet. Reloading only the cost table leaves that snapshot stale. Close just
                // our menu (vanilla returns its input stacks), notify the player cleanly, and let
                // the next open read freshly reloaded datapack data.
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler) {
                    player.closeContainer();
                    player.displayClientMessage(
                        Component.translatable("message.chiseled_enchanting_table.reload_closed"),
                        true
                    );
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
            LAST_DISPLAYS.put(player.getUUID(), payload.transformations());
            if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu) {
                menu.applyTransformationSnapshot(payload.transformations());
            }
        } catch (RuntimeException e) {
            // Never let a display-only sync break a join or a reload.
            ChiseledEnchantingTable.LOGGER.warn("Failed to send the enchanting cost table to {}", player.getName().getString(), e);
        }
    }
}
