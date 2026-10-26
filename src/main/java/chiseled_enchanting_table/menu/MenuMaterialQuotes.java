package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import net.minecraft.world.item.ItemStack;

import static chiseled_enchanting_table.menu.MenuPricing.syncedRow;

/** MenuMaterialQuotes consumes read-only economic capabilities, never the menu coordinator. */
final class MenuMaterialQuotes {
    private MenuMaterialQuotes() {}

    public static List<List<ItemStack>> getEnchantmentItemCostOptions(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type
    ) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return List.of();
        }
        var synced = syncedRow(h, e, type);
        if (synced != null) {
            return synced.costOptions();
        }
        if (h.world().isClientSide()
            && SyncedEnchantmentCosts.hasServerTable()
            && !SyncedEnchantmentCosts.costTableComplete()) {
            return List.of();
        }
        int maxLevel = h.resolveEnchantmentMaxLevel(e);
        if (h.usesSaltedSingleMaterial()
            && (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id()))) {
            List<ItemStack> classicBundle = ChiseledEnchantingTableData.classicTransformationCosts(
                e.enchantment_id(), h.playerUuid());
            if (!classicBundle.isEmpty()) {
                return List.of(classicBundle);
            }
        }
        if (h.usesSaltedSingleMaterial() && type == OptionType.ENCHANTMENT) {
            var salted = ChiseledEnchantingTableData.classicSaltedItemCosts(e, maxLevel, h.playerUuid());
            if (!salted.isEmpty()) {
                return List.of(salted);
            }
        }
        if (h.usesSaltedSingleMaterial() && type == OptionType.BOOK_RESEARCH_UPGRADE) {
            var salted = ChiseledEnchantingTableData.classicSaltedResearchCosts(e, maxLevel, h.playerUuid());
            if (!salted.isEmpty()) {
                return List.of(salted);
            }
        }
        if (type == OptionType.TREASURE_EPIPHANY) {
            return ChiseledEnchantingTableData.treasureEpiphanyCostOptions(
                e.enchantment_id(),
                e.enchantment_level(),
                maxLevel
            );
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchCostOptions(e, maxLevel);
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            return ChiseledEnchantingTableData.targetedStripCostOptions(
                e.enchantment_id(),
                e.enchantment_level(),
                maxLevel
            );
        }
        if (type == OptionType.PURGE_CURSE) {
            return ChiseledEnchantingTableData.cursePurgeCostOptions(e.enchantment_id());
        }
        if (type == OptionType.DISENCHANT_ALL || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return List.of();
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationCostOptions(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.itemCostOptions(e, maxLevel);
    }

    public static List<List<ItemStack>> getEffectiveItemCostOptions(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type
    ) {
        if (type == OptionType.BOOK_COMBINE_UPGRADE
            || type == OptionType.DISENCHANT_ALL
            || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return List.of();
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            ItemStack target = h.getEnchantableItem();
            if (EnchantmentHelper.getEnchantmentsForCrafting(target).size() <= 1) {
                return List.of();
            }
            return h.getEnchantmentItemCostOptions(e, OptionType.STRIP_SINGLE_ENCHANTMENT);
        }
        if (type == OptionType.PURGE_CURSE) {
            return h.getEnchantmentItemCostOptions(e, OptionType.PURGE_CURSE);
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            int batchCount = h.resolveTransformationBatchCount(e.enchantment_id());
            if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(e.enchantment_id())) {
                return BookshelfProgression.applyDepletedTomeMaterialTierWithBatch(
                    h.shelfGatesConfig(),
                    h.getEnchantmentItemCostOptions(e, type),
                    h.depletedTomeTier(),
                    batchCount
                );
            }
            return BookshelfProgression.scaleBundlesWithBatch(
                h.getEnchantmentItemCostOptions(e, OptionType.TRANSFORMATION),
                batchCount
            );
        }

        int existingLevel = h.currentTargetEnchantLevel(e.enchantment_id());
        if (!h.usesIncrementalCredit() || existingLevel <= 0 || e.enchantment_level() <= existingLevel) {
            return h.getEnchantmentItemCostOptions(e, type);
        }

        OptionType baselineType = (type == OptionType.BOOK_RESEARCH_UPGRADE) ? OptionType.ENCHANTMENT : type;
        List<List<ItemStack>> targetBundles = h.getEnchantmentItemCostOptions(e, baselineType);
        List<List<ItemStack>> prevBundles = h.getEnchantmentItemCostOptions(
            new EnchantmentWithLevel(e.enchantment_id(), existingLevel),
            baselineType
        );

        var lr = h.libraryResonanceConfig();
        boolean masterGuided = h.storedMasterLevel(e.enchantment_id()) >= e.enchantment_level();
        double guidanceMultiplier = masterGuided
            ? lr.masterTomeUpgradeMaterialMultiplier()
            : (h.regularBookshelves() >= lr.minDiscountBookshelves() ? lr.shelfUpgradeMaterialMultiplier() : 1.0);

        List<List<ItemStack>> researchBundles = (type == OptionType.BOOK_RESEARCH_UPGRADE
            || (h.getEnchantableItem().is(Items.ENCHANTED_BOOK) && e.enchantment_level() == existingLevel + 1))
            ? h.getEnchantmentItemCostOptions(e, OptionType.BOOK_RESEARCH_UPGRADE)
            : List.of();

        List<List<ItemStack>> out = new ArrayList<>(targetBundles.size() + researchBundles.size());
        for (int bIdx = 0; bIdx < targetBundles.size(); bIdx++) {
            List<ItemStack> tgtBundle = targetBundles.get(bIdx);
            if (tgtBundle.isEmpty()) {
                out.add(List.of());
                continue;
            }
            List<ItemStack> prvBundle = bIdx < prevBundles.size() ? prevBundles.get(bIdx) : List.of();
            List<ItemStack> resMatch = findResearchBundleByPrimary(researchBundles, tgtBundle.getFirst());

            List<ItemStack> reduced = new ArrayList<>(tgtBundle.size());
            for (ItemStack st : tgtBundle) {
                ItemStack copy = st.copy();
                if (!copy.is(BlockRegistry.ARCANE_CATALYST)) {
                    int prevCount = findMatchingStackCount(prvBundle, st);
                    int delta = Math.max(1, st.getCount() - prevCount);
                    int guided = Math.max(1, (int) Math.floor(delta * guidanceMultiplier));
                    int resCap = findMatchingStackCount(resMatch, st);
                    if (resCap > 0) {
                        guided = Math.min(guided, resCap);
                    }
                    copy.setCount(guided);
                }
                reduced.add(copy);
            }
            if (!reduced.isEmpty()) {
                out.add(List.copyOf(reduced));
            }
        }
        for (List<ItemStack> rb : researchBundles) {
            if (!rb.isEmpty() && findResearchBundleByPrimary(out, rb.getFirst()).isEmpty()) {
                out.add(rb);
            }
        }
        return List.copyOf(out);
    }

    private static int findMatchingStackCount(List<ItemStack> bundle, ItemStack probe) {
        if (bundle == null || probe.isEmpty()) return 0;
        for (ItemStack s : bundle) {
            if (ItemStack.isSameItemSameComponents(s, probe)) {
                return s.getCount();
            }
        }
        return 0;
    }

    private static List<ItemStack> findResearchBundleByPrimary(List<List<ItemStack>> bundles, ItemStack primary) {
        if (bundles == null || primary.isEmpty()) return List.of();
        for (List<ItemStack> b : bundles) {
            if (!b.isEmpty() && ItemStack.isSameItemSameComponents(b.getFirst(), primary)) {
                return b;
            }
        }
        return List.of();
    }

}