package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * REI category mirroring the JEI one. The default {@code DisplayRenderer} that
 * {@link DisplayCategory} derives from the display's inputs/outputs is enough here: the two input
 * ingredients (target items, book) plus the output already read as "item + book → enchanted item",
 * so no custom renderer widgets are needed.
 */
public class ReiEnchantingCategory implements DisplayCategory<ReiEnchantingDisplay> {
    public static final CategoryIdentifier<ReiEnchantingDisplay> ID =
        CategoryIdentifier.of(ChiseledEnchantingTable.identifier("enchanting"));

    @Override
    public CategoryIdentifier<? extends ReiEnchantingDisplay> getCategoryIdentifier() {
        return ID;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.chiseled_enchanting_table.category.enchanting");
    }

    @Override
    public Renderer getIcon() {
        return Widgets.createSlot(new Point(0, 0))
            .entry(EntryStacks.of(new ItemStack(Blocks.CHISELED_BOOKSHELF)));
    }

    @Override
    public int getDisplayHeight() {
        return 42;
    }
}
