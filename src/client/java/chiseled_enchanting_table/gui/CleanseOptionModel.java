package chiseled_enchanting_table.gui;


import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.MenuCleanseQuotes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import static chiseled_enchanting_table.gui.EnchantmentDisplayNames.*;
/** Cleanse candidate selection without row creation or navigation side effects. */
final class CleanseOptionModel {
    private CleanseOptionModel() {}
    record Result(List<AvailableOption> pinned, Map<Identifier, List<AvailableOption>> groups,
        Map<Identifier, Holder<Enchantment>> holders) {
        Result {
            pinned = List.copyOf(pinned);
            var ordered = new LinkedHashMap<Identifier, List<AvailableOption>>();
            groups.forEach((id, values) -> ordered.put(id, List.copyOf(values)));
            groups = java.util.Collections.unmodifiableMap(ordered);
            holders = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(holders));
        }
    }
    static Result build(ItemStack target, boolean allowCursePurge) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        var pinned = new ArrayList<AvailableOption>();
        // 1. Standalone top-pinned Cleanse rows: Full Disenchant + any Curse Purge items
        int nonCurseCount = DisenchantmentApplication.countNonCurseEnchantments(target);
        if (nonCurseCount >= 1) {
            pinned.add(AvailableOption.disenchantAll(DisenchantmentApplication.DISENCHANT_ALL_ID));
        }

        List<Holder<Enchantment>> sortedKeys = new ArrayList<>(current.keySet());
        sortedKeys.sort((a, b) -> baseEnchantmentName(a).compareTo(baseEnchantmentName(b)));

        int totalTraits = current.size();
        Map<Identifier, List<AvailableOption>> groupedCleanse = new LinkedHashMap<>();
        Map<Identifier, Holder<Enchantment>> holdersById = new LinkedHashMap<>();

        for (Holder<Enchantment> holder : sortedKeys) {
            int curLvl = current.getLevel(holder);
            if (curLvl <= 0) continue;
            Identifier id = enchantmentToIdentifier(holder);
            if (DisenchantmentApplication.isCurse(holder)) {
                if (allowCursePurge) {
                    pinned.add(AvailableOption.purgeCurse(id, curLvl));
                }
            } else {
                List<AvailableOption> subRows = new ArrayList<>();
                for (int nextLvl = curLvl - 1; nextLvl >= 1; nextLvl--) {
                    subRows.add(AvailableOption.downgradeEnchantment(id, nextLvl));
                }
                if (MenuCleanseQuotes.canExtractToBook(target)) {
                    subRows.add(AvailableOption.extractEnchantmentBook(id, curLvl));
                }
                if (totalTraits >= 2) {
                    subRows.add(AvailableOption.stripSingleEnchantment(id, curLvl));
                }
                if (!subRows.isEmpty()) {
                    groupedCleanse.put(id, subRows);
                    holdersById.put(id, holder);
                }
            }
        }

        return new Result(pinned, groupedCleanse, holdersById);
    }
}
