package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Bidirectional REI category for Chiseled Enchanting Table item transformations.
 */
public class ReiTransformationCategory implements DisplayCategory<ReiTransformationDisplay> {
    public static final CategoryIdentifier<ReiTransformationDisplay> ID =
        CategoryIdentifier.of(ChiseledEnchantingTable.identifier("transformation"));

    private static final int WIDTH = 132;
    private static final int HEIGHT = 44;

    @Override
    public CategoryIdentifier<? extends ReiTransformationDisplay> getCategoryIdentifier() {
        return ID;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.chiseled_enchanting_table.category.transformation");
    }

    @Override
    public Renderer getIcon() {
        return slot(0, 0, itemStack(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE)));
    }

    @Override
    public int getDisplayWidth(ReiTransformationDisplay display) {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public List<Widget> setupDisplay(ReiTransformationDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        int x = bounds.x;
        int y = bounds.y;

        List<EntryIngredient> inputs = display.getInputEntries();
        widgets.add(slot(x + 4, y + 5, inputs.get(0)).markInput());
        if (inputs.size() > 1) {
            widgets.add(slot(x + 22, y + 5, inputs.get(1)).markInput());
        }
        if (inputs.size() > 2) {
            widgets.add(slot(x + 40, y + 5, inputs.get(2)).markInput());
        }
        if (inputs.size() > 3) {
            widgets.add(slot(x + 58, y + 5, inputs.get(3)).markInput());
        }
        widgets.add(Widgets.createArrow(new Point(x + 80, y + 5)));
        widgets.add(slot(x + 108, y + 5, display.getOutputEntries().getFirst()).markOutput());

        var xpReq = display.xpRequirement();
        widgets.add(Widgets.createLabel(
                new Point(x + WIDTH / 2, y + 28),
                Component.translatable(
                    "jei.chiseled_enchanting_table.tooltip.xp_vanilla",
                    xpReq.requiredLevel(),
                    xpReq.deductedLevels()
                )
            )
            .tooltip(
                Component.translatable(
                    display.serverCosts()
                        ? "jei.chiseled_enchanting_table.tooltip.cost_synced"
                        : "jei.chiseled_enchanting_table.tooltip.cost_hint"
                )
            ));
        return widgets;
    }

    private static Slot slot(int x, int y, EntryIngredient ingredient) {
        return Widgets.createSlot(new Point(x, y)).entries(ingredient);
    }

    private static Slot slot(int x, int y, EntryStack<ItemStack> stack) {
        return Widgets.createSlot(new Point(x, y)).entry(stack);
    }

    private static EntryStack<ItemStack> itemStack(ItemStack stack) {
        return EntryStack.of(VanillaEntryTypes.ITEM, stack);
    }
}
