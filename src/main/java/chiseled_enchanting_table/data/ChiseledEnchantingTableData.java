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
    // Table identifiers live in DataIds (god-class split, 2026-10-24 audit); aliases keep call sites stable.
    private static final Identifier DEFAULT_ENCHANTMENTS_ID = DataIds.DEFAULT_ENCHANTMENTS_ID;
    private static final Identifier ENCHANTMENT_COSTS_ID = DataIds.ENCHANTMENT_COSTS_ID;
    private static final Identifier ITEM_TRANSFORMATIONS_ID = DataIds.ITEM_TRANSFORMATIONS_ID;
    private static final Identifier LOOT_INJECTIONS_ID = DataIds.LOOT_INJECTIONS_ID;
    private static final Identifier BOOKSHELF_BOOK_POOL_ID = DataIds.BOOKSHELF_BOOK_POOL_ID;
    private static final Identifier BOOK_UPGRADES_ID = DataIds.BOOK_UPGRADES_ID;
    private static final Identifier TREASURE_EPIPHANY_ID = DataIds.TREASURE_EPIPHANY_ID;
    private static final Identifier DISENCHANTING_ID = DataIds.DISENCHANTING_ID;
    private static final Identifier SHELF_GATES_ID = DataIds.SHELF_GATES_ID;
    private static final Identifier LIBRARY_RESONANCE_ID = DataIds.LIBRARY_RESONANCE_ID;
    private static final Identifier PACK_POLICY_ID = DataIds.PACK_POLICY_ID;

    public enum VanillaChestBooksMode {
        KEEP_BOTH,
        STRIP_VANILLA,
        DISABLED;

        public String token() {
            return switch (this) {
                case KEEP_BOTH -> "keep_both";
                case STRIP_VANILLA -> "strip_vanilla";
                case DISABLED -> "disabled";
            };
        }

        public static VanillaChestBooksMode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return KEEP_BOTH;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "strip_vanilla", "replace", "strip" -> STRIP_VANILLA;
                case "disabled", "off", "none", "false" -> DISABLED;
                default -> KEEP_BOTH;
            };
        }
    }

    private static volatile Map<Identifier, ItemTransformation> rawItemTransformations = builtInItemTransformations();
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
    private static volatile LootRegistryBuildRules pendingLootRegistryBuild;
    public static void clearLootRegistryBuildRules() { pendingLootRegistryBuild = null; }
    public static LootRegistryBuildRules lootRegistryBuildRules() {
        var pending = pendingLootRegistryBuild;
        return pending != null ? pending : new LootRegistryBuildRules(lootInjections(), vanillaChestBooksMode(),
            chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.activeChestSpawnConfig());
    }
    private static volatile BookshelfBookPool bookshelfBookPool = rawBookshelfBookPool;
    private static volatile BookUpgradeRules bookUpgradeRules = rawBookUpgradeRules;
    private static volatile TreasureEpiphanyRules treasureEpiphanyRules = rawTreasureEpiphanyRules;
    private static volatile DisenchantingRules disenchantingRules = rawDisenchantingRules;
    private static volatile chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig =
        rawShelfGatesConfig;
    private static volatile chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig =
        rawLibraryResonanceConfig;
    private static volatile chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritualConfig =
        rawRitualConfig;

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
    private static volatile RuntimeFrame runtimeFrame;
    public static RuntimeFrame runtimeFrame() { return runtimeFrame; }
    public static chiseled_enchanting_table.config.ModRuntimeConfig runtimeConfiguration() {
        RuntimeFrame frame = runtimeFrame;
        return frame == null ? chiseled_enchanting_table.config.ModRuntimeConfig.get() : frame.configuration();
    }

    static {
        applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig.get());
    }

    public static void init() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(PackType.SERVER_DATA)
            .registerReloadListener(new ChiseledEnchantingTableData());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            server -> {
                clearLootRegistryBuildRules();
                validateBookshelfTagDisjointness(server.registryAccess());
            }
        );
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resourceManager, success) -> {
                clearLootRegistryBuildRules();
                if (success) {
                    validateBookshelfTagDisjointness(server.registryAccess());
                }
            }
        );
    }

    /**
     * Startup/reload self-check (COUP-05): warns if any {@link net.minecraft.world.level.block.ChiseledBookShelfBlock}
     * was placed in {@code #chiseled_enchanting_table:regular_bookshelves} or
     * {@code #minecraft:enchantment_power_provider}, which are strictly reserved for wooden shelves
     * (chiseled bookshelves are defensively excluded at scan time regardless).
     */
    public static void validateBookshelfTagDisjointness(net.minecraft.core.RegistryAccess registryAccess) {
        if (registryAccess == null) {
            return;
        }
        var blockLookupOpt = registryAccess.lookup(Registries.BLOCK);
        if (blockLookupOpt.isEmpty()) {
            return;
        }
        var blockLookup = blockLookupOpt.get();
        warnIfChiseledShelfInTag(blockLookup, CetTags.Blocks.REGULAR_BOOKSHELVES);
        warnIfChiseledShelfInTag(blockLookup, net.minecraft.tags.BlockTags.ENCHANTMENT_POWER_PROVIDER);
    }

    private static void warnIfChiseledShelfInTag(
        HolderGetter<net.minecraft.world.level.block.Block> blockLookup,
        TagKey<net.minecraft.world.level.block.Block> tag
    ) {
        blockLookup.get(tag).ifPresent(set -> {
            for (var holder : set) {
                if (holder.value() instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock) {
                    ChiseledEnchantingTable.LOGGER.warn(
                        "Block tag #{} contains ChiseledBookShelfBlock '{}' — chiseled bookshelves belong to the Master Tome "
                            + "channel and are ignored as regular wooden bookshelves to prevent double-counting",
                        tag.location(),
                        holder.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown")
                    );
                }
            }
        });
    }

    /**
     * Explicit, hand-picked default-unlocked enchantments from the JSON {@code values} list only
     * (no tag expansion). Kept for callers without registry access.
     */
    public static Set<EnchantmentWithLevel> defaultEnchantments() {
        return new HashSet<>((runtimeFrame == null ? defaultEnchantmentValues : runtimeFrame.defaultEnchantmentValues()));
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
        // 1.0.3 Classic: the old default_enchantments.json expanded
        // #minecraft:in_enchanting_table with NO treasure/curse/epiphany exclusions.
        // Filtering first and re-adding 7 vanilla IDs afterwards (the previous
        // approach) still dropped third-party enchantments carrying both tags.
        boolean unfilteredDefaults = runtimeConfiguration().defaultUnlockFilter()
            == chiseled_enchanting_table.config.ModRuntimeConfig.DefaultUnlockFilter.CLASSIC_UNFILTERED;
        Set<Identifier> excluded = unfilteredDefaults
            ? new HashSet<>()
            : new HashSet<>((runtimeFrame == null ? defaultExcludedIds : runtimeFrame.defaultExcludedIds()));
        if (!unfilteredDefaults) {
            excluded.addAll((runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).explicitTreasureIds());
        }
        for (Identifier exTag : unfilteredDefaults
            ? List.<Identifier>of()
            : (runtimeFrame == null ? defaultExcludedTags : runtimeFrame.defaultExcludedTags())) {
            TagKey<Enchantment> tagKey = TagKey.create(Registries.ENCHANTMENT, exTag);
            enchantmentLookup.get(tagKey).ifPresent(named -> named.stream().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> excluded.add(key.identifier()))
            ));
        }

        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (EnchantmentWithLevel explicit : (runtimeFrame == null ? defaultEnchantmentValues : runtimeFrame.defaultEnchantmentValues())) {
            if (!excluded.contains(explicit.enchantment_id())) {
                // Audit H-06: datapack "unlocked level" entries never exceed the live
                // definition maximum (over-level unlocks used to leak into inscription).
                int unlocked = Math.max(1, explicit.enchantment_level());
                var holder = enchantmentLookup.get(ResourceKey.create(Registries.ENCHANTMENT, explicit.enchantment_id()));
                if (holder.isPresent()) {
                    unlocked = Math.min(unlocked, Math.max(1, holder.get().value().getMaxLevel()));
                }
                result.add(unlocked == explicit.enchantment_level()
                    ? explicit
                    : new EnchantmentWithLevel(explicit.enchantment_id(), unlocked));
            }
        }
        for (DefaultEnchantmentTagRule rule : (runtimeFrame == null ? defaultEnchantmentTags : runtimeFrame.defaultEnchantmentTags())) {
            TagKey<Enchantment> tagKey = TagKey.create(Registries.ENCHANTMENT, rule.tagId());
            enchantmentLookup.get(tagKey).ifPresent(named -> named.stream().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> {
                    if (!excluded.contains(key.identifier())) {
                        int unlocked = Math.max(1, Math.min(
                            Math.max(1, rule.level()),
                            Math.max(1, holder.value().getMaxLevel())
                        ));
                        result.add(new EnchantmentWithLevel(key.identifier(), unlocked));
                    }
                })
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

    public static List<List<ItemStack>> itemCostOptions(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
        return (runtimeFrame == null ? costTables : runtimeFrame.costTables()).itemCostOptions(enchantmentWithLevel, Math.max(1, maxLevel));
    }

    public static List<ItemStack> itemCosts(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
        List<List<ItemStack>> options = itemCostOptions(enchantmentWithLevel, maxLevel);
        return options.isEmpty() ? List.of() : options.getFirst();
    }

    public static List<ItemStack> additionalItemCosts(EnchantmentWithLevel enchantmentWithLevel, String salt, int maxLevel) {
        List<ItemStack> costs = itemCosts(enchantmentWithLevel, salt, maxLevel);
        return costs.size() <= 1 ? List.of() : List.copyOf(costs.subList(1, costs.size()));
    }

    public static List<ItemCostOption> rawItemCostOptions(Identifier enchantmentId, int level, int maxLevel) {
        var ct = runtimeFrame().costTables();
        ItemCostRule rule = ct.itemCosts().getOrDefault(enchantmentId, ct.defaultItemCost());
        return rule.rawOptionsForLevel(level, maxLevel);
    }

    public static int xpCost(EnchantmentWithLevel enchantmentWithLevel) {
        return xpCost(enchantmentWithLevel, Math.max(1, enchantmentWithLevel.enchantment_level()));
    }

    public static int xpCost(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
        return (runtimeFrame == null ? costTables : runtimeFrame.costTables()).xpCost(enchantmentWithLevel, Math.max(1, maxLevel));
    }


    public static boolean bookCombineEnabled() {
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules()).combineEnabled();
    }

    public static boolean bookResearchEnabled() {
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules()).researchEnabled();
    }

    public static int bookCombineXpCost(EnchantmentWithLevel target, int maxLevel) {
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules()).combineXpCost(target, Math.max(1, maxLevel), (runtimeFrame == null ? costTables : runtimeFrame.costTables()).xpCost(target, Math.max(1, maxLevel)));
    }

    public static int bookResearchXpCost(EnchantmentWithLevel target, int maxLevel) {
        var rules = (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules());
        int direct = (runtimeFrame == null ? costTables : runtimeFrame.costTables()).xpCost(target, Math.max(1, maxLevel));
        // 1.0.3 Classic (R2-04): research XP floors (15 x 0.5 = 7, not 8).
        return runtimeConfiguration().researchRoundingMode()
            == chiseled_enchanting_table.config.ModRuntimeConfig.ResearchRoundingMode.FLOOR_DOWN
            ? rules.classicResearchXpCost(target, Math.max(1, maxLevel), direct)
            : rules.researchXpCost(target, Math.max(1, maxLevel), direct);
    }

    /** 1.0.3 Classic per-player salted direct material costs (single option). */
    public static List<ItemStack> classicSaltedItemCosts(EnchantmentWithLevel enchantmentWithLevel, int maxLevel, String salt) {
        return (runtimeFrame == null ? costTables : runtimeFrame.costTables())
            .saltedCosts(enchantmentWithLevel, Math.max(1, maxLevel), salt == null ? "" : salt);
    }

    /** 1.0.3 Classic per-player salted book-research material costs. */
    public static List<ItemStack> classicSaltedResearchCosts(EnchantmentWithLevel target, int maxLevel, String salt) {
        String safeSalt = salt == null ? "" : salt;
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules())
            .saltedResearchCosts(target, Math.max(1, maxLevel), safeSalt, classicSaltedItemCosts(target, maxLevel, safeSalt));
    }

    public static List<List<ItemStack>> bookResearchCostOptions(EnchantmentWithLevel target, int maxLevel) {
        int safeMax = Math.max(1, maxLevel);
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules()).researchCostOptions(target, safeMax, (runtimeFrame == null ? costTables : runtimeFrame.costTables()).itemCostOptions(target, safeMax));
    }

    public static List<ItemStack> bookResearchCosts(EnchantmentWithLevel target, String salt, int maxLevel) {
        List<List<ItemStack>> options = bookResearchCostOptions(target, maxLevel);
        return options.isEmpty() ? List.of() : options.getFirst();
    }

    public static ItemStack bookResearchPrimaryCost(EnchantmentWithLevel target, String salt, int maxLevel) {
        List<ItemStack> costs = bookResearchCosts(target, salt, maxLevel);
        return costs.isEmpty() ? ItemStack.EMPTY : costs.getFirst();
    }

    public static List<ItemStack> bookResearchAdditionalCosts(EnchantmentWithLevel target, String salt, int maxLevel) {
        List<ItemStack> costs = bookResearchCosts(target, salt, maxLevel);
        return costs.size() <= 1 ? List.of() : List.copyOf(costs.subList(1, costs.size()));
    }

    public static int treasureEpiphanyMinBookshelves() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).minRegularBookshelves();
    }

    public static double treasureEpiphanyBaseChance() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).baseChance();
    }

    public static double treasureEpiphanyPerShelfChance() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).chancePerExtraBookshelf();
    }

    public static double treasureEpiphanyPerTomeChance() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).chancePerMasterTome();
    }

    public static double treasureEpiphanyMaxChance() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).maxChance();
    }

    public static int treasureEpiphanyMaxSlotsCap() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).maxSlotsCap();
    }

    public static int treasureEpiphanyScorePerSlot() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).scorePerExtraSlot();
    }

    public static int treasureEpiphanyCycleTicks() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).cycleTicks();
    }

    public static int treasureEpiphanyFreezeDurationTicks() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).freezeDurationTicks();
    }

    public static int treasureEpiphanyMaxFreezeTicks() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).maxFreezeTicks();
    }

    public static int treasureEpiphanyFreezeXpLevels() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).freezeXpLevels();
    }

    public static List<ItemStack> treasureEpiphanyFreezeCostOptions() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).freezeStacks();
    }

    public static Set<Identifier> treasureEpiphanyExplicitIds() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).explicitTreasureIds();
    }

    public static Set<Identifier> treasureEpiphanyExplicitIds(net.minecraft.core.RegistryAccess registryAccess) {
        Set<Identifier> resolved = new HashSet<>((runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).explicitTreasureIds());
        if (registryAccess == null) {
            return Set.copyOf(resolved);
        }
        var lookup = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        for (Identifier tagId : (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).treasureTagIds()) {
            lookup.get(TagKey.create(Registries.ENCHANTMENT, tagId)).ifPresent(named ->
                named.stream().forEach(holder -> holder.unwrapKey().ifPresent(k -> resolved.add(k.identifier())))
            );
        }
        for (Identifier exTagId : (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).excludedTagIds()) {
            lookup.get(TagKey.create(Registries.ENCHANTMENT, exTagId)).ifPresent(named ->
                named.stream().forEach(holder -> holder.unwrapKey().ifPresent(k -> resolved.remove(k.identifier())))
            );
        }
        return Set.copyOf(resolved);
    }

    public static double treasureEpiphanyChance(int regularBookshelves, int chiseledBooks) {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).chanceFor(regularBookshelves, chiseledBooks);
    }

    public static int treasureEpiphanyMaxSlots(int regularBookshelves, int chiseledBooks) {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).maxSlotsFor(regularBookshelves, chiseledBooks);
    }

    public static int treasureEpiphanyRerollXpLevels() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).rerollXpLevels();
    }

    public static List<ItemStack> treasureEpiphanyRerollCostOptions() {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).rerollStacks();
    }

    public static int treasureEpiphanyXpCost(Identifier enchantmentId) {
        return treasureEpiphanyXpCost(enchantmentId, 1, 1);
    }

    public static int treasureEpiphanyXpCost(Identifier enchantmentId, int level, int maxLevel) {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).xpCost(enchantmentId, level, maxLevel);
    }

    public static List<List<ItemStack>> treasureEpiphanyCostOptions(Identifier enchantmentId) {
        return treasureEpiphanyCostOptions(enchantmentId, 1, 1);
    }

    public static List<List<ItemStack>> treasureEpiphanyCostOptions(Identifier enchantmentId, int level, int maxLevel) {
        return (runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).costOptions(enchantmentId, level, maxLevel);
    }

    public record TransformationDescriptor(
        Identifier id,
        Identifier inputItemId,
        Identifier outputItemId,
        int outputCount,
        boolean inputIsTag,
        int minRequiredLevel,
        int deductedLevels,
        boolean unlocked
    ) {}

    public static Set<EnchantmentWithLevel> transformationEnchantmentsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (ItemTransformation transformation : (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).values()) {
            if (transformation.isResolvable() && transformation.matchesInput(input)) {
                result.add(new EnchantmentWithLevel(transformation.id(), transformation.level()));
            }
        }
        return result;
    }

    public static boolean hasTransformationForInput(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (ItemTransformation transformation : (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).values()) {
            if (transformation.isResolvable() && transformation.matchesInput(input)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<ItemTransformation> itemTransformation(Identifier id) {
        return Optional.ofNullable((runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id))
            .filter(ItemTransformation::isResolvable);
    }

    public static Set<Identifier> transformationIds() {
        return (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).keySet();
    }

    public static boolean isTransformationUnlocked(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player, Identifier id) {
        ItemTransformation rule = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return rule == null || rule.isUnlockedFor(player);
    }

    public static List<TransformationDescriptor> transformationDescriptors() {
        return transformationDescriptors(null);
    }

    public static List<TransformationDescriptor> transformationDescriptors(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player) {
        List<TransformationDescriptor> result = new ArrayList<>();
        for (ItemTransformation transformation : (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).values()) {
            if (!transformation.isResolvable()) {
                continue;
            }
            result.add(new TransformationDescriptor(
                transformation.id(),
                transformation.inputItemId(),
                transformation.outputItemId(),
                transformation.level(),
                transformation.inputIsTag(),
                transformation.minRequiredLevel(),
                transformation.deductedLevels(),
                transformation.isUnlockedFor(player)
            ));
        }
        return List.copyOf(result);
    }

    public static boolean isTransformation(Identifier id) {
        ItemTransformation t = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return t != null && t.isResolvable();
    }

    public static final Identifier BLANK_OVERWRITE_ID = ChiseledEnchantingTable.identifier("blank_overwrite");

    /**
     * Returns true if {@code stack} is a {@code depleted_tome} OR a legacy pre-fix {@code minecraft:book}
     * carrying leftover enchantment/glint components from an earlier merge/strip operation.
     */
    public static boolean isResidualBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME)) return true;
        if (!stack.is(Items.BOOK)) return false;
        if (stack.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) return true;
        if (stack.has(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) return true;
        var direct = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        return direct != null && !direct.isEmpty();
    }

    /** Hard ceiling for one merged cost entry; exceeding it invalidates the bundle (audit R-02). */
    public static final int MAX_MERGED_COST_COUNT = 4096;

    /**
     * Merges repeated {@link ItemStack#isSameItemSameComponents} stacks inside one cost bundle so
     * duplicate materials across primary + additional costs never under-check or partially deduct.
     */
    public static List<ItemStack> mergeEquivalentCostBundle(List<ItemStack> rawBundle) {
        if (rawBundle == null || rawBundle.isEmpty()) {
            return List.of();
        }
        List<ItemStack> merged = new ArrayList<>(rawBundle.size());
        for (ItemStack stack : rawBundle) {
            if (stack == null || stack.isEmpty()) {
                return List.of();
            }
            boolean combined = false;
            for (int i = 0; i < merged.size(); i++) {
                ItemStack existing = merged.get(i);
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    long total = (long) existing.getCount() + (long) stack.getCount();
                    // Audit R-02: an over-ceiling merge used to be silently clamped to 4096,
                    // discounting the cost. Over-large bundles are invalid datapack data and
                    // must fail closed instead of undercharging.
                    if (total > MAX_MERGED_COST_COUNT) {
                        ChiseledEnchantingTable.LOGGER.warn(
                            "Rejected merged cost {} x{} exceeding max {}; treating bundle as invalid.",
                            BuiltInRegistries.ITEM.getKey(stack.getItem()), total, MAX_MERGED_COST_COUNT);
                        return List.of();
                    }
                    ItemStack updated = existing.copy();
                    updated.setCount((int) total);
                    merged.set(i, updated);
                    combined = true;
                    break;
                }
            }
            if (!combined) {
                merged.add(stack.copy());
            }
        }
        return List.copyOf(merged);
    }

    public static List<List<ItemStack>> transformationCostOptions(Identifier id) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        if (transformation == null) return List.of();
        return transformation.resolvedBundles();
    }

    /**
     * 1.0.3 Classic transformation bundle (primary + additional) where ranged
     * {@code min_count..max_count} options resolve per player via the original
     * stable-hash formula (tag 26.1.2_1.0.3 ItemCostOption#computeCount), instead
     * of the current engine's deterministic midpoint. Used for the classic
     * Enchanted Golden Apple emerald cost of 2-5.
     */
    /** XP level cost of a classic transformation from the active frame table
     *  (datapack overlay included), or -1 when the id is not a transformation. */
    public static int classicTransformationXpCost(Identifier id) {
        var frame = runtimeFrame;
        var table = frame == null ? classicItemTransformations : frame.itemTransformations();
        var transformation = table.get(id);
        return transformation == null ? -1 : Math.max(0, transformation.xpCost());
    }

    public static List<ItemStack> classicTransformationCosts(Identifier id, String salt) {
        ItemTransformation transformation = (runtimeFrame == null ? rawItemTransformations : runtimeFrame.itemTransformations()).get(id);
        if (transformation == null) {
            return List.of();
        }
        String safeSalt = salt == null ? "" : salt;
        List<ItemStack> out = new ArrayList<>();
        ItemStack primary = transformation.primaryCostStack(safeSalt);
        if (!primary.isEmpty()) {
            out.add(primary);
        }
        for (ItemCostOption option : transformation.additionalCosts()) {
            if (option.minCount() > 0 && option.maxCount() >= option.minCount()) {
                List<Item> candidates = option.candidateItems();
                if (candidates.isEmpty()) {
                    continue;
                }
                int spread = option.maxCount() - option.minCount() + 1;
                int count = option.minCount() + stablePositiveHash(
                    id + ":" + transformation.level() + ":" + transformation.level()
                        + ":" + safeSalt + ":count:" + option.itemId()
                ) % spread;
                out.add(new ItemStack(candidates.getFirst(), count));
            } else {
                ItemStack stack = option.toSingleStack(id, transformation.level(), transformation.level(), safeSalt);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return List.copyOf(out);
    }

    public static ItemStack transformationPrimaryCost(Identifier id, String salt) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return transformation == null ? ItemStack.EMPTY : transformation.primaryCostStack(salt == null ? "" : salt);
    }

    public static List<ItemStack> transformationAdditionalCosts(Identifier id, String salt) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return transformation == null ? List.of() : transformation.additionalCostStacks(salt == null ? "" : salt);
    }

    public static int transformationXpCost(Identifier id) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return transformation == null ? 0 : transformation.xpCost();
    }

    public static int transformationMinRequiredLevel(Identifier id) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return transformation == null ? 1 : transformation.minRequiredLevel();
    }

    public static int transformationMinPlayerLevel(Identifier id) {
        return transformationMinRequiredLevel(id);
    }

    public static int transformationDeductedLevels(Identifier id) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return transformation == null ? 0 : transformation.deductedLevels();
    }

    /**
     * Preloads {@code loot_injections.json} and {@code pack_policy.json} right before
     * {@code ReloadableServerRegistries.reload} builds {@code Registries.LOOT_TABLE} so
     * {@code LootTableEvents.MODIFY} always sees the active datapack rules on both initial
     * world boot and {@code /reload}.
     */
    public static void preloadForLootRegistries(ResourceManager resourceManager) {
        LootInjectionTable loaded = loadLootInjections(resourceManager);
        var policy = loadPackPolicy(resourceManager);
        var cfg = chiseled_enchanting_table.config.ModRuntimeConfig.get();
        VanillaChestBooksMode effectiveMode = resolveEffectiveChestBooksMode(loaded.mode(), cfg, policy);
        // Publish all registry-build inputs together, only after parsing/validation succeeds.
        // Live chest-spawn state is updated later by the successful full reload, never here.
        pendingLootRegistryBuild = new LootRegistryBuildRules(loaded.entries(), effectiveMode, loaded.chestSpawns());
    }

    private static VanillaChestBooksMode resolveEffectiveChestBooksMode(
        VanillaChestBooksMode datapackMode,
        chiseled_enchanting_table.config.ModRuntimeConfig cfg,
        chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy policy
    ) {
        if (policy != null && policy.lockInGameConfig()) {
            return datapackMode;
        }
        boolean dpGoverned = policy != null && policy.datapackOverriddenKeys().contains("vanilla_chest_books_mode");
        boolean lockedKey = policy != null && policy.lockedKeys().contains("vanilla_chest_books_mode");
        if (lockedKey || (dpGoverned && (policy.enforceDatapackPriority() || !cfg.overrideDatapacks()))) {
            return datapackMode;
        }
        return cfg.vanillaChestBooksMode();
    }

    public static List<LootInjection> lootInjections() {
        return (runtimeFrame == null ? lootInjections : runtimeFrame.lootInjections());
    }

    public static VanillaChestBooksMode vanillaChestBooksMode() {
        return (runtimeFrame == null ? vanillaChestBooksMode : runtimeFrame.vanillaChestBooksMode());
    }

    public static boolean bookshelfReplacementEnabled() {
        return (runtimeFrame == null ? bookshelfBookPool : runtimeFrame.bookshelfBookPool()).enabled();
    }

    public static BookshelfBookPool bookshelfBookPool() {
        return (runtimeFrame == null ? bookshelfBookPool : runtimeFrame.bookshelfBookPool());
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        synchronized (ChiseledEnchantingTableData.class) {
        var previous_defaultEnchantmentValues = defaultEnchantmentValues;
        var previous_defaultEnchantmentTags = defaultEnchantmentTags;
        var previous_defaultExcludedIds = defaultExcludedIds;
        var previous_defaultExcludedTags = defaultExcludedTags;
        var previous_costTables = costTables;
        var previous_rawItemTransformations = rawItemTransformations;
        var previous_rawVanillaChestBooksMode = rawVanillaChestBooksMode;
        var previous_lootInjections = lootInjections;
        var previous_rawBookshelfBookPool = rawBookshelfBookPool;
        var previous_rawBookUpgradeRules = rawBookUpgradeRules;
        var previous_rawTreasureEpiphanyRules = rawTreasureEpiphanyRules;
        var previous_rawDisenchantingRules = rawDisenchantingRules;
        var previous_rawShelfGatesConfig = rawShelfGatesConfig;
        var previous_rawLibraryResonanceConfig = rawLibraryResonanceConfig;
        var previous_rawRitualConfig = rawRitualConfig;
        var previous_classicCostTables = classicCostTables;
        var previous_classicBookUpgradeRules = classicBookUpgradeRules;
        var previous_classicItemTransformations = classicItemTransformations;
        var previous_chestSpawns = chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.activeChestSpawnConfig();
        try {
        var candidatePolicy = loadPackPolicy(resourceManager);
        DefaultEnchantmentTable loadedDefaults = loadDefaultEnchantments(resourceManager);
        defaultEnchantmentValues = loadedDefaults.values();
        defaultEnchantmentTags = loadedDefaults.tags();
        defaultExcludedIds = loadedDefaults.excludedIds();
        defaultExcludedTags = loadedDefaults.excludedTags();
        costTables = loadCostTables(resourceManager);
        rawItemTransformations = loadItemTransformations(resourceManager);
        classicCostTables = loadClassicJson(resourceManager,
            "enchantment_costs_classic_1_0_3.json", CostTables::fromJson, BUILT_IN_CLASSIC_COSTS);
        classicBookUpgradeRules = loadClassicJson(resourceManager,
            "book_upgrades_classic_1_0_3.json", BookUpgradeRules::fromJson, BUILT_IN_CLASSIC_UPGRADES);
        classicItemTransformations = loadClassicJson(resourceManager,
            "item_transformations_classic_1_0_3.json",
            root -> parseItemTransformations(root), BUILT_IN_CLASSIC_TRANSFORMATIONS);
        LootInjectionTable loadedLoot = loadLootInjections(resourceManager);
        rawVanillaChestBooksMode = loadedLoot.mode();
        lootInjections = loadedLoot.entries();
        chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.updateChestSpawnConfig(loadedLoot.chestSpawns());
        rawBookshelfBookPool = loadBookshelfBookPool(resourceManager);
        rawBookUpgradeRules = loadBookUpgradeRules(resourceManager);
        rawTreasureEpiphanyRules = loadTreasureEpiphanyRules(resourceManager);
        rawDisenchantingRules = loadDisenchantingRules(resourceManager);
        rawShelfGatesConfig = loadShelfGatesConfig(resourceManager);
        LibraryResonanceAndRitual loadedResonance = loadLibraryResonanceAndRitual(resourceManager);
        rawLibraryResonanceConfig = loadedResonance.resonance();
        rawRitualConfig = loadedResonance.ritual();
        applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig.get(), candidatePolicy, () -> {});
        clearLootRegistryBuildRules();
        chiseled_enchanting_table.config.ModRuntimeConfig.setServerPackPolicy(candidatePolicy);
        ChiseledEnchantingTable.LOGGER.info(
            "Loaded chiseled enchanting data: {} default enchantments, {} default-unlock tag rules, {} item cost overrides, {} transformation rules, {} loot injections",
            defaultEnchantmentValues.size(),
            defaultEnchantmentTags.size(),
            costTables.itemCosts().size(),
            itemTransformations.size(),
            lootInjections.size()
        );
        } catch (RuntimeException failure) {
            defaultEnchantmentValues = previous_defaultEnchantmentValues;
            defaultEnchantmentTags = previous_defaultEnchantmentTags;
            defaultExcludedIds = previous_defaultExcludedIds;
            defaultExcludedTags = previous_defaultExcludedTags;
            costTables = previous_costTables;
            rawItemTransformations = previous_rawItemTransformations;
            rawVanillaChestBooksMode = previous_rawVanillaChestBooksMode;
            lootInjections = previous_lootInjections;
            rawBookshelfBookPool = previous_rawBookshelfBookPool;
            rawBookUpgradeRules = previous_rawBookUpgradeRules;
            rawTreasureEpiphanyRules = previous_rawTreasureEpiphanyRules;
            rawDisenchantingRules = previous_rawDisenchantingRules;
            classicCostTables = previous_classicCostTables;
            classicBookUpgradeRules = previous_classicBookUpgradeRules;
            classicItemTransformations = previous_classicItemTransformations;
            rawShelfGatesConfig = previous_rawShelfGatesConfig;
            rawLibraryResonanceConfig = previous_rawLibraryResonanceConfig;
            rawRitualConfig = previous_rawRitualConfig;
            chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.updateChestSpawnConfig(previous_chestSpawns);
            clearLootRegistryBuildRules();
            throw failure;
        }
        }
    }

    /**
     * Overlays {@link chiseled_enchanting_table.config.ModRuntimeConfig} onto the active datapack
     * tables according to {@code cfg.shouldApplyKey(...)}, preserving datapack overrides when
     * {@code override_datapacks == false} unless a field was explicitly edited in-game.
     */
    private static chiseled_enchanting_table.config.ModRuntimeConfig rawRuntimeConfig() {
        var d = new chiseled_enchanting_table.config.ModRuntimeConfig.Draft();
        d.directGatesEnabled = rawShelfGatesConfig.directGatesEnabled();
        d.blankMaxTierMode = rawShelfGatesConfig.blankMaxTierMode();
        d.maxDirectBookshelves = rawShelfGatesConfig.maxDirectBookshelves();
        d.horizontalScanRadius = rawShelfGatesConfig.horizontalScanRadius();
        d.verticalScanRadius = rawShelfGatesConfig.verticalScanRadius();
        d.requireBreathingRoom = rawShelfGatesConfig.requireBreathingRoom();
        d.minOpenAdjacentBlocks = rawShelfGatesConfig.minOpenAdjacentBlocks();
        d.requireLineOfSight = rawShelfGatesConfig.requireLineOfSight();
        d.vanillaTableMode = rawShelfGatesConfig.vanillaTableMode();
        d.tier1Bookshelves = rawShelfGatesConfig.tier1Bookshelves();
        d.tier2Bookshelves = rawShelfGatesConfig.tier2Bookshelves();
        d.tier3QualifyingEnchantments = rawShelfGatesConfig.tier3QualifyingEnchantments();
        d.transformationMaxBatchSize = rawShelfGatesConfig.baseBatchSize();
        d.tomeConsumptionMode = rawLibraryResonanceConfig.tomeConsumptionMode();
        d.maxTomeUses = rawLibraryResonanceConfig.maxTomeUses();
        d.bookCopyWearCost = rawLibraryResonanceConfig.bookCopyWearCost();
        d.tomeDepleteBehavior = rawLibraryResonanceConfig.tomeDepleteBehavior();
        d.tomeRegenEnabled = rawLibraryResonanceConfig.tomeRegenEnabled();
        d.tomeRegenBaseSeconds = rawLibraryResonanceConfig.tomeRegenBaseSeconds();
        d.tomeRegenTier1Shelves = rawLibraryResonanceConfig.tomeRegenTier1Shelves();
        d.tomeRegenTier1Seconds = rawLibraryResonanceConfig.tomeRegenTier1Seconds();
        d.tomeRegenTier2Shelves = rawLibraryResonanceConfig.tomeRegenTier2Shelves();
        d.tomeRegenTier2Seconds = rawLibraryResonanceConfig.tomeRegenTier2Seconds();
        d.lockedShelfBreakable = rawLibraryResonanceConfig.lockedShelfBreakable();
        d.lockedBookExtractionBlocked = rawLibraryResonanceConfig.lockedBookExtractionBlocked();
        d.earlyUnlockXpLevels = rawLibraryResonanceConfig.earlyUnlockXpLevels();
        d.earlyUnlockGritCount = rawLibraryResonanceConfig.earlyUnlockGritCount();
        d.sacrificeRequired = rawLibraryResonanceConfig.sacrificeRequired();
        d.tomePageRequiredShelves = rawLibraryResonanceConfig.tomePageRequiredShelves();
        d.maxWoodenDiscountPercent = rawLibraryResonanceConfig.maxWoodenDiscountPercent();
        d.maxTotalXpDiscountPercent = rawLibraryResonanceConfig.maxTotalXpDiscountPercent();
        d.bookCombineEnabled = rawBookUpgradeRules.combineEnabled();
        d.bookCombineXpMultiplier = rawBookUpgradeRules.combineXpMultiplier();
        d.bookResearchEnabled = rawBookUpgradeRules.researchEnabled();
        d.bookResearchXpMultiplier = rawBookUpgradeRules.researchXpMultiplier();
        d.epiphanyMinBookshelves = rawTreasureEpiphanyRules.minRegularBookshelves();
        d.epiphanyBaseChance = rawTreasureEpiphanyRules.baseChance();
        d.epiphanyMaxChance = rawTreasureEpiphanyRules.maxChance();
        d.epiphanyMaxSlotsCap = rawTreasureEpiphanyRules.maxSlotsCap();
        d.epiphanyCycleSeconds = rawTreasureEpiphanyRules.cycleTicks() / 20;
        d.epiphanyFreezeXpLevels = rawTreasureEpiphanyRules.freezeXpLevels();
        d.epiphanyFreezeDurationSeconds = rawTreasureEpiphanyRules.freezeDurationTicks() / 20;
        d.epiphanyMaxFreezeSeconds = rawTreasureEpiphanyRules.maxFreezeTicks() / 20;
        d.epiphanyRerollXpLevels = rawTreasureEpiphanyRules.rerollXpLevels();
        d.disenchantEnabled = rawDisenchantingRules.enabled();
        d.xpRefundScale = rawDisenchantingRules.xpRefundScale();
        d.externalXpRefundScale = rawDisenchantingRules.externalXpRefundScale();
        d.stripXpMin = rawDisenchantingRules.stripXpMin();
        d.stripXpMax = rawDisenchantingRules.stripXpMax();
        d.allowCursePurge = rawDisenchantingRules.allowCursePurge();
        d.cursePurgeXpCost = rawDisenchantingRules.curseXpCost();
        d.bookshelfPoolEnabled = rawBookshelfBookPool.enabled();
        d.bookshelfMinBooks = rawBookshelfBookPool.minBooks();
        d.bookshelfMaxBooks = rawBookshelfBookPool.maxBooks();
        d.bookshelfEnchantedProb = rawBookshelfBookPool.enchantedBookProbability();
        d.vanillaChestBooksMode = rawVanillaChestBooksMode;
        return d.build();
    }

    public static synchronized void applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig cfg) {
        applyRuntimeConfig(cfg, chiseled_enchanting_table.config.ModRuntimeConfig.serverPackPolicy(), () -> {});
    }
    public static synchronized void applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig cfg,
        chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy policy, Runnable persist) {
        if (cfg == null) {
            cfg = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT;
        }
        var prepared = chiseled_enchanting_table.config.rules.RuntimeRules.prepare(cfg, rawRuntimeConfig(), policy);
        cfg = prepared.configuration();
        var sg = rawShelfGatesConfig;
        int maxDirect = cfg.maxDirectBookshelves();
        int t2 = Math.min(maxDirect, cfg.tier2Bookshelves());
        int t1 = Math.min(t2, cfg.tier1Bookshelves());
        int batchCap = cfg.transformationMaxBatchSize();
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig = new chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config(
            cfg.directGatesEnabled(),
            maxDirect,
            Math.min(maxDirect, sg.twoLevelMaxBookshelves()),
            sg.gamma(),
            cfg.blankMaxTierMode(),
            cfg.horizontalScanRadius(),
            cfg.verticalScanRadius(),
            sg.overrides(),
            t1,
            t2,
            sg.tier2MaterialMultiplier(),
            cfg.tier3QualifyingEnchantments(),
            batchCap,
            cfg.transformationMaxBatchSize(),
            cfg.vanillaTableMode(),
            cfg.requireBreathingRoom(),
            cfg.minOpenAdjacentBlocks(),
            cfg.requireLineOfSight()
        );

        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig =
            rawLibraryResonanceConfig.withRuntimeOverrides(cfg);

        boolean useClassicCostTable = cfg.costTableDataset()
            == chiseled_enchanting_table.config.ModRuntimeConfig.CostTableDataset.CLASSIC;
        var bu = useClassicCostTable ? classicBookUpgradeRules : rawBookUpgradeRules;
        double effectiveCombineMult = (useClassicCostTable && !cfg.customizedKeys().contains("book_combine_xp_multiplier"))
            ? bu.combineXpMultiplier()
            : cfg.bookCombineXpMultiplier();
        double effectiveResearchMult = (useClassicCostTable && !cfg.customizedKeys().contains("book_research_xp_multiplier"))
            ? bu.researchXpMultiplier()
            : cfg.bookResearchXpMultiplier();
        BookUpgradeRules bookUpgradeRules = new BookUpgradeRules(
            cfg.bookCombineEnabled(),
            effectiveCombineMult,
            cfg.bookResearchEnabled(),
            effectiveResearchMult,
            bu.researchMaterialMultiplier(),
            bu.defaultResearchCost(),
            bu.researchOverrides()
        );
        CostTables frameCostTables = useClassicCostTable ? classicCostTables : costTables;

        var ep = rawTreasureEpiphanyRules;
        boolean epiphanyOff = !cfg.epiphanyEnabled();
        double epBase = epiphanyOff ? 0.0 : cfg.epiphanyBaseChance();
        double epMax = epiphanyOff ? 0.0 : Math.max(epBase, cfg.epiphanyMaxChance());
        int frzTicks = Math.max(200, cfg.epiphanyFreezeDurationSeconds() * 20);
        int maxFrzTicks = Math.max(
            frzTicks,
            Math.max(200, cfg.epiphanyMaxFreezeSeconds() * 20)
        );
        TreasureEpiphanyRules treasureEpiphanyRules = new TreasureEpiphanyRules(
            cfg.epiphanyMinBookshelves(),
            epBase,
            epiphanyOff ? 0.0 : ep.chancePerExtraBookshelf(),
            epiphanyOff ? 0.0 : ep.chancePerMasterTome(),
            epMax,
            cfg.epiphanyMaxSlotsCap(),
            ep.scorePerExtraSlot(),
            Math.max(200, cfg.epiphanyCycleSeconds() * 20),
            frzTicks,
            maxFrzTicks,
            cfg.epiphanyFreezeXpLevels(),
            ep.freezeOptions(),
            cfg.epiphanyRerollXpLevels(),
            ep.rerollOptions(),
            ep.explicitTreasureIds(),
            ep.treasureTagIds(),
            ep.excludedTagIds(),
            ep.defaultXpLevels(),
            ep.defaultMaterialCost(),
            ep.entries()
        );

        var dis = rawDisenchantingRules;
        double refScale = cfg.xpRefundScale();
        double extScale = Math.min(
            refScale,
            cfg.externalXpRefundScale()
        );
        int sMin = cfg.stripXpMin();
        int sMax = Math.max(sMin, cfg.stripXpMax());
        DisenchantingRules disenchantingRules = new DisenchantingRules(
            cfg.disenchantEnabled(),
            cfg.allowCursePurge(),
            refScale,
            extScale,
            sMin,
            sMax,
            dis.stripMaterialCost(),
            cfg.cursePurgeXpCost(),
            dis.curseMaterialCost()
        );

        Map<Identifier, ItemTransformation> itemTransformations;
        if (cfg.transformationSetMode() == chiseled_enchanting_table.config.ModRuntimeConfig.TransformationSetMode.CLASSIC_TWO) {
            itemTransformations = classicItemTransformations;
        } else if (!cfg.requirePriorDiscovery()) {
            Map<Identifier, ItemTransformation> unlockedMap = new java.util.LinkedHashMap<>();
            for (var entry : rawItemTransformations.entrySet()) {
                ItemTransformation t = entry.getValue();
                unlockedMap.put(
                    entry.getKey(),
                    new ItemTransformation(
                        t.id(),
                        t.inputItemId(),
                        t.inputIsTag(),
                        t.outputItemId(),
                        t.level(),
                        t.xpCost(),
                        t.minRequiredLevel(),
                        t.deductedLevels(),
                        false,
                        null,
                        t.primaryCost(),
                        t.additionalCosts(),
                        t.alternativePrimaryOptions(),
                        t.materialFree()
                    )
                );
            }
            itemTransformations = Map.copyOf(unlockedMap);
        } else {
            itemTransformations = rawItemTransformations;
        }

        VanillaChestBooksMode vanillaChestBooksMode = cfg.vanillaChestBooksMode();

        var bp = rawBookshelfBookPool;
        int minB = cfg.bookshelfMinBooks();
        int maxB = Math.max(minB, cfg.bookshelfMaxBooks());
        BookshelfBookPool bookshelfBookPool = new BookshelfBookPool(
            cfg.bookshelfPoolEnabled(),
            minB,
            maxB,
            cfg.bookshelfEnchantedProb(),
            bp.enchantPoolSource(),
            bp.useBaseWeight(),
            bp.useVanillaWeightFactor(),
            bp.maxLevelWeightRatio(),
            bp.enchantSets()
        );

        if (!chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication.verifyRuntimeConservation(
            libraryResonanceConfig,
            disenchantingRules.xpRefundScale()
        )) {
            ChiseledEnchantingTable.LOGGER.error(
                "Forcing disenchanting XP refund scales to 0.0 as fail-safe after conservation check failure"
            );
            disenchantingRules = new DisenchantingRules(
                disenchantingRules.enabled(),
                disenchantingRules.allowCursePurge(),
                0.0,
                0.0,
                disenchantingRules.stripXpMin(),
                disenchantingRules.stripXpMax(),
                disenchantingRules.stripMaterialCost(),
                disenchantingRules.curseXpCost(),
                disenchantingRules.curseMaterialCost()
            );
        }
        var activeRitualCfg = rawRitualConfig != null
            ? rawRitualConfig
            : chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.DEFAULT;
        RuntimeFrame next = new RuntimeFrame(itemTransformations, vanillaChestBooksMode, bookshelfBookPool, bookUpgradeRules, treasureEpiphanyRules, disenchantingRules, shelfGatesConfig, libraryResonanceConfig, activeRitualCfg, frameCostTables, defaultEnchantmentValues, defaultEnchantmentTags, defaultExcludedIds, defaultExcludedTags, lootInjections, cfg, policy, prepared.snapshot());
        chiseled_enchanting_table.config.rules.RuntimeRules.commit(prepared, persist);
        runtimeFrame = next;

    }

    private static chiseled_enchanting_table.config.ModRuntimeConfig.PackPolicy loadPackPolicy(
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

    private static void collectExternalDatapackKeys(
        ResourceManager resourceManager,
        Identifier tableId,
        String tableName,
        Set<String> outKeys
    ) {
        DatapackKeyExtractor.collectExternalDatapackKeys(resourceManager, tableId, tableName, outKeys);
    }

    public static Set<String> extractConfigKeysFromDatapackJson(String tableName, JsonObject root) {
        return DatapackKeyExtractor.extractConfigKeysFromDatapackJson(tableName, root);
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

    /** Accepts both {@code "#namespace:path"} (data-pack tag convention) and a bare id. */
    private static String stripTagPrefix(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static CostTables loadCostTables(ResourceManager resourceManager) {
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
            throw new IllegalArgumentException("Invalid data table " + ITEM_TRANSFORMATIONS_ID, e);
        }
    }

    private static JsonObject readBundledJsonObject(String fileName) {
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

    private static Map<Identifier, ItemTransformation> builtInItemTransformations() {
        return parseItemTransformations(readBundledJsonObject("item_transformations.json"));
    }

    /** The exact two transformations of tag 26.1.2_1.0.3, bundled verbatim. */
    /**
     * Classic overlay entry point: a datapack file at
     * {@code data/chiseled_enchanting_table/chiseled_enchanting_table/<file>}
     * replaces the bundled 1.0.3 baseline for that table; without one, the
     * previously loaded (bundled) table is kept.
     */
    private static <T> T loadClassicJson(
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

    private static Map<Identifier, ItemTransformation> classic103ItemTransformations() {
        return parseItemTransformations(readBundledJsonObject("item_transformations_classic_1_0_3.json"));
    }

    private static Map<Identifier, ItemTransformation> parseItemTransformations(JsonObject root) {
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

    private static Identifier parseIdentifier(String value) {
        return value.indexOf(':') >= 0 ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
    }

    private static Set<EnchantmentWithLevel> builtInDefaultEnchantments() {
        return Set.of();
    }

    private static List<DefaultEnchantmentTagRule> builtInDefaultEnchantmentTags() {
        return List.of(new DefaultEnchantmentTagRule(Identifier.withDefaultNamespace("in_enchanting_table"), 1));
    }

    private static Set<Identifier> builtInExcludedTreasureIds() {
        return Set.of();
    }

    private static List<Identifier> builtInExcludedTags() {
        return List.of(
            Identifier.withDefaultNamespace("treasure"),
            Identifier.withDefaultNamespace("curse"),
            ChiseledEnchantingTable.identifier("epiphany_exclusive")
        );
    }

    private static DefaultEnchantmentTable builtInDefaultEnchantmentTable() {
        return new DefaultEnchantmentTable(
            builtInDefaultEnchantments(),
            builtInDefaultEnchantmentTags(),
            builtInExcludedTreasureIds(),
            builtInExcludedTags()
        );
    }

    /** Parsed {@code default_enchantments.json} contents: explicit values plus tag-expansion & treasure-exclusion rules. */
    private record DefaultEnchantmentTable(
        Set<EnchantmentWithLevel> values,
        List<DefaultEnchantmentTagRule> tags,
        Set<Identifier> excludedIds,
        List<Identifier> excludedTags
    ) {}

    /** One {@code tags} entry: every enchantment in {@code tagId} unlocks at {@code level}. */
    public record DefaultEnchantmentTagRule(Identifier tagId, int level) {}

    public record ItemTransformation(
        Identifier id,
        Identifier inputItemId,
        boolean inputIsTag,
        Identifier outputItemId,
        int level,
        int xpCost,
        int minRequiredLevel,
        int deductedLevels,
        boolean requirePriorDiscovery,
        @org.jetbrains.annotations.Nullable Identifier requiredAdvancement,
        ItemCostOption primaryCost,
        List<ItemCostOption> additionalCosts,
        List<ItemCostOption> alternativePrimaryOptions,
        boolean materialFree
    ) {
        public ItemTransformation(
            Identifier id,
            Identifier inputItemId,
            boolean inputIsTag,
            Identifier outputItemId,
            int level,
            int xpCost,
            int minRequiredLevel,
            int deductedLevels,
            boolean requirePriorDiscovery,
            @org.jetbrains.annotations.Nullable Identifier requiredAdvancement,
            ItemCostOption primaryCost,
            List<ItemCostOption> additionalCosts,
            List<ItemCostOption> alternativePrimaryOptions
        ) {
            this(
                id, inputItemId, inputIsTag, outputItemId, level, xpCost, minRequiredLevel,
                deductedLevels, requirePriorDiscovery, requiredAdvancement, primaryCost,
                additionalCosts, alternativePrimaryOptions, false
            );
        }

        public ItemTransformation(
            Identifier id,
            Identifier inputItemId,
            Identifier outputItemId,
            int level,
            int xpCost,
            ItemCostOption primaryCost,
            List<ItemCostOption> additionalCosts,
            List<ItemCostOption> alternativePrimaryOptions
        ) {
            this(
                id,
                inputItemId,
                false,
                outputItemId,
                level,
                xpCost,
                1,
                0,
                false,
                null,
                primaryCost,
                additionalCosts,
                alternativePrimaryOptions,
                false
            );
        }

        public ItemTransformation(
            Identifier id,
            Identifier inputItemId,
            boolean inputIsTag,
            Identifier outputItemId,
            int level,
            int xpCost,
            int minRequiredLevel,
            int deductedLevels,
            ItemCostOption primaryCost,
            List<ItemCostOption> additionalCosts,
            List<ItemCostOption> alternativePrimaryOptions
        ) {
            this(
                id,
                inputItemId,
                inputIsTag,
                outputItemId,
                level,
                xpCost,
                minRequiredLevel,
                deductedLevels,
                false,
                null,
                primaryCost,
                additionalCosts,
                alternativePrimaryOptions,
                false
            );
        }

        public int minPlayerLevel() {
            return minRequiredLevel;
        }

        static ItemTransformation fromJson(JsonObject json) {
            Identifier id = parseIdentifier(json.get("id").getAsString());
            String rawInput = json.get("input").getAsString();
            boolean isTag = rawInput.startsWith("#");
            Identifier input = parseIdentifier(stripTagPrefix(rawInput));
            Identifier output = parseIdentifier(json.get("output").getAsString());
            int level = intOr(json, "level", intOr(json, "count", 1));
            // Audit R-02: reject absurd transformation levels at load time (reload rollback
            // keeps the previous table instead of silently clamping economy data).
            if (level < 1 || level > 64) {
                throw new IllegalArgumentException("Invalid transformation level " + level + " for " + id + " (allowed 1..64)");
            }
            boolean materialFree = boolOr(json, "material_free", boolOr(json, "xp_only", false));
            int xpCost = intOr(json, "required_player_level", intOr(json, "xp_cost", intOr(json, "xp_levels", 0)));
            int minRequiredLevel = Math.max(
                1,
                intOr(json, "min_required_level", intOr(json, "min_player_level", 1))
            );
            int deductedLevels = Math.max(
                0,
                intOr(json, "deducted_levels", intOr(json, "min_deduction_levels", 0))
            );
            boolean requireDiscovery = boolOr(json, "require_prior_discovery", false);
            Identifier reqAdv = null;
            if (json.has("required_advancement") && json.get("required_advancement").isJsonPrimitive()) {
                String rawAdv = json.get("required_advancement").getAsString().trim();
                if (!rawAdv.isEmpty()) {
                    reqAdv = parseIdentifier(rawAdv);
                }
            }
            List<ItemCostOption> additionalCosts = new ArrayList<>();
            if (json.has("additional_costs")) {
                for (JsonElement element : json.getAsJsonArray("additional_costs")) {
                    additionalCosts.add(ItemCostOption.fromJson(element.getAsJsonObject()));
                }
            }
            List<ItemCostOption> altOptions = new ArrayList<>();
            if (json.has("cost_options")) {
                for (JsonElement element : json.getAsJsonArray("cost_options")) {
                    altOptions.add(ItemCostOption.fromJson(element.getAsJsonObject()));
                }
            }
            ItemCostOption primaryCost = json.has("primary_cost")
                ? ItemCostOption.fromJson(json.getAsJsonObject("primary_cost"))
                : (!altOptions.isEmpty()
                    ? altOptions.getFirst()
                    : new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 1, 0, 0, 0, 0, 0, 1.0, List.of()));
            return new ItemTransformation(
                id,
                input,
                isTag,
                output,
                Math.max(1, level),
                Math.max(0, xpCost),
                minRequiredLevel,
                deductedLevels,
                requireDiscovery,
                reqAdv,
                primaryCost,
                List.copyOf(additionalCosts),
                List.copyOf(altOptions),
                materialFree
            );
        }

        public boolean matchesInput(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (id.equals(BLANK_OVERWRITE_ID) && isResidualBook(stack)) {
                return true;
            }
            if (inputIsTag) {
                return stack.is(TagKey.create(Registries.ITEM, inputItemId));
            }
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(inputItemId);
        }

        public Optional<Item> resolveOutputItem() {
            return BuiltInRegistries.ITEM.getOptional(outputItemId).filter(i -> i != Items.AIR);
        }

        public boolean isResolvable() {
            if (resolveOutputItem().isEmpty()) {
                return false;
            }
            if (inputIsTag) {
                boolean hasAnyTagEntry = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, inputItemId))
                    .map(named -> named.size() > 0)
                    .orElse(false);
                if (!hasAnyTagEntry) {
                    return false;
                }
            } else if (!id.equals(BLANK_OVERWRITE_ID)) {
                if (BuiltInRegistries.ITEM.getOptional(inputItemId).filter(i -> i != Items.AIR).isEmpty()) {
                    return false;
                }
            }
            for (ItemCostOption option : additionalCosts) {
                if (!option.hasResolvableItems()) {
                    return false;
                }
            }
            List<ItemCostOption> primarySources = !alternativePrimaryOptions.isEmpty()
                ? alternativePrimaryOptions
                : List.of(primaryCost);
            for (ItemCostOption option : primarySources) {
                if (option.hasResolvableItems()) {
                    return true;
                }
            }
            return false;
        }

        public List<List<ItemStack>> resolvedBundles() {
            if (materialFree) {
                return List.of(List.of());
            }
            // Audit H-03: additional costs expand recursively like extras instead of being
            // collapsed to a single first-candidate stack.
            List<List<ItemStack>> additionalAlternatives = new ArrayList<>();
            additionalAlternatives.add(List.of());
            for (ItemCostOption option : additionalCosts) {
                List<List<ItemStack>> expandedExtras = option.expandBundles(id, level, level, "");
                if (expandedExtras.isEmpty()) {
                    return List.of();
                }
                List<List<ItemStack>> next = new ArrayList<>();
                for (List<ItemStack> base : additionalAlternatives) {
                    for (List<ItemStack> extraBundle : expandedExtras) {
                        List<ItemStack> joined = new ArrayList<>(base.size() + extraBundle.size());
                        joined.addAll(base);
                        joined.addAll(extraBundle);
                        next.add(joined);
                    }
                }
                additionalAlternatives = next;
            }

            List<ItemCostOption> primarySources = !alternativePrimaryOptions.isEmpty()
                ? alternativePrimaryOptions
                : List.of(primaryCost);
            List<List<ItemStack>> bundles = new ArrayList<>();
            for (ItemCostOption opt : primarySources) {
                for (List<ItemStack> expanded : opt.expandBundles(id, level, level, "")) {
                    if (expanded.isEmpty()) continue;
                    for (List<ItemStack> additional : additionalAlternatives) {
                        List<ItemStack> combined = new ArrayList<>(expanded.size() + additional.size());
                        combined.addAll(expanded);
                        combined.addAll(additional);
                        List<ItemStack> normalized = mergeEquivalentCostBundle(combined);
                        if (!normalized.isEmpty()) {
                            bundles.add(normalized);
                        }
                    }
                }
            }
            return List.copyOf(bundles);
        }

        public List<List<ItemStack>> alternativeBundles() {
            return resolvedBundles();
        }

        public ItemStack outputStack(int count) {
            Optional<Item> resolved = resolveOutputItem();
            if (resolved.isEmpty()) {
                ChiseledEnchantingTable.LOGGER.warn("Unknown transformation output {}; refusing transformation", outputItemId);
                return ItemStack.EMPTY;
            }
            return new ItemStack(resolved.get(), Math.max(1, count));
        }

        public ItemStack primaryCostStack(String salt) {
            return primaryCost.toSingleStack(id, level, level, salt);
        }

        public boolean isUnlockedFor(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player) {
            if (!requirePriorDiscovery && requiredAdvancement == null) {
                return true;
            }
            if (player == null) {
                return false;
            }
            if (player.getAbilities().instabuild) {
                return true;
            }
            Item outItem = BuiltInRegistries.ITEM.getValue(outputItemId);
            if (requirePriorDiscovery && outItem != null && outItem != Items.AIR) {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack st = player.getInventory().getItem(slot);
                    if (!st.isEmpty() && st.is(outItem)) {
                        return true;
                    }
                }
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    var stats = sp.getStats();
                    if (stats.getValue(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(outItem)) > 0
                        || stats.getValue(net.minecraft.stats.Stats.ITEM_USED.get(outItem)) > 0
                        || stats.getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(outItem)) > 0) {
                        return true;
                    }
                    if (outItem == Items.TOTEM_OF_UNDYING
                        && hasCompletedAdvancement(sp, Identifier.withDefaultNamespace("adventure/totem_of_undying"))) {
                        return true;
                    }
                }
            }
            if (requiredAdvancement != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                if (hasCompletedAdvancement(sp, requiredAdvancement)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasCompletedAdvancement(net.minecraft.server.level.ServerPlayer sp, Identifier advId) {
            var server = sp.level().getServer();
            if (server == null) {
                return false;
            }
            var holder = server.getAdvancements().get(advId);
            return holder != null && sp.getAdvancements().getOrStartProgress(holder).isDone();
        }

        public List<ItemStack> additionalCostStacks(String salt) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemCostOption option : additionalCosts) {
                stacks.add(option.toSingleStack(id, level, level, salt));
            }
            return List.copyOf(stacks);
        }
    }


    private static CostTables builtInCostTables() {
        return CostTables.fromJson(readBundledJsonObject("enchantment_costs.json"));
    }

    private static BookUpgradeRules builtInBookUpgradeRules() {
        return BookUpgradeRules.fromJson(readBundledJsonObject("book_upgrades.json"));
    }

    public record ItemCostOption(
        Identifier itemId,
        boolean itemIsTag,
        int count,
        int countPerLevel,
        int minCount,
        int maxCount,
        int baseCount,
        int maxLevelCount,
        double levelExponent,
        List<ItemCostOption> extras
    ) {
        public ItemCostOption(
            Identifier itemId,
            int count,
            int countPerLevel,
            int minCount,
            int maxCount,
            int baseCount,
            int maxLevelCount,
            double levelExponent,
            List<ItemCostOption> extras
        ) {
            this(
                itemId, false, count, countPerLevel, minCount, maxCount,
                baseCount, maxLevelCount, levelExponent, extras
            );
        }

        /** Upper bound for any single datapack-declared count field (audit R-02). */
        private static final int MAX_DECLARED_COUNT = MAX_MERGED_COST_COUNT;

        private static int checkedCount(JsonObject json, String key, int fallback) {
            int value = intOr(json, key, fallback);
            if (value < 0 || value > MAX_DECLARED_COUNT) {
                throw new IllegalArgumentException(
                    "Invalid item cost '" + key + "'=" + value + " (allowed 0.." + MAX_DECLARED_COUNT + ")");
            }
            return value;
        }

        static ItemCostOption fromJson(JsonObject json) {
            String rawItem = json.get("item").getAsString();
            boolean isTag = rawItem.startsWith("#");
            Identifier itemId = parseIdentifier(stripTagPrefix(rawItem));
            int count = checkedCount(json, "count", 0);
            int countPerLevel = checkedCount(json, "count_per_level", 0);
            int minCount = checkedCount(json, "min_count", 0);
            int maxCount = checkedCount(json, "max_count", 0);
            int baseCount = checkedCount(json, "base_count", 0);
            int maxLevelCount = checkedCount(json, "max_level_count", 0);
            double levelExponent = doubleOr(json, "level_exponent", 1.3D);
            if (!Double.isFinite(levelExponent) || levelExponent <= 0.0 || levelExponent > 10.0) {
                throw new IllegalArgumentException("Invalid item cost 'level_exponent'=" + levelExponent + " (allowed (0, 10])");
            }
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
            if (json.has("additional_costs")) {
                JsonArray extraArray = json.getAsJsonArray("additional_costs");
                for (JsonElement extra : extraArray) {
                    extras.add(fromJson(extra.getAsJsonObject()));
                }
            }
            return new ItemCostOption(
                itemId, isTag, count, countPerLevel, minCount, maxCount,
                baseCount, maxLevelCount, levelExponent, List.copyOf(extras)
            );
        }

        private List<Item> candidateItems() {
            if (itemIsTag) {
                List<Item> out = new ArrayList<>();
                BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, itemId))
                    .ifPresent(named -> named.stream().map(net.minecraft.core.Holder::value).filter(i -> i != Items.AIR).forEach(out::add));
                if (out.isEmpty()) {
                    ChiseledEnchantingTable.LOGGER.warn("Empty or unknown item cost tag #{}; skipping unresolvable cost option", itemId);
                    return List.of();
                }
                return List.copyOf(out);
            }
            Optional<Item> single = BuiltInRegistries.ITEM.getOptional(itemId).filter(i -> i != Items.AIR);
            if (single.isEmpty()) {
                ChiseledEnchantingTable.LOGGER.warn("Unknown item cost id {}; skipping unresolvable cost option", itemId);
                return List.of();
            }
            return List.of(single.get());
        }

        /** Total bundle cap for nested cartesian expansion (audit H-03/R-02). */
        private static final int MAX_EXPANDED_BUNDLES = 256;
        private static final int MAX_EXTRAS_DEPTH = 16;

        List<List<ItemStack>> expandBundles(Identifier enchantmentId, int level, int maxLevel, String salt) {
            return expandBundles(enchantmentId, level, maxLevel, salt, 0);
        }

        /**
         * Audit H-03: extras recurse fully. Previously every extra collapsed through
         * {@link #toSingleStack} to its first tag candidate and its own nested extras were
         * silently dropped, so datapacks undercharged. Now each extra expands to its own
         * bundle alternatives and the cartesian product is taken (bounded, with a warning
         * on truncation so datapack authors notice).
         */
        private List<List<ItemStack>> expandBundles(Identifier enchantmentId, int level, int maxLevel, String salt, int depth) {
            int computedCount = computeCount(level, maxLevel);
            List<Item> primaries = candidateItems();
            if (primaries.isEmpty()) {
                return List.of();
            }
            if (depth > MAX_EXTRAS_DEPTH) {
                ChiseledEnchantingTable.LOGGER.warn("Item cost extras nested deeper than {}; treating option as invalid.", MAX_EXTRAS_DEPTH);
                return List.of();
            }
            List<List<ItemStack>> partials = new ArrayList<>();
            partials.add(List.of());
            for (ItemCostOption extra : extras) {
                List<List<ItemStack>> extraBundles = extra.expandBundles(enchantmentId, level, maxLevel, salt, depth + 1);
                if (extraBundles.isEmpty()) {
                    return List.of();
                }
                List<List<ItemStack>> next = new ArrayList<>();
                for (List<ItemStack> partial : partials) {
                    for (List<ItemStack> extraBundle : extraBundles) {
                        if (next.size() >= MAX_EXPANDED_BUNDLES) break;
                        List<ItemStack> joined = new ArrayList<>(partial.size() + extraBundle.size());
                        joined.addAll(partial);
                        joined.addAll(extraBundle);
                        next.add(joined);
                    }
                    if (next.size() >= MAX_EXPANDED_BUNDLES) break;
                }
                if ((long) partials.size() * extraBundles.size() > MAX_EXPANDED_BUNDLES) {
                    ChiseledEnchantingTable.LOGGER.warn(
                        "Item cost expansion for {} exceeded {} bundles; truncating alternatives.",
                        enchantmentId, MAX_EXPANDED_BUNDLES);
                }
                partials = next;
                if (partials.isEmpty()) {
                    return List.of();
                }
            }
            List<List<ItemStack>> out = new ArrayList<>();
            for (Item primary : primaries) {
                for (List<ItemStack> extraBundle : partials) {
                    if (out.size() >= MAX_EXPANDED_BUNDLES) break;
                    List<ItemStack> bundle = new ArrayList<>(1 + extraBundle.size());
                    bundle.add(new ItemStack(primary, computedCount));
                    bundle.addAll(extraBundle);
                    List<ItemStack> normalized = mergeEquivalentCostBundle(bundle);
                    if (!normalized.isEmpty()) {
                        out.add(normalized);
                    }
                }
                if (out.size() >= MAX_EXPANDED_BUNDLES) break;
            }
            return List.copyOf(out);
        }

        List<ItemStack> toStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<List<ItemStack>> expanded = expandBundles(enchantmentId, level, maxLevel, salt);
            return expanded.isEmpty() ? List.of() : expanded.getFirst();
        }

        /**
         * Computes the concrete item count required for {@code level} within {@code [1..maxLevel]}.
         *
         * <p><b>Why {@code span = min(1.30, 0.52 + 0.096 * safeMax)} stretches {@code maxLevelCount}:</b>
         * A single {@link CostTables.TierEntry} configures {@code baseCount} and {@code maxLevelCount} per
         * enchantment rarity tier rather than per individual enchantment. Within the same rarity tier,
         * enchantments vary widely in maximum level: single-step enchantments ({@code maxLevel = 1}),
         * mid-ladder enchantments ({@code maxLevel = 3}, {@code span = 0.808}), and full five-tier ladders
         * ({@code maxLevel = 5}, {@code span = 1.000}, matching {@code maxLevelCount} 1:1).
         * Scaling {@code span} linearly with {@code safeMax} (anchored at {@code 1.0} for {@code maxLevel = 5}
         * and capped at {@code 1.30} for over-leveled datapacks up to {@code VIII+}) prevents a 2- or 3-level
         * enchantment's final rank from charging the full endgame 5-level capstone cost on its third step,
         * while allowing datapacks with {@code maxLevel > 5} to scale smoothly up to {@code 1.30x}.</p>
         */
        private int computeCount(int level, int maxLevel) {
            // Audit R-02: long arithmetic so large datapack multipliers cannot overflow
            // into a negative (free) count; results cap at the merge ceiling, where a
            // still-too-large total invalidates the bundle in mergeEquivalentCostBundle.
            long computedCount;
            if (baseCount > 0 && maxLevelCount >= baseCount) {
                int safeMax = Math.max(1, maxLevel);
                int safeLvl = Math.max(1, Math.min(safeMax, level));
                if (safeMax == 1) {
                    computedCount = Math.max(1, Math.round(baseCount + (maxLevelCount - baseCount) * 0.45));
                } else {
                    double span = Math.min(1.30, 0.52 + 0.096 * safeMax);
                    double effectiveMax = baseCount + (maxLevelCount - baseCount) * span;
                    double u = (double) (safeLvl - 1) / (double) (safeMax - 1);
                    double exp = levelExponent > 0.0 ? levelExponent : 1.30;
                    double curved = baseCount + (effectiveMax - baseCount) * Math.pow(u, exp);
                    computedCount = Double.isFinite(curved)
                        ? Math.max(1, Math.round(curved))
                        : MAX_MERGED_COST_COUNT;
                }
            } else if (count > 0) {
                computedCount = count;
            } else if (minCount > 0 && maxCount >= minCount) {
                computedCount = ((long) minCount + (long) maxCount + 1L) / 2L;
            } else {
                computedCount = (long) countPerLevel * Math.max(1, level);
            }
            if (countPerLevel > 0 && (count > 0 || minCount > 0)) {
                computedCount += (long) countPerLevel * Math.max(1, level);
            }
            return (int) Math.max(1L, Math.min((long) MAX_MERGED_COST_COUNT, computedCount));
        }

        boolean hasResolvableItems() {
            if (candidateItems().isEmpty()) {
                return false;
            }
            for (ItemCostOption extra : extras) {
                if (!extra.hasResolvableItems()) {
                    return false;
                }
            }
            return true;
        }

        ItemStack toSingleStack(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<Item> candidates = candidateItems();
            if (candidates.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(candidates.getFirst(), computeCount(level, maxLevel));
        }

        /**
         * 1.0.3 Classic salted count (tag 26.1.2_1.0.3 ItemCostOption#computeCount):
         * ranged min/max counts resolve per player by stable hash instead of the
         * current engine's deterministic midpoint. Only used in classic frames.
         */
        public int saltedCount(int level, int maxLevel, String salt, Identifier enchantmentId) {
            if (baseCount > 0) {
                return computeCount(level, maxLevel);
            }
            int computedCount;
            if (count > 0) {
                computedCount = count;
            } else if (minCount > 0 && maxCount >= minCount) {
                int spread = maxCount - minCount + 1;
                computedCount = minCount + stablePositiveHash(
                    enchantmentId + ":" + Math.max(1, level) + ":" + Math.max(1, maxLevel)
                        + ":" + salt + ":count:" + itemId
                ) % spread;
            } else {
                computedCount = countPerLevel * Math.max(1, level);
            }
            if (countPerLevel > 0 && (count > 0 || minCount > 0)) {
                computedCount += countPerLevel * Math.max(1, level);
            }
            return Math.max(1, computedCount);
        }

        ItemStack toSaltedSingleStack(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<Item> candidates = candidateItems();
            if (candidates.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(candidates.getFirst(), saltedCount(level, maxLevel, salt, enchantmentId));
        }

        /** 1.0.3: one option stack plus each extra at its salted count. */
        List<ItemStack> saltedStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<ItemStack> stacks = new ArrayList<>();
            ItemStack primary = toSaltedSingleStack(enchantmentId, level, maxLevel, salt);
            if (primary.isEmpty()) {
                return List.of();
            }
            stacks.add(primary);
            for (ItemCostOption extra : extras) {
                ItemStack extraStack = extra.toSaltedSingleStack(enchantmentId, level, maxLevel, salt);
                if (!extraStack.isEmpty()) {
                    stacks.add(extraStack);
                }
            }
            return List.copyOf(stacks);
        }
    }

    // ---------- U1: loot injections + bookshelf book pool ----------

    private record LootInjectionTable(
        VanillaChestBooksMode mode,
        List<LootInjection> entries,
        chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics.DepletedTomeChestSpawnConfig chestSpawns
    ) {}

    private static LootInjectionTable parseLootInjectionRoot(JsonObject root) {
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

    private static LootInjectionTable builtInLootInjections() {
        return parseLootInjectionRoot(readBundledJsonObject("loot_injections.json"));
    }

    private static LootInjectionTable loadLootInjections(ResourceManager resourceManager) {
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


    private static BookUpgradeRules loadBookUpgradeRules(ResourceManager resourceManager) {
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

    private static TreasureEpiphanyRules builtInTreasureEpiphanyRules() {
        return TreasureEpiphanyRules.fromJson(readBundledJsonObject("treasure_epiphany.json"), builtInExcludedTreasureIds());
    }

    private static TreasureEpiphanyRules loadTreasureEpiphanyRules(ResourceManager resourceManager) {
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

    private static BookshelfBookPool loadBookshelfBookPool(ResourceManager resourceManager) {
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
        boolean enabled,
        int minBooks, int maxBooks,
        double enchantedBookProbability,
        String enchantPoolSource,
        boolean useBaseWeight,
        boolean useVanillaWeightFactor,
        double maxLevelWeightRatio,
        List<List<LootInjectionEntry>> enchantSets
    ) {
        public BookshelfBookPool(
            int minBooks, int maxBooks,
            double enchantedBookProbability,
            String enchantPoolSource,
            boolean useBaseWeight,
            boolean useVanillaWeightFactor,
            double maxLevelWeightRatio,
            List<List<LootInjectionEntry>> enchantSets
        ) {
            this(
                true,
                minBooks, maxBooks,
                enchantedBookProbability,
                enchantPoolSource,
                useBaseWeight,
                useVanillaWeightFactor,
                maxLevelWeightRatio,
                enchantSets
            );
        }

        static BookshelfBookPool emptyFallback() {
            return new BookshelfBookPool(true, 3, 5, 0.03, "loot_injections", true, true, 0.1, List.of());
        }

        static BookshelfBookPool fromJson(JsonObject root) {
            boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
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
                enabled,
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

    public static boolean disenchantEnabled() {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).enabled();
    }

    public static boolean disenchantAllowCursePurge() {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).allowCursePurge();
    }

    public static double disenchantRefundScale() {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).xpRefundScale();
    }

    public static double disenchantExternalRefundScale() {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).externalXpRefundScale();
    }

    public static int targetedStripXpCost(Identifier id, int level, int maxLevel) {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).targetedStripXpCost(level, maxLevel);
    }

    public static List<List<ItemStack>> targetedStripCostOptions(Identifier id, int level, int maxLevel) {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).targetedStripCostOptions(id, level, maxLevel);
    }

    public static int cursePurgeXpCost() {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).curseXpCost();
    }

    public static List<List<ItemStack>> cursePurgeCostOptions(Identifier id) {
        return (runtimeFrame == null ? disenchantingRules : runtimeFrame.disenchantingRules()).cursePurgeCostOptions(id);
    }

    public static chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig() {
        return (runtimeFrame == null ? shelfGatesConfig : runtimeFrame.shelfGatesConfig());
    }

    public static chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig() {
        return (runtimeFrame == null ? libraryResonanceConfig : runtimeFrame.libraryResonanceConfig());
    }

    public static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritualConfig() {
        return (runtimeFrame == null ? ritualConfig : runtimeFrame.ritualConfig());
    }

    private static chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config builtInRitualConfig() {
        return chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.fromJson(
            readBundledJsonObject("library_resonance.json")
        );
    }

    private record LibraryResonanceAndRitual(
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config resonance,
        chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config ritual
    ) {}

    private static chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config loadShelfGatesConfig(
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

    private static LibraryResonanceAndRitual loadLibraryResonanceAndRitual(
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

    private static DisenchantingRules builtInDisenchantingRules() {
        return DisenchantingRules.fromJson(
            readBundledJsonObject("disenchanting.json"),
            () -> readBundledJsonObject("disenchanting.json")
        );
    }

    private static DisenchantingRules loadDisenchantingRules(ResourceManager resourceManager) {
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
}
