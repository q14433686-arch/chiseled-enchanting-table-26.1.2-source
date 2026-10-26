package chiseled_enchanting_table.data;

import static chiseled_enchanting_table.data.DataMaterialRules.*;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static chiseled_enchanting_table.data.DataJson.*;

/** ItemTransformation domain; independent of reload/publication orchestration. */
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
