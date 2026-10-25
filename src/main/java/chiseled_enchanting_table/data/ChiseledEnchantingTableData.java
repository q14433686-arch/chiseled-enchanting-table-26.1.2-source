package chiseled_enchanting_table.data;

import static chiseled_enchanting_table.data.DataJson.*;
import static chiseled_enchanting_table.data.DataTableLoader.*;

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

    public static Set<String> extractConfigKeysFromDatapackJson(String tableName, JsonObject root) {
        return DatapackKeyExtractor.extractConfigKeysFromDatapackJson(tableName, root);
    }

    /** Accepts both {@code "#namespace:path"} (data-pack tag convention) and a bare id. */

    private static Map<Identifier, ItemTransformation> builtInItemTransformations() {
        return parseItemTransformations(readBundledJsonObject("item_transformations.json"));
    }

    /** One {@code tags} entry: every enchantment in {@code tagId} unlocks at {@code level}. */
    public record DefaultEnchantmentTagRule(Identifier tagId, int level) {}

    // ---------- U1: loot injections + bookshelf book pool ----------

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

}
