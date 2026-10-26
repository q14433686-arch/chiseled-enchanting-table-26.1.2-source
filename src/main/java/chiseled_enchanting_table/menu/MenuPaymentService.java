package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler.PaymentReceipt;

/** Quotes, affordability and payment/receipt side effects via explicit inventory capabilities. */
final class MenuPaymentService {
    private MenuPaymentService() {}
    interface Access {
        Player player();
        ItemStack costSlot();
        void tableChanged();
        int availableUnits(List<ItemStack> bundle);
        List<List<ItemStack>> costOptions(EnchantmentWithLevel enchantment, OptionType type);
        boolean materialFree(EnchantmentWithLevel enchantment, OptionType type);
        XpCostConversion.XpRequirement xpRequirement(EnchantmentWithLevel enchantment, OptionType type);
    }
    static void consumeAdditionalCosts(Access access, List<ItemStack> costs) {
        for (int i = 1; i < costs.size(); i++) {
            ItemStack required = costs.get(i).copy();
            for (int slot = 0; slot < access.player().getInventory().getContainerSize() && !required.isEmpty(); slot++) {
                ItemStack candidate = access.player().getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, required)) {
                    int consumed = Math.min(candidate.getCount(), required.getCount());
                    candidate.shrink(consumed);
                    required.shrink(consumed);
                    access.player().getInventory().setChanged();
                }
            }
        }
    }

    static boolean isBundleAffordableWithOverflow(Access access, List<ItemStack> bundle) {
        if (bundle == null || bundle.isEmpty()) {
            return true;
        }
        if (access.player().getAbilities().instabuild) {
            return true;
        }
        return access.availableUnits(bundle) >= 1;
    }

    static List<ItemStack> findMatchingAffordableCostBundle(Access access, EnchantmentWithLevel ewl, OptionType type) {
        List<List<ItemStack>> options = access.costOptions(ewl, type);
        if (options.isEmpty()) {
            // Fail closed (audit H-02): "no pricing data" is only free for whitelisted
            // material-free operations; datapacks declare XP-only costs explicitly via
            // "material_free": true, which produces one empty bundle instead.
            return access.materialFree(ewl, type) ? List.of() : null;
        }
        for (List<ItemStack> bundle : options) {
            if (bundle.isEmpty()) {
                return List.of();
            }
        }
        if (access.player().getAbilities().instabuild) {
            return ChiseledEnchantingTableData.mergeEquivalentCostBundle(options.getFirst());
        }
        for (List<ItemStack> bundle : options) {
            List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(bundle);
            if (!merged.isEmpty() && isBundleAffordableWithOverflow(access, merged)) {
                return merged;
            }
        }
        return null;
    }

    static void deductMatchedBundle(Access access, List<ItemStack> matchedBundle) {
        if (access.player().getAbilities().instabuild || matchedBundle == null || matchedBundle.isEmpty()) {
            return;
        }
        List<ItemStack> merged = ChiseledEnchantingTableData.mergeEquivalentCostBundle(matchedBundle);
        if (merged.isEmpty()) {
            return;
        }
        ItemStack primary = merged.getFirst();
        if (!primary.isEmpty()) {
            int remainingPrimary = primary.getCount();
            ItemStack costSlot = access.costSlot();
            if (!costSlot.isEmpty() && ItemStack.isSameItemSameComponents(costSlot, primary)) {
                int takeSlot = Math.min(remainingPrimary, costSlot.getCount());
                costSlot.shrink(takeSlot);
                remainingPrimary -= takeSlot;
            }
            for (int slot = 0; slot < access.player().getInventory().getContainerSize() && remainingPrimary > 0; slot++) {
                ItemStack candidate = access.player().getInventory().getItem(slot);
                if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, primary)) {
                    int takeInv = Math.min(remainingPrimary, candidate.getCount());
                    candidate.shrink(takeInv);
                    remainingPrimary -= takeInv;
                }
            }
            access.tableChanged();
            access.player().getInventory().setChanged();
        }
        consumeAdditionalCosts(access, merged);
    }

    static PaymentReceipt applyEnchantmentConditionWithReceipt(Access access, ApplyEnchantmentPayload payload) {
        EnchantmentWithLevel ewl = new EnchantmentWithLevel(payload.enchantment_id(), payload.enchantment_level());
        OptionType effectiveType = payload.option_type();
        if (effectiveType == OptionType.ENCHANTMENT && ChiseledEnchantingTableData.isTransformation(payload.enchantment_id())) {
            effectiveType = OptionType.TRANSFORMATION;
        }
        var xpReq = access.xpRequirement(ewl, effectiveType);
        if (!xpReq.isMetBy(access.player())) {
            return new PaymentReceipt(false, 0, List.of());
        }

        List<ItemStack> matchedBundle = findMatchingAffordableCostBundle(access, ewl, effectiveType);
        if (!access.player().getAbilities().instabuild && matchedBundle == null) {
            return new PaymentReceipt(false, 0, List.of());
        }

        if (!access.player().getAbilities().instabuild) {
            int xpBefore = XpCostConversion.totalPlayerXpPoints(access.player());
            XpCostConversion.deductXp(access.player(), xpReq);
            int xpAfter = XpCostConversion.totalPlayerXpPoints(access.player());
            int paidXpPoints = Math.max(0, xpBefore - xpAfter);
            List<ItemStack> paidCopies = matchedBundle == null
                ? List.of()
                : matchedBundle.stream().map(ItemStack::copy).toList();
            deductMatchedBundle(access, matchedBundle);
            return new PaymentReceipt(true, paidXpPoints, paidCopies);
        }
        return new PaymentReceipt(true, 0, List.of());
    }
    static int countAvailableInInventory(Access access, ItemStack required) {
        if (required == null || required.isEmpty() || access.player() == null) {
            return 0;
        }
        int found = 0;
        for (int slot = 0; slot < access.player().getInventory().getContainerSize(); slot++) {
            ItemStack candidate = access.player().getInventory().getItem(slot);
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, required)) {
                found += candidate.getCount();
            }
        }
        return found;
    }
    static int countAvailableIncludingCostSlot(Access access, ItemStack required) {
        if (required == null || required.isEmpty()) {
            return 0;
        }
        ItemStack costSlot = access.costSlot();
        int fromSlot = (!costSlot.isEmpty() && ItemStack.isSameItemSameComponents(costSlot, required))
            ? costSlot.getCount()
            : 0;
        return fromSlot + countAvailableInInventory(access, required);
    }
}
