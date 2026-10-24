package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.registry.BlockRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Serverbound payload registration + receivers for the chiseled enchanting menu.
 *
 * <p>Extracted verbatim from {@link ChiseledEnchantingTableScreenHandler#init()}
 * (god-class split, 2026-10-24 audit): the screen handler keeps menu/container
 * responsibilities; all networking wiring and the per-player reopen throttle
 * live here. Receivers only call public handler methods, so behavior is
 * unchanged.</p>
 */
public final class EnchantingNetworking {

    private EnchantingNetworking() {}

    /** Per-player game-time of the last accepted Reopen payload (server thread only). */
    private static final java.util.Map<java.util.UUID, Long> LAST_REOPEN_TICK = new java.util.HashMap<>();

    public static void init() {
        // Lifecycle discipline for static per-player state (audit): throttle entries die
        // with the connection, mirroring EnchantCostSync.LAST_DISPLAYS, so long-running
        // servers do not accumulate one UUID entry per player who ever joined.
        ServerPlayConnectionEvents.DISCONNECT.register(
            (listener, server) -> LAST_REOPEN_TICK.remove(listener.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REOPEN_TICK.clear());
        PayloadTypeRegistry.serverboundPlay().register(
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID,
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                    && payload.targets(menu.containerId)
                    && menu.stillValid(player)) {
                    menu.applyEnchantement(payload);
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.TYPE,
            ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                    && payload.targets(menu.containerId)
                    && menu.stillValid(player)) {
                    menu.rerollEpiphany();
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.TYPE,
            ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                    && payload.targets(menu.containerId)
                    && menu.stillValid(player)) {
                    menu.freezeEpiphany();
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.ReopenTablePayload.TYPE,
            ChiseledEnchantingMenuPayloads.ReopenTablePayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.ReopenTablePayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                // Audit: reopening runs the full shelf scan + cost-table build, so a
                // spammed payload must not be able to re-trigger it every tick.
                long now = player.level().getGameTime();
                Long last = LAST_REOPEN_TICK.get(player.getUUID());
                if (last != null && now - last < 10L) {
                    return;
                }
                LAST_REOPEN_TICK.put(player.getUUID(), now);
                var pos = payload.pos();
                if (pos == null || !player.level().hasChunkAt(pos)) {
                    return;
                }
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
                    return;
                }
                var state = player.level().getBlockState(pos);
                if (!state.is(BlockRegistry.CHISELED_ENCHANTING_TABLE)) {
                    return;
                }
                var provider = state.getMenuProvider(player.level(), pos);
                if (provider != null) {
                    player.openMenu(provider);
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.TomePageActionPayload.TYPE,
            ChiseledEnchantingMenuPayloads.TomePageActionPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.TomePageActionPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler menu
                    && payload.targets(menu.containerId)
                    && menu.stillValid(player)) {
                    menu.handleTomePageAction(payload);
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE,
            ChiseledEnchantingMenuPayloads.SkipRitualPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ChiseledEnchantingMenuPayloads.SkipRitualPayload.TYPE, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler chiseledEnchantingTableScreenHandler) {
                    if (chiseledEnchantingTableScreenHandler.containerId == payload.containerId()
                        && chiseledEnchantingTableScreenHandler.ritualSession() == payload.session()
                        && chiseledEnchantingTableScreenHandler.stillValid(player)) {
                        chiseledEnchantingTableScreenHandler.skipActiveRitual(payload.sacrificeXp());
                    }
                }
            });
        });
    }
}
