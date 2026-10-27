package chiseled_enchanting_table.compat;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
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
        var resonanceCfg = SyncedEnchantmentCosts.libraryResonanceConfig();
        boolean hasSyncedPayload = SyncedEnchantmentCosts.hasServerData();

        var displays = new ArrayList<>(SyncedEnchantmentCosts.transformationDisplays().orElseGet(
            chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.AvailableEnchantmentPayload::buildTransformationDisplays));
        displays.sort(Comparator.comparing(d -> d.id().toString()));

        List<Entry> results = new ArrayList<>(displays.size());
        for (var display : displays) {
            if (!display.unlocked()) continue;
            Identifier id = display.id();
            Identifier inputId = display.input_item();
            Identifier outputId = display.output_item();
            int level = display.level();
            int minReq = display.min_player_level();
            int dedLvls = display.deducted_levels();
            Item outputItem = BuiltInRegistries.ITEM.getValue(outputId);
            if (outputItem == null || outputItem == Items.AIR) continue;

            List<ItemStack> inputs = new ArrayList<>();
            if (display.input_is_tag()) {
                var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, inputId);
                for (Item item : BuiltInRegistries.ITEM) {
                    var stack = new ItemStack(item);
                    if (stack.is(tag)) inputs.add(stack);
                }
            } else {
                Item inputItem = BuiltInRegistries.ITEM.getValue(inputId);
                if (inputItem != null && inputItem != Items.AIR) inputs.add(new ItemStack(inputItem));
            }

            var synced = SyncedEnchantmentCosts.find(OptionType.TRANSFORMATION, id, level);
            if (hasSyncedPayload && synced.isEmpty()) {
                chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.warn(
                    "Skipping transformation {}: server catalog has no matching synchronized cost row", id);
                continue; // Never silently mix a server catalog with client-local prices.
            }
            boolean serverCosts = synced.isPresent();
            int rawXp = synced.map(SyncedEnchantmentCosts.Cost::xpLevelCost)
                .orElseGet(() -> ChiseledEnchantingTableData.transformationXpCost(id));
            List<List<ItemStack>> costOptions = synced.map(SyncedEnchantmentCosts.Cost::costOptions)
                .orElseGet(() -> ChiseledEnchantingTableData.transformationCostOptions(id));

            XpRequirement xpReq = XpCostConversion.forTransformation(
                resonanceCfg,
                id,
                rawXp,
                0,
                minReq,
                dedLvls
            );
            for (ItemStack input : inputs) {
                results.add(new Entry(id, level, input, costOptions, new ItemStack(outputItem), xpReq, serverCosts));
            }
        }
        if (SyncedEnchantmentCosts.disenchantEnabled()) {
            Identifier sampleId = Identifier.withDefaultNamespace("sharpness");
            int sampleRefund = DisenchantmentApplication.essencePotentialFromParams(
                resonanceCfg,
                5,
                5,
                1,
                false,
                SyncedEnchantmentCosts.disenchantRefundScale()
            );
            results.add(new Entry(
                DisenchantmentApplication.DISENCHANT_ALL_ID,
                1,
                new ItemStack(Items.ENCHANTED_BOOK),
                List.of(),
                new ItemStack(chiseled_enchanting_table.registry.BlockRegistry.DEPLETED_TOME),
                XpRequirement.refund(sampleRefund),
                hasSyncedPayload
            ));

            var syncedStrip = SyncedEnchantmentCosts.find(OptionType.STRIP_SINGLE_ENCHANTMENT, sampleId, 3);
            int stripRaw = syncedStrip.map(SyncedEnchantmentCosts.Cost::xpLevelCost)
                .orElseGet(() -> ChiseledEnchantingTableData.targetedStripXpCost(sampleId, 3, 5));
            List<List<ItemStack>> stripOptions = syncedStrip.map(SyncedEnchantmentCosts.Cost::costOptions)
                .orElseGet(() -> ChiseledEnchantingTableData.targetedStripCostOptions(sampleId, 3, 5));
            results.add(new Entry(
                DisenchantmentApplication.STRIP_SINGLE_ID,
                1,
                new ItemStack(Items.ENCHANTED_BOOK),
                stripOptions,
                new ItemStack(Items.ENCHANTED_BOOK),
                XpCostConversion.forTargetedCleanse(resonanceCfg, 3, 5, stripRaw, 0),
                syncedStrip.isPresent()
            ));

            if (SyncedEnchantmentCosts.disenchantAllowCursePurge()) {
                Identifier curseSampleId = Identifier.withDefaultNamespace("binding_curse");
                var syncedCurse = SyncedEnchantmentCosts.find(OptionType.PURGE_CURSE, curseSampleId, 1);
                int curseRaw = syncedCurse.map(SyncedEnchantmentCosts.Cost::xpLevelCost)
                    .orElseGet(ChiseledEnchantingTableData::cursePurgeXpCost);
                List<List<ItemStack>> curseOptions = syncedCurse.map(SyncedEnchantmentCosts.Cost::costOptions)
                    .orElseGet(() -> ChiseledEnchantingTableData.cursePurgeCostOptions(curseSampleId));
                results.add(new Entry(
                    DisenchantmentApplication.PURGE_CURSE_ID,
                    1,
                    new ItemStack(Items.ENCHANTED_BOOK),
                    curseOptions,
                    new ItemStack(Items.ENCHANTED_BOOK),
                    XpCostConversion.forFullArcaneRitual(resonanceCfg, curseRaw, 0),
                    syncedCurse.isPresent()
                ));
            }
        }
        return List.copyOf(results);
    }
}
