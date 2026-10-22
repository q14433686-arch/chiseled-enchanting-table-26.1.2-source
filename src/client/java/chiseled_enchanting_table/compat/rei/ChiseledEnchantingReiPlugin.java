package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.List;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * REI plugin: same data as the JEI adapter ({@link EnchantQueryIndex}), different display objects.
 *
 * <p>Registered through the {@code rei_client} entrypoint, so REI only loads this class when REI is
 * installed; like JEI, it is a compile-only dependency and never a runtime requirement.</p>
 */
public class ChiseledEnchantingReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new ReiEnchantingCategory());
        registry.add(new ReiTransformationCategory());
        registry.addWorkstations(
            ReiEnchantingCategory.ID,
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE))),
            EntryIngredient.of(entry(new ItemStack(Blocks.CHISELED_BOOKSHELF)))
        );
        registry.addWorkstations(
            ReiTransformationCategory.ID,
            EntryIngredient.of(entry(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE)))
        );
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        HolderLookup.Provider registries = registries();
        if (registries == null) return;

        List<EnchantQueryIndex.Entry> rows = EnchantQueryIndex.build(registries);
        int added = 0;
        for (EnchantQueryIndex.Entry row : rows) {
            Holder.Reference<Enchantment> holder =
                EnchantQueryIndex.holder(registries, row.enchantmentId()).orElse(null);
            if (holder == null) continue;
            registry.add(new ReiEnchantingDisplay(row, holder));
            added++;
        }
        List<TransformationQueryIndex.Entry> transforms = TransformationQueryIndex.build();
        for (TransformationQueryIndex.Entry trans : transforms) {
            registry.add(new ReiTransformationDisplay(trans));
        }
        ChiseledEnchantingTable.LOGGER.debug(
            "REI integration: registered {} enchanting displays and {} transformation displays",
            added,
            transforms.size()
        );
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) return minecraft.level.registryAccess();
        if (minecraft.getConnection() != null) return minecraft.getConnection().registryAccess();
        ChiseledEnchantingTable.LOGGER.debug("REI integration: no registry access yet, skipping displays");
        return null;
    }

    /**
     * Item entry built without {@code EntryStacks}: that helper's fluid overloads would drag
     * Architectury's fluid types into the compile classpath for no benefit.
     */
    private static EntryStack<ItemStack> entry(ItemStack stack) {
        return EntryStack.of(VanillaEntryTypes.ITEM, stack);
    }
}
