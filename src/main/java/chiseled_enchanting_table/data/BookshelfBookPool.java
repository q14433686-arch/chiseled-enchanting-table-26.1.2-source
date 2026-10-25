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

/** BookshelfBookPool domain; independent of reload/publication orchestration. */
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
