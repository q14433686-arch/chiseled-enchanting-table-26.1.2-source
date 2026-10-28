package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow-capability Cleanse pricing, extraction cost quotes, and bag-or-slot payment helpers.
 *
 * <p>Consumes {@link MenuPricingAccess} and {@link MenuPaymentService.Access} only — never the
 * screen-handler coordinator — keeping economic ratchets decoupled and unit-testable.</p>
 */
public final class MenuCleanseQuotes {
    public static final int EXTRACT_GRIT_COUNT = 2;
    public static final int EXTRACT_MAX_RAW_XP_LEVELS = 15;

    private MenuCleanseQuotes() {}

    /** Pure balanced 1.5x strip-XP formula capped at 15 levels (`min(15, ceil(1.5 * stripXp))`). */
    public static int computeExtractRawXpLevels(int stripRawXp) {
        if (stripRawXp <= 0) {
            return 0;
        }
        return Math.min(EXTRACT_MAX_RAW_XP_LEVELS, Math.max(1, (int) Math.ceil(stripRawXp * 1.5)));
    }

    public static boolean bundleUsesCatalyst(@Nullable List<ItemStack> bundle) {
        return bundle != null
            && bundle.stream().anyMatch(s -> s != null && !s.isEmpty() && s.is(BlockRegistry.ARCANE_CATALYST));
    }

    /**
     * Single-trait {@code Enchanted Book}s hide {@code EXTRACT_ENCHANTMENT_BOOK} to prevent no-op
     * book-to-book churning; gear with >= 1 non-curse trait and multi-trait books allow extraction.
     */
    public static boolean canExtractToBook(@Nullable ItemStack target) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        int traits = EnchantmentHelper.getEnchantmentsForCrafting(target).size();
        return traits >= 1 && !(target.is(Items.ENCHANTED_BOOK) && traits <= 1);
    }

    public static List<List<ItemStack>> extractBookCostOptions() {
        return List.of(
            List.of(new ItemStack(Items.BOOK, 1), new ItemStack(BlockRegistry.CLEANSING_GRIT, EXTRACT_GRIT_COUNT)),
            List.of(new ItemStack(BlockRegistry.DEPLETED_TOME, 1), new ItemStack(BlockRegistry.CLEANSING_GRIT, EXTRACT_GRIT_COUNT)),
            List.of(new ItemStack(BlockRegistry.ARCANE_CATALYST, 1), new ItemStack(Items.BOOK, 1)),
            List.of(new ItemStack(BlockRegistry.ARCANE_CATALYST, 1), new ItemStack(BlockRegistry.DEPLETED_TOME, 1))
        );
    }

    public static XpCostConversion.XpRequirement extractBookXpRequirement(
        MenuPricingAccess h,
        EnchantmentWithLevel e
    ) {
        if (!canExtractToBook(h.getEnchantableItem())) {
            return XpCostConversion.XpRequirement.FREE;
        }
        int rawStrip = h.getBaseEnchantmentXpLevelCost(e, OptionType.STRIP_SINGLE_ENCHANTMENT);
        int rawExtract = computeExtractRawXpLevels(rawStrip);
        int maxLvl = h.resolveEnchantmentMaxLevel(e);
        int discount = h.effectiveXpDiscountPercent(e.enchantment_id(), OptionType.STRIP_SINGLE_ENCHANTMENT);
        return XpCostConversion.forTargetedCleanse(
            h.libraryResonanceConfig(), e.enchantment_level(), maxLvl, rawExtract, discount);
    }

    public static boolean isCleanseBundleAffordableFromBagOrSlot(
        MenuPaymentService.Access access,
        @Nullable List<ItemStack> bundle
    ) {
        if (bundle == null || bundle.isEmpty() || access.player().getAbilities().instabuild) {
            return true;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(bundle);
        for (ItemStack req : merged) {
            if (!req.isEmpty() && MenuPaymentService.countAvailableIncludingCostSlot(access, req) < req.getCount()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static List<ItemStack> resolveCleanseCostBundle(
        MenuPaymentService.Access access,
        EnchantmentWithLevel ewl,
        OptionType type,
        int costOptionIndex
    ) {
        List<List<ItemStack>> options = access.costOptions(ewl, type);
        if (options.isEmpty()) {
            return (costOptionIndex <= 0 && access.materialFree(ewl, type)) ? List.of() : null;
        }
        if (costOptionIndex >= 0) {
            if (costOptionIndex >= options.size()) {
                return null;
            }
            List<ItemStack> chosen = options.get(costOptionIndex);
            if (chosen.isEmpty()) {
                return List.of();
            }
            List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(chosen);
            if (access.player().getAbilities().instabuild) {
                return merged;
            }
            return (!merged.isEmpty() && isCleanseBundleAffordableFromBagOrSlot(access, merged)) ? merged : null;
        }

        for (List<ItemStack> bundle : options) {
            if (bundle.isEmpty()) {
                return List.of();
            }
        }
        if (access.player().getAbilities().instabuild) {
            return ChiseledEnchantingTableData.mergeEquivalentCostBundle(options.getFirst());
        }
        ItemStack costSlot = access.costSlot();
        if (!costSlot.isEmpty()) {
            for (List<ItemStack> bundle : options) {
                List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(bundle);
                boolean matchesSlot = merged.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, costSlot));
                if (matchesSlot && !merged.isEmpty() && isCleanseBundleAffordableFromBagOrSlot(access, merged)) {
                    return merged;
                }
            }
        }
        for (List<ItemStack> bundle : options) {
            List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(bundle);
            if (!merged.isEmpty() && isCleanseBundleAffordableFromBagOrSlot(access, merged)) {
                return merged;
            }
        }
        return null;
    }

    public static void deductCleanseBundle(MenuPaymentService.Access access, @Nullable List<ItemStack> matchedBundle) {
        if (access.player().getAbilities().instabuild || matchedBundle == null || matchedBundle.isEmpty()) {
            return;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(matchedBundle);
        if (merged.isEmpty()) {
            return;
        }
        boolean touchedSlot = false;
        boolean touchedInv = false;
        ItemStack costSlot = access.costSlot();
        for (ItemStack req : merged) {
            int remaining = req.getCount();
            if (remaining > 0 && !costSlot.isEmpty() && ItemStack.isSameItemSameComponents(costSlot, req)) {
                int takeSlot = Math.min(remaining, costSlot.getCount());
                costSlot.shrink(takeSlot);
                remaining -= takeSlot;
                touchedSlot = true;
            }
            for (int slot = 0; slot < access.player().getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack candidate = access.player().getInventory().getItem(slot);
                if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, req)) {
                    int takeInv = Math.min(remaining, candidate.getCount());
                    candidate.shrink(takeInv);
                    remaining -= takeInv;
                    touchedInv = true;
                }
            }
        }
        if (touchedSlot) {
            access.tableChanged();
        }
        if (touchedInv) {
            access.player().getInventory().setChanged();
        }
    }
}
