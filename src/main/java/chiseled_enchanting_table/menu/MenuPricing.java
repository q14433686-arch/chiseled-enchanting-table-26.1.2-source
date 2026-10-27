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
    ) { return syncedRow(h.pricingAccess(), e, type); }

    public static SyncedEnchantmentCosts.Cost syncedRow(
        MenuPricingAccess h,
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
    ) { return getEnchantmentItemCostOptions(h.pricingAccess(), e, type); }

    public static List<List<ItemStack>> getEnchantmentItemCostOptions(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type
    ) { return MenuMaterialQuotes.getEnchantmentItemCostOptions(h, e, type); }

    public static int getBaseEnchantmentXpLevelCost(
        ChiseledEnchantingTableScreenHandler h,
        EnchantmentWithLevel e,
        OptionType type
    ) { return getBaseEnchantmentXpLevelCost(h.pricingAccess(), e, type); }

    public static int getBaseEnchantmentXpLevelCost(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type
    ) { return MenuXpQuotes.getBaseEnchantmentXpLevelCost(h, e, type); }

    public static List<List<ItemStack>> getEffectiveItemCostOptions(
        ChiseledEnchantingTableScreenHandler h,
        EnchantmentWithLevel e,
        OptionType type
    ) { return getEffectiveItemCostOptions(h.pricingAccess(), e, type); }

    public static List<List<ItemStack>> getEffectiveItemCostOptions(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type
    ) { return MenuMaterialQuotes.getEffectiveItemCostOptions(h, e, type); }

    public static int maxBundleUnitsAvailable(ChiseledEnchantingTableScreenHandler h, List<ItemStack> unitBundle) { return maxBundleUnitsAvailable(h.pricingAccess(), unitBundle); }

    public static int maxBundleUnitsAvailable(MenuPricingAccess h, List<ItemStack> unitBundle) {
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

    public static int resolveTransformationBatchCount(ChiseledEnchantingTableScreenHandler h, Identifier transformationId) { return resolveTransformationBatchCount(h.pricingAccess(), transformationId); }

    public static int resolveTransformationBatchCount(MenuPricingAccess h, Identifier transformationId) {
        if (transformationId == null) {
            return 1;
        }
        boolean isBlank = ChiseledEnchantingTableData.BLANK_OVERWRITE_ID.equals(transformationId);
        int inputCount = Math.max(1, h.getEnchantableItem().getCount());
        int maxCap = isBlank ? Math.max(1, h.depletedTomeMaxBatchSize()) : h.transformationMaxBatchSize();
        int desired = Math.max(1, Math.min(maxCap, inputCount));
        if (h.creative()) {
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
            h.playerLevel()
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
    ) { return collectBookCombineEntryRequirements(h.pricingAccess()); }

    public static List<XpCostConversion.XpRequirement> collectBookCombineEntryRequirements(
        MenuPricingAccess h
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
    ) { return resolveEffectiveXpRequirement(h.pricingAccess(), e, type, applyUpgradeRouteRawCap); }

    public static XpCostConversion.XpRequirement resolveEffectiveXpRequirement(
        MenuPricingAccess h,
        EnchantmentWithLevel e,
        OptionType type,
        boolean applyUpgradeRouteRawCap
    ) { return MenuXpQuotes.resolveEffectiveXpRequirement(h, e, type, applyUpgradeRouteRawCap); }




}
