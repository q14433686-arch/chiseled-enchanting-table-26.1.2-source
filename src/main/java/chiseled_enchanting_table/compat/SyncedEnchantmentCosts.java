package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.menu.EnchantCostTablePayload.CostEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side cache of the server's resolved multi-option cost rows, book-upgrade flags,
 * and Grand Library Treasure Epiphany & Stasis Freeze configuration.
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
    private static volatile EnchantCostTablePayload syncedPayload = null;
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
        syncedPayload = payload;
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
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyMinBookshelves() : ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves();
    }

    public static double epiphanyChance(int regularBookshelves, int chiseledBooks) {
        EnchantCostTablePayload p = syncedPayload;
        if (p != null) {
            if (regularBookshelves < p.epiphanyMinBookshelves()) return 0.0;
            double raw = p.epiphanyBaseChance()
                + (regularBookshelves - p.epiphanyMinBookshelves()) * p.epiphanyPerShelfChance()
                + Math.max(0, chiseledBooks) * p.epiphanyPerTomeChance();
            return Math.max(0.0, Math.min(p.epiphanyMaxChance(), raw));
        }
        return ChiseledEnchantingTableData.treasureEpiphanyChance(regularBookshelves, chiseledBooks);
    }

    public static int epiphanyMaxSlots(int regularBookshelves, int chiseledBooks) {
        EnchantCostTablePayload p = syncedPayload;
        if (p != null) {
            if (regularBookshelves < p.epiphanyMinBookshelves()) return 0;
            int score = Math.max(0, regularBookshelves - p.epiphanyMinBookshelves()) + Math.max(0, chiseledBooks) * 2;
            return Math.min(p.epiphanyMaxSlotsCap(), 1 + score / Math.max(1, p.epiphanyScorePerSlot()));
        }
        return ChiseledEnchantingTableData.treasureEpiphanyMaxSlots(regularBookshelves, chiseledBooks);
    }

    public static int epiphanyCycleTicks() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyCycleTicks() : ChiseledEnchantingTableData.treasureEpiphanyCycleTicks();
    }

    public static int epiphanyFreezeDurationTicks() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyFreezeDurationTicks() : ChiseledEnchantingTableData.treasureEpiphanyFreezeDurationTicks();
    }

    public static int epiphanyMaxFreezeTicks() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyMaxFreezeTicks() : ChiseledEnchantingTableData.treasureEpiphanyMaxFreezeTicks();
    }

    public static int epiphanyFreezeXpLevels() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyFreezeXpLevels() : ChiseledEnchantingTableData.treasureEpiphanyFreezeXpLevels();
    }

    public static List<ItemStack> epiphanyFreezeCosts() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyFreezeCosts() : ChiseledEnchantingTableData.treasureEpiphanyFreezeCostOptions();
    }

    public static int epiphanyRerollXpLevels() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyRerollXpLevels() : ChiseledEnchantingTableData.treasureEpiphanyRerollXpLevels();
    }

    public static List<ItemStack> epiphanyRerollCosts() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyRerollCosts() : ChiseledEnchantingTableData.treasureEpiphanyRerollCostOptions();
    }

    public static Set<Identifier> explicitTreasureIds() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.explicitTreasureIds() : ChiseledEnchantingTableData.treasureEpiphanyExplicitIds();
    }

    public static boolean isEmpty() {
        return rows.isEmpty();
    }

    public static void clear() {
        rows = Map.of();
        combineEnabledOverride = null;
        researchEnabledOverride = null;
        syncedPayload = null;
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
