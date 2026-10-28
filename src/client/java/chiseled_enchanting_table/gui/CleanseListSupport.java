package chiseled_enchanting_table.gui;

import chiseled_enchanting_table.gui.EnchantementListWidget.EnchantUiEntry;
import chiseled_enchanting_table.gui.row.EnchantmentRowRenderer;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import chiseled_enchanting_table.menu.AvailableOption;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.menu.MenuCleanseQuotes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Cleanse-tab entry population, filtering, and row sub-annotations extracted from
 * {@link EnchantementListWidget} to keep list-coordinator responsibilities compact.
 */
final class CleanseListSupport {
    private CleanseListSupport() {}

    static void populate(
        EnchantementListWidget list,
        Minecraft minecraft,
        Set<Identifier> expandedCleanseCategories,
        Set<Identifier> visibleCleanseGroupIds,
        Function<AvailableOption, EnchantUiEntry> buildTopEntry,
        Function<AvailableOption, EnchantUiEntry> buildChildEntry,
        Consumer<EnchantUiEntry> addRow,
        int navigationIndex,
        boolean targetChanged,
        double previousScroll
    ) {
        visibleCleanseGroupIds.clear();
        if (!list.handler.disenchantEnabled() || list.enchantable_item.isEmpty()) {
            return;
        }
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(list.enchantable_item);
        if (current.isEmpty()) {
            return;
        }

        String query = list.cleanseFilter.query();
        CleanseFilterBar.Mode mode = list.cleanseFilter.mode();
        boolean onlyAffordable = list.cleanseFilter.affordableOnly();

        var model = CleanseOptionModel.build(list.enchantable_item, list.handler.disenchantAllowCursePurge());
        for (AvailableOption option : model.pinned()) {
            if (!mode.accepts(option.type())) continue;
            EnchantUiEntry built = buildTopEntry.apply(option);
            if ((!onlyAffordable || built.canEnchant())
                && InscribeFilterBar.matchesQuery(query, list.displayName(option), option.id(), built.cost_options)) {
                addRow.accept(built);
            }
        }

        var groupedCleanse = model.groups();
        var holdersById = model.holders();
        if (targetChanged && groupedCleanse.size() == 1) {
            expandedCleanseCategories.add(groupedCleanse.keySet().iterator().next());
        }

        for (var groupEntry : groupedCleanse.entrySet()) {
            Identifier id = groupEntry.getKey();
            List<AvailableOption> children = groupEntry.getValue();
            Holder<Enchantment> holder = holdersById.get(id);
            int curLvl = current.getLevel(holder);
            String headerTitle = Enchantment.getFullname(holder, curLvl).getString();

            List<EnchantUiEntry> matchedChildren = new ArrayList<>(children.size());
            for (AvailableOption child : children) {
                if (!mode.accepts(child.type())) continue;
                EnchantUiEntry built = buildChildEntry.apply(child);
                if ((!onlyAffordable || built.canEnchant())
                    && (InscribeFilterBar.matchesQuery(query, headerTitle, id, built.cost_options)
                        || InscribeFilterBar.matchesQuery(query, list.displayName(child), child.id(), built.cost_options))) {
                    matchedChildren.add(built);
                }
            }
            if (matchedChildren.isEmpty()) continue;
            visibleCleanseGroupIds.add(id);

            int traitEssence = DisenchantmentApplication.provenanceEssencePotential(
                list.handler.libraryResonanceConfig(),
                list.enchantable_item,
                holder,
                curLvl,
                list.handler.disenchantRefundScale(),
                list.handler.disenchantExternalRefundScale()
            );
            boolean open = list.cleanseFilter.hasActiveFilter() || list.isCategoryExpanded(id);
            addRow.accept(EnchantUiEntry.categoryEntry(
                minecraft,
                list.handler,
                id,
                headerTitle,
                I18n.get("gui.chiseled_enchanting_table.category.count", matchedChildren.size()),
                RowRenderSupport.JADE_MINT,
                traitEssence,
                true,
                list
            ));
            if (open) {
                for (int idx = 0; idx < matchedChildren.size(); idx++) {
                    EnchantUiEntry child = matchedChildren.get(idx);
                    child.last_child = (idx == matchedChildren.size() - 1);
                    addRow.accept(child);
                }
            }
        }

        list.resumeNavigation(navigationIndex);
        if (!targetChanged) {
            list.setScrollAmount(Math.min(previousScroll, list.maxScrollAmount()));
        }
    }

    @Nullable
    static EnchantmentRowRenderer.OverrideAnnotation cleanseOverrideAnnotation(EnchantUiEntry entry) {
        OptionType type = entry.option_type;
        if (type == OptionType.DISENCHANT_ALL) {
            if (Minecraft.getInstance().screen instanceof ChiseledEnchantingTableScreen tableScreen
                && tableScreen.isDisenchantAllConfirmPending()) {
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.cleanse.confirm_row_sub"),
                    RowRenderSupport.EPIPHANY_GOLD
                );
            }
            String sub = entry.enchantable_item.is(Items.ENCHANTED_BOOK)
                ? I18n.get("gui.chiseled_enchanting_table.cleanse.all_sub_book")
                : I18n.get("gui.chiseled_enchanting_table.cleanse.all_sub_item");
            return new EnchantmentRowRenderer.OverrideAnnotation(sub, RowRenderSupport.LIGHT_GREEN);
        }
        if (type == OptionType.DOWNGRADE_ENCHANTMENT) {
            return new EnchantmentRowRenderer.OverrideAnnotation(
                I18n.get("gui.chiseled_enchanting_table.cleanse.downgrade_sub"),
                RowRenderSupport.LIGHT_GREEN
            );
        }
        if (type == OptionType.EXTRACT_ENCHANTMENT_BOOK) {
            if (MenuCleanseQuotes.bundleUsesCatalyst(entry.activeCostBundle())) {
                return new EnchantmentRowRenderer.OverrideAnnotation(
                    I18n.get("gui.chiseled_enchanting_table.cleanse.extract_sub_catalyst"),
                    RowRenderSupport.ARCANE_PURPLE
                );
            }
            int kept = Math.max(0, EnchantmentHelper.getEnchantmentsForCrafting(entry.enchantable_item).size() - 1);
            return new EnchantmentRowRenderer.OverrideAnnotation(
                I18n.get("gui.chiseled_enchanting_table.cleanse.extract_sub", kept),
                RowRenderSupport.JADE_MINT
            );
        }
        if (type == OptionType.STRIP_SINGLE_ENCHANTMENT) {
            int kept = Math.max(1, EnchantmentHelper.getEnchantmentsForCrafting(entry.enchantable_item).size() - 1);
            return new EnchantmentRowRenderer.OverrideAnnotation(
                I18n.get("gui.chiseled_enchanting_table.cleanse.strip_sub", kept),
                RowRenderSupport.CYAN
            );
        }
        if (type == OptionType.PURGE_CURSE) {
            return new EnchantmentRowRenderer.OverrideAnnotation(
                I18n.get("gui.chiseled_enchanting_table.cleanse.curse_sub"),
                RowRenderSupport.EPIPHANY_GOLD
            );
        }
        return null;
    }
}
