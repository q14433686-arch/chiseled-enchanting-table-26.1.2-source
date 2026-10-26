package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import net.minecraft.world.item.ItemStack;

/** MenuXpQuotes consumes read-only economic capabilities, never the menu coordinator. */
final class MenuXpQuotes {
    private MenuXpQuotes() {}

    public static int getBaseEnchantmentXpLevelCost(
        MenuPricingAccess h,
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

    public static XpCostConversion.XpRequirement resolveEffectiveXpRequirement(
        MenuPricingAccess h,
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
            return XpCostConversion.forFullArcaneRitual(resonanceCfg, rawPurge, h.xpDiscountPercent());
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

}