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


/** Stack identity and material-bundle validation without loading the runtime data facade. */
final class DataMaterialRules {
    static final int MAX_MERGED_COST_COUNT = 4096;
    static final Identifier BLANK_OVERWRITE_ID = ChiseledEnchantingTable.identifier("blank_overwrite");
    private DataMaterialRules() {}

    public static boolean isResidualBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME)) return true;
        if (!stack.is(Items.BOOK)) return false;
        if (stack.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) return true;
        if (stack.has(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) return true;
        var direct = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        return direct != null && !direct.isEmpty();
    }

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
}
