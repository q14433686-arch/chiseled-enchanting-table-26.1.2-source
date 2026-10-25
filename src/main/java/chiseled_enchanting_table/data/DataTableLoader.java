package chiseled_enchanting_table.data;

import static chiseled_enchanting_table.data.DataJson.*;

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

import static chiseled_enchanting_table.data.DataJson.*;
import static chiseled_enchanting_table.data.DataIds.*;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.*;

/** Resource parsing and bundled fallbacks only; no live publication or mutable table cache. */
final class DataTableLoader {
    private static final Gson GSON = new Gson();
    private DataTableLoader() {}

    static chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy loadPackPolicy(
        ResourceManager resourceManager
    ) {
        Set<String> externalKeys = new java.util.LinkedHashSet<>();
        collectExternalDatapackKeys(resourceManager, SHELF_GATES_ID, "shelf_gates", externalKeys);
        collectExternalDatapackKeys(resourceManager, LIBRARY_RESONANCE_ID, "library_resonance", externalKeys);
        collectExternalDatapackKeys(resourceManager, BOOK_UPGRADES_ID, "book_upgrades", externalKeys);
        collectExternalDatapackKeys(resourceManager, TREASURE_EPIPHANY_ID, "treasure_epiphany", externalKeys);
        collectExternalDatapackKeys(resourceManager, DISENCHANTING_ID, "disenchanting", externalKeys);
        collectExternalDatapackKeys(resourceManager, ITEM_TRANSFORMATIONS_ID, "item_transformations", externalKeys);
        collectExternalDatapackKeys(resourceManager, LOOT_INJECTIONS_ID, "loot_injections", externalKeys);
        collectExternalDatapackKeys(resourceManager, BOOKSHELF_BOOK_POOL_ID, "bookshelf_book_pool", externalKeys);

        JsonObject policyRoot = null;
        try {
            var resOpt = resourceManager.getResource(PACK_POLICY_ID);
            if (resOpt.isPresent()) {
                try (Reader reader = resOpt.get().openAsReader()) {
                    policyRoot = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pack policy: " + PACK_POLICY_ID, e);
        }
        return chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy.fromJson(policyRoot, externalKeys);
    }

    static void collectExternalDatapackKeys(
        ResourceManager resourceManager,
        Identifier tableId,
        String tableName,
        Set<String> outKeys
    ) {
        DatapackKeyExtractor.collectExternalDatapackKeys(resourceManager, tableId, tableName, outKeys);
    }

    static DefaultEnchantmentTable loadDefaultEnchantments(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(DEFAULT_ENCHANTMENTS_ID);
        if (resource.isEmpty()) {
            ChiseledEnchantingTable.LOGGER.warn("Missing {}; using built-in fallback defaults", DEFAULT_ENCHANTMENTS_ID);
            return builtInDefaultEnchantmentTable();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");
            Set<EnchantmentWithLevel> loadedValues = new HashSet<>();
            if (values != null) {
                for (JsonElement element : values) {
                    JsonObject entry = element.getAsJsonObject();
                    Identifier enchantmentId = parseIdentifier(entry.get("enchantment").getAsString());
                    int level = Math.max(1, entry.get("level").getAsInt());
                    loadedValues.add(new EnchantmentWithLevel(enchantmentId, level));
                }
            }

            List<DefaultEnchantmentTagRule> loadedTags = new ArrayList<>();
            JsonArray tags = root.getAsJsonArray("tags");
            if (tags != null) {
                for (JsonElement element : tags) {
                    JsonObject entry = element.getAsJsonObject();
                    Identifier tagId = parseIdentifier(stripTagPrefix(entry.get("tag").getAsString()));
                    int level = Math.max(1, intOr(entry, "level", 1));
                    loadedTags.add(new DefaultEnchantmentTagRule(tagId, level));
                }
            }

            Set<Identifier> excludedIds = new HashSet<>();
            JsonArray excludeArr = root.getAsJsonArray("exclude");
            if (excludeArr != null) {
                for (JsonElement element : excludeArr) {
                    excludedIds.add(parseIdentifier(element.getAsString()));
                }
            }

            List<Identifier> excludedTags = new ArrayList<>();
            JsonArray excludeTagArr = root.getAsJsonArray("exclude_tags");
            if (excludeTagArr != null) {
                for (JsonElement element : excludeTagArr) {
                    excludedTags.add(parseIdentifier(stripTagPrefix(element.getAsString())));
                }
            }

            if (loadedValues.isEmpty() && loadedTags.isEmpty()) {
                return builtInDefaultEnchantmentTable();
            }
            return new DefaultEnchantmentTable(
                loadedValues,
                List.copyOf(loadedTags),
                Set.copyOf(excludedIds),
                List.copyOf(excludedTags)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + DEFAULT_ENCHANTMENTS_ID, e);
        }
    }

    static CostTables loadCostTables(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(ENCHANTMENT_COSTS_ID);
        if (resource.isEmpty()) {
            ChiseledEnchantingTable.LOGGER.warn("Missing {}; using built-in fallback costs", ENCHANTMENT_COSTS_ID);
            return builtInCostTables();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return CostTables.fromJson(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + ENCHANTMENT_COSTS_ID, e);
        }
    }

    static Map<Identifier, ItemTransformation> loadItemTransformations(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(ITEM_TRANSFORMATIONS_ID);
        if (resource.isEmpty()) {
            return builtInItemTransformations();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");
            Map<Identifier, ItemTransformation> loaded = new HashMap<>();
            if (values != null) {
                for (JsonElement element : values) {
                    ItemTransformation transformation = ItemTransformation.fromJson(element.getAsJsonObject());
                    loaded.put(transformation.id(), transformation);
                }
            }
            return Map.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + ITEM_TRANSFORMATIONS_ID, e);
        }
    }

    static JsonObject readBundledJsonObject(String fileName) {
        String resourcePath = "/data/chiseled_enchanting_table/chiseled_enchanting_table/" + fileName;
        try (var stream = ChiseledEnchantingTableData.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                try (Reader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to read bundled default resource {}", resourcePath, e);
        }
        return new JsonObject();
    }

    static volatile Map<Identifier, ItemTransformation> rawItemTransformations = builtInItemTransformations();
    private static final LootInjectionTable BUILT_IN_LOOT_TABLE = builtInLootInjections();
    private static volatile VanillaChestBooksMode rawVanillaChestBooksMode = BUILT_IN_LOOT_TABLE.mode();
    private static volatile BookshelfBookPool rawBookshelfBookPool = BookshelfBookPool.emptyFallback();
    private static volatile BookUpgradeRules rawBookUpgradeRules = builtInBookUpgradeRules();
    // Classic overlay set: bundled baselines, replaceable per-file by a
    // datapack carrying the same path (loaded in the reload listener below).
    private static final CostTables BUILT_IN_CLASSIC_COSTS = CostTables.fromJson(readBundledJsonObject("enchantment_costs_classic_1_0_3.json"));
    private static final BookUpgradeRules BUILT_IN_CLASSIC_UPGRADES = BookUpgradeRules.fromJson(readBundledJsonObject("book_upgrades_classic_1_0_3.json"));
    private static final Map<Identifier, ItemTransformation> BUILT_IN_CLASSIC_TRANSFORMATIONS = classic103ItemTransformations();
    private static volatile CostTables classicCostTables = BUILT_IN_CLASSIC_COSTS;
    private static volatile BookUpgradeRules classicBookUpgradeRules = BUILT_IN_CLASSIC_UPGRADES;
    private static volatile Map<Identifier, ItemTransformation> classicItemTransformations = BUILT_IN_CLASSIC_TRANSFORMATIONS;
    private static volatile TreasureEpiphanyRules rawTreasureEpiphanyRules = builtInTreasureEpiphanyRules();
    private static volatile DisenchantingRules rawDisenchantingRules = builtInDisenchantingRules();
    private static volatile chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config rawShelfGatesConfig =
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config.DEFAULT;
    private static volatile chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config rawLibraryResonanceConfig =
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;
    private static volatile chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config rawRitualConfig =
        builtInRitualConfig();

    private static volatile Set<EnchantmentWithLevel> defaultEnchantmentValues = builtInDefaultEnchantments();
    private static volatile List<DefaultEnchantmentTagRule> defaultEnchantmentTags = builtInDefaultEnchantmentTags();
    private static volatile Set<Identifier> defaultExcludedIds = builtInExcludedTreasureIds();
    private static volatile List<Identifier> defaultExcludedTags = builtInExcludedTags();
    private static volatile CostTables costTables = builtInCostTables();
    private static volatile Map<Identifier, ItemTransformation> itemTransformations = rawItemTransformations;
    private static volatile VanillaChestBooksMode vanillaChestBooksMode = rawVanillaChestBooksMode;
    private static volatile List<LootInjection> lootInjections = BUILT_IN_LOOT_TABLE.entries();
    /** Registry preparation is NOT a live gameplay generation; failed reloads must not leak it. */
    public record LootRegistryBuildRules(List<LootInjection> entries, VanillaChestBooksMode mode,
        chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.DepletedTomeChestSpawnConfig chestSpawns) {
        public LootRegistryBuildRules { entries = List.copyOf(entries); }
    }

    static <T> T loadClassicJson(
        ResourceManager resourceManager,
        String fileName,
        java.util.function.Function<JsonObject, T> parser,
        T fallback
    ) {
        Identifier id = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/" + fileName);
        var resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            return fallback;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parser.apply(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid classic data table " + id, e);
        }
    }

    static Map<Identifier, ItemTransformation> classic103ItemTransformations() {
        return parseItemTransformations(readBundledJsonObject("item_transformations_classic_1_0_3.json"));
    }

    static Map<Identifier, ItemTransformation> parseItemTransformations(JsonObject root) {
        Map<Identifier, ItemTransformation> fallback = new HashMap<>();
        JsonArray values = root.getAsJsonArray("values");
        if (values != null) {
            for (JsonElement element : values) {
                ItemTransformation transformation = ItemTransformation.fromJson(element.getAsJsonObject());
                fallback.put(transformation.id(), transformation);
            }
        }
        return Map.copyOf(fallback);
    }

    static Set<EnchantmentWithLevel> builtInDefaultEnchantments() {
        return Set.of();
    }

    static List<DefaultEnchantmentTagRule> builtInDefaultEnchantmentTags() {
        return List.of(new DefaultEnchantmentTagRule(Identifier.withDefaultNamespace("in_enchanting_table"), 1));
    }

    static Set<Identifier> builtInExcludedTreasureIds() {
        return Set.of();
    }

    static List<Identifier> builtInExcludedTags() {
        return List.of(
            Identifier.withDefaultNamespace("treasure"),
            Identifier.withDefaultNamespace("curse"),
            ChiseledEnchantingTable.identifier("epiphany_exclusive")
        );
    }

    static DefaultEnchantmentTable builtInDefaultEnchantmentTable() {
        return new DefaultEnchantmentTable(
            builtInDefaultEnchantments(),
            builtInDefaultEnchantmentTags(),
            builtInExcludedTreasureIds(),
            builtInExcludedTags()
        );
    }

    static CostTables builtInCostTables() {
        return CostTables.fromJson(readBundledJsonObject("enchantment_costs.json"));
    }

    static BookUpgradeRules builtInBookUpgradeRules() {
        return BookUpgradeRules.fromJson(readBundledJsonObject("book_upgrades.json"));
    }

    static LootInjectionTable parseLootInjectionRoot(JsonObject root) {
        if (root == null) {
            return new LootInjectionTable(
                VanillaChestBooksMode.KEEP_BOTH,
                List.of(),
                chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.DepletedTomeChestSpawnConfig.EMPTY
            );
        }
        VanillaChestBooksMode mode = root.has("vanilla_chest_books") && root.get("vanilla_chest_books").isJsonPrimitive()
            ? VanillaChestBooksMode.fromToken(root.get("vanilla_chest_books").getAsString())
            : VanillaChestBooksMode.KEEP_BOTH;
        JsonArray entries = root.getAsJsonArray("entries");
        List<LootInjection> loaded = new ArrayList<>();
        if (entries != null) {
            for (JsonElement element : entries) {
                LootInjection injection = LootInjection.fromJson(element.getAsJsonObject());
                if (injection != null) {
                    loaded.add(injection);
                }
            }
        }
        var chestSpawns = chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
            .DepletedTomeChestSpawnConfig.fromLootInjectionsJson(root);
        return new LootInjectionTable(mode, List.copyOf(loaded), chestSpawns);
    }

    static LootInjectionTable builtInLootInjections() {
        return parseLootInjectionRoot(readBundledJsonObject("loot_injections.json"));
    }

    static LootInjectionTable loadLootInjections(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(LOOT_INJECTIONS_ID);
        if (resource.isEmpty()) {
            return builtInLootInjections();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return parseLootInjectionRoot(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + LOOT_INJECTIONS_ID, e);
        }
    }

    static BookUpgradeRules loadBookUpgradeRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(BOOK_UPGRADES_ID);
        if (resource.isEmpty()) {
            return builtInBookUpgradeRules();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return BookUpgradeRules.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + BOOK_UPGRADES_ID, e);
        }
    }

    static TreasureEpiphanyRules builtInTreasureEpiphanyRules() {
        return TreasureEpiphanyRules.fromJson(readBundledJsonObject("treasure_epiphany.json"), builtInExcludedTreasureIds());
    }

    static TreasureEpiphanyRules loadTreasureEpiphanyRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(TREASURE_EPIPHANY_ID);
        if (resource.isEmpty()) {
            return builtInTreasureEpiphanyRules();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return TreasureEpiphanyRules.fromJson(JsonParser.parseReader(reader).getAsJsonObject(), builtInExcludedTreasureIds());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + TREASURE_EPIPHANY_ID, e);
        }
    }

    static BookshelfBookPool loadBookshelfBookPool(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(BOOKSHELF_BOOK_POOL_ID);
        if (resource.isEmpty()) {
            return BookshelfBookPool.emptyFallback();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return BookshelfBookPool.fromJson(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + BOOKSHELF_BOOK_POOL_ID, e);
        }
    }

    static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config builtInRitualConfig() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.fromJson(
            readBundledJsonObject("library_resonance.json")
        );
    }

    static chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config loadShelfGatesConfig(
        ResourceManager resourceManager
    ) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(SHELF_GATES_ID);
        if (resource.isEmpty()) {
            return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config.DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config.fromJson(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + SHELF_GATES_ID, e);
        }
    }

    static LibraryResonanceAndRitual loadLibraryResonanceAndRitual(
        ResourceManager resourceManager
    ) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(LIBRARY_RESONANCE_ID);
        if (resource.isEmpty()) {
            return new LibraryResonanceAndRitual(
                chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT,
                chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.DEFAULT
            );
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return new LibraryResonanceAndRitual(
                chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.fromJson(root),
                chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.fromJson(root)
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + LIBRARY_RESONANCE_ID, e);
        }
    }

    static DisenchantingRules builtInDisenchantingRules() {
        return DisenchantingRules.fromJson(
            readBundledJsonObject("disenchanting.json"),
            () -> readBundledJsonObject("disenchanting.json")
        );
    }

    static DisenchantingRules loadDisenchantingRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(DISENCHANTING_ID);
        if (resource.isEmpty()) {
            return builtInDisenchantingRules();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return DisenchantingRules.fromJson(
                JsonParser.parseReader(reader).getAsJsonObject(),
                () -> readBundledJsonObject("disenchanting.json")
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data table " + DISENCHANTING_ID, e);
        }
    }

    record DefaultEnchantmentTable(
        Set<EnchantmentWithLevel> values,
        List<DefaultEnchantmentTagRule> tags,
        Set<Identifier> excludedIds,
        List<Identifier> excludedTags
    ) {}

    record LootInjectionTable(
        VanillaChestBooksMode mode,
        List<LootInjection> entries,
        chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.DepletedTomeChestSpawnConfig chestSpawns
    ) {}

    record LibraryResonanceAndRitual(
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config resonance,
        chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritual
    ) {}
}
