package chiseled_enchanting_table.data;

import chiseled_enchanting_table.data.DataCostTables.BookUpgradeRules;
import chiseled_enchanting_table.data.DataCostTables.CostTables;
import chiseled_enchanting_table.data.DataCostTables.DisenchantingRules;
import chiseled_enchanting_table.data.DataCostTables.TreasureEpiphanyRules;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.*;

/** RuntimeFrame domain; independent of reload/publication orchestration. */
public record RuntimeFrame(Map<Identifier, ItemTransformation> itemTransformations,
    VanillaChestBooksMode vanillaChestBooksMode,
    BookshelfBookPool bookshelfBookPool,
    BookUpgradeRules bookUpgradeRules,
    TreasureEpiphanyRules treasureEpiphanyRules,
    DisenchantingRules disenchantingRules,
    chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig,
    chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig,
    chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritualConfig,
    CostTables costTables,
    Set<EnchantmentWithLevel> defaultEnchantmentValues,
    List<DefaultEnchantmentTagRule> defaultEnchantmentTags,
    Set<Identifier> defaultExcludedIds,
    List<Identifier> defaultExcludedTags,
    List<LootInjection> lootInjections,
    chiseled_enchanting_table.config.ModRuntimeConfig configuration,
    chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy policy,
    chiseled_enchanting_table.config.rules.RuleBook.Snapshot rules) {
    public RuntimeFrame {
        itemTransformations = Map.copyOf(itemTransformations);
        defaultEnchantmentValues = Set.copyOf(defaultEnchantmentValues);
        defaultEnchantmentTags = List.copyOf(defaultEnchantmentTags);
        defaultExcludedIds = Set.copyOf(defaultExcludedIds);
        defaultExcludedTags = List.copyOf(defaultExcludedTags);
        lootInjections = List.copyOf(lootInjections);
    }
}
