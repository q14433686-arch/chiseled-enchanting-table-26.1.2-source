package chiseled_enchanting_table.data;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.DataCostTables.BookUpgradeRules;
import chiseled_enchanting_table.data.DataCostTables.CostTables;
import chiseled_enchanting_table.data.DataCostTables.DisenchantingRules;
import chiseled_enchanting_table.data.DataCostTables.ItemCostRule;
import chiseled_enchanting_table.data.DataCostTables.TreasureEpiphanyRules;
import chiseled_enchanting_table.registry.CetTags;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

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
    chiseled_enchanting_table.config.rules.RuleBook.Snapshot rules) {}
