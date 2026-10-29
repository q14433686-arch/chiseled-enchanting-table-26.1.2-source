package enchantment_fusion.compat;

import enchantment_fusion.item.FusedEnchantedBookItem;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Pure server/common item-mutation and XP-essence calculator for {@code 纂刻附魔台}
 * Cleanse & Curse-Purge operations on {@link FusionDataComponents#FUSED_PAIRS},
 * {@link FusionItems#FUSED_ENCHANTED_BOOK}, and {@link FusionDataComponents#TRANSMUTED_ENCHANTS}.
 *
 * <p>Guarantees zero duplication of native primary enchantments mirrored into vanilla
 * {@code DataComponents.ENCHANTMENTS}, and enforces curse-first purification before stripping
 * or extracting any aberrant binary fusion.</p>
 */
public final class CetCleanseOperations {
    public record ExtractResult(ItemStack updatedTarget, ItemStack extractedBook) {}

    private CetCleanseOperations() {}

    public static boolean hasAnyCleanseableData(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!fused.isEmpty()) {
            return true;
        }
        Map<String, Integer> transmuted = stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        return !transmuted.isEmpty();
    }

    /**
     * Returns all primary enchantment IDs currently mirrored into vanilla {@code ENCHANTMENTS}
     * by socketed {@link FusedPair}s on {@code stack}, so {@code 纂刻附魔台} never lists or extracts
     * a fusion's mirrored primary separately from the fusion itself.
     */
    public static Set<String> mirroredPrimaryEnchantIds(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return Set.of();
        }
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (fused.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (FusedPair pair : fused.pairs()) {
            ids.add(pair.primaryId());
        }
        return ids;
    }

    public static boolean isMirroredFusionPrimary(@Nullable ItemStack stack, @Nullable Identifier enchantId) {
        if (enchantId == null) {
            return false;
        }
        return mirroredPrimaryEnchantIds(stack).contains(enchantId.toString());
    }

    public static int countMirroredVanillaNonCurses(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return 0;
        }
        Set<String> mirrored = mirroredPrimaryEnchantIds(stack);
        if (mirrored.isEmpty()) {
            return 0;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int count = 0;
        for (Holder<Enchantment> h : current.keySet()) {
            String id = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (mirrored.contains(id) && !id.endsWith("_curse")) {
                count++;
            }
        }
        return count;
    }

    public static int countAberrantFusions(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        int count = 0;
        for (FusedPair p : fused.pairs()) {
            if (p.isAberrant()) {
                count++;
            }
        }
        return count;
    }

    public static int countCleanFusions(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        int count = 0;
        for (FusedPair p : fused.pairs()) {
            if (!p.isAberrant()) {
                count++;
            }
        }
        return count;
    }

    public static int countTransmutedSingles(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of()).size();
    }

    /**
     * Net delta added to {@code DisenchantmentApplication.countNonCurseEnchantments(stack)}:
     * adds clean fused pairs + transmuted singles, and subtracts mirrored native primaries
     * already counted in vanilla {@code ENCHANTMENTS}.
     */
    public static int netNonCurseTraitDelta(@Nullable ItemStack stack) {
        return countCleanFusions(stack) + countTransmutedSingles(stack) - countMirroredVanillaNonCurses(stack);
    }

    /**
     * Total independent traits on {@code stack} (unfused vanilla + all fused pairs + transmuted singles),
     * used to decide whether targeted single-trait stripping (`STRIP_SINGLE_ENCHANTMENT`) is meaningful.
     */
    public static int countTotalIndependentTraits(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments vanilla = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        Set<String> mirrored = mirroredPrimaryEnchantIds(stack);
        int unfusedVanilla = 0;
        for (Holder<Enchantment> h : vanilla.keySet()) {
            String id = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (!mirrored.contains(id)) {
                unfusedVanilla++;
            }
        }
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        return unfusedVanilla + fused.size() + countTransmutedSingles(stack);
    }

    public static boolean canExtractAnyFusionTraitToBook(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return false; // Already a single Fused Enchanted Book — prevent no-op book-to-book churning!
        }
        return countCleanFusions(stack) + countTransmutedSingles(stack) >= 1;
    }

    public static int essenceForFusedPair(FusedPair pair, double refundScale) {
        if (pair == null || pair.isAberrant()) {
            return 0;
        }
        int base = 20 + 9 * Math.max(1, pair.primaryLevel()) + 9 * Math.max(1, pair.secondaryLevel());
        return Math.max(4, (int) Math.round(base * Math.max(0.05D, refundScale)));
    }

    public static int essenceForTransmuted(int level, double refundScale) {
        if (level <= 0) {
            return 0;
        }
        int base = 10 + 9 * level;
        return Math.max(2, (int) Math.round(base * Math.max(0.05D, refundScale)));
    }

    public static int essenceForTransmutedStepDown(int fromLevel, int toLevel, double refundScale) {
        if (fromLevel <= toLevel || toLevel < 1) {
            return 0;
        }
        return Math.max(2, essenceForTransmuted(fromLevel, refundScale) - essenceForTransmuted(toLevel, refundScale));
    }

    public static int additionalFullDisenchantEssence(@Nullable ItemStack stack, double refundScale) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int total = 0;
        FusedEnchantmentList fused = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        for (FusedPair p : fused.pairs()) {
            if (!p.isAberrant()) {
                // Only add the secondary/synergy portion if primary is already mirrored in vanilla ENCHANTMENTS
                boolean primaryMirrored = !stack.is(FusionItems.FUSED_ENCHANTED_BOOK)
                    && hasVanillaEnchant(stack, p.primaryId());
                if (primaryMirrored) {
                    total += Math.max(4, (int) Math.round((12 + 9 * Math.max(1, p.secondaryLevel())) * Math.max(0.05D, refundScale)));
                } else {
                    total += essenceForFusedPair(p, refundScale);
                }
            }
        }
        Map<String, Integer> trans = stack.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        for (int lv : trans.values()) {
            total += essenceForTransmuted(lv, refundScale);
        }
        return total;
    }

    /**
     * Purifies one Aberrant binary fusion at {@code pairIndex}, removing its side-effect while
     * preserving the pure {@code [A ▷ B]} fusion intact!
     */
    public static @Nullable ItemStack purifySingleAberrant(ItemStack source, int pairIndex) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        FusedEnchantmentList list = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (pairIndex < 0 || pairIndex >= list.size()) {
            return null;
        }
        FusedPair targetPair = list.pairs().get(pairIndex);
        if (!targetPair.isAberrant()) {
            return null;
        }
        FusedPair purified = new FusedPair(
            targetPair.primaryId(),
            targetPair.primaryLevel(),
            targetPair.secondaryId(),
            targetPair.secondaryLevel(),
            null
        );
        ItemStack copy = source.copy();
        copy.set(FusionDataComponents.FUSED_PAIRS, list.replaceAt(pairIndex, purified));
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    /**
     * Purifies ALL Aberrant binary fusions on {@code source} into pure {@code [A ▷ B]} fusions.
     */
    public static @Nullable ItemStack purifyAllAberrants(ItemStack source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        FusedEnchantmentList list = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (list.isEmpty()) {
            return null;
        }
        boolean changed = false;
        List<FusedPair> updated = new ArrayList<>(list.size());
        for (FusedPair p : list.pairs()) {
            if (p.isAberrant()) {
                updated.add(new FusedPair(p.primaryId(), p.primaryLevel(), p.secondaryId(), p.secondaryLevel(), null));
                changed = true;
            } else {
                updated.add(p);
            }
        }
        if (!changed) {
            return null;
        }
        ItemStack copy = source.copy();
        copy.set(FusionDataComponents.FUSED_PAIRS, new FusedEnchantmentList(updated));
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    public static @Nullable ItemStack stripSingleFusedPair(ItemStack source, int pairIndex) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        FusedEnchantmentList list = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (pairIndex < 0 || pairIndex >= list.size()) {
            return null;
        }
        FusedPair removed = list.pairs().get(pairIndex);
        if (removed.isAberrant()) {
            return null; // Must purify curse first!
        }
        FusedEnchantmentList next = list.removeAt(pairIndex);
        if (source.is(FusionItems.FUSED_ENCHANTED_BOOK) && next.isEmpty()) {
            return new ItemStack(Items.BOOK, source.getCount());
        }
        ItemStack copy = source.copy();
        if (next.isEmpty()) {
            copy.remove(FusionDataComponents.FUSED_PAIRS);
        } else {
            copy.set(FusionDataComponents.FUSED_PAIRS, next);
        }
        if (!next.containsEnchantmentId(removed.primaryId())) {
            FusionStationOperations.removeGearVanillaEnchant(copy, removed.primaryId());
        }
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    public static @Nullable ExtractResult extractFusedPairToBook(ItemStack source, int pairIndex) {
        if (source == null || source.isEmpty() || source.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            return null;
        }
        FusedEnchantmentList list = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (pairIndex < 0 || pairIndex >= list.size()) {
            return null;
        }
        FusedPair pair = list.pairs().get(pairIndex);
        if (pair.isAberrant()) {
            return null;
        }
        ItemStack updated = stripSingleFusedPair(source, pairIndex);
        if (updated == null) {
            return null;
        }
        ItemStack fusedBook = FusedEnchantedBookItem.createForPair(pair);
        return new ExtractResult(updated, fusedBook);
    }

    public static @Nullable ItemStack downgradeTransmuted(ItemStack source, String enchantId, int targetLevel) {
        if (source == null || source.isEmpty() || enchantId == null || targetLevel < 1) {
            return null;
        }
        Map<String, Integer> map = source.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        int cur = map.getOrDefault(enchantId, 0);
        if (cur <= 1 || targetLevel >= cur) {
            return null;
        }
        Map<String, Integer> next = new LinkedHashMap<>(map);
        next.put(enchantId, targetLevel);
        ItemStack copy = source.copy();
        copy.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Collections.unmodifiableMap(next));
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    public static @Nullable ItemStack stripSingleTransmuted(ItemStack source, String enchantId) {
        if (source == null || source.isEmpty() || enchantId == null) {
            return null;
        }
        Map<String, Integer> map = source.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        if (!map.containsKey(enchantId)) {
            return null;
        }
        Map<String, Integer> next = new LinkedHashMap<>(map);
        next.remove(enchantId);
        ItemStack copy = source.copy();
        if (next.isEmpty()) {
            copy.remove(FusionDataComponents.TRANSMUTED_ENCHANTS);
        } else {
            copy.set(FusionDataComponents.TRANSMUTED_ENCHANTS, Collections.unmodifiableMap(next));
        }
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    public static @Nullable ExtractResult extractTransmutedToBook(
        ItemStack source,
        String enchantId,
        HolderLookup.Provider registries
    ) {
        if (source == null || source.isEmpty() || enchantId == null) {
            return null;
        }
        Map<String, Integer> map = source.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());
        int lv = map.getOrDefault(enchantId, 0);
        if (lv <= 0) {
            return null;
        }
        Optional<Holder<Enchantment>> holderOpt = FusionStationOperations.lookupEnchantment(registries, enchantId);
        if (holderOpt.isEmpty()) {
            return null;
        }
        ItemStack updated = stripSingleTransmuted(source, enchantId);
        if (updated == null) {
            return null;
        }
        ItemStack book = FusionStationOperations.createSingleEnchantedBook(holderOpt.get(), lv);
        return new ExtractResult(updated, book);
    }

    /**
     * Strips all clean {@link FusedPair}s, all {@code TRANSMUTED_ENCHANTS}, and all non-curse
     * vanilla enchantments from {@code source} in one unified full-disenchant pass, while leaving
     * unpurged curses & aberrant fusions intact.
     */
    public static @Nullable ItemStack disenchantAllCombined(ItemStack source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        FusedEnchantmentList fused = source.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        Map<String, Integer> trans = source.getOrDefault(FusionDataComponents.TRANSMUTED_ENCHANTS, Map.of());

        if (source.is(FusionItems.FUSED_ENCHANTED_BOOK)) {
            List<FusedPair> remainingAberrants = fused.pairs().stream().filter(FusedPair::isAberrant).toList();
            if (remainingAberrants.size() == fused.size()) {
                return null; // Only aberrant curses present
            }
            if (remainingAberrants.isEmpty()) {
                return new ItemStack(Items.BOOK, source.getCount());
            }
            ItemStack copy = source.copy();
            copy.set(FusionDataComponents.FUSED_PAIRS, new FusedEnchantmentList(remainingAberrants));
            return copy;
        }

        boolean changed = false;
        ItemStack copy = source.copy();

        if (!fused.isEmpty()) {
            List<FusedPair> keptAberrant = new ArrayList<>();
            List<FusedPair> removedClean = new ArrayList<>();
            for (FusedPair p : fused.pairs()) {
                if (p.isAberrant()) {
                    keptAberrant.add(p);
                } else {
                    removedClean.add(p);
                }
            }
            if (!removedClean.isEmpty()) {
                changed = true;
                if (keptAberrant.isEmpty()) {
                    copy.remove(FusionDataComponents.FUSED_PAIRS);
                } else {
                    copy.set(FusionDataComponents.FUSED_PAIRS, new FusedEnchantmentList(keptAberrant));
                }
                FusedEnchantmentList keptList = new FusedEnchantmentList(keptAberrant);
                for (FusedPair rem : removedClean) {
                    if (!keptList.containsEnchantmentId(rem.primaryId())) {
                        FusionStationOperations.removeGearVanillaEnchant(copy, rem.primaryId());
                    }
                }
            }
        }

        if (!trans.isEmpty()) {
            copy.remove(FusionDataComponents.TRANSMUTED_ENCHANTS);
            changed = true;
        }

        // Also strip non-curse vanilla enchantments (keeping curses & kept aberrant primaries)
        FusedEnchantmentList remainingFused = copy.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        ItemEnchantments vanilla = EnchantmentHelper.getEnchantmentsForCrafting(copy);
        if (!vanilla.isEmpty()) {
            ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            boolean strippedVanilla = false;
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : vanilla.entrySet()) {
                Holder<Enchantment> h = entry.getKey();
                String id = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
                boolean isCurse = id.endsWith("_curse") || h.unwrapKey()
                    .map(k -> h.is(net.minecraft.tags.EnchantmentTags.CURSE))
                    .orElse(false);
                if (isCurse || remainingFused.containsEnchantmentId(id)) {
                    mut.set(h, entry.getIntValue());
                } else {
                    strippedVanilla = true;
                }
            }
            if (strippedVanilla) {
                changed = true;
                ItemEnchantments nextEnch = mut.toImmutable();
                if (copy.is(Items.ENCHANTED_BOOK) && nextEnch.isEmpty() && remainingFused.isEmpty()) {
                    return new ItemStack(Items.BOOK, copy.getCount());
                }
                EnchantmentHelper.setEnchantments(copy, nextEnch);
            }
        }

        if (!changed) {
            return null;
        }
        FusionStationOperations.syncGlintOverride(copy);
        return copy;
    }

    private static boolean hasVanillaEnchant(ItemStack stack, String enchantId) {
        ItemEnchantments enchs = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> h : enchs.keySet()) {
            String id = h.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            if (enchantId.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
