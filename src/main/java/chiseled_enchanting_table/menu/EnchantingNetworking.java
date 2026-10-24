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
    /** Per-player per-tick burst guard on ApplyEnchantmentPayload (packed tick << 16 | count). */
    private static final java.util.Map<java.util.UUID, Long> APPLY_BURST_STATE = new java.util.HashMap<>();
    /** Per-player per-tick burst guard on secondary menu action payloads (NET-02). */
    private static final java.util.Map<java.util.UUID, Long> ACTION_BURST_STATE = new java.util.HashMap<>();
    private static final int MAX_APPLIES_PER_TICK = 8;
    private static final int MAX_ACTIONS_PER_TICK = 6;

    private static boolean allowActionBurst(java.util.UUID uuid, long tick) {
        Long prev = ACTION_BURST_STATE.get(uuid);
        int count = (prev != null && (prev >>> 16) == (tick & 0xFFFFFFFFFFFFL))
            ? (int) (prev & 0xFFFFL)
            : 0;
        if (count >= MAX_ACTIONS_PER_TICK) {
            return false;
        }
        ACTION_BURST_STATE.put(uuid, ((tick & 0xFFFFFFFFFFFFL) << 16) | ((count + 1) & 0xFFFFL));
        return true;
    }

    public static void init() {
        // Lifecycle discipline for static per-player state (audit): throttle entries die
        // with the connection, mirroring EnchantCostSync.LAST_DISPLAYS, so long-running
        // servers do not accumulate one UUID entry per player who ever joined.
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
            LAST_REOPEN_TICK.remove(listener.player.getUUID());
            APPLY_BURST_STATE.remove(listener.player.getUUID());
            ACTION_BURST_STATE.remove(listener.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_REOPEN_TICK.clear();
            APPLY_BURST_STATE.clear();
            ACTION_BURST_STATE.clear();
        });
        PayloadTypeRegistry.serverboundPlay().register(
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID,
            ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID, (payload, ctx) -> {
            var player = ctx.player();
            var server = ctx.server();
            server.execute(() -> {
                long tick = player.level().getGameTime();
                Long prev = APPLY_BURST_STATE.get(player.getUUID());
                int count = (prev != null && (prev >>> 16) == (tick & 0xFFFFFFFFFFFFL))
                    ? (int) (prev & 0xFFFFL)
                    : 0;
                if (count >= MAX_APPLIES_PER_TICK) {
                    return;
                }
                APPLY_BURST_STATE.put(player.getUUID(), ((tick & 0xFFFFFFFFFFFFL) << 16) | ((count + 1) & 0xFFFFL));
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
                if (!allowActionBurst(player.getUUID(), player.level().getGameTime())) {
                    return;
                }
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
                if (!allowActionBurst(player.getUUID(), player.level().getGameTime())) {
                    return;
                }
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
                long now = player.level().getGameTime();
                var pos = payload.pos();
                if (pos == null || player.isSpectator() || !player.level().hasChunkAt(pos)) {
                    return;
                }
                // Fast-path for JEI/REI return when the menu is already open: resync slot & ritual
                // state in place without reopening or churning containerId.
                if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler openMenu
                    && pos.equals(openMenu.tableBlockPos())
                    && openMenu.stillValid(player)) {
                    if (!allowActionBurst(player.getUUID(), now)) {
                        return;
                    }
                    openMenu.sendAllDataToRemote();
                    openMenu.refreshLiveShelfState();
                    openMenu.pushRitualStateToViewer();
                    return;
                }
                // Audit: reopening runs the full shelf scan + cost-table build, so a
                // spammed payload must not be able to re-trigger it every tick.
                Long last = LAST_REOPEN_TICK.get(player.getUUID());
                if (last != null && now - last < 10L) {
                    return;
                }
                LAST_REOPEN_TICK.put(player.getUUID(), now);
                var state = player.level().getBlockState(pos);
                if (!state.is(BlockRegistry.CHISELED_ENCHANTING_TABLE)) {
                    return;
                }
                if (!(player.level().getBlockEntity(pos) instanceof chiseled_enchanting_table.block.ChiseledEnchantingTableBlockEntity tableBe)
                    || !tableBe.stillValid(player)) {
                    return;
                }
                var provider = state.getMenuProvider(player.level(), pos);
                if (provider != null) {
                    player.openMenu(provider);
                    if (player.containerMenu instanceof ChiseledEnchantingTableScreenHandler reopenedMenu) {
                        reopenedMenu.pushRitualStateToViewer();
                        reopenedMenu.sendAllDataToRemote();
                    }
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
                if (!allowActionBurst(player.getUUID(), player.level().getGameTime())) {
                    return;
                }
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
                if (!allowActionBurst(player.getUUID(), player.level().getGameTime())) {
                    return;
                }
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
