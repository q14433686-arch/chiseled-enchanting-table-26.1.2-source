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
    private static final Identifier TREASURE_EPIPHANY_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/treasure_epiphany.json");
    private static final Identifier DISENCHANTING_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/disenchanting.json");
    private static final Identifier SHELF_GATES_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/shelf_gates.json");
    private static final Identifier LIBRARY_RESONANCE_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/library_resonance.json");
    private static final Identifier PACK_POLICY_ID = ChiseledEnchantingTable.identifier("chiseled_enchanting_table/pack_policy.json");

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

    private static Map<Identifier, ItemTransformation> rawItemTransformations = builtInItemTransformations();
    private static VanillaChestBooksMode rawVanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
    private static BookshelfBookPool rawBookshelfBookPool = BookshelfBookPool.emptyFallback();
    private static BookUpgradeRules rawBookUpgradeRules = BookUpgradeRules.builtInFallback();
    private static TreasureEpiphanyRules rawTreasureEpiphanyRules = TreasureEpiphanyRules.builtInFallback();
    private static DisenchantingRules rawDisenchantingRules = DisenchantingRules.builtInFallback();
    private static chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config rawShelfGatesConfig =
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config.DEFAULT;
    private static chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config rawLibraryResonanceConfig =
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;

    private static Set<EnchantmentWithLevel> defaultEnchantmentValues = builtInDefaultEnchantments();
    private static List<DefaultEnchantmentTagRule> defaultEnchantmentTags = builtInDefaultEnchantmentTags();
    private static Set<Identifier> defaultExcludedIds = builtInExcludedTreasureIds();
    private static List<Identifier> defaultExcludedTags = builtInExcludedTags();
    private static CostTables costTables = CostTables.builtInFallback();
    private static Map<Identifier, ItemTransformation> itemTransformations = rawItemTransformations;
    private static VanillaChestBooksMode vanillaChestBooksMode = rawVanillaChestBooksMode;
    private static List<LootInjection> lootInjections = List.of();
    private static BookshelfBookPool bookshelfBookPool = rawBookshelfBookPool;
    private static BookUpgradeRules bookUpgradeRules = rawBookUpgradeRules;
    private static TreasureEpiphanyRules treasureEpiphanyRules = rawTreasureEpiphanyRules;
    private static DisenchantingRules disenchantingRules = rawDisenchantingRules;
    private static chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig =
        rawShelfGatesConfig;
    private static chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig =
        rawLibraryResonanceConfig;

    public record RuntimeFrame(Map<Identifier, ItemTransformation> itemTransformations,
        VanillaChestBooksMode vanillaChestBooksMode,
        BookshelfBookPool bookshelfBookPool,
        BookUpgradeRules bookUpgradeRules,
        TreasureEpiphanyRules treasureEpiphanyRules,
        DisenchantingRules disenchantingRules,
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig,
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig,
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
        Set<Identifier> excluded = new HashSet<>((runtimeFrame == null ? defaultExcludedIds : runtimeFrame.defaultExcludedIds()));
        excluded.addAll((runtimeFrame == null ? treasureEpiphanyRules : runtimeFrame.treasureEpiphanyRules()).explicitTreasureIds());
        for (Identifier exTag : (runtimeFrame == null ? defaultExcludedTags : runtimeFrame.defaultExcludedTags())) {
            TagKey<Enchantment> tagKey = TagKey.create(Registries.ENCHANTMENT, exTag);
            enchantmentLookup.get(tagKey).ifPresent(named -> named.stream().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> excluded.add(key.identifier()))
            ));
        }

        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (EnchantmentWithLevel explicit : (runtimeFrame == null ? defaultEnchantmentValues : runtimeFrame.defaultEnchantmentValues())) {
            if (!excluded.contains(explicit.enchantment_id())) {
                result.add(explicit);
            }
        }
        for (DefaultEnchantmentTagRule rule : (runtimeFrame == null ? defaultEnchantmentTags : runtimeFrame.defaultEnchantmentTags())) {
            TagKey<Enchantment> tagKey = TagKey.create(Registries.ENCHANTMENT, rule.tagId());
            enchantmentLookup.get(tagKey).ifPresent(named -> named.stream().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> {
                    if (!excluded.contains(key.identifier())) {
                        result.add(new EnchantmentWithLevel(key.identifier(), rule.level()));
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
        return (runtimeFrame == null ? bookUpgradeRules : runtimeFrame.bookUpgradeRules()).researchXpCost(target, Math.max(1, maxLevel), (runtimeFrame == null ? costTables : runtimeFrame.costTables()).xpCost(target, Math.max(1, maxLevel)));
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

    public static Set<EnchantmentWithLevel> transformationEnchantmentsFor(ItemStack input) {
        Set<EnchantmentWithLevel> result = new HashSet<>();
        for (ItemTransformation transformation : (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).values()) {
            if (transformation.matchesInput(input)) {
                result.add(new EnchantmentWithLevel(transformation.id(), transformation.level()));
            }
        }
        return result;
    }


    public static Optional<ItemTransformation> itemTransformation(Identifier id) {
        return Optional.ofNullable((runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id));
    }

    public static Set<Identifier> transformationIds() {
        return (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).keySet();
    }

    /**
     * Compact display table (id, input item, output item, level) for every loaded transformation.
     * The screen handler syncs this to the client when the menu opens so client UI no longer has to
     * rely on its own local data load matching the server's (important for datapack transformations
     * on dedicated servers). Returns a list of {@code Object[]} {id, inputItemId, outputItemId, level}
     * to avoid a compile dependency from the data package onto the menu package.
     */
    public static boolean isTransformationUnlocked(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player, Identifier id) {
        ItemTransformation rule = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        return rule == null || rule.isUnlockedFor(player);
    }

    public static List<Object[]> transformationDisplayTuples() {
        return transformationDisplayTuples(null);
    }

    public static List<Object[]> transformationDisplayTuples(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player) {
        List<Object[]> result = new ArrayList<>();
        for (ItemTransformation transformation : (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).values()) {
            result.add(new Object[] {
                transformation.id(),
                transformation.inputItemId(),
                transformation.outputItemId(),
                transformation.level(),
                transformation.inputIsTag(),
                transformation.minRequiredLevel(),
                transformation.deductedLevels(),
                transformation.isUnlockedFor(player)
            });
        }
        return List.copyOf(result);
    }

    public static boolean isTransformation(Identifier id) {
        return (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).containsKey(id);
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

    public static List<List<ItemStack>> transformationCostOptions(Identifier id) {
        ItemTransformation transformation = (runtimeFrame == null ? itemTransformations : runtimeFrame.itemTransformations()).get(id);
        if (transformation == null) return List.of();
        if (!transformation.alternativePrimaryOptions().isEmpty()) {
            return transformation.alternativeBundles();
        }
        List<ItemStack> bundle = new ArrayList<>();
        bundle.add(transformation.primaryCostStack(""));
        bundle.addAll(transformation.additionalCostStacks(""));
        return List.of(List.copyOf(bundle));
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
     * Loot-table injection rules loaded from {@code loot_injections.json}.
     * <p>Consumed by {@code mechanics.loot.EnchantedBookLootInjector} at server start; the JSON is
     * the sole source of truth (Java owns dispatch, JSON owns which loot table gets which
     * enchanted book at what weight). Empty by default so an integrator can opt-in.</p>
     */
    public static List<LootInjection> lootInjections() {
        return (runtimeFrame == null ? lootInjections : runtimeFrame.lootInjections());
    }

    /**
     * Weighted 6-slot fill rules for chiseled bookshelves placed by
     * {@code structureProcessor.BookshelfReplacerProcessor}. Also loaded from JSON only.
     */
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
        DefaultEnchantmentTable loadedDefaults = loadDefaultEnchantments(resourceManager);
        defaultEnchantmentValues = loadedDefaults.values();
        defaultEnchantmentTags = loadedDefaults.tags();
        defaultExcludedIds = loadedDefaults.excludedIds();
        defaultExcludedTags = loadedDefaults.excludedTags();
        costTables = loadCostTables(resourceManager);
        rawItemTransformations = loadItemTransformations(resourceManager);
        LootInjectionTable loadedLoot = loadLootInjections(resourceManager);
        rawVanillaChestBooksMode = loadedLoot.mode();
        lootInjections = loadedLoot.entries();
        rawBookshelfBookPool = loadBookshelfBookPool(resourceManager);
        rawBookUpgradeRules = loadBookUpgradeRules(resourceManager);
        rawTreasureEpiphanyRules = loadTreasureEpiphanyRules(resourceManager);
        rawDisenchantingRules = loadDisenchantingRules(resourceManager);
        rawShelfGatesConfig = loadShelfGatesConfig(resourceManager);
        rawLibraryResonanceConfig = loadLibraryResonanceConfig(resourceManager);
        chiseled_enchanting_table.config.ModRuntimeConfig.setServerPackPolicy(loadPackPolicy(resourceManager));
        applyRuntimeConfig(chiseled_enchanting_table.config.ModRuntimeConfig.get());
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
        int maxDirect = true ? cfg.maxDirectBookshelves() : sg.maxDirectBookshelves();
        int t2 = Math.min(maxDirect, true ? cfg.tier2Bookshelves() : sg.tier2Bookshelves());
        int t1 = Math.min(t2, true ? cfg.tier1Bookshelves() : sg.tier1Bookshelves());
        int batchCap = true ? cfg.transformationMaxBatchSize() : sg.baseBatchSize();
        chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config shelfGatesConfig = new chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config(
            true ? cfg.directGatesEnabled() : sg.directGatesEnabled(),
            maxDirect,
            sg.gamma(),
            true ? cfg.blankMaxTierMode() : sg.blankMaxTierMode(),
            true ? cfg.horizontalScanRadius() : sg.horizontalScanRadius(),
            true ? cfg.verticalScanRadius() : sg.verticalScanRadius(),
            sg.overrides(),
            t1,
            t2,
            sg.tier2MaterialMultiplier(),
            true ? cfg.tier3QualifyingEnchantments() : sg.tier3QualifyingEnchantments(),
            batchCap,
            true ? cfg.transformationMaxBatchSize() : sg.tier3BatchSize(),
            true ? cfg.vanillaTableMode() : sg.vanillaTableMode(),
            true ? cfg.requireBreathingRoom() : sg.requireBreathingRoom(),
            true ? cfg.minOpenAdjacentBlocks() : sg.minOpenAdjacentBlocks(),
            true ? cfg.requireLineOfSight() : sg.requireLineOfSight()
        );

        var lr = rawLibraryResonanceConfig;
        int woodDisc = true ? cfg.maxWoodenDiscountPercent() : lr.maxWoodenDiscountPercent();
        int totalDisc = Math.max(
            woodDisc,
            true ? cfg.maxTotalXpDiscountPercent() : lr.maxTotalXpDiscountPercent()
        );
        chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config libraryResonanceConfig = new chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config(
            lr.minDiscountBookshelves(),
            Math.max(lr.minDiscountBookshelves() + 1, maxDirect),
            woodDisc,
            lr.maxGlobalMasteryDiscountPercent(),
            lr.maxBaseXpDiscountPercent(),
            lr.maxSpecificEnchantBonusPercent(),
            lr.specificEnchantExponent(),
            totalDisc,
            lr.masteryUnitExponent(),
            lr.globalMasteryMultiplier(),
            lr.masterTomeUpgradeMaterialMultiplier(),
            lr.shelfUpgradeMaterialMultiplier(),
            lr.epiphanyProbabilityGlobalWeight(),
            lr.epiphanyProbabilityAffinityWeight(),
            lr.blankBookAffinityWeight(),
            lr.epiphanyShelfScoreFactor(),
            lr.epiphanyGlobalMasteryScoreFactor(),
            lr.epiphanyAffinityMasteryScoreFactor(),
            lr.epiphanyTier2MaxProb(),
            lr.epiphanyTier2ScoreDivisor(),
            lr.epiphanyTier2MasteryBonus(),
            lr.epiphanyTierPlusMaxProb(),
            lr.epiphanyTierPlusScoreDivisor(),
            lr.epiphanyTierPlusMasteryBonus(),
            lr.incrementalStepCreditFactor(),
            true ? cfg.tomeConsumptionMode() : lr.tomeConsumptionMode(),
            true ? cfg.maxTomeUses() : lr.maxTomeUses(),
            true ? cfg.bookCopyWearCost() : lr.bookCopyWearCost(),
            true ? cfg.tomeDepleteBehavior() : lr.tomeDepleteBehavior(),
            true ? cfg.tomeRegenEnabled() : lr.tomeRegenEnabled(),
            true ? cfg.tomeRegenBaseSeconds() : lr.tomeRegenBaseSeconds(),
            true ? cfg.tomeRegenTier1Shelves() : lr.tomeRegenTier1Shelves(),
            true ? cfg.tomeRegenTier1Seconds() : lr.tomeRegenTier1Seconds(),
            true ? cfg.tomeRegenTier2Shelves() : lr.tomeRegenTier2Shelves(),
            true ? cfg.tomeRegenTier2Seconds() : lr.tomeRegenTier2Seconds()
        );

        var bu = rawBookUpgradeRules;
        BookUpgradeRules bookUpgradeRules = new BookUpgradeRules(
            true ? cfg.bookCombineEnabled() : bu.combineEnabled(),
            true ? cfg.bookCombineXpMultiplier() : bu.combineXpMultiplier(),
            true ? cfg.bookResearchEnabled() : bu.researchEnabled(),
            true ? cfg.bookResearchXpMultiplier() : bu.researchXpMultiplier(),
            bu.researchMaterialMultiplier(),
            bu.defaultResearchCost(),
            bu.researchOverrides()
        );

        var ep = rawTreasureEpiphanyRules;
        double epBase = true ? cfg.epiphanyBaseChance() : ep.baseChance();
        double epMax = Math.max(epBase, true ? cfg.epiphanyMaxChance() : ep.maxChance());
        int frzTicks = true
            ? Math.max(200, cfg.epiphanyFreezeDurationSeconds() * 20)
            : ep.freezeDurationTicks();
        int maxFrzTicks = Math.max(
            frzTicks,
            true
                ? Math.max(200, cfg.epiphanyMaxFreezeSeconds() * 20)
                : ep.maxFreezeTicks()
        );
        TreasureEpiphanyRules treasureEpiphanyRules = new TreasureEpiphanyRules(
            true ? cfg.epiphanyMinBookshelves() : ep.minRegularBookshelves(),
            epBase,
            ep.chancePerExtraBookshelf(),
            ep.chancePerMasterTome(),
            epMax,
            true ? cfg.epiphanyMaxSlotsCap() : ep.maxSlotsCap(),
            ep.scorePerExtraSlot(),
            true ? Math.max(200, cfg.epiphanyCycleSeconds() * 20) : ep.cycleTicks(),
            frzTicks,
            maxFrzTicks,
            true ? cfg.epiphanyFreezeXpLevels() : ep.freezeXpLevels(),
            ep.freezeOptions(),
            true ? cfg.epiphanyRerollXpLevels() : ep.rerollXpLevels(),
            ep.rerollOptions(),
            ep.explicitTreasureIds(),
            ep.treasureTagIds(),
            ep.excludedTagIds(),
            ep.defaultXpLevels(),
            ep.defaultMaterialCost(),
            ep.entries()
        );

        var dis = rawDisenchantingRules;
        double refScale = true ? cfg.xpRefundScale() : dis.xpRefundScale();
        double extScale = Math.min(
            refScale,
            true ? cfg.externalXpRefundScale() : dis.externalXpRefundScale()
        );
        int sMin = true ? cfg.stripXpMin() : dis.stripXpMin();
        int sMax = Math.max(sMin, true ? cfg.stripXpMax() : dis.stripXpMax());
        DisenchantingRules disenchantingRules = new DisenchantingRules(
            true ? cfg.disenchantEnabled() : dis.enabled(),
            true ? cfg.allowCursePurge() : dis.allowCursePurge(),
            refScale,
            extScale,
            sMin,
            sMax,
            dis.stripMaterialCost(),
            true ? cfg.cursePurgeXpCost() : dis.curseXpCost(),
            dis.curseMaterialCost()
        );

        Map<Identifier, ItemTransformation> itemTransformations;
        if (true && !cfg.requirePriorDiscovery()) {
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
                        t.alternativePrimaryOptions()
                    )
                );
            }
            itemTransformations = Map.copyOf(unlockedMap);
        } else {
            itemTransformations = rawItemTransformations;
        }

        VanillaChestBooksMode vanillaChestBooksMode = true
            ? cfg.vanillaChestBooksMode()
            : rawVanillaChestBooksMode;

        var bp = rawBookshelfBookPool;
        int minB = true ? cfg.bookshelfMinBooks() : bp.minBooks();
        int maxB = Math.max(minB, true ? cfg.bookshelfMaxBooks() : bp.maxBooks());
        BookshelfBookPool bookshelfBookPool = new BookshelfBookPool(
            true ? cfg.bookshelfPoolEnabled() : bp.enabled(),
            minB,
            maxB,
            true ? cfg.bookshelfEnchantedProb() : bp.enchantedBookProbability(),
            bp.enchantPoolSource(),
            bp.useBaseWeight(),
            bp.useVanillaWeightFactor(),
            bp.maxLevelWeightRatio(),
            bp.enchantSets()
        );

        chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication.verifyRuntimeConservation(
            libraryResonanceConfig,
            disenchantingRules.xpRefundScale()
        );
        RuntimeFrame next = new RuntimeFrame(itemTransformations, vanillaChestBooksMode, bookshelfBookPool, bookUpgradeRules, treasureEpiphanyRules, disenchantingRules, shelfGatesConfig, libraryResonanceConfig, costTables, defaultEnchantmentValues, defaultEnchantmentTags, defaultExcludedIds, defaultExcludedTags, lootInjections, cfg, policy, prepared.snapshot());
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
        // The table loader uses getResource (whole-file replacement), so only the selected layer
        // may govern fields. Never lock keys from a shadowed lower layer or assume stack index 0.
        var selected = resourceManager.getResource(tableId);
        if (selected.isEmpty()) return;
        var resource = selected.get();
        try (Reader reader = resource.openAsReader()) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            String source = resource.sourcePackId();
            boolean own = source.equals("mod/chiseled_enchanting_table") || source.equals("chiseled_enchanting_table");
            // Fabric's aggregate built-in pack may not expose a per-mod identity. Compare its
            // selected content to our bundled resource rather than treating any first layer as ours.
            if (source.equals("fabric") || source.equals("vanilla")) {
                try (var stream = ChiseledEnchantingTableData.class.getResourceAsStream(
                    "/data/" + tableId.getNamespace() + "/" + tableId.getPath())) {
                    if (stream != null) own = object.equals(JsonParser.parseReader(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
            if (!own) outKeys.addAll(extractConfigKeysFromDatapackJson(tableName, object));
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to inspect effective table " + tableId, error);
        }
    }

    public static Set<String> extractConfigKeysFromDatapackJson(String tableName, JsonObject root) {
        if (root == null) {
            return Set.of();
        }
        Set<String> keys = new java.util.LinkedHashSet<>();
        switch (tableName) {
            case "shelf_gates" -> {
                String[] directKeys = {
                    "direct_gates_enabled", "blank_max_tier_mode", "max_direct_bookshelves",
                    "horizontal_scan_radius", "vertical_scan_radius", "require_breathing_room",
                    "min_open_adjacent_blocks", "require_line_of_sight", "vanilla_table_mode",
                    "tier1_bookshelves", "tier2_bookshelves", "tier3_qualifying_enchantments"
                };
                for (String k : directKeys) {
                    if (root.has(k)) keys.add(k);
                }
                if (root.has("base_batch_size") || root.has("tier3_batch_size")) {
                    keys.add("transformation_max_batch_size");
                }
            }
            case "library_resonance" -> {
                JsonObject wooden = root.has("wooden_bookshelves") && root.get("wooden_bookshelves").isJsonObject()
                    ? root.getAsJsonObject("wooden_bookshelves") : null;
                JsonObject mastery = root.has("chiseled_mastery") && root.get("chiseled_mastery").isJsonObject()
                    ? root.getAsJsonObject("chiseled_mastery") : null;
                if (hasAny(root, wooden, "max_wooden_discount_percent")) keys.add("max_wooden_discount_percent");
                if (hasAny(root, mastery, "max_total_xp_discount_percent")) keys.add("max_total_xp_discount_percent");
                String[] masteryKeys = {
                    "tome_consumption_mode", "max_tome_uses", "book_copy_wear_cost", "tome_deplete_behavior",
                    "tome_regen_enabled", "tome_regen_base_seconds", "tome_regen_tier1_shelves",
                    "tome_regen_tier1_seconds", "tome_regen_tier2_shelves", "tome_regen_tier2_seconds"
                };
                for (String k : masteryKeys) {
                    if (hasAny(root, mastery, k)) keys.add(k);
                }
            }
            case "book_upgrades" -> {
                JsonObject comb = root.has("combine_route") && root.get("combine_route").isJsonObject()
                    ? root.getAsJsonObject("combine_route") : null;
                JsonObject res = root.has("research_route") && root.get("research_route").isJsonObject()
                    ? root.getAsJsonObject("research_route") : null;
                if (comb != null && comb.has("enabled")) keys.add("book_combine_enabled");
                if (comb != null && comb.has("xp_cost_multiplier_of_vanilla_like")) keys.add("book_combine_xp_multiplier");
                if (res != null && res.has("enabled")) keys.add("book_research_enabled");
                if (res != null && res.has("xp_cost_multiplier_of_direct_enchant")) keys.add("book_research_xp_multiplier");
            }
            case "treasure_epiphany" -> {
                if (root.has("min_regular_bookshelves")) keys.add("epiphany_min_bookshelves");
                if (root.has("base_chance")) keys.add("epiphany_base_chance");
                if (root.has("max_chance")) keys.add("epiphany_max_chance");
                if (root.has("max_slots_cap")) keys.add("epiphany_max_slots_cap");
                if (root.has("cycle_ticks")) keys.add("epiphany_cycle_seconds");
                if (root.has("freeze_xp_levels")) keys.add("epiphany_freeze_xp_levels");
                if (root.has("freeze_duration_ticks")) keys.add("epiphany_freeze_duration_seconds");
                if (root.has("max_freeze_ticks")) keys.add("epiphany_max_freeze_seconds");
                if (root.has("reroll_xp_levels")) keys.add("epiphany_reroll_xp_levels");
            }
            case "disenchanting" -> {
                if (root.has("enabled")) keys.add("disenchant_enabled");
                if (root.has("xp_refund_scale")) keys.add("xp_refund_scale");
                if (root.has("external_xp_refund_scale")) keys.add("external_xp_refund_scale");
                if (root.has("allow_curse_purge")) keys.add("allow_curse_purge");
                JsonObject strip = root.has("targeted_strip") && root.get("targeted_strip").isJsonObject()
                    ? root.getAsJsonObject("targeted_strip") : null;
                if (strip != null && (strip.has("xp_cost_min") || strip.has("xp_levels"))) keys.add("strip_xp_min");
                if (strip != null && strip.has("xp_cost_max")) keys.add("strip_xp_max");
                JsonObject curse = root.has("curse_purge") && root.get("curse_purge").isJsonObject()
                    ? root.getAsJsonObject("curse_purge") : null;
                if (curse != null && (curse.has("xp_cost") || curse.has("xp_levels"))) keys.add("curse_purge_xp_cost");
            }
            case "item_transformations" -> {
                if (root.has("require_prior_discovery")) {
                    keys.add("require_prior_discovery");
                } else if (root.has("values") && root.get("values").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("values")) {
                        if (el.isJsonObject() && el.getAsJsonObject().has("require_prior_discovery")) {
                            keys.add("require_prior_discovery");
                            break;
                        }
                    }
                }
            }
            case "loot_injections" -> {
                if (root.has("vanilla_chest_books")) keys.add("vanilla_chest_books_mode");
            }
            case "bookshelf_book_pool" -> {
                if (root.has("enabled")) keys.add("bookshelf_pool_enabled");
                if (root.has("min_books")) keys.add("bookshelf_min_books");
                if (root.has("max_books")) keys.add("bookshelf_max_books");
                if (root.has("enchanted_book_probability")) keys.add("bookshelf_enchanted_prob");
            }
            default -> {}
        }
        return Set.copyOf(keys);
    }

    private static boolean hasAny(JsonObject root, JsonObject sub, String key) {
        return root.has(key) || (sub != null && sub.has(key));
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
        Map<Identifier, ItemTransformation> fallback = new HashMap<>();
        JsonObject root = readBundledJsonObject("item_transformations.json");
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
    private record DefaultEnchantmentTagRule(Identifier tagId, int level) {}

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
        List<ItemCostOption> alternativePrimaryOptions
    ) {
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
                alternativePrimaryOptions
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
                alternativePrimaryOptions
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
                List.copyOf(altOptions)
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

        public List<List<ItemStack>> alternativeBundles() {
            List<List<ItemStack>> bundles = new ArrayList<>(alternativePrimaryOptions.size());
            for (ItemCostOption opt : alternativePrimaryOptions) {
                bundles.add(opt.toStacks(id, level, level, ""));
            }
            return List.copyOf(bundles);
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
            return new BookUpgradeRules(combineEnabled, combineXpMultiplier, researchEnabled, researchXpMultiplier, researchMaterialMultiplier, defaultResearchCost, Map.copyOf(overrides));
        }

        static BookUpgradeRules builtInFallback() {
            return fromJson(readBundledJsonObject("book_upgrades.json"));
        }

        int combineXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int)Math.round(directXpCost * combineXpMultiplier));
        }

        int researchXpCost(EnchantmentWithLevel target, int maxLevel, int directXpCost) {
            return Math.max(1, (int)Math.round(directXpCost * researchXpMultiplier));
        }

        List<List<ItemStack>> researchCostOptions(EnchantmentWithLevel target, int maxLevel, List<List<ItemStack>> directCostOptions) {
            ItemCostRule rule = researchOverrides.getOrDefault(target.enchantment_id(), defaultResearchCost);
            List<List<ItemStack>> themed = rule == null ? List.of() : rule.toBundles(target.enchantment_id(), target.enchantment_level(), maxLevel);
            if (!themed.isEmpty()) {
                return themed;
            }
            if (!directCostOptions.isEmpty()) {
                List<List<ItemStack>> discountedBundles = new ArrayList<>(directCostOptions.size());
                for (List<ItemStack> direct : directCostOptions) {
                    List<ItemStack> discounted = new ArrayList<>(direct.size());
                    for (ItemStack stack : direct) {
                        ItemStack copy = stack.copy();
                        copy.setCount(Math.max(1, (int)Math.round(copy.getCount() * researchMaterialMultiplier)));
                        discounted.add(copy);
                    }
                    discountedBundles.add(List.copyOf(discounted));
                }
                return List.copyOf(discountedBundles);
            }
            return List.of();
        }
    }

    private record CostTables(XpCostRule xpCostRule, ItemCostRule defaultItemCost, Map<Identifier, ItemCostRule> itemCosts) {
        static CostTables fromJson(JsonObject root) {
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

        static CostTables builtInFallback() {
            return fromJson(readBundledJsonObject("enchantment_costs.json"));
        }

        List<List<ItemStack>> itemCostOptions(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
            ItemCostRule itemCost = itemCosts.getOrDefault(enchantmentWithLevel.enchantment_id(), defaultItemCost);
            return itemCost.toBundles(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel);
        }

        int xpCost(EnchantmentWithLevel enchantmentWithLevel, int maxLevel) {
            return xpCostRule.cost(enchantmentWithLevel.enchantment_id(), enchantmentWithLevel.enchantment_level(), maxLevel);
        }
    }

    private record XpCostRule(int levelOneCost, int commonMaxCost, int rareSingleLevelCost, Map<Identifier, Map<Integer, Integer>> overrides) {
        static XpCostRule defaults() {
            return new XpCostRule(2, 10, 12, Map.of());
        }

        static XpCostRule fromJson(JsonObject json) {
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
            return new ItemCostRule(List.of(new ItemCostOption(itemId, count, countPerLevel, minCount, maxCount, 0, 0, 1.0, List.of())), Map.of(), null);
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
            } else if (json.has("cost_options")) {
                JsonArray optionArray = json.getAsJsonArray("cost_options");
                for (JsonElement option : optionArray) {
                    options.add(ItemCostOption.fromJson(option.getAsJsonObject()));
                }
            } else if (json.has("item")) {
                options.add(ItemCostOption.fromJson(json));
            }

            if (options.isEmpty()) {
                options.add(new ItemCostOption(Identifier.withDefaultNamespace("lapis_lazuli"), 0, 0, 0, 0, 3, 20, 1.3, List.of()));
            }
            return new ItemCostRule(List.copyOf(options), Map.copyOf(levelOverrides), maxLevelOverride);
        }

        List<List<ItemStack>> toBundles(Identifier enchantmentId, int level, int maxLevel) {
            ItemCostRule override = levelOverrides.get(level);
            if (override != null) {
                return override.toBundles(enchantmentId, level, maxLevel);
            }
            if (level >= maxLevel && maxLevelOverride != null) {
                return maxLevelOverride.toBundles(enchantmentId, level, maxLevel);
            }
            List<List<ItemStack>> bundles = new ArrayList<>(options.size());
            for (ItemCostOption opt : options) {
                bundles.addAll(opt.expandBundles(enchantmentId, level, maxLevel, ""));
            }
            return List.copyOf(bundles);
        }

        List<ItemStack> toStacks(Identifier enchantmentId, int level, int maxLevel, String salt) {
            List<List<ItemStack>> bundles = toBundles(enchantmentId, level, maxLevel);
            return bundles.isEmpty() ? List.of() : bundles.getFirst();
        }
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

        static ItemCostOption fromJson(JsonObject json) {
            String rawItem = json.get("item").getAsString();
            boolean isTag = rawItem.startsWith("#");
            Identifier itemId = parseIdentifier(stripTagPrefix(rawItem));
            int count = intOr(json, "count", 0);
            int countPerLevel = intOr(json, "count_per_level", 0);
            int minCount = intOr(json, "min_count", 0);
            int maxCount = intOr(json, "max_count", 0);
            int baseCount = intOr(json, "base_count", 0);
            int maxLevelCount = intOr(json, "max_level_count", 0);
            double levelExponent = doubleOr(json, "level_exponent", 1.3D);
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
                    .ifPresent(named -> named.stream().forEach(h -> out.add(h.value())));
                if (!out.isEmpty()) {
                    return List.copyOf(out);
                }
            }
            Item single = BuiltInRegistries.ITEM.getValue(itemId);
            if (single == null || single == Items.AIR) {
                ChiseledEnchantingTable.LOGGER.warn("Unknown item cost id {}; using lapis_lazuli fallback", itemId);
                return List.of(Items.LAPIS_LAZULI);
            }
            return List.of(single);
        }

        List<List<ItemStack>> expandBundles(Identifier enchantmentId, int level, int maxLevel, String salt) {
            int computedCount = computeCount(level, maxLevel);
            List<Item> primaries = candidateItems();
            List<List<ItemStack>> out = new ArrayList<>(primaries.size());
            for (Item primary : primaries) {
                List<ItemStack> bundle = new ArrayList<>(1 + extras.size());
                bundle.add(new ItemStack(primary, computedCount));
                for (ItemCostOption extra : extras) {
                    bundle.add(extra.toSingleStack(enchantmentId, level, maxLevel, salt));
                }
                out.add(List.copyOf(bundle));
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
            int computedCount;
            if (baseCount > 0 && maxLevelCount >= baseCount) {
                int safeMax = Math.max(1, maxLevel);
                int safeLvl = Math.max(1, Math.min(safeMax, level));
                if (safeMax == 1) {
                    computedCount = Math.max(1, (int) Math.round(baseCount + (maxLevelCount - baseCount) * 0.45));
                } else {
                    double span = Math.min(1.30, 0.52 + 0.096 * safeMax);
                    double effectiveMax = baseCount + (maxLevelCount - baseCount) * span;
                    double u = (double) (safeLvl - 1) / (double) (safeMax - 1);
                    double exp = levelExponent > 0.0 ? levelExponent : 1.30;
                    computedCount = Math.max(1, (int) Math.round(baseCount + (effectiveMax - baseCount) * Math.pow(u, exp)));
                }
            } else if (count > 0) {
                computedCount = count;
            } else if (minCount > 0 && maxCount >= minCount) {
                computedCount = (minCount + maxCount + 1) / 2;
            } else {
                computedCount = countPerLevel * Math.max(1, level);
            }
            if (countPerLevel > 0 && (count > 0 || minCount > 0)) {
                computedCount += countPerLevel * Math.max(1, level);
            }
            return Math.max(1, computedCount);
        }

        ItemStack toSingleStack(Identifier enchantmentId, int level, int maxLevel, String salt) {
            return new ItemStack(candidateItems().getFirst(), computeCount(level, maxLevel));
        }
    }

    // ---------- U1: loot injections + bookshelf book pool ----------

    private record LootInjectionTable(VanillaChestBooksMode mode, List<LootInjection> entries) {}

    private static LootInjectionTable loadLootInjections(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(LOOT_INJECTIONS_ID);
        if (resource.isEmpty()) {
            return new LootInjectionTable(VanillaChestBooksMode.KEEP_BOTH, List.of());
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
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
            return new LootInjectionTable(mode, List.copyOf(loaded));
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; loot injection disabled", LOOT_INJECTIONS_ID, e);
            return new LootInjectionTable(VanillaChestBooksMode.KEEP_BOTH, List.of());
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

    private static TreasureEpiphanyRules loadTreasureEpiphanyRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(TREASURE_EPIPHANY_ID);
        if (resource.isEmpty()) {
            return TreasureEpiphanyRules.builtInFallback();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return TreasureEpiphanyRules.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in fallback treasure-epiphany rules", TREASURE_EPIPHANY_ID, e);
            return TreasureEpiphanyRules.builtInFallback();
        }
    }

    private record TreasureEpiphanyEntry(int xpLevels, ItemCostRule materialCost) {}

    private record TreasureEpiphanyRules(
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
        static TreasureEpiphanyRules fromJson(JsonObject root) {
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
            Set<Identifier> explicitIds = new HashSet<>(builtInExcludedTreasureIds());
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

        static TreasureEpiphanyRules builtInFallback() {
            return fromJson(readBundledJsonObject("treasure_epiphany.json"));
        }

        double chanceFor(int regularBookshelves, int chiseledBooks) {
            if (regularBookshelves < minRegularBookshelves) return 0.0;
            double raw = baseChance
                + (regularBookshelves - minRegularBookshelves) * chancePerExtraBookshelf
                + Math.max(0, chiseledBooks) * chancePerMasterTome;
            return Math.max(0.0, Math.min(maxChance, raw));
        }

        int maxSlotsFor(int regularBookshelves, int chiseledBooks) {
            if (regularBookshelves < minRegularBookshelves) return 0;
            int score = Math.max(0, regularBookshelves - minRegularBookshelves) + Math.max(0, chiseledBooks) * 2;
            return Math.min(maxSlotsCap, 1 + score / scorePerExtraSlot);
        }

        List<ItemStack> freezeStacks() {
            Identifier dummy = Identifier.withDefaultNamespace("mending");
            List<ItemStack> out = new ArrayList<>(freezeOptions.size());
            for (ItemCostOption opt : freezeOptions) {
                out.add(opt.toSingleStack(dummy, 1, 1, ""));
            }
            return List.copyOf(out);
        }

        List<ItemStack> rerollStacks() {
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

        int xpCost(Identifier id, int level, int maxLevel) {
            TreasureEpiphanyEntry e = entries.get(id);
            int base = e != null ? e.xpLevels() : defaultXpLevels;
            return Math.max(1, (int) Math.round(base * tierMultiplier(level, maxLevel)));
        }

        List<List<ItemStack>> costOptions(Identifier id, int level, int maxLevel) {
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
                    if (stack.is(chiseled_enchanting_table.registry.BlockRegistry.ARCANE_CATALYST)) {
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
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in shelf gate defaults", SHELF_GATES_ID, e);
            return chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.Config.DEFAULT;
        }
    }

    private static chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config loadLibraryResonanceConfig(
        ResourceManager resourceManager
    ) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(LIBRARY_RESONANCE_ID);
        if (resource.isEmpty()) {
            return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.fromJson(root);
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in library resonance defaults", LIBRARY_RESONANCE_ID, e);
            return chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;
        }
    }

    private static DisenchantingRules loadDisenchantingRules(ResourceManager resourceManager) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = resourceManager.getResource(DISENCHANTING_ID);
        if (resource.isEmpty()) {
            return DisenchantingRules.builtInFallback();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return DisenchantingRules.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.error("Failed to load {}; using built-in disenchanting defaults", DISENCHANTING_ID, e);
            return DisenchantingRules.builtInFallback();
        }
    }

    private record DisenchantingRules(
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
        static DisenchantingRules builtInFallback() {
            return fromJson(readBundledJsonObject("disenchanting.json"));
        }

        static DisenchantingRules fromJson(JsonObject root) {
            boolean enabled = boolOr(root, "enabled", true);
            boolean allowCurse = boolOr(root, "allow_curse_purge", true);
            double scale = Math.max(0.0, Math.min(1.0, doubleOr(root, "xp_refund_scale", 1.0)));
            double extScale = Math.max(
                0.0,
                Math.min(
                    scale,
                    doubleOr(
                        root,
                        "external_xp_refund_scale",
                        chiseled_enchanting_table.mechanics.enchanting.EnchantmentProvenance.DEFAULT_EXTERNAL_XP_REFUND_SCALE
                    )
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
                bundled = readBundledJsonObject("disenchanting.json");
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
                    bundled = readBundledJsonObject("disenchanting.json");
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

        int targetedStripXpCost(int level, int maxLevel) {
            int safeMax = Math.max(1, maxLevel);
            int safeLvl = Math.clamp(level, 1, safeMax);
            if (safeMax <= 1) {
                return Math.round((stripXpMin + stripXpMax) * 0.5f);
            }
            double u = (double) (safeLvl - 1) / (double) (safeMax - 1);
            return Math.max(1, (int) Math.round(stripXpMin + (stripXpMax - stripXpMin) * u));
        }

        List<List<ItemStack>> targetedStripCostOptions(Identifier id, int level, int maxLevel) {
            return stripMaterialCost.toBundles(id, level, maxLevel);
        }

        List<List<ItemStack>> cursePurgeCostOptions(Identifier id) {
            return curseMaterialCost.toBundles(id, 1, 1);
        }
    }
}
