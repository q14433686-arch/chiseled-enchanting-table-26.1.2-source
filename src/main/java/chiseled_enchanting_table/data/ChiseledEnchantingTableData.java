package chiseled_enchanting_table.data;

import chiseled_enchanting_table.ChiseledEnchantingTable;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Data-backed tables for chiseled enchanting mechanics.
 *
 * <p>Architecture note: content/rule/cost data belongs in JSON where possible. This loader keeps
 * Java responsible for validation/loading while JSON owns default enchantments and cost values.</p>
 *
 * <p>Cross-mod compatibility note (third-party content/rule mods, e.g. Ore Enchants-style mods that
 * register new enchantments, or "Universal Enchants" style mods that widen vanilla
 * {@code supported_items}/{@code exclusive_set}): every lookup here is by {@link Identifier} or by
 * vanilla {@link TagKey}, never by a hardcoded namespace list, so third-party enchantment ids work
 * without any Java change as long as the content is exposed through JSON (see
 * {@code default_enchantments.json}'s {@code tags} entries) or discovered live from the registry
 * (book scanning, {@code canEnchant}/{@code areCompatible} checks elsewhere in this mod).</p>
 */
public final class ChiseledEnchantingTableData implements SimpleSynchronousResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Identifier RELOAD_ID = ChiseledEnchantingTable.identifier("data_tables");
    private static final Identifier DEFAULT_ENCHANTMENTS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/default_enchantments.json");
    private static final Identifier ENCHANTMENT_COSTS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/enchantment_costs.json");
    private static final Identifier ITEM_TRANSFORMATIONS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/item_transformations.json");
    // U1: JSON tables backing the (new to the port) loot-injection and structure-bookshelf features.
    private static final Identifier LOOT_INJECTIONS_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/loot_injections.json");
    private static final Identifier BOOKSHELF_BOOK_POOL_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/bookshelf_book_pool.json");
    private static final Identifier BOOK_UPGRADES_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/book_upgrades.json");

    private static Set<EnchantmentWithLevel> defaultEnchantmentValues = builtInDefaultEnchantments();
    private static List<DefaultEnchantmentTagRule> defaultEnchantmentTags = builtInDefaultEnchantmentTags();
    private static CostTables costTables = CostTables.builtInFallback();
    private static Map<Identifier, ItemTransformation> itemTransformations = builtInItemTransformations();
    private static List<LootInjection> lootInjections = List.of();
    private static BookshelfBookPool bookshelfBookPool = BookshelfBookPool.emptyFallback();
    private static BookUpgradeRules bookUpgradeRules = BookUpgradeRules.builtInFallback();

    public static void init() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(PackType.SERVER_DATA)
            .registerReloadListener(new ChiseledEnchantingTableData());
    }

    /**
     * Explicit, hand-picked default-unlocked enchantments from the JSON {@code values} list only
     * (no tag expansion). Kept for callers without registry access.
     */
    public static Set<EnchantmentWithLevel> defaultEnchantments() {
        return new HashSet<>(defaultEnchantmentValues);
    }

    /**
     * Full default-unlocked set: the explicit {@code values} list plus every enchantment currently
     * present in any of the JSON {@code tags} (e.g. {@code #minecraft:in_enchanting_table} — the
     * actual tag the vanilla enchanting table reads, and the documented mod extension point;
     * {@code #minecraft:non_treasure} is its vanilla-only default content, not itself an extension
     * point third-party mods are expected to add to), resolved against the live registry. This is
     * how third-party enchantment mods (which commonly append their own enchantments directly into
     * {@code in_enchanting_table.json}) become available at this table without editing any id list
     * by hand.
     */
    public static Set<EnchantmentWithLevel> defaultEnchantments(HolderGetter<Enchantment> enchantmentLookup) {
        Set<EnchantmentWithLevel> result = new HashSet<>(defaultEnchantmentValues);
        for (DefaultEnchantmentTagRule rule : defaultEnchantmentTags) {
            TagKey<Enchantment> tagKey = TagKey.create(Registries.ENCHANTMENT, rule.tagId());
            enchantmentLookup.get(tagKey).ifPresent(named -> named.stream().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> result.add(new EnchantmentWithLevel(key.identifier(), rule.level())))
            ));
        }
        return result;
    }

    public static ItemStack itemCost(EnchantmentWithLevel enchantmentWithLevel) {
        return itemCost(enchantmentWithLevel, "", Math.max(1, enchantmentWithLevel.enchantment_level()));
    }

    public static ItemStack itemCost(EnchantmentWithLevel enchantmentWithLevel, String salt) {
        return itemCost(enchantmentWithLevel, salt, Math.max(1, enchantmentWithLevel.enchantment_level()));
    }

    public static ItemStack itemCost(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
        List<ItemStack> costs = itemCosts(enchantmentWithLevel, salt, maxLevel);
        return costs.isEmpty() ? ItemStack.EMPTY : costs.getFirst();
    }

    public static List<ItemStack> itemCosts(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
        return costTables.itemCosts(enchantmentWithLevel, salt == null ? "" : salt, Math.max(1, maxLevel));
    }

    public static List<ItemStack> additionalItemCosts(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
        List<ItemStack> costs = itemCosts(enchantmentWithLevel, salt, maxLevel);
        return costs.size() <= 1 ? List.of() : List.copyOf(costs.subList(1, costs.size()));
    }

    public static int xpCost(EnchantmentWithLevel enchantmentWithLevel) {
        return xpCost(enchantmentWithLevel, Math.max(1, enchantmentWithLevel.enchantment_level()));
    }

    public static int xpCost(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
        return costTables.xpCost(enchantmentWithLevel, Math.max(1, maxLevel));
    }


    public static boolean bookCombineEnabled() {
        return bookUpgradeRules.combineEnabled();
    }

    public static boolean bookResearchEnabled() {
        return bookUpgradeRules.researchEnabled();
    }

    public static int bookCombineXpCost(EnchantmentWithLevel target, int maxLevel) {
        return bookUpgradeRules.combineXpCost(target, Math.max(1, maxLevel), costTables.xpCost(target, Math.max(1, maxLevel)));
    }

    public static int bookResearchXpCost(EnchantmentWithLevel target, int maxLevel) {
        return bookUpgradeRules.researchXpCost(target, Math.max(1, maxLevel), costTables.xpCost(target, Math.max(1, maxLevel)));
    }

    public static List<ItemStack> bookResearchCosts(EnchantmentWithLevel target, String salt, int maxLevel) {
        return bookUpgradeRules.researchCosts(target, salt == null ? "" : salt, Math.max(1, maxLevel), costTables.itemCosts(target, salt == null ? "" : salt, Math.max(1, maxLevel)));
    }

    public static ItemStack bookResearchPrimaryCost(EnchantmentWithLevel target, String salt, int maxLevel) {
        List<ItemStack> costs = bookResearchCosts(target, salt, maxLevel);
        return costs.isEmpty() ? ItemStack.EMPTY : costs.getFirst();
    }

    public static List<ItemStack> bookResearchAdditionalCosts(EnchantmentWithLevel target, String salt, int maxLevel) {
        List<ItemStack> costs = bookResearchCosts(target, salt, maxLevel);
        return costs.size() <= 1 ? List.of() : List.copyOf(costs.subList(1, costs.size()));
    }

    public static Set<EnchantmentWithLevel> transformationEnchantmentsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (ItemTransformation transformation : itemTransformations.values()) {
            if (transformation.matchesInput(input)) {
                result.add(new EnchantmentWithLevel(transformation.id(), transformation.level()));
            }
        }
        return result;
    }


    public static Optional<ItemTransformation> itemTransformation(Identifier id) {
        return Optional.ofNullable(itemTransformations.get(id));
    }

    /**
     * Compact display table (id, input item, output item, level) for every loaded transformation.
     * The screen handler syncs this to the client when the menu opens so client UI no longer has to
     * rely on its own local data load matching the server's (important for datapack transformations
     * on dedicated servers). Returns a list of {@code Object[]} {id, inputItemId, outputItemId, level}
     * to avoid a compile dependency from the data package onto the menu package.
     */
    public static List<Object[]> transformationDisplayTuples() {
        List<Object[]> result = new ArrayList<>();
        for (ItemTransformation transformation : itemTransformations.values()) {
            result.add(new Object[] {
                transformation.id(),
                transformation.inputItemId(),
                transformation.outputItemId(),
                transformation.level()
            });
        }
        return List.copyOf(result);
    }

    public static boolean isTransformation(Identifier id) {
        return itemTransformations.containsKey(id);
    }

    public static ItemStack transformationPrimaryCost(Identifier id, String salt) {
        ItemTransformation transformation = itemTransformations.get(id);
        return transformation == null ? ItemStack.EMPTY : transformation.primaryCostStack(salt == null ? "" : salt);
    }

    public static List<ItemStack> transformationAdditionalCosts(Identifier id, String salt) {
        ItemTransformation transformation = itemTransformations.get(id);
        return transformation == null ? List.of() : transformation.additionalCostStacks(salt == null ? "" : salt);
    }

    public static int transformationXpCost(Identifier id) {
        ItemTransformation transformation = itemTransformations.get(id);
        return transformation == null ? 0 : transformation.xpCost();
    }

    /**
     * Loot-table injection rules loaded from {@code loot_injections.json}.
     * <p>Consumed by {@code mechanics.loot.EnchantedBookLootInjector} at server start; the JSON is
     * the sole source of truth (Java owns dispatch, JSON owns which loot table gets which
     * enchanted book at what weight). Empty by default so an integrator can opt-in.</p>
     */
    public static List<LootInjection> lootInjections() {
        return lootInjections;
    }

    /**
     * Weighted 6-slot fill rules for chiseled bookshelves placed by
     * {@code structureProcessor.BookshelfReplacerProcessor}. Also loaded from JSON only.
     */
    public static BookshelfBookPool bookshelfBookPool() {
        return bookshelfBookPool;
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        DefaultEnchantmentTable loadedDefaults = loadDefaultEnchantments(resourceManager);
        defaultEnchantmentValues = loadedDefaults.values();
        defaultEnchantmentTags = loadedDefaults.tags();
        costTables = loadCostTables(resourceManager);
        itemTransformations = loadItemTransformations(resourceManager);
        lootInjections = loadLootInjections(resourceManager);
        bookshelfBookPool = loadBookshelfBookPool(resourceManager);
        bookUpgradeRules = loadBookUpgradeRules(resourceManager);
        ChiseledEnchantingTable.LOGGER.info(
            "Loaded chiseled enchanting data: {} default enchantments, {} default-unlock tag rules, {} item cost overrides, {} transformations, {} loot injections",
            defaultEnchantmentValues.size(),
            defaultEnchantmentTags.size(),
            costTables.itemCosts().size(),
            itemTransformations.size(),
            lootInjections.size()
        );
    }

    /**
     * Parses {@code default_enchantments.json}, which has two complementary entry kinds:
     * <ul>
     *   <li>{@code values}: explicit {@code {enchantment, level}} entries (works without registry
     *       access, e.g. before the world's dynamic registries are available).</li>
     *   <li>{@code tags}: {@code {tag, level}} entries (tag id like
     *       {@code "#minecraft:in_enchanting_table"} or {@code "minecraft:in_enchanting_table"})
     *       expanded against the live enchantment registry at lookup time via
     *       {@link #defaultEnchantments(HolderGetter)}. This is the cross-mod-compatible extension
     *       point: {@code in_enchanting_table} is the actual tag the vanilla enchanting table reads
     *       (its content defaults to {@code #minecraft:non_treasure}, but mods commonly append
     *       directly to {@code in_enchanting_table.json} rather than to {@code non_treasure.json},
     *       since the latter is conventionally treated as a vanilla-only list — confirmed against
     *       real mod jars in P20, see {@code docs/COMPATIBILITY.md}), so any enchantment mod that
     *       follows this convention is automatically included, with no per-enchantment id list to
     *       maintain.</li>
     * </ul>
     */
    private static DefaultEnchantmentTable loadDefaultEnchantments(ResourceManager resourceManager) {
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

            if (loadedValues.isEmpty() && loadedTags.isEmpty()) {
                return builtInDefaultEnchantmentTable();
            }
            return new DefaultEnchantmentTable(loadedValues, List.copyOf(loadedTags));
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in fallback defaults", DEFAULT_ENCHANTMENTS_ID, e);
            return builtInDefaultEnchantmentTable();
        }
    }

    /** Accepts both {@code "#namespace:path"} (data-pack tag convention) and a bare id. */
    private static String stripTagPrefix(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static CostTables loadCostTables(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(ENCHANTMENT_COSTS_ID);
        if (resource.isEmpty()) {
            ChiseledEnchantingTable.LOGGER.warn("Missing {}; using built-in fallback costs", ENCHANTMENT_COSTS_ID);
            return CostTables.builtInFallback();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return CostTables.fromJson(root);
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in fallback costs", ENCHANTMENT_COSTS_ID, e);
            return CostTables.builtInFallback();
        }
    }


    private static Map<Identifier, ItemTransformation> loadItemTransformations(ResourceManager resourceManager) {
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
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in fallback transformations", ITEM_TRANSFORMATIONS_ID, e);
            return builtInItemTransformations();
        }
    }

    private static Map<Identifier, ItemTransformation> builtInItemTransformations() {
        String json = """
            {
              "values": [
                { "id": "chiseled_enchanting_table:enchanted_golden_apple", "input": "minecraft:golden_apple", "output": "minecraft:enchanted_golden_apple", "level": 1, "xp_cost": 3, "primary_cost": { "item": "minecraft:gold_block", "count": 8 }, "additional_costs": [ { "item": "minecraft:emerald", "min_count": 2, "max_count": 5 } ] },
                { "id": "chiseled_enchanting_table:experience_bottle", "input": "minecraft:glass_bottle", "output": "minecraft:experience_bottle", "level": 1, "xp_cost": 1, "primary_cost": { "item": "minecraft:lapis_lazuli", "count": 6 }, "additional_costs": [ { "item": "minecraft:amethyst_shard", "count": 2 } ] }
              ]
            }
            """;
        Map<Identifier, ItemTransformation> fallback = new HashMap<>();
        JsonArray values = GSON.fromJson(json, JsonObject.class).getAsJsonArray("values");
        for (JsonElement element : values) {
            ItemTransformation transformation = ItemTransformation.fromJson(element.getAsJsonObject());
            fallback.put(transformation.id(), transformation);
        }
        return Map.copyOf(fallback);
    }

    private static Identifier parseIdentifier(String value) {
        return value.indexOf(':') >= 0 ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
    }

    private static Set<EnchantmentWithLevel> builtInDefaultEnchantments() {
        Set<EnchantmentWithLevel> fallback = new HashSet<>();
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("protection"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("projectile_protection"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("fire_protection"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("blast_protection"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("efficiency"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("sharpness"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("smite"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("bane_of_arthropods"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("power"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("density"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("breach"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("impaling"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("piercing"), 1));
        fallback.add(new EnchantmentWithLevel(Identifier.withDefaultNamespace("unbreaking"), 1));
        return fallback;
    }

    /** Built-in fallback tag rule: mirrors the actual tag the vanilla enchanting table reads (see
     * {@code in_enchanting_table} discussion above), so the fallback (used only if the JSON file is
     * missing/unparseable) still benefits from any enchantment mod that tags itself the standard
     * way. */
    private static List<DefaultEnchantmentTagRule> builtInDefaultEnchantmentTags() {
        return List.of(new DefaultEnchantmentTagRule(Identifier.withDefaultNamespace("in_enchanting_table"), 1));
    }

    private static DefaultEnchantmentTable builtInDefaultEnchantmentTable() {
        return new DefaultEnchantmentTable(builtInDefaultEnchantments(), builtInDefaultEnchantmentTags());
    }

    /** Parsed {@code default_enchantments.json} contents: explicit values plus tag-expansion rules. */
    private record DefaultEnchantmentTable(Set<EnchantmentWithLevel> values, List<DefaultEnchantmentTagRule> tags) {}

    /** One {@code tags} entry: every enchantment in {@code tagId} unlocks at {@code level}. */
    private record DefaultEnchantmentTagRule(Identifier tagId, int level) {}

    public record ItemTransformation(Identifier id, Identifier inputItemId, Identifier outputItemId, int level, int xpCost, ItemCostOption primaryCost, List<ItemCostOption> additionalCosts) {
        static ItemTransformation fromJson(JsonObject json) {
            Identifier id = parseIdentifier(json.get("id").getAsString());
            Identifier input = parseIdentifier(json.get("input").getAsString());
            Identifier output = parseIdentifier(json.get("output").getAsString());
            int level = intOr(json, "level", 1);
            int xpCost = intOr(json, "xp_cost", 0);
            ItemCostOption primaryCost = ItemCostOption.fromJson(json.getAsJsonObject("primary_cost"));
            List<ItemCostOption> additionalCosts = new ArrayList<>();
            if (json.has("additional_costs")) {
                for (JsonElement element : json.getAsJsonArray("additional_costs")) {
                    additionalCosts.add(ItemCostOption.fromJson(element.getAsJsonObject()));
                }
            }
            return new ItemTransformation(id, input, output, Math.max(1, level), Math.max(0, xpCost), primaryCost, List.copyOf(additionalCosts));
        }

        public boolean matchesInput(ItemStack stack) {
            return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(inputItemId);
        }

        public ItemStack outputStack(int count) {
            Item item = BuiltInRegistries.ITEM.getValue(outputItemId);
            if (item == null || item == Items.AIR) {
                ChiseledEnchantingTable.LOGGER.warn("Unknown transformation output {}; using apple fallback", outputItemId);
                item = Items.APPLE;
            }
            return new ItemStack(item, Math.max(1, count));
        }

        public ItemStack primaryCostStack(String salt) {
            return primaryCost.toSingleStack(id, level, level, salt);
        }

        public List<ItemStack> additionalCostStacks(String salt) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemCostOption option : additionalCosts) {
                stacks.add(option.toSingleStack(id, level, level, salt));
            }
            return List.copyOf(stacks);
        }
    }


    private record BookUpgradeRules(boolean combineEnabled, double combineXpMultiplier, boolean researchEnabled, double researchXpMultiplier, double researchMaterialMultiplier, ItemCostRule defaultResearchCost, Map<Identifier, ItemCostRule> researchOverrides) {
        static BookUpgradeRules fromJson(JsonObject root) {
            JsonObject combine = root.has("combine_route") ? root.getAsJsonObject("combine_route") : new JsonObject();
            JsonObject research = root.has("research_route") ? root.getAsJsonObject("research_route") : new JsonObject();
            boolean combineEnabled = boolOr(combine, "enabled", true);
            double combineXpMultiplier = doubleOr(combine, "xp_cost_multiplier_of_vanilla_like", 1.0D);
            boolean researchEnabled = boolOr(research, "enabled", true);
            double researchXpMultiplier = doubleOr(research, "xp_cost_multiplier_of_direct_enchant", 0.5D);
            double researchMaterialMultiplier = doubleOr(research, "material_cost_multiplier_of_direct_enchant", 0.5D);
            ItemCostRule defaultResearchCost = research.has("default_material_cost")
                ? ItemCostRule.fromJson(research.getAsJsonObject("default_material_cost"))
                : ItemCostRule.single(Identifier.withDefaultNamespace("lapis_lazuli"), 1, 1, 0, 0);
            Map<Identifier, ItemCostRule> overrides = new HashMap<>();
            if (research.has("enchantment_overrides")) {
                JsonObject entries = research.getAsJsonObject("enchantment_overrides");
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    JsonObject cost = value.has("material_cost") ? value.getAsJsonObject("material_cost") : value;
                    overrides.put(parseIdentifier(entry.getKey()), ItemCostRule.fromJson(cost));
                }
            }
            return new BookUpgradeRules(combineEnabled, combineXpMultiplier, researchEnabled, researchXpMultiplier, researchMaterialMultiplier, defaultResearchCost, Map.copyOf(overrides));
        }

        static BookUpgradeRules builtInFallback() {
            String json = """
                {
                  \"combine_route\": { \"enabled\": true, \"xp_cost_multiplier_of_vanilla_like\": 1.0 },
                  \"research_route\": {
                    \"enabled\": true,
                    \"xp_cost_multiplier_of_direct_enchant\": 0.5,
                    \"material_cost_multiplier_of_direct_enchant\": 0.5,
                    \"default_material_cost\": { \"item\": \"minecraft:lapis_lazuli\", \"count_per_level\": 1 },
                    \"enchantment_overrides\": {
                      \"minecraft:sharpness\": { \"material_cost\": { \"item\": \"minecraft:flint\", \"count_per_level\": 1 } },
                      \"minecraft:smite\": { \"material_cost\": { \"item\": \"minecraft:bone\", \"count_per_level\": 1 } },
                      \"minecraft:bane_of_arthropods\": { \"material_cost\": { \"item\": \"minecraft:spider_eye\", \"count_per_level\": 1 } },
                      \"minecraft:fire_aspect\": { \"material_cost\": { \"item\": \"minecraft:blaze_powder\", \"count_per_level\": 1 } },
                      \"minecraft:flame\": { \"material_cost\": { \"item\": \"minecraft:blaze_powder\", \"count_per_level\": 1 } },
                      \"minecraft:respiration\": { \"material_cost\": { \"item\": \"minecraft:pufferfish\", \"count_per_level\": 1 } },
                      \"minecraft:depth_strider\": { \"material_cost\": { \"item\": \"minecraft:prismarine_shard\", \"count_per_level\": 1 } },
                      \"minecraft:frost_walker\": { \"material_cost\": { \"item\": \"minecraft:blue_ice\", \"count\": 1 } },
                      \"minecraft:efficiency\": { \"material_cost\": { \"item\": \"minecraft:redstone\", \"count_per_level\": 1 } },
                      \"minecraft:unbreaking\": { \"material_cost\": { \"item\": \"minecraft:amethyst_shard\", \"count_per_level\": 1 } }
                    }
                  }
                }
                """;
            return fromJson(GSON.fromJson(json, JsonObject.class));
        }

        int combineXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int)Math.round(directXpCost * combineXpMultiplier));
        }

        int researchXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int)Math.floor(directXpCost * researchXpMultiplier));
        }

        List<ItemStack> researchCosts(EnchantmentWithLevel target, String salt, int maxLevel, List<ItemStack> directCosts) {
            ItemCostRule rule = researchOverrides.getOrDefault(target.enchantment_id(), defaultResearchCost);
            List<ItemStack> themed = rule.toStacks(target.enchantment_id(), target.enchantment_level(), maxLevel, salt);
            if (themed.isEmpty() && !directCosts.isEmpty()) {
                ItemStack discounted = directCosts.getFirst().copy();
                discounted.setCount(Math.max(1, (int)Math.floor(discounted.getCount() * researchMaterialMultiplier)));
                return List.of(discounted);
            }
            return themed;
        }
    }

    private record CostTables(XpCostRule xpCostRule, ItemCostRule defaultItemCost, Map<Identifier, ItemCostRule> itemCosts) {
        static CostTables fromJson(JsonObject root) {
            XpCostRule xpCostRule = root.has("xp") ? XpCostRule.fromJson(root.getAsJsonObject("xp")) : XpCostRule.defaults();
            ItemCostRule defaultItemCost = root.has("default_item_cost")
                ? ItemCostRule.fromJson(root.getAsJsonObject("default_item_cost"))
                : ItemCostRule.single(Identifier.withDefaultNamespace("lapis_lazuli"), 0, 2, 0, 0);

            Map<Identifier, ItemCostRule> itemCosts = new HashMap<>();
            if (root.has("item_costs")) {
                JsonObject costs = root.getAsJsonObject("item_costs");
                for (Map.Entry<String, JsonElement> entry : costs.entrySet()) {
                    itemCosts.put(parseIdentifier(entry.getKey()), ItemCostRule.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
            return new CostTables(xpCostRule, defaultItemCost, itemCosts);
        }

        static CostTables builtInFallback() {
            String json = """
                {
                  "xp": { "level_one_cost": 5, "common_max_cost": 25, "rare_single_level_cost": 35 },
                  "default_item_cost": { "options": [{ "item": "minecraft:lapis_lazuli", "count_per_level": 2 }] },
                  "item_costs": {}
                }
                """;
            return fromJson(GSON.fromJson(json, JsonObject.class));
        }

        List<ItemStack> itemCosts(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
            ItemCostRule itemCost = itemCosts.getOrDefault(enchantmentWithLevel.enchantment_id(), defaultItemCost);
            return itemCost.toStacks(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel, salt);
        }

        int xpCost(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
            return xpCostRule.cost(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel);
        }
    }

    private record XpCostRule(int levelOneCost, int commonMaxCost, int rareSingleLevelCost, Map<Identifier, Map<Integer, Integer>> overrides) {
        static XpCostRule defaults() {
            return new XpCostRule(5, 25, 35, Map.of());
        }

        static XpCostRule fromJson(JsonObject json) {
            int levelOneCost = intOr(json, "level_one_cost", 5);
            int commonMaxCost = intOr(json, "common_max_cost", 25);
            int rareSingleLevelCost = intOr(json, "rare_single_level_cost", 35);
            Map<Identifier, Map<Integer, Integer>> overrides = new HashMap<>();
            if (json.has("overrides")) {
                JsonObject overrideRoot = json.getAsJsonObject("overrides");
                for (Map.Entry<String, JsonElement> enchantmentEntry : overrideRoot.entrySet()) {
                    Map<Integer, Integer> byLevel = new HashMap<>();
                    JsonObject levels = enchantmentEntry.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> levelEntry : levels.entrySet()) {
                        byLevel.put(Integer.parseInt(levelEntry.getKey()), levelEntry.getValue().getAsInt());
                    }
                    overrides.put(parseIdentifier(enchantmentEntry.getKey()), Map.copyOf(byLevel));
                }
            }
            return new XpCostRule(levelOneCost, commonMaxCost, rareSingleLevelCost, Map.copyOf(overrides));
        }

        int cost(Identifier enchantmentId, int level, int maxLevel) {
            Map<Integer, Integer> byLevel = overrides.get(enchantmentId);
            if (byLevel != null && byLevel.containsKey(level)) {
                return byLevel.get(level);
            }
            int safeLevel = Math.max(1, level);
            int safeMaxLevel = Math.max(1, maxLevel);
            if (safeMaxLevel == 1) {
                return rareSingleLevelCost;
            }
            if (safeLevel <= 1) {
                return levelOneCost;
            }
            double progress = (double)(safeLevel - 1) / (double)(safeMaxLevel - 1);
            return Math.max(levelOneCost, (int)Math.round(levelOneCost + progress * (commonMaxCost - levelOneCost)));
        }
    }

    private record ItemCostRule(List<ItemCostOption> options, Map<Integer, ItemCostRule> levelOverrides, ItemCostRule maxLevelOverride) {
        static ItemCostRule single(Identifier itemId, int count, int countPerLevel, int minCount, int maxCount) {
            return new ItemCostRule(List.of(new ItemCostOption(itemId, count, countPerLevel, minCount, maxCount, List.of())), Map.of(), null);
        }

        static ItemCostRule fromJson(JsonObject json) {
            Map<Integer, ItemCostRule> levelOverrides = new HashMap<>();
            if (json.has("level_overrides")) {
                JsonObject overrides = json.getAsJsonObject("level_overrides");
                for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
                    levelOverrides.put(Integer.parseInt(entry.getKey()), fromJson(entry.getValue().getAsJsonObject()));
                }
            }
            ItemCostRule maxLevelOverride = json.has("max_level") ? fromJson(json.getAsJsonObject("max_level")) : null;

            List<ItemCostOption> options = new ArrayList<>();
            if (json.has("options")) {
                JsonArray optionArray = json.getAsJsonArray("options");
                for (JsonElement option : optionArray) {
                    options.add(ItemCostOption.fromJson(option.getAsJsonObject()));
                }
            } else if (json.has("item")) {
                options.add(ItemCostOption.fromJson(json));
            }

            if (options.isEmpty()) {
                options.add(new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 0, 2, 0, 0, List.of()));
            }
            return new ItemCostRule(List.copyOf(options), Map.copyOf(levelOverrides), maxLevelOverride);
        }

        List<ItemStack> toStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            ItemCostRule override = levelOverrides.get(level);
            if (override != null) {
                return override.toStacks(enchantmentId, level, maxLevel, salt);
            }
            if (level >= maxLevel && maxLevelOverride != null) {
                return maxLevelOverride.toStacks(enchantmentId, level, maxLevel, salt);
            }
            int index = stablePositiveHash(enchantmentId + ":" + level + ":" + maxLevel + ":" + salt + ":option") % options.size();
            return options.get(index).toStacks(enchantmentId, level, maxLevel, salt);
        }
    }

    public record ItemCostOption(Identifier itemId, int count, int countPerLevel, int minCount, int maxCount, List<ItemCostOption> extras) {
        static ItemCostOption fromJson(JsonObject json) {
            Identifier itemId = parseIdentifier(json.get("item").getAsString());
            int count = intOr(json, "count", 0);
            int countPerLevel = intOr(json, "count_per_level", 0);
            int minCount = intOr(json, "min_count", 0);
            int maxCount = intOr(json, "max_count", 0);
            List<ItemCostOption> extras = new ArrayList<>();
            if (json.has("extra")) {
                extras.add(fromJson(json.getAsJsonObject("extra")));
            }
            if (json.has("extras")) {
                JsonArray extraArray = json.getAsJsonArray("extras");
                for (JsonElement extra : extraArray) {
                    extras.add(fromJson(extra.getAsJsonObject()));
                }
            }
            return new ItemCostOption(itemId, count, countPerLevel, minCount, maxCount, List.copyOf(extras));
        }

        List<ItemStack> toStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<ItemStack> stacks = new ArrayList<>();
            stacks.add(toSingleStack(enchantmentId, level, maxLevel, salt));
            for (ItemCostOption extra : extras) {
                stacks.add(extra.toSingleStack(enchantmentId, level, maxLevel, salt));
            }
            return List.copyOf(stacks);
        }

        ItemStack toSingleStack(Identifier enchantmentId, int level, int maxLevel, String salt) {
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item == null || item == Items.AIR) {
                ChiseledEnchantingTable.LOGGER.warn("Unknown item cost id {}; using lapis_lazuli fallback", itemId);
                item = Items.LAPIS_LAZULI;
            }
            int computedCount;
            if (count > 0) {
                computedCount = count;
            } else if (minCount > 0 && maxCount >= minCount) {
                int spread = maxCount - minCount + 1;
                computedCount = minCount + (stablePositiveHash(enchantmentId + ":" + level + ":" + maxLevel + ":" + salt + ":count:" + itemId) % spread);
            } else {
                computedCount = countPerLevel * Math.max(1, level);
            }
            if (countPerLevel > 0 && (count > 0 || minCount > 0)) {
                computedCount += countPerLevel * Math.max(1, level);
            }
            return new ItemStack(item, Math.max(1, computedCount));
        }
    }

    // ---------- U1: loot injections + bookshelf book pool ----------

    private static List<LootInjection> loadLootInjections(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(LOOT_INJECTIONS_ID);
        if (resource.isEmpty()) {
            return List.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
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
            return List.copyOf(loaded);
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; loot injection disabled", LOOT_INJECTIONS_ID, e);
            return List.of();
        }
    }


    private static BookUpgradeRules loadBookUpgradeRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(BOOK_UPGRADES_ID);
        if (resource.isEmpty()) {
            return BookUpgradeRules.builtInFallback();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return BookUpgradeRules.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in fallback book-upgrade rules", BOOK_UPGRADES_ID, e);
            return BookUpgradeRules.builtInFallback();
        }
    }

    private static BookshelfBookPool loadBookshelfBookPool(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(BOOKSHELF_BOOK_POOL_ID);
        if (resource.isEmpty()) {
            return BookshelfBookPool.emptyFallback();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return BookshelfBookPool.fromJson(root);
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; bookshelf book pool disabled", BOOKSHELF_BOOK_POOL_ID, e);
            return BookshelfBookPool.emptyFallback();
        }
    }

    /**
     * One entry in {@code loot_injections.json}: append a new pool to the given loot table (or, if
     * the id starts with {@code #}, to every table whose id matches that tag). Each entry rolls
     * {@code rolls} times and picks one of {@code entries} by weight, producing an enchanted book
     * with the specified enchantment/level and a purple "Chiseled Enchanting Table" lore stamp so
     * players can trace injected books back to this mod.
     */
    public record LootInjection(String tableSelector, int rolls, List<LootInjectionEntry> entries) {
        static LootInjection fromJson(JsonObject json) {
            String table = json.get("table").getAsString();
            int rolls = Math.max(1, intOr(json, "rolls", 1));
            List<LootInjectionEntry> loadedEntries = new ArrayList<>();
            JsonArray array = json.getAsJsonArray("entries");
            if (array != null) {
                for (JsonElement element : array) {
                    LootInjectionEntry entry = LootInjectionEntry.fromJson(element.getAsJsonObject());
                    if (entry != null) {
                        loadedEntries.add(entry);
                    }
                }
            }
            if (loadedEntries.isEmpty()) {
                return null;
            }
            return new LootInjection(table, rolls, List.copyOf(loadedEntries));
        }

        public boolean isTag() {
            return tableSelector != null && !tableSelector.isEmpty() && tableSelector.charAt(0) == '#';
        }

        public Identifier tableId() {
            return parseIdentifier(isTag() ? tableSelector.substring(1) : tableSelector);
        }
    }

    public record LootInjectionEntry(Identifier enchantmentId, int level, int weight, boolean empty, String special) {
        static LootInjectionEntry fromJson(JsonObject json) {
            int weight = Math.max(1, intOr(json, "weight", 1));
            // Empty filler entry (upstream's EmptyEntry.builder().weight(N)): keeps the pool total
            // roll-weight identical to upstream even when we don't want to drop a book.
            if (json.has("empty") && json.get("empty").getAsBoolean()) {
                return new LootInjectionEntry(null, 0, weight, true, null);
            }
            if (!json.has("enchantment")) return null;
            Identifier enchantmentId = parseIdentifier(json.get("enchantment").getAsString());
            int level = Math.max(1, intOr(json, "level", 1));
            String special = json.has("special") && !json.get("special").isJsonNull()
                ? json.get("special").getAsString() : null;
            return new LootInjectionEntry(enchantmentId, level, weight, false, special);
        }
    }

    /**
     * Weighted structure-bookshelf fill rules parsed from {@code bookshelf_book_pool.json}
     * (schema v2 — the earlier per-content-type weight table is retired; v2 uses a book-count
     * range + a per-book enchanted-book probability + a data-driven enchantment weighting).
     *
     * <p>Each generated shelf picks {@code N ∈ [minBooks, maxBooks]} books, places them in
     * {@code N} random slots out of 6, and for each book rolls
     * {@code enchantedBookProbability} to decide whether it's a plain book or an enchanted one.
     * Enchanted books are weighted by
     * {@code baseWeight × vanillaWeightFactor(getWeight()) × maxLevelWeightRatio^relativeLevel}
     * where {@code relativeLevel = (level-1)/(enchantmentMaxLevel-1)} — so a book at its
     * enchantment's max level always gets the same {@code ratio^1 = ratio} penalty regardless
     * of the numeric level, and single-level enchantments (Mending, Silk Touch) are never
     * level-penalised at all.</p>
     */
    public record BookshelfBookPool(
        int minBooks, int maxBooks,
        double enchantedBookProbability,
        String enchantPoolSource,
        boolean useBaseWeight,
        boolean useVanillaWeightFactor,
        double maxLevelWeightRatio,
        List<List<LootInjectionEntry>> enchantSets
    ) {
        static BookshelfBookPool emptyFallback() {
            return new BookshelfBookPool(3, 5, 0.03, "loot_injections", true, true, 0.1, List.of());
        }

        static BookshelfBookPool fromJson(JsonObject root) {
            JsonObject bookCount = root.has("book_count") ? root.getAsJsonObject("book_count") : new JsonObject();
            int minBooks = Math.max(0, intOr(bookCount, "min", 3));
            int maxBooks = Math.max(minBooks, intOr(bookCount, "max", 5));

            double enchantedProb = root.has("enchanted_book_probability")
                ? Math.max(0.0, Math.min(1.0, root.get("enchanted_book_probability").getAsDouble()))
                : 0.03;

            JsonObject sel = root.has("enchanted_book_selection")
                ? root.getAsJsonObject("enchanted_book_selection") : new JsonObject();
            String source = sel.has("enchant_pool_source") ? sel.get("enchant_pool_source").getAsString() : "loot_injections";
            boolean useBase = !sel.has("use_base_weight") || sel.get("use_base_weight").getAsBoolean();
            boolean useVanilla = !sel.has("use_vanilla_weight_factor") || sel.get("use_vanilla_weight_factor").getAsBoolean();
            // max_level_weight_ratio replaces the older level_falloff field. Accept both spellings
            // during the transition so a stale data pack doesn't silently revert to the fallback
            // default; the JSON schema check in scripts/compat_check.py enforces the new name.
            double maxLevelRatio;
            if (sel.has("max_level_weight_ratio")) {
                maxLevelRatio = sel.get("max_level_weight_ratio").getAsDouble();
            } else if (sel.has("level_falloff")) {
                // Old semantics: level_falloff was applied per numeric level (levelFalloff^level).
                // We map it approximately to the new "at max level" semantics by treating a
                // typical max level of ~4 (blast protection tier). Not exact, but keeps existing
                // data packs from getting silently reset to defaults; new packs should use the
                // new field name.
                maxLevelRatio = Math.pow(sel.get("level_falloff").getAsDouble(), 4);
            } else {
                maxLevelRatio = 0.1;
            }
            // Clamp to (0, 1]: 0 would zero-out max-level books entirely, >1 would make them
            // more likely than level-1 books, neither of which is what "ratio" is meant to mean.
            maxLevelRatio = Math.max(1e-6, Math.min(1.0, maxLevelRatio));

            List<List<LootInjectionEntry>> sets = new ArrayList<>();
            if (root.has("enchant_sets")) {
                for (JsonElement setElement : root.getAsJsonArray("enchant_sets")) {
                    List<LootInjectionEntry> set = new ArrayList<>();
                    for (JsonElement bookElement : setElement.getAsJsonArray()) {
                        LootInjectionEntry entry = LootInjectionEntry.fromJson(bookElement.getAsJsonObject());
                        if (entry != null) set.add(entry);
                    }
                    if (!set.isEmpty()) sets.add(List.copyOf(set));
                }
            }

            return new BookshelfBookPool(
                minBooks, maxBooks, enchantedProb,
                source, useBase, useVanilla, maxLevelRatio,
                List.copyOf(sets)
            );
        }
    }

    private static int stablePositiveHash(String value) {
        int hash = value.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    private static boolean boolOr(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static double doubleOr(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
