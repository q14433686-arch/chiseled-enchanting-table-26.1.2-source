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

/** LootInjectionEntry domain; independent of reload/publication orchestration. */
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
