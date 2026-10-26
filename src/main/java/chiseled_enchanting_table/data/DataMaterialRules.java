package chiseled_enchanting_table.data;

import static chiseled_enchanting_table.data.DataJson.*;
import static chiseled_enchanting_table.data.DataTableLoader.*;
import chiseled_enchanting_table.ChiseledEnchantingTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
