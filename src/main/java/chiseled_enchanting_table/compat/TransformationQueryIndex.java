package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Viewer-neutral index for all Chiseled Enchanting Table item transformations, shared by JEI & REI.
 *
 * <p>Uses zero third-party types so it compiles cleanly even when optional JEI/REI jars are omitted.</p>
 */
public final class TransformationQueryIndex {
    private TransformationQueryIndex() {}

    public record Entry(
        Identifier id,
        int level,
        ItemStack inputStack,
        List<List<ItemStack>> costOptions,
        ItemStack outputStack,
        XpRequirement xpRequirement,
        boolean serverCosts
    ) {
        public List<ItemStack> primaryCostAlternatives() {
            List<ItemStack> primaries = new ArrayList<>(costOptions.size());
            for (List<ItemStack> bundle : costOptions) {
                if (bundle.isEmpty() || bundle.getFirst().isEmpty()) continue;
                ItemStack candidate = bundle.getFirst();
                boolean duplicate = false;
                for (ItemStack existing : primaries) {
                    if (ItemStack.isSameItemSameComponents(existing, candidate)
                        && existing.getCount() == candidate.getCount()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    primaries.add(candidate);
                }
            }
            return List.copyOf(primaries);
        }

        /** Distinct secondary stacks at 0-based extra index {@code extraIndex} (0 = 1st extra, 1 = 2nd extra). */
        public List<ItemStack> secondaryCostAt(int extraIndex) {
            int bundlePos = extraIndex + 1;
            List<ItemStack> out = new ArrayList<>();
            for (List<ItemStack> bundle : costOptions) {
                if (bundle.size() <= bundlePos) continue;
                ItemStack candidate = bundle.get(bundlePos);
                if (candidate.isEmpty()) continue;
                boolean duplicate = false;
                for (ItemStack existing : out) {
                    if (ItemStack.isSameItemSameComponents(existing, candidate)
                        && existing.getCount() == candidate.getCount()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    out.add(candidate);
                }
            }
            return List.copyOf(out);
        }
    }

    public static List<Entry> build() {
        List<Object[]> tuples = new ArrayList<>(ChiseledEnchantingTableData.transformationDisplayTuples());
        tuples.sort(Comparator.comparing(t -> ((Identifier) t[0]).toString()));

        List<Entry> results = new ArrayList<>(tuples.size());
        for (Object[] tuple : tuples) {
            Identifier id = (Identifier) tuple[0];
            Identifier inputId = (Identifier) tuple[1];
            Identifier outputId = (Identifier) tuple[2];
            int level = (Integer) tuple[3];

            Item inputItem = BuiltInRegistries.ITEM.getValue(inputId);
            Item outputItem = BuiltInRegistries.ITEM.getValue(outputId);
            if (inputItem == null || inputItem == Items.AIR || outputItem == null || outputItem == Items.AIR) {
                continue;
            }

            var synced = SyncedEnchantmentCosts.find(OptionType.TRANSFORMATION, id, level);
            boolean serverCosts = synced.isPresent();
            int rawXp = synced.map(SyncedEnchantmentCosts.Cost::xpLevelCost)
                .orElseGet(() -> ChiseledEnchantingTableData.transformationXpCost(id));
            List<List<ItemStack>> costOptions = synced.map(SyncedEnchantmentCosts.Cost::costOptions)
                .orElseGet(() -> ChiseledEnchantingTableData.transformationCostOptions(id));

            XpRequirement xpReq = XpCostConversion.forTransformation(id, rawXp, 0);
            results.add(new Entry(
                id,
                level,
                new ItemStack(inputItem),
                costOptions,
                new ItemStack(outputItem),
                xpReq,
                serverCosts
            ));
        }
        return List.copyOf(results);
    }
}
