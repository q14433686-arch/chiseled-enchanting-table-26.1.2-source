package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Shared client/server menu pricing resolver extracted from {@link ChiseledEnchantingTableScreenHandler}
 * (Audit COUP-06 / D-07): resolves material cost options, batch counts, resonance discounts, and
 * effective XP requirements identically across server deduction and client preview.
 */
public final class MenuPricing {
    private MenuPricing() {}

    @Nullable
    public static SyncedEnchantmentCosts.Cost syncedRow(
        ChiseledEnchantingTableScreenHandler h,
        EnchantmentWithLevel e,
        OptionType type
    ) {
        if (!h.world().isClientSide()) {
            return null;
        }
        return SyncedEnchantmentCosts
            .find(type, e.enchantment_id(), e.enchantment_level())
            .orElse(null);
    }

    public static List<List<ItemStack>> getEnchantmentItemCostOptions(
        ChiseledEnchantingTableScreenHandler h,
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
                e.enchantment_id(), h.player.getStringUUID());
            if (!classicBundle.isEmpty()) {
                return List.of(classicBundle);
            }
        }
        if (h.usesSaltedSingleMaterial() && type == OptionType.ENCHANTMENT) {
            var salted = ChiseledEnchantingTableData.classicSaltedItemCosts(e, maxLevel, h.player.getStringUUID());
            if (!salted.isEmpty()) {
                return List.of(salted);
            }
        }
        if (h.usesSaltedSingleMaterial() && type == OptionType.BOOK_RESEARCH_UPGRADE) {
            var salted = ChiseledEnchantingTableData.classicSaltedResearchCosts(e, maxLevel, h.player.getStringUUID());
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

    public static int getBaseEnchantmentXpLevelCost(
        ChiseledEnchantingTableScreenHandler h,
        EnchantmentWithLevel e,
        OptionType type
    ) {
        var serverRow = syncedRow(h, e, type);
        if (serverRow != null) {
            return serverRow.xpLevelCost();
        }
        int maxLevel = h.resolveEnchantmentMaxLevel(e);
        if (h.usesClassicTransformations()
            && (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id()))) {
            int xp = ChiseledEnchantingTableData.classicTransformationXpCost(e.enchantment_id());
            if (xp >= 0) {
                return xp;
            }
        }
        if (type == OptionType.TREASURE_EPIPHANY) {
            return ChiseledEnchantingTableData.treasureEpiphanyXpCost(
                e.enchantment_id(),
                e.enchantment_level(),
                maxLevel
            );
        }
        if (type == OptionType.BOOK_COMBINE_UPGRADE) {
            return ChiseledEnchantingTableData.bookCombineXpCost(e, maxLevel);
        }
        if (type == OptionType.BOOK_RESEARCH_UPGRADE) {
            return ChiseledEnchantingTableData.bookResearchXpCost(e, maxLevel);
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            return ChiseledEnchantingTableData.targetedStripXpCost(
                e.enchantment_id(),
                e.enchantment_level(),
                maxLevel
            );
        }
        if (type == OptionType.PURGE_CURSE) {
            return ChiseledEnchantingTableData.cursePurgeXpCost();
        }
        if (type == OptionType.DISENCHANT_ALL || type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return 0;
        }
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            return ChiseledEnchantingTableData.transformationXpCost(e.enchantment_id());
        }
        return ChiseledEnchantingTableData.xpCost(e, maxLevel);
    }

    public static List<List<ItemStack>> getEffectiveItemCostOptions(
        ChiseledEnchantingTableScreenHandler h,
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
            : (h.regularBookshelves >= lr.minDiscountBookshelves() ? lr.shelfUpgradeMaterialMultiplier() : 1.0);

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

    public static int maxBundleUnitsAvailable(ChiseledEnchantingTableScreenHandler h, List<ItemStack> unitBundle) {
        if (unitBundle == null || unitBundle.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(unitBundle);
        if (merged.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        ItemStack primary = merged.getFirst();
        ItemStack costSlot = h.getCostItem();
        if (primary.isEmpty() || costSlot.isEmpty() || !ItemStack.isSameItemSameComponents(primary, costSlot)) {
            return 0;
        }
        int units = h.countAvailableIncludingCostSlot(primary) / Math.max(1, primary.getCount());
        for (int i = 1; i < merged.size(); i++) {
            ItemStack add = merged.get(i);
            if (add.isEmpty()) continue;
            int totalPool = h.countAvailableInInventory(add);
            int unitNeed = Math.max(1, add.getCount());
            units = Math.min(units, totalPool / unitNeed);
        }
        return Math.max(0, units);
    }

    public static int resolveTransformationBatchCount(ChiseledEnchantingTableScreenHandler h, Identifier transformationId) {
        if (transformationId == null) {
            return 1;
        }
        boolean isBlank = ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(transformationId);
        int inputCount = Math.max(1, h.getEnchantableItem().getCount());
        int maxCap = isBlank ? Math.max(1, h.depletedTomeMaxBatchSize()) : h.transformationMaxBatchSize();
        int desired = Math.max(1, Math.min(maxCap, inputCount));
        if (h.player != null && h.player.getAbilities().instabuild) {
            return desired;
        }
        if (desired <= 1) {
            return 1;
        }

        int level = isBlank ? 1 : Math.max(1, h.syncedTransformationLevel(transformationId));
        EnchantmentWithLevel unitRef = new EnchantmentWithLevel(transformationId, level);
        List<List<ItemStack>> unitBundles = isBlank
            ? BookshelfProgression.applyDepletedTomeMaterialTier(
                h.shelfGatesConfig(),
                h.getEnchantmentItemCostOptions(unitRef, OptionType.TRANSFORMATION),
                h.depletedTomeTier()
            )
            : h.getEnchantmentItemCostOptions(unitRef, OptionType.TRANSFORMATION);

        int rawUnitXp = h.getBaseEnchantmentXpLevelCost(unitRef, OptionType.TRANSFORMATION);
        var baseUnitXp = XpCostConversion.forTransformation(
            h.libraryResonanceConfig(),
            transformationId,
            rawUnitXp,
            h.effectiveXpDiscountPercent(transformationId, OptionType.TRANSFORMATION),
            h.syncedTransformationMinPlayerLevel(transformationId),
            h.syncedTransformationDeductedLevels(transformationId)
        );
        var effectiveUnitXp = isBlank
            ? BookshelfProgression.applyDepletedTomeXpTierWithBatch(baseUnitXp, h.depletedTomeTier(), 1)
            : baseUnitXp;

        int maxXpUnits = XpCostConversion.maxTransformationBatchForLevel(
            effectiveUnitXp,
            h.player != null ? h.player.experienceLevel : 0
        );
        if (unitBundles.isEmpty()) {
            return maxXpUnits >= 1 ? Math.min(desired, maxXpUnits) : 1;
        }

        int maxMatUnits = 0;
        for (List<ItemStack> bundle : unitBundles) {
            maxMatUnits = Math.max(maxMatUnits, maxBundleUnitsAvailable(h, bundle));
        }
        int affordable = Math.min(maxMatUnits, maxXpUnits);
        return affordable >= 1 ? Math.min(desired, affordable) : 1;
    }

    public static List<XpCostConversion.XpRequirement> collectBookCombineEntryRequirements(
        ChiseledEnchantingTableScreenHandler h
    ) {
        ItemStack targetBook = h.getEnchantableItem();
        ItemStack costBook = h.getCostItem();
        if (!targetBook.is(Items.ENCHANTED_BOOK) || !costBook.is(Items.ENCHANTED_BOOK)) {
            return List.of();
        }
        ItemEnchantments targetEnchants = EnchantmentHelper.getEnchantmentsForCrafting(targetBook);
        ItemEnchantments costEnchants = EnchantmentHelper.getEnchantmentsForCrafting(costBook);
        List<XpCostConversion.XpRequirement> parts = new ArrayList<>();
        for (var entry : costEnchants.entrySet()) {
            var incoming = entry.getKey();
            int current = targetEnchants.getLevel(incoming);
            int incomingLevel = entry.getIntValue();
            int mergedLevel = current == incomingLevel
                ? Math.min(Math.max(1, incoming.value().getMaxLevel()), incomingLevel + 1)
                : Math.max(current, incomingLevel);
            if (mergedLevel > current) {
                parts.add(h.resolveEffectiveXpRequirement(
                    new EnchantmentWithLevel(ChiseledEnchantingTableScreenHandler.enchantmentToIdentifier(incoming), mergedLevel),
                    OptionType.BOOK_COMBINE_UPGRADE
                ));
            }
        }
        return parts;
    }

    public static XpCostConversion.XpRequirement resolveEffectiveXpRequirement(
        ChiseledEnchantingTableScreenHandler h,
        EnchantmentWithLevel e,
        OptionType type,
        boolean applyUpgradeRouteRawCap
    ) {
        var resonanceCfg = h.libraryResonanceConfig();
        if (type == OptionType.DISENCHANT_ALL) {
            int totalRefund = DisenchantmentApplication.totalFullDisenchantRefund(
                resonanceCfg,
                h.getEnchantableItem(),
                h.disenchantRefundScale(),
                h.disenchantExternalRefundScale()
            );
            return XpCostConversion.XpRequirement.refund(totalRefund);
        }
        if (type == OptionType.DOWNGRADE_ENCHANTMENT) {
            var optHolder = ChiseledEnchantingTableScreenHandler.findEnchantment(e.enchantment_id(), h.world());
            if (optHolder.isEmpty()) {
                return XpCostConversion.XpRequirement.FREE;
            }
            int cur = h.currentTargetEnchantLevel(e.enchantment_id());
            int refund = DisenchantmentApplication.provenanceRefundForStepDown(
                resonanceCfg,
                h.getEnchantableItem(),
                optHolder.get(),
                cur,
                e.enchantment_level(),
                h.disenchantRefundScale(),
                h.disenchantExternalRefundScale()
            );
            return XpCostConversion.XpRequirement.refund(refund);
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            ItemStack target = h.getEnchantableItem();
            var curMap = EnchantmentHelper.getEnchantmentsForCrafting(target);
            if (curMap.size() <= 1) {
                var optHolder = ChiseledEnchantingTableScreenHandler.findEnchantment(e.enchantment_id(), h.world());
                if (optHolder.isEmpty()) {
                    return XpCostConversion.XpRequirement.FREE;
                }
                int refund = DisenchantmentApplication.provenanceEssencePotential(
                    resonanceCfg,
                    target,
                    optHolder.get(),
                    curMap.getLevel(optHolder.get()),
                    h.disenchantRefundScale(),
                    h.disenchantExternalRefundScale()
                );
                return XpCostConversion.XpRequirement.refund(refund);
            }
            int rawStrip = h.getBaseEnchantmentXpLevelCost(e, OptionType.STRIP_SINGLE_ENCHANTMENT);
            int maxLvl = h.resolveEnchantmentMaxLevel(e);
            int stripDiscount = h.effectiveXpDiscountPercent(e.enchantment_id(), OptionType.STRIP_SINGLE_ENCHANTMENT);
            return XpCostConversion.forTargetedCleanse(resonanceCfg, e.enchantment_level(), maxLvl, rawStrip, stripDiscount);
        }
        if (type == OptionType.PURGE_CURSE) {
            int rawPurge = h.getBaseEnchantmentXpLevelCost(e, OptionType.PURGE_CURSE);
            return XpCostConversion.forFullArcaneRitual(resonanceCfg, rawPurge, h.xpDiscountPercent);
        }

        int rawCost = h.getBaseEnchantmentXpLevelCost(e, type);
        int discount = h.effectiveXpDiscountPercent(e.enchantment_id(), type);
        boolean classicFullXp = h.usesClassicFullPointXp();
        if (type == OptionType.TRANSFORMATION || ChiseledEnchantingTableData.isTransformation(e.enchantment_id())) {
            if (classicFullXp && h.usesClassicTransformations()) {
                return XpCostConversion.forFullArcaneRitual(resonanceCfg, rawCost, discount);
            }
            var baseTransformXp = XpCostConversion.forTransformation(
                resonanceCfg,
                e.enchantment_id(),
                rawCost,
                discount,
                h.syncedTransformationMinPlayerLevel(e.enchantment_id()),
                h.syncedTransformationDeductedLevels(e.enchantment_id())
            );
            int batchCount = h.resolveTransformationBatchCount(e.enchantment_id());
            if (ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(e.enchantment_id())) {
                return BookshelfProgression.applyDepletedTomeXpTierWithBatch(
                    baseTransformXp,
                    h.depletedTomeTier(),
                    batchCount
                );
            }
            return XpCostConversion.scaleTransformationXpForBatch(baseTransformXp, batchCount);
        }

        int existingLevel = h.currentTargetEnchantLevel(e.enchantment_id());
        int maxLvl = LibraryResonance.resolveMaxLevel(h.world(), e.enchantment_id());
        if (h.usesIncrementalCredit() && existingLevel > 0 && e.enchantment_level() > existingLevel) {
            if (!classicFullXp
                && h.storedMasterLevel(e.enchantment_id()) >= e.enchantment_level()
                && (type == OptionType.ENCHANTMENT
                    || type == OptionType.BOOK_RESEARCH_UPGRADE
                    || type == OptionType.BOOK_COMBINE_UPGRADE)) {
                int fullRaw = h.getBaseEnchantmentXpLevelCost(e, OptionType.ENCHANTMENT);
                int effectiveMasterRaw = type == OptionType.BOOK_COMBINE_UPGRADE
                    ? Math.min(rawCost, fullRaw)
                    : fullRaw;
                return XpCostConversion.forIncrementalMasterTomeEnchant(
                    resonanceCfg,
                    existingLevel,
                    e.enchantment_level(),
                    maxLvl,
                    effectiveMasterRaw,
                    discount
                );
            }
            int targetRaw = h.getBaseEnchantmentXpLevelCost(
                new EnchantmentWithLevel(e.enchantment_id(), e.enchantment_level()),
                OptionType.ENCHANTMENT
            );
            int prevRaw = h.getBaseEnchantmentXpLevelCost(
                new EnchantmentWithLevel(e.enchantment_id(), existingLevel),
                OptionType.ENCHANTMENT
            );
            int deltaRaw = Math.max(1, targetRaw - prevRaw);
            int effectiveRaw = applyUpgradeRouteRawCap ? Math.min(rawCost, deltaRaw) : deltaRaw;
            if (classicFullXp) {
                return XpCostConversion.forFullArcaneRitual(resonanceCfg, effectiveRaw, discount);
            }
            return XpCostConversion.forFullArcaneEnchantOrUpgrade(
                resonanceCfg,
                existingLevel,
                e.enchantment_level(),
                maxLvl,
                effectiveRaw,
                discount
            );
        }

        if (classicFullXp) {
            return XpCostConversion.forFullArcaneRitual(resonanceCfg, rawCost, discount);
        }
        if (h.isMasterTomeBacked(e, type)) {
            return XpCostConversion.forMasterTomeEnchant(resonanceCfg, e.enchantment_level(), maxLvl, rawCost, discount);
        }
        return XpCostConversion.forFullArcaneEnchantOrUpgrade(
            resonanceCfg,
            0,
            e.enchantment_level(),
            maxLvl,
            rawCost,
            discount
        );
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
