package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import static chiseled_enchanting_table.gui.EnchantmentDisplayNames.findEnchantment;
import static chiseled_enchanting_table.gui.EnchantmentDisplayNames.identifierToEnchantment;
import static chiseled_enchanting_table.gui.EnchantmentDisplayNames.enchantmentToIdentifier;
import static chiseled_enchanting_table.gui.EnchantmentDisplayNames.baseEnchantmentName;

/** Candidate selection and ordering, independent of list entries, collapse state and scrolling. */
final class InscribeOptionModel {
    interface Catalog {
        boolean allowsDirectTierUpgrade();
        boolean usesClassicShelfBoost();
        boolean canDirectUpgradeExistingEnchantment(Holder<Enchantment> holder, Identifier id, int existing, int target);
        boolean canDirectResearchUpgradeTo(Identifier id, int target);
        List<EnchantmentWithLevel> computeActiveTreasureEpiphanies(ItemStack target);
        int requiredBookshelvesForDirectTier(Identifier id, int target, int max);
        Set<EnchantmentWithLevel> syncedTransformationsFor(ItemStack target);
        boolean syncedTransformationMatches(Identifier id, ItemStack target);
    }

    record DisplayOptionSlot(AvailableOption option, boolean shelfLocked, int requiredBookshelves) {}
    record Result(List<DisplayOptionSlot> pinned, Map<Identifier, List<DisplayOptionSlot>> groups) {
        Result {
            pinned = List.copyOf(pinned);
            var ordered = new LinkedHashMap<Identifier, List<DisplayOptionSlot>>();
            groups.forEach((id, values) -> ordered.put(id, List.copyOf(values)));
            groups = java.util.Collections.unmodifiableMap(ordered);
        }
    }
    private InscribeOptionModel() {}
    static boolean isDirectWoodenShelfEligible(Holder<Enchantment> holder, Identifier id) {
        if (DisenchantmentApplication.isCurse(holder) || DisenchantmentApplication.isTreasure(holder)) {
            return false;
        }
        return !SyncedEnchantmentCosts.explicitTreasureIds().contains(id);
    }
    static Result build(Catalog handler, Player player, ItemStack enchantable_item,
        ItemStack cost_item_stack, Set<EnchantmentWithLevel> unlocked_enchantements,
        java.util.function.Function<AvailableOption, String> displayName) {
        var enchantFromBook = cost_item_stack.is(Items.ENCHANTED_BOOK);
        ItemEnchantments targetEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(enchantable_item);

        List<DisplayOptionSlot> optionSlots = new ArrayList<>();
        Set<EnchantmentWithLevel> unlockedDirectSet = new HashSet<>();
        Map<Identifier, Integer> maxAddedTierById = new LinkedHashMap<>();

        if (enchantFromBook) {
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            for (var entry : enchantments.entrySet()) {
                int existingLvl = targetEnchantments.getLevel(entry.getKey());
                if (entry.getIntValue() > existingLvl) {
                    Identifier id = enchantmentToIdentifier(entry.getKey());
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(id, entry.getIntValue()), false, 0));
                }
            }
        } else if (!cost_item_stack.is(Items.BOOK)) {
            if (unlocked_enchantements != null) {
                for (EnchantmentWithLevel e : unlocked_enchantements) {
                    var optH = findEnchantment(e.enchantment_id(), player);
                    if (optH.isEmpty()) continue;
                    Holder<Enchantment> h = optH.get();
                    int existingLvl = targetEnchantments.getLevel(h);
                    // Audit M-04: strictly-greater only — the server rejects same-level and
                    // downgrade applies, so listing them as candidates was a dead click.
                    if (e.enchantment_level() > existingLvl) {
                        optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(e), false, 0));
                        unlockedDirectSet.add(e);
                        maxAddedTierById.merge(e.enchantment_id(), e.enchantment_level(), Math::max);
                    }
                }
            }
            // Track 2 (Existing-category upgrade on gear): once an item already carries level >= 1,
            // regular bookshelves unlock upgrades up to maxUnlockedDirectTier (1..MAX at 38 shelves).
            if (handler.allowsDirectTierUpgrade()
                && !enchantable_item.is(Items.ENCHANTED_BOOK)
                && !enchantable_item.is(Items.BOOK)) {
                for (var existingEntry : targetEnchantments.entrySet()) {
                    int existingLvl = existingEntry.getIntValue();
                    int maxLvl = Math.max(1, existingEntry.getKey().value().getMaxLevel());
                    if (existingLvl < 1 || existingLvl >= maxLvl) continue;
                    Identifier id = enchantmentToIdentifier(existingEntry.getKey());
                    for (int lvl = existingLvl + 1; lvl <= maxLvl; lvl++) {
                        EnchantmentWithLevel candidate = new EnchantmentWithLevel(id, lvl);
                        if (!unlockedDirectSet.contains(candidate)
                            && handler.canDirectUpgradeExistingEnchantment(existingEntry.getKey(), id, existingLvl, lvl)) {
                            optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(candidate), false, 0));
                            unlockedDirectSet.add(candidate);
                            maxAddedTierById.merge(id, lvl, Math::max);
                        }
                    }
                }
            }
            for (EnchantmentWithLevel epiphany : handler.computeActiveTreasureEpiphanies(enchantable_item)) {
                var optH = findEnchantment(epiphany.enchantment_id(), player);
                if (optH.isEmpty()) continue;
                Holder<Enchantment> h = optH.get();
                if (epiphany.enchantment_level() > targetEnchantments.getLevel(h)) {
                    optionSlots.add(new DisplayOptionSlot(
                        AvailableOption.treasureEpiphany(epiphany.enchantment_id(), epiphany.enchantment_level()),
                        false,
                        0
                    ));
                }
            }
        }
        if (enchantable_item.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments costEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(cost_item_stack);
            boolean combineEnabled = SyncedEnchantmentCosts.combineEnabled();
            boolean researchEnabled = SyncedEnchantmentCosts.researchEnabled();
            for (var entry : targetEnchantments.entrySet()) {
                int level = entry.getIntValue();
                int maxLevel = entry.getKey().value().getMaxLevel();
                if (level >= maxLevel) continue;
                Identifier id = enchantmentToIdentifier(entry.getKey());
                int targetLevel = level + 1;
                if (combineEnabled && cost_item_stack.is(Items.ENCHANTED_BOOK) && costEnchantments.getLevel(entry.getKey()) == level) {
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.bookCombineUpgrade(id, targetLevel), false, 0));
                    maxAddedTierById.merge(id, targetLevel, Math::max);
                } else if (researchEnabled
                    && !cost_item_stack.is(Items.ENCHANTED_BOOK)
                    && !cost_item_stack.is(Items.BOOK)
                    && !unlockedDirectSet.contains(new EnchantmentWithLevel(id, targetLevel))) {
                    if (handler.usesClassicShelfBoost() || handler.canDirectResearchUpgradeTo(id, targetLevel)) {
                        optionSlots.add(new DisplayOptionSlot(AvailableOption.bookResearchUpgrade(id, targetLevel), false, 0));
                        maxAddedTierById.merge(id, targetLevel, Math::max);
                    } else if (isDirectWoodenShelfEligible(entry.getKey(), id)) {
                        int reqShelves = handler.requiredBookshelvesForDirectTier(id, targetLevel, maxLevel);
                        optionSlots.add(new DisplayOptionSlot(
                            AvailableOption.bookResearchUpgrade(id, targetLevel),
                            true,
                            reqShelves
                        ));
                        maxAddedTierById.merge(id, targetLevel, Math::max);
                    }
                }
            }
        }

        // Surface greyed-out shelf-locked direct tiers (with exact bookshelf goal) rather than hiding goals!
        if (!handler.usesClassicShelfBoost()
            && !enchantFromBook
            && !cost_item_stack.is(Items.BOOK)
            && !ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
            Set<Identifier> candidateDirectIds = new LinkedHashSet<>(maxAddedTierById.keySet());
            for (var entry : targetEnchantments.entrySet()) {
                Identifier id = enchantmentToIdentifier(entry.getKey());
                if (isDirectWoodenShelfEligible(entry.getKey(), id)) {
                    candidateDirectIds.add(id);
                }
            }
            for (Identifier enchId : candidateDirectIds) {
                Holder<Enchantment> holder = identifierToEnchantment(enchId, player);
                if (!isDirectWoodenShelfEligible(holder, enchId)) continue;
                int maxLevel = Math.max(1, holder.value().getMaxLevel());
                int existingLvl = targetEnchantments.getLevel(holder);
                int unlockedTop = maxAddedTierById.getOrDefault(enchId, 0);
                int startLocked = Math.max(unlockedTop + 1, Math.max(2, existingLvl + 1));
                for (int lvl = startLocked; lvl <= maxLevel; lvl++) {
                    int reqShelves = handler.requiredBookshelvesForDirectTier(enchId, lvl, maxLevel);
                    optionSlots.add(new DisplayOptionSlot(AvailableOption.enchantment(enchId, lvl), true, reqShelves));
                }
            }
        }

        for (EnchantmentWithLevel t : handler.syncedTransformationsFor(enchantable_item)) {
            optionSlots.add(new DisplayOptionSlot(AvailableOption.transformation(t.enchantment_id(), t.enchantment_level()), false, 0));
        }

        List<DisplayOptionSlot> filtered = optionSlots.stream().filter(slot -> {
            AvailableOption option = slot.option();
            if (option.isTransformation()) {
                return handler.syncedTransformationMatches(option.id(), enchantable_item);
            }
            if (ChiseledEnchantingTableData.isResidualBook(enchantable_item)) {
                return false;
            }
            var optEnchant = findEnchantment(option.id(), player);
            if (optEnchant.isEmpty()) {
                return false;
            }
            Holder<Enchantment> enchant = optEnchant.get();
            return enchantable_item.is(Items.BOOK) || enchantable_item.is(Items.ENCHANTED_BOOK) || enchant.value().canEnchant(enchantable_item);
        }).toList();

        // 1. Standalone top-pinned rows: ALL Treasure Epiphanies + Item Transformations (never collapsed!)
        List<DisplayOptionSlot> pinnedTop = new ArrayList<>();
        Map<Identifier, List<DisplayOptionSlot>> groupedEnchants = new LinkedHashMap<>();

        filtered.stream()
            .filter(s -> s.option().isTreasureEpiphany() || s.option().isTransformation())
            .sorted((s1, s2) -> {
                AvailableOption o1 = s1.option();
                AvailableOption o2 = s2.option();
                int p1 = o1.isTreasureEpiphany() ? 0 : 1;
                int p2 = o2.isTreasureEpiphany() ? 0 : 1;
                if (p1 != p2) return Integer.compare(p1, p2);
                int c = displayName.apply(o1).compareTo(displayName.apply(o2));
                return c != 0 ? c : Integer.compare(o1.level(), o2.level());
            })
            .forEach(pinnedTop::add);

        filtered.stream()
            .filter(s -> !s.option().isTreasureEpiphany() && !s.option().isTransformation())
            .sorted((s1, s2) -> {
                AvailableOption o1 = s1.option();
                AvailableOption o2 = s2.option();
                String n1 = baseEnchantmentName(identifierToEnchantment(o1.id(), player));
                String n2 = baseEnchantmentName(identifierToEnchantment(o2.id(), player));
                int c = n1.compareTo(n2);
                if (c != 0) return c;
                int lvlCmp = Integer.compare(o1.level(), o2.level());
                if (lvlCmp != 0) return lvlCmp;
                return Integer.compare(o1.type().ordinal(), o2.type().ordinal());
            })
            .forEach(slot -> groupedEnchants.computeIfAbsent(slot.option().id(), ignored -> new ArrayList<>()).add(slot));

        return new Result(pinnedTop, groupedEnchants);
    }
}
