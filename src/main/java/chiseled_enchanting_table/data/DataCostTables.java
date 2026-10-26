package chiseled_enchanting_table.data;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ItemCostOption;
import chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance;
import chiseled_enchanting_table.registry.BlockRegistry;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Cohesive pricing-table domain records and JSON parsers extracted from
 * {@link ChiseledEnchantingTableData}: direct XP/material cost tables, book-upgrade rules,
 * Grand Library Treasure Epiphany rules, and disenchanting/strip/curse-purge rules.
 */
public final class DataCostTables {
    private DataCostTables() {}

    static Identifier parseIdentifier(String value) {
        return value.indexOf(':') >= 0 ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
    }

    static String stripTagPrefix(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '#' ? value.substring(1) : value;
    }

    static int stablePositiveHash(String value) {
        int hash = value.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    static boolean boolOr(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    static double doubleOr(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    public record XpCostRule(
        int levelOneCost,
        int commonMaxCost,
        int rareSingleLevelCost,
        Map<Identifier, Map<Integer, Integer>> overrides
    ) {
        public static XpCostRule defaults() {
            return new XpCostRule(2, 10, 12, Map.of());
        }

        public static XpCostRule fromJson(JsonObject json) {
            int levelOneCost = intOr(json, "level_one_cost", 2);
            int commonMaxCost = intOr(json, "common_max_cost", 10);
            int rareSingleLevelCost = intOr(json, "rare_single_level_cost", 12);
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

        public int cost(Identifier enchantmentId, int level, int maxLevel) {
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
            double progress = (double) (safeLevel - 1) / (double) (safeMaxLevel - 1);
            return Math.max(levelOneCost, (int) Math.round(levelOneCost + progress * (commonMaxCost - levelOneCost)));
        }
    }

    public record ItemCostRule(
        List<ItemCostOption> options,
        Map<Integer, ItemCostRule> levelOverrides,
        ItemCostRule maxLevelOverride,
        boolean materialFree
    ) {
        public ItemCostRule(List<ItemCostOption> options, Map<Integer, ItemCostRule> levelOverrides, ItemCostRule maxLevelOverride) {
            this(options, levelOverrides, maxLevelOverride, false);
        }

        public static ItemCostRule single(Identifier itemId, int count, int countPerLevel, int minCount, int maxCount) {
            return new ItemCostRule(
                List.of(new ItemCostOption(itemId, count, countPerLevel, minCount, maxCount, 0, 0, 1.0, List.of())),
                Map.of(),
                null
            );
        }

        public static ItemCostRule fromJson(JsonObject json) {
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
            } else if (json.has("cost_options")) {
                JsonArray optionArray = json.getAsJsonArray("cost_options");
                for (JsonElement option : optionArray) {
                    options.add(ItemCostOption.fromJson(option.getAsJsonObject()));
                }
            } else if (json.has("item")) {
                options.add(ItemCostOption.fromJson(json));
            }

            boolean materialFree = boolOr(json, "material_free", boolOr(json, "xp_only", false));
            if (options.isEmpty() && !materialFree) {
                options.add(new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 0, 0, 0, 0, 3, 20, 1.3, List.of()));
            }
            return new ItemCostRule(List.copyOf(options), Map.copyOf(levelOverrides), maxLevelOverride, materialFree && options.isEmpty());
        }

        public List<ItemCostOption> rawOptionsForLevel(int level, int maxLevel) {
            ItemCostRule override = levelOverrides.get(level);
            if (override != null) {
                return override.rawOptionsForLevel(level, maxLevel);
            }
            if (level >= maxLevel && maxLevelOverride != null) {
                return maxLevelOverride.rawOptionsForLevel(level, maxLevel);
            }
            return options;
        }

        public List<List<ItemStack>> toBundles(Identifier enchantmentId, int level, int maxLevel) {
            ItemCostRule override = levelOverrides.get(level);
            if (override != null) {
                return override.toBundles(enchantmentId, level, maxLevel);
            }
            if (level >= maxLevel && maxLevelOverride != null) {
                return maxLevelOverride.toBundles(enchantmentId, level, maxLevel);
            }
            if (materialFree) {
                return List.of(List.of());
            }
            List<List<ItemStack>> bundles = new ArrayList<>(options.size());
            for (ItemCostOption opt : options) {
                bundles.addAll(opt.expandBundles(enchantmentId, level, maxLevel, ""));
            }
            return List.copyOf(bundles);
        }

        public List<ItemStack> saltedStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            ItemCostRule override = levelOverrides.get(level);
            if (override != null) {
                return override.saltedStacks(enchantmentId, level, maxLevel, salt);
            }
            if (level >= maxLevel && maxLevelOverride != null) {
                return maxLevelOverride.saltedStacks(enchantmentId, level, maxLevel, salt);
            }
            if (materialFree || options.isEmpty()) {
                return List.of();
            }
            int index = stablePositiveHash(
                enchantmentId + ":" + Math.max(1, level) + ":" + Math.max(1, maxLevel) + ":" + salt + ":option"
            ) % options.size();
            return options.get(index).saltedStacks(enchantmentId, level, maxLevel, salt);
        }
    }

    public record CostTables(
        XpCostRule xpCostRule,
        ItemCostRule defaultItemCost,
        Map<Identifier, ItemCostRule> itemCosts
    ) {
        public static CostTables fromJson(JsonObject root) {
            XpCostRule xpCostRule = root.has("xp") ? XpCostRule.fromJson(root.getAsJsonObject("xp")) : XpCostRule.defaults();
            ItemCostRule defaultItemCost = root.has("default_item_cost")
                ? ItemCostRule.fromJson(root.getAsJsonObject("default_item_cost"))
                : ItemCostRule.single(Identifier.withDefaultNamespace("lapis_lazuli"), 0, 1, 0, 0);

            Map<Identifier, ItemCostRule> itemCosts = new HashMap<>();
            if (root.has("item_costs")) {
                JsonObject costs = root.getAsJsonObject("item_costs");
                for (Map.Entry<String, JsonElement> entry : costs.entrySet()) {
                    itemCosts.put(parseIdentifier(entry.getKey()), ItemCostRule.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
            return new CostTables(xpCostRule, defaultItemCost, itemCosts);
        }

        public List<List<ItemStack>> itemCostOptions(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
            ItemCostRule itemCost = itemCosts.getOrDefault(enchantmentWithLevel.enchantment_id(), defaultItemCost);
            return itemCost.toBundles(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel);
        }

        public int xpCost(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
            return xpCostRule.cost(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel);
        }

        public List<ItemStack> saltedCosts(EnchantmentWithLevel enchantmentWithLevel, int maxLevel, String salt) {
            ItemCostRule itemCost = itemCosts.getOrDefault(enchantmentWithLevel.enchantment_id(), defaultItemCost);
            return itemCost.saltedStacks(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel, salt);
        }
    }

    public record BookUpgradeRules(
        boolean combineEnabled,
        double combineXpMultiplier,
        boolean researchEnabled,
        double researchXpMultiplier,
        double researchMaterialMultiplier,
        ItemCostRule defaultResearchCost,
        Map<Identifier, ItemCostRule> researchOverrides
    ) {
        public static BookUpgradeRules fromJson(JsonObject root) {
            JsonObject combine = root.has("combine_route") ? root.getAsJsonObject("combine_route") : new JsonObject();
            JsonObject research = root.has("research_route") ? root.getAsJsonObject("research_route") : new JsonObject();
            boolean combineEnabled = boolOr(combine, "enabled", true);
            double combineXpMultiplier = doubleOr(combine, "xp_cost_multiplier_of_vanilla_like", 1.0D);
            boolean researchEnabled = boolOr(research, "enabled", true);
            double researchXpMultiplier = doubleOr(research, "xp_cost_multiplier_of_direct_enchant", 0.5D);
            double researchMaterialMultiplier = doubleOr(research, "material_cost_multiplier_of_direct_enchant", 0.5D);
            ItemCostRule defaultResearchCost = research.has("default_material_cost")
                ? ItemCostRule.fromJson(research.getAsJsonObject("default_material_cost"))
                : null;
            Map<Identifier, ItemCostRule> overrides = new HashMap<>();
            if (research.has("enchantment_overrides")) {
                JsonObject entries = research.getAsJsonObject("enchantment_overrides");
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    JsonObject cost = value.has("material_cost") ? value.getAsJsonObject("material_cost") : value;
                    overrides.put(parseIdentifier(entry.getKey()), ItemCostRule.fromJson(cost));
                }
            }
            return new BookUpgradeRules(
                combineEnabled, combineXpMultiplier, researchEnabled, researchXpMultiplier,
                researchMaterialMultiplier, defaultResearchCost, Map.copyOf(overrides)
            );
        }

        public int combineXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int) Math.round(directXpCost * combineXpMultiplier));
        }

        public int researchXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int) Math.round(directXpCost * researchXpMultiplier));
        }

        public int classicResearchXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int) Math.floor(directXpCost * researchXpMultiplier));
        }

        public List<ItemStack> saltedResearchCosts(
            EnchantmentWithLevel target,
            int maxLevel,
            String salt,
            List<ItemStack> directSaltedCosts
        ) {
            ItemCostRule rule = researchOverrides.getOrDefault(target.enchantment_id(), defaultResearchCost);
            List<ItemStack> themed = rule == null
                ? List.of()
                : rule.saltedStacks(target.enchantment_id(), target.enchantment_level(), maxLevel, salt);
            if (!themed.isEmpty()) {
                return themed;
            }
            if (!directSaltedCosts.isEmpty()) {
                ItemStack discounted = directSaltedCosts.getFirst().copy();
                discounted.setCount(Math.max(1, (int) Math.floor(discounted.getCount() * researchMaterialMultiplier)));
                return List.of(discounted);
            }
            return List.of();
        }

        public List<List<ItemStack>> researchCostOptions(
            EnchantmentWithLevel target,
            int maxLevel,
            List<List<ItemStack>> directCostOptions
        ) {
            ItemCostRule rule = researchOverrides.getOrDefault(target.enchantment_id(), defaultResearchCost);
            List<List<ItemStack>> themed = rule == null
                ? List.of()
                : rule.toBundles(target.enchantment_id(), target.enchantment_level(), maxLevel);
            if (!themed.isEmpty()) {
                return themed;
            }
            if (!directCostOptions.isEmpty()) {
                List<List<ItemStack>> discountedBundles = new ArrayList<>(directCostOptions.size());
                for (List<ItemStack> direct : directCostOptions) {
                    List<ItemStack> discounted = new ArrayList<>(direct.size());
                    for (ItemStack stack : direct) {
                        ItemStack copy = stack.copy();
                        copy.setCount(Math.max(1, (int) Math.round(copy.getCount() * researchMaterialMultiplier)));
                        discounted.add(copy);
                    }
                    discountedBundles.add(List.copyOf(discounted));
                }
                return List.copyOf(discountedBundles);
            }
            return List.of();
        }
    }

    public record TreasureEpiphanyEntry(int xpLevels, ItemCostRule materialCost) {}

    public record TreasureEpiphanyRules(
        int minRegularBookshelves,
        double baseChance,
        double chancePerExtraBookshelf,
        double chancePerMasterTome,
        double maxChance,
        int maxSlotsCap,
        int scorePerExtraSlot,
        int cycleTicks,
        int freezeDurationTicks,
        int maxFreezeTicks,
        int freezeXpLevels,
        List<ItemCostOption> freezeOptions,
        int rerollXpLevels,
        List<ItemCostOption> rerollOptions,
        Set<Identifier> explicitTreasureIds,
        List<Identifier> treasureTagIds,
        List<Identifier> excludedTagIds,
        int defaultXpLevels,
        ItemCostRule defaultMaterialCost,
        Map<Identifier, TreasureEpiphanyEntry> entries
    ) {
        public static TreasureEpiphanyRules fromJson(JsonObject root, Set<Identifier> seedExcludedIds) {
            int minShelves = intOr(root, "min_regular_bookshelves", 32);
            double base = root.has("base_chance") ? root.get("base_chance").getAsDouble() : 0.30;
            double perExtra = root.has("chance_per_extra_bookshelf") ? root.get("chance_per_extra_bookshelf").getAsDouble() : 0.01;
            double perTome = root.has("chance_per_master_tome") ? root.get("chance_per_master_tome").getAsDouble() : 0.02;
            double cap = root.has("max_chance") ? root.get("max_chance").getAsDouble() : 0.85;
            int maxSlots = Math.max(1, intOr(root, "max_slots_cap", 4));
            int scoreStep = Math.max(1, intOr(root, "score_per_extra_slot", 12));
            int cycTicks = Math.max(200, intOr(root, "cycle_ticks", 12000));
            int frzTicks = Math.max(200, intOr(root, "freeze_duration_ticks", 24000));
            int maxFrzTicks = Math.max(frzTicks, intOr(root, "max_freeze_ticks", 72000));
            int frzXp = Math.max(0, intOr(root, "freeze_xp_levels", 1));
            List<ItemCostOption> frzOpts = new ArrayList<>();
            if (root.has("freeze_cost_options")) {
                for (JsonElement el : root.getAsJsonArray("freeze_cost_options")) {
                    frzOpts.add(ItemCostOption.fromJson(el.getAsJsonObject()));
                }
            }
            if (frzOpts.isEmpty()) {
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("packed_ice"), 16, 0, 0, 0, 0, 0, 1.0, List.of()));
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("blue_ice"), 4, 0, 0, 0, 0, 0, 1.0, List.of()));
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 16, 0, 0, 0, 0, 0, 1.0, List.of()));
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("emerald"), 14, 0, 0, 0, 0, 0, 1.0, List.of()));
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("amethyst_shard"), 6, 0, 0, 0, 0, 0, 1.0, List.of()));
                frzOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("crying_obsidian"), 2, 0, 0, 0, 0, 0, 1.0, List.of()));
            }
            int rerollXp = intOr(root, "reroll_xp_levels", 2);
            List<ItemCostOption> rerollOpts = new ArrayList<>();
            if (root.has("reroll_cost_options")) {
                for (JsonElement el : root.getAsJsonArray("reroll_cost_options")) {
                    rerollOpts.add(ItemCostOption.fromJson(el.getAsJsonObject()));
                }
            }
            if (rerollOpts.isEmpty()) {
                rerollOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 10, 0, 0, 0, 0, 0, 1.0, List.of()));
                rerollOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("emerald"), 8, 0, 0, 0, 0, 0, 1.0, List.of()));
                rerollOpts.add(new ItemCostOption(Identifier.withDefaultNamespace("amethyst_shard"), 3, 0, 0, 0, 0, 0, 1.0, List.of()));
            }
            Set<Identifier> explicitIds = new HashSet<>(seedExcludedIds);
            if (root.has("treasure_enchantments")) {
                for (JsonElement el : root.getAsJsonArray("treasure_enchantments")) {
                    explicitIds.add(parseIdentifier(el.getAsString()));
                }
            }
            List<Identifier> tTags = new ArrayList<>();
            if (root.has("treasure_tags")) {
                for (JsonElement el : root.getAsJsonArray("treasure_tags")) {
                    tTags.add(parseIdentifier(stripTagPrefix(el.getAsString())));
                }
            } else {
                tTags.add(Identifier.withDefaultNamespace("treasure"));
                tTags.add(ChiseledEnchantingTable.identifier("epiphany_exclusive"));
            }
            List<Identifier> exTags = new ArrayList<>();
            if (root.has("exclude_tags")) {
                for (JsonElement el : root.getAsJsonArray("exclude_tags")) {
                    exTags.add(parseIdentifier(stripTagPrefix(el.getAsString())));
                }
            } else {
                exTags.add(Identifier.withDefaultNamespace("curse"));
            }
            int defaultXp = intOr(root, "default_xp_levels", 25);
            ItemCostRule defaultCost = root.has("default_material_cost")
                ? ItemCostRule.fromJson(root.getAsJsonObject("default_material_cost"))
                : ItemCostRule.single(Identifier.withDefaultNamespace("diamond"), 8, 0, 0, 0);
            Map<Identifier, TreasureEpiphanyEntry> map = new HashMap<>();
            if (root.has("treasure_costs")) {
                JsonObject obj = root.getAsJsonObject("treasure_costs");
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    JsonObject val = e.getValue().getAsJsonObject();
                    int xp = intOr(val, "xp_levels", defaultXp);
                    ItemCostRule mat = val.has("material_cost") ? ItemCostRule.fromJson(val.getAsJsonObject("material_cost")) : defaultCost;
                    Identifier id = parseIdentifier(e.getKey());
                    map.put(id, new TreasureEpiphanyEntry(xp, mat));
                }
            }
            return new TreasureEpiphanyRules(
                Math.max(1, minShelves), base, perExtra, perTome, cap,
                maxSlots, scoreStep, cycTicks, frzTicks, maxFrzTicks, frzXp,
                List.copyOf(frzOpts), Math.max(0, rerollXp), List.copyOf(rerollOpts),
                Set.copyOf(explicitIds), List.copyOf(tTags), List.copyOf(exTags),
                defaultXp, defaultCost, Map.copyOf(map)
            );
        }

        public double chanceFor(int regularBookshelves, int chiseledBooks) {
            if (regularBookshelves < minRegularBookshelves) return 0.0;
            double raw = baseChance
                + (regularBookshelves - minRegularBookshelves) * chancePerExtraBookshelf
                + Math.max(0, chiseledBooks) * chancePerMasterTome;
            return Math.max(0.0, Math.min(maxChance, raw));
        }

        public int maxSlotsFor(int regularBookshelves, int chiseledBooks) {
            if (regularBookshelves < minRegularBookshelves) return 0;
            int score = Math.max(0, regularBookshelves - minRegularBookshelves) + Math.max(0, chiseledBooks) * 2;
            return Math.min(maxSlotsCap, 1 + score / scorePerExtraSlot);
        }

        public List<ItemStack> freezeStacks() {
            Identifier dummy = Identifier.withDefaultNamespace("mending");
            List<ItemStack> out = new ArrayList<>(freezeOptions.size());
            for (ItemCostOption opt : freezeOptions) {
                out.add(opt.toSingleStack(dummy, 1, 1, ""));
            }
            return List.copyOf(out);
        }

        public List<ItemStack> rerollStacks() {
            Identifier dummy = Identifier.withDefaultNamespace("mending");
            List<ItemStack> out = new ArrayList<>(rerollOptions.size());
            for (ItemCostOption opt : rerollOptions) {
                out.add(opt.toSingleStack(dummy, 1, 1, ""));
            }
            return List.copyOf(out);
        }

        private static double tierMultiplier(int level, int maxLevel) {
            int safeMax = Math.max(1, maxLevel);
            int safeLvl = Math.max(1, Math.min(safeMax, level));
            if (safeMax <= 1 || safeLvl <= 1) {
                return 1.0;
            }
            double u = (double) (safeLvl - 1) / (double) (safeMax - 1);
            return 1.0 + 0.35 * u;
        }

        public int xpCost(Identifier id, int level, int maxLevel) {
            TreasureEpiphanyEntry e = entries.get(id);
            int base = e != null ? e.xpLevels() : defaultXpLevels;
            return Math.max(1, (int) Math.round(base * tierMultiplier(level, maxLevel)));
        }

        public List<List<ItemStack>> costOptions(Identifier id, int level, int maxLevel) {
            TreasureEpiphanyEntry e = entries.get(id);
            ItemCostRule rule = e != null ? e.materialCost() : defaultMaterialCost;
            List<List<ItemStack>> baseBundles = rule.toBundles(id, level, maxLevel);
            double mult = tierMultiplier(level, maxLevel);
            if (mult <= 1.0001) {
                return baseBundles;
            }
            List<List<ItemStack>> scaled = new ArrayList<>(baseBundles.size());
            for (List<ItemStack> bundle : baseBundles) {
                List<ItemStack> next = new ArrayList<>(bundle.size());
                for (ItemStack stack : bundle) {
                    if (stack.is(BlockRegistry.ARCANE_CATALYST)) {
                        next.add(stack.copy());
                    } else {
                        ItemStack copy = stack.copy();
                        copy.setCount(Math.min(64, Math.max(1, (int) Math.round(stack.getCount() * mult))));
                        next.add(copy);
                    }
                }
                scaled.add(List.copyOf(next));
            }
            return List.copyOf(scaled);
        }
    }

    public record DisenchantingRules(
        boolean enabled,
        boolean allowCursePurge,
        double xpRefundScale,
        double externalXpRefundScale,
        int stripXpMin,
        int stripXpMax,
        ItemCostRule stripMaterialCost,
        int curseXpCost,
        ItemCostRule curseMaterialCost
    ) {
        public static DisenchantingRules fromJson(JsonObject root, Supplier<JsonObject> bundledFallbackSupplier) {
            boolean enabled = boolOr(root, "enabled", true);
            boolean allowCurse = boolOr(root, "allow_curse_purge", true);
            double scale = Math.max(0.0, Math.min(1.0, doubleOr(root, "xp_refund_scale", 1.0)));
            double extScale = Math.max(
                0.0,
                Math.min(
                    scale,
                    doubleOr(root, "external_xp_refund_scale", EnchantmentProvenance.DEFAULT_EXTERNAL_XP_REFUND_SCALE)
                )
            );

            JsonObject bundled = null;
            JsonObject stripObj = root.has("targeted_strip") ? root.getAsJsonObject("targeted_strip") : new JsonObject();
            int stripMin = Math.max(1, intOr(stripObj, "xp_cost_min", intOr(stripObj, "xp_levels", 3)));
            int stripMax = Math.max(stripMin, intOr(stripObj, "xp_cost_max", Math.max(stripMin, 10)));
            ItemCostRule stripCost;
            if (stripObj.has("options") || stripObj.has("cost_options")) {
                stripCost = ItemCostRule.fromJson(stripObj);
            } else {
                bundled = bundledFallbackSupplier.get();
                JsonObject bundledStrip = bundled.has("targeted_strip") ? bundled.getAsJsonObject("targeted_strip") : new JsonObject();
                stripCost = ItemCostRule.fromJson(bundledStrip);
            }

            JsonObject curseObj = root.has("curse_purge") ? root.getAsJsonObject("curse_purge") : new JsonObject();
            int curseXp = Math.max(1, intOr(curseObj, "xp_cost", intOr(curseObj, "xp_levels", 12)));
            ItemCostRule curseCost;
            if (curseObj.has("options") || curseObj.has("cost_options")) {
                curseCost = ItemCostRule.fromJson(curseObj);
            } else {
                if (bundled == null) {
                    bundled = bundledFallbackSupplier.get();
                }
                JsonObject bundledCurse = bundled.has("curse_purge") ? bundled.getAsJsonObject("curse_purge") : new JsonObject();
                curseCost = ItemCostRule.fromJson(bundledCurse);
            }

            return new DisenchantingRules(
                enabled, allowCurse, scale, extScale,
                stripMin, stripMax, stripCost,
                curseXp, curseCost
            );
        }

        public int targetedStripXpCost(int level, int maxLevel) {
            int safeMax = Math.max(1, maxLevel);
            int safeLvl = Math.clamp(level, 1, safeMax);
            if (safeMax <= 1) {
                return Math.round((stripXpMin + stripXpMax) * 0.5f);
            }
            double u = (double) (safeLvl - 1) / (double) (safeMax - 1);
            return Math.max(1, (int) Math.round(stripXpMin + (stripXpMax - stripXpMin) * u));
        }

        public List<List<ItemStack>> targetedStripCostOptions(Identifier id, int level, int maxLevel) {
            return stripMaterialCost.toBundles(id, level, maxLevel);
        }

        public List<List<ItemStack>> cursePurgeCostOptions(Identifier id) {
            return curseMaterialCost.toBundles(id, 1, 1);
        }
    }
}
