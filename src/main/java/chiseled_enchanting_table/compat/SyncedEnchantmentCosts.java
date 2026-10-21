package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.menu.EnchantCostTablePayload.CostEntry;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side cache of the cost rows the server sent for <b>this player</b>.
 *
 * <h2>Why this exists</h2>
 * <p>{@code enchantment_costs.json} is a server data pack file
 * ({@code ChiseledEnchantingTableData} is registered on {@code PackType.SERVER_DATA}), and the cost a
 * player actually pays is salted with their UUID
 * ({@code stablePositiveHash(id:level:maxLevel:uuid:option) % options.size()}). A client therefore
 * cannot derive the real cost on its own in two cases:</p>
 * <ol>
 *   <li>the client's data pack copy differs from the server's (dedicated server with a custom
 *       {@code enchantment_costs.json} — the viewer would otherwise show the jar defaults);</li>
 *   <li>the player's own option index is one the client never resolved.</li>
 * </ol>
 *
 * <p>So the server resolves one row per (enchantment, level) with the player's UUID and ships the
 * result in {@link EnchantCostTablePayload}. {@link EnchantQueryIndex} prefers these rows and only
 * falls back to the canonical empty-salt option when nothing was synced (no server, vanilla
 * handshake never completed, or a client that joins before the payload arrives).</p>
 *
 * <p>Deliberately a plain static holder: it is written once per join/reload, read from viewer
 * registration code, and cleared on disconnect. The server side never populates it — the server
 * resolves costs from its own tables, so a shared static cannot leak one player's option into
 * another player's screen.</p>
 */
public final class SyncedEnchantmentCosts {
    /** What a viewer needs from a synced row, decoupled from the wire record. */
    public record Cost(int xpLevelCost, List<ItemStack> itemCosts) {}

    private static volatile Map<EnchantmentWithLevel, Cost> rows = Map.of();

    private SyncedEnchantmentCosts() {}

    /** Replaces the whole table. Called on the client thread from the S2C payload handler. */
    public static void apply(List<CostEntry> incoming) {
        Map<EnchantmentWithLevel, Cost> next = new HashMap<>(Math.max(16, incoming.size() * 2));
        for (CostEntry entry : incoming) {
            next.put(
                new EnchantmentWithLevel(entry.enchantmentId(), entry.level()),
                new Cost(entry.xpLevelCost(), entry.itemCosts())
            );
        }
        rows = Map.copyOf(next);
    }

    /** The server-resolved row, or empty when nothing was synced for this key. */
    public static Optional<Cost> find(Identifier enchantmentId, int level) {
        return Optional.ofNullable(rows.get(new EnchantmentWithLevel(enchantmentId, level)));
    }

    public static boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Clears the table: the next server decides the costs, not the previous one. */
    public static void clear() {
        rows = Map.of();
    }
}
