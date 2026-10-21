package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import java.util.List;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
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
        registry.addWorkstations(
            ReiEnchantingCategory.ID,
            EntryStacks.of(new ItemStack(chiseled_enchanting_table.registry.BlockRegistry.CHISELED_ENCHANTING_TABLE)),
            EntryStacks.of(new ItemStack(Blocks.CHISELED_BOOKSHELF))
        );
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        HolderLookup.Provider registries = registries();
        if (registries == null) return;
        for (EnchantQueryIndex.Entry entry : EnchantQueryIndex.build(registries)) {
            registry.add(new ReiEnchantingDisplay(entry.enchantmentId(), entry.level()));
        }
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) return minecraft.level.registryAccess();
        if (minecraft.getConnection() != null) return minecraft.getConnection().registryAccess();
        ChiseledEnchantingTable.LOGGER.debug("REI integration: no registry access yet, skipping displays");
        return null;
    }

    /** Kept so the plugin compiles even when only the category is used. */
    @SuppressWarnings("unused")
    private static final List<Class<?>> EXPOSED = List.of(ReiEnchantingDisplay.class, ReiEnchantingCategory.class);
}
