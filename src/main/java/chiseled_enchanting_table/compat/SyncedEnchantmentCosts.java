package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
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
 * shelf gates, library resonance config, and Grand Library Treasure Epiphany settings.
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

    /** True once any server cost table has been received this session. */
    public static boolean hasServerTable() {
        return syncedPayload != null;
    }

    /**
     * Audit R-01: false when the server had to truncate the table to fit wire budgets.
     * A missing row then means "not synced", not "price it locally" — the menu layer
     * fails closed for material-requiring rows instead of previewing local datapack data
     * the server may not share.
     */
    public static boolean costTableComplete() {
        EnchantCostTablePayload p = syncedPayload;
        return p == null || p.costTableComplete();
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

    public static boolean disenchantEnabled() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.disenchantEnabled() : ChiseledEnchantingTableData.disenchantEnabled();
    }

    public static boolean disenchantAllowCursePurge() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.disenchantAllowCursePurge() : ChiseledEnchantingTableData.disenchantAllowCursePurge();
    }

    public static double disenchantRefundScale() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.disenchantRefundScale() : ChiseledEnchantingTableData.disenchantRefundScale();
    }

    public static double disenchantExternalRefundScale() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.disenchantExternalRefundScale() : ChiseledEnchantingTableData.disenchantExternalRefundScale();
    }

    public static int epiphanyMinBookshelves() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyMinBookshelves() : ChiseledEnchantingTableData.treasureEpiphanyMinBookshelves();
    }

    public static double epiphanyBaseChance() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyBaseChance() : ChiseledEnchantingTableData.treasureEpiphanyBaseChance();
    }

    public static double epiphanyPerShelfChance() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyPerShelfChance() : ChiseledEnchantingTableData.treasureEpiphanyPerShelfChance();
    }

    public static double epiphanyPerTomeChance() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyPerTomeChance() : ChiseledEnchantingTableData.treasureEpiphanyPerTomeChance();
    }

    public static double epiphanyMaxChance() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyMaxChance() : ChiseledEnchantingTableData.treasureEpiphanyMaxChance();
    }

    public static int epiphanyMaxSlotsCap() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyMaxSlotsCap() : ChiseledEnchantingTableData.treasureEpiphanyMaxSlotsCap();
    }

    public static int epiphanyScorePerSlot() {
        EnchantCostTablePayload p = syncedPayload;
        return p != null ? p.epiphanyScorePerSlot() : ChiseledEnchantingTableData.treasureEpiphanyScorePerSlot();
    }

    public static double epiphanyChance(int regularBookshelves, int chiseledBooks) {
        return LibraryResonance.epiphanyProbability(
            libraryResonanceConfig(),
            regularBookshelves,
            epiphanyMinBookshelves(),
            epiphanyBaseChance(),
            epiphanyPerShelfChance(),
            epiphanyPerTomeChance(),
            epiphanyMaxChance(),
            chiseledBooks,
            0.0
        );
    }

    public static int epiphanyMaxSlots(int regularBookshelves, int chiseledBooks) {
        return LibraryResonance.epiphanyMaxSlots(
            libraryResonanceConfig(),
            regularBookshelves,
            epiphanyMinBookshelves(),
            epiphanyMaxSlotsCap(),
            epiphanyScorePerSlot(),
            chiseledBooks,
            0.0
        );
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

    public static BookshelfProgression.Config shelfGatesConfig() {
        EnchantCostTablePayload p = syncedPayload;
        return (p != null && p.shelfGatesConfig() != null)
            ? p.shelfGatesConfig()
            : ChiseledEnchantingTableData.shelfGatesConfig();
    }

    public static LibraryResonance.Config libraryResonanceConfig() {
        EnchantCostTablePayload p = syncedPayload;
        return (p != null && p.libraryResonanceConfig() != null)
            ? p.libraryResonanceConfig()
            : ChiseledEnchantingTableData.libraryResonanceConfig();
    }

    public static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritualConfig() {
        EnchantCostTablePayload p = syncedPayload;
        return (p != null && p.ritualConfig() != null)
            ? p.ritualConfig()
            : ChiseledEnchantingTableData.ritualConfig();
    }

    /** Empty server rule lists are authoritative, not a request for jar-default fallback. */
    public static java.util.Optional<java.util.List<chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TransformationDisplay>> transformationDisplays() {
        var payload = syncedPayload;
        return payload == null ? java.util.Optional.empty() : java.util.Optional.of(payload.transformations());
    }

    /** Applies a lightweight delta update to the cached transformation unlock list. */
    public static void updateTransformations(
        java.util.List<chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TransformationDisplay> transformations
    ) {
        EnchantCostTablePayload p = syncedPayload;
        if (p == null || transformations == null) {
            return;
        }
        syncedPayload = new EnchantCostTablePayload(
            p.combineEnabled(),
            p.researchEnabled(),
            p.disenchantEnabled(),
            p.disenchantAllowCursePurge(),
            p.disenchantRefundScale(),
            p.disenchantExternalRefundScale(),
            p.epiphanyMinBookshelves(),
            p.epiphanyBaseChance(),
            p.epiphanyChancePerShelf(),
            p.epiphanyChancePerTome(),
            p.epiphanyMaxChance(),
            p.epiphanyMaxSlotsCap(),
            p.epiphanyScorePerSlot(),
            p.epiphanyCycleTicks(),
            p.epiphanyFreezeDurationTicks(),
            p.epiphanyMaxFreezeTicks(),
            p.epiphanyFreezeXpLevels(),
            p.epiphanyFreezeCosts(),
            p.epiphanyRerollXpLevels(),
            p.epiphanyRerollCosts(),
            p.explicitTreasureIds(),
            p.shelfGatesConfig(),
            p.libraryResonanceConfig(),
            p.ritualConfig(),
            p.costs(),
            java.util.List.copyOf(transformations),
            p.costTableComplete()
        );
        notifyListeners();
    }

    public static boolean hasServerData() {
        return syncedPayload != null;
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
            } catch (RuntimeException error) {
                chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.error("Failed to refresh a synchronized enchanting-data consumer", error);
            }
        }
    }
}
