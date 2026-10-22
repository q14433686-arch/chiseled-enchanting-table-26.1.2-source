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
 * Client-side cache of the server's resolved multi-option cost rows, book-upgrade flags,
 * and Grand Library Treasure Epiphany configuration.
 */
public final class SyncedEnchantmentCosts {
    public record Cost(int xpLevelCost, List<List<ItemStack>> costOptions) {
        public List<ItemStack> itemCosts() {
            return costOptions.isEmpty() ? List.of() : costOptions.getFirst();
        }
    }

    private record Key(OptionType optionType, Identifier id, int level) {}

    private static volatile Map<Key, Cost> rows = Map.of();
    private static volatile Boolean combineEnabledOverride = null;
    private static volatile Boolean researchEnabledOverride = null;
    private static volatile Integer epiphanyMinBookshelvesOverride = null;
    private static volatile Double epiphanyBaseChanceOverride = null;
    private static volatile Double epiphanyPerShelfChanceOverride = null;
    private static volatile Double epiphanyMaxChanceOverride = null;
    private static volatile Integer epiphanyRerollXpOverride = null;
    private static volatile List<ItemStack> epiphanyRerollCostsOverride = null;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private SyncedEnchantmentCosts() {}

    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void onChange(Runnable listener) {
        addListener(listener);
    }

    public static void apply(EnchantCostTablePayload payload) {
        Map<Key, Cost> next = new HashMap<>(Math.max(16, payload.costs().size() * 2));
        for (CostEntry entry : payload.costs()) {
            next.put(
                new Key(entry.optionType(), entry.enchantmentId(), entry.level()),
                new Cost(entry.xpLevelCost(), entry.costOptions())
            );
        }
        rows = Map.copyOf(next);
        combineEnabledOverride = payload.combineEnabled();
        researchEnabledOverride = payload.researchEnabled();
        epiphanyMinBookshelvesOverride = payload.epiphanyMinBookshelves();
        epiphanyBaseChanceOverride = payload.epiphanyBaseChance();
        epiphanyPerShelfChanceOverride = payload.epiphanyPerShelfChance();
        epiphanyMaxChanceOverride = payload.epiphanyMaxChance();
        epiphanyRerollXpOverride = payload.epiphanyRerollXpLevels();
        epiphanyRerollCostsOverride = payload.epiphanyRerollCosts();
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

    public static int epiphanyMinBookshelves() {
        Integer synced = epiphanyMinBookshelvesOverride;
        return synced != null ? synced : ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves();
    }

    public static double epiphanyChance(int regularBookshelves) {
        int minShelves = epiphanyMinBookshelves();
        if (regularBookshelves < minShelves) return 0.0;
        Double base = epiphanyBaseChanceOverride;
        Double per = epiphanyPerShelfChanceOverride;
        Double max = epiphanyMaxChanceOverride;
        if (base != null && per != null && max != null) {
            return Math.max(0.0, Math.min(max, base + (regularBookshelves - minShelves) * per));
        }
        return ChiseledEnchantingTableData.treasureEpiphanyChance(regularBookshelves);
    }

    public static int epiphanyRerollXpLevels() {
        Integer synced = epiphanyRerollXpOverride;
        return synced != null ? synced : ChiseledEnchantingTableData.treasureEpiphanyRerollXpLevels();
    }

    public static List<ItemStack> epiphanyRerollCosts() {
        List<ItemStack> synced = epiphanyRerollCostsOverride;
        return synced != null ? synced : ChiseledEnchantingTableData.treasureEpiphanyRerollCostOptions();
    }

    public static boolean isEmpty() {
        return rows.isEmpty();
    }

    public static void clear() {
        rows = Map.of();
        combineEnabledOverride = null;
        researchEnabledOverride = null;
        epiphanyMinBookshelvesOverride = null;
        epiphanyBaseChanceOverride = null;
        epiphanyPerShelfChanceOverride = null;
        epiphanyMaxChanceOverride = null;
        epiphanyRerollXpOverride = null;
        epiphanyRerollCostsOverride = null;
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
