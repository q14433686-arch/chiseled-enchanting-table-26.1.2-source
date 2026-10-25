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

/** LootInjection domain; independent of reload/publication orchestration. */
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
