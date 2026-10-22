package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.menu.EnchantCostTablePayload.CostEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side cache of the server's resolved cost rows and book-upgrade feature flags for this player.
 */
public final class SyncedEnchantmentCosts {
    public record Cost(int xpLevelCost, List<ItemStack> itemCosts) {}

    private record Key(OptionType optionType, Identifier id, int level) {}

    private static volatile Map<Key, Cost> rows = Map.of();
    private static volatile Boolean combineEnabledOverride = null;
    private static volatile Boolean researchEnabledOverride = null;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private SyncedEnchantmentCosts() {}

    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void apply(EnchantCostTablePayload payload) {
        Map<Key, Cost> next = new HashMap<>(Math.max(16, payload.costs().size() * 2));
        for (CostEntry entry : payload.costs()) {
            next.put(
                new Key(entry.optionType(), entry.enchantmentId(), entry.level()),
                new Cost(entry.xpLevelCost(), entry.itemCosts())
            );
        }
        rows = Map.copyOf(next);
        combineEnabledOverride = payload.combineEnabled();
        researchEnabledOverride = payload.researchEnabled();
        notifyListeners();
    }

    public static Optional<Cost> find(Identifier enchantmentId, int level) {
        return find(OptionType.ENCHANTMENT, enchantmentId, level);
    }

    public static Optional<Cost> find(OptionType optionType, Identifier enchantmentId, int level) {
        return Optional.ofNullable(rows.get(new Key(optionType, enchantmentId, level)));
    }

    public static boolean combineEnabled() {
        Boolean synced = combineEnabledOverride;
        return synced != null ? synced : ChiseledEnchantingTableData.bookCombineEnabled();
    }

    public static boolean researchEnabled() {
        Boolean synced = researchEnabledOverride;
        return synced != null ? synced : ChiseledEnchantingTableData.bookResearchEnabled();
    }

    public static boolean isEmpty() {
        return rows.isEmpty();
    }

    public static void clear() {
        rows = Map.of();
        combineEnabledOverride = null;
        researchEnabledOverride = null;
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
