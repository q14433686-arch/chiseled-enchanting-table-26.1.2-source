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

import static chiseled_enchanting_table.data.DataJson.*;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.*;

/** ItemCostOption domain; independent of reload/publication orchestration. */
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
