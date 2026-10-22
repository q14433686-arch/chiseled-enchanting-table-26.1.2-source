package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
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
import net.minecraft.world.level.block.Blocks;

/**
 * REI category for
 * {@code [target item] + [enchanted book] + [primary material] + [optional secondary material] -> [enchanted item]}.
 */
public class ReiEnchantingCategory implements DisplayCategory<ReiEnchantingDisplay> {
    public static final CategoryIdentifier<ReiEnchantingDisplay> ID =
        CategoryIdentifier.of(ChiseledEnchantingTable.identifier("enchanting"));

    private static final int WIDTH = 132;
    private static final int HEIGHT = 44;

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
        return slot(0, 0, itemStack(new ItemStack(Blocks.CHISELED_BOOKSHELF)));
    }

    @Override
    public int getDisplayWidth(ReiEnchantingDisplay display) {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public List<Widget> setupDisplay(ReiEnchantingDisplay display, Rectangle bounds) {
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

        var xpReq = chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.forMasterTomeEnchant(
            display.row().level(), display.row().maxLevel(), display.xpLevelCost(), 0
        );
        widgets.add(Widgets.createLabel(
                new Point(x + WIDTH / 2, y + 28),
                Component.translatable(
                    "jei.chiseled_enchanting_table.tooltip.xp_vanilla",
                    xpReq.requiredLevel(),
                    xpReq.deductedLevels()
                )
            )
            .tooltip(tooltipLines(display)));
        return widgets;
    }

    private static Component[] tooltipLines(ReiEnchantingDisplay display) {
        List<Component> lines = new ArrayList<>(4);
        lines.add(Component.translatable(
            display.serverCosts()
                ? "jei.chiseled_enchanting_table.tooltip.cost_synced"
                : "jei.chiseled_enchanting_table.tooltip.cost_hint"
        ));
        lines.add(Component.translatable("jei.chiseled_enchanting_table.tooltip.bookshelf"));
        if (!display.conflicts().isEmpty()) {
            lines.add(Component.translatable(
                "jei.chiseled_enchanting_table.tooltip.conflicts",
                EnchantQueryIndex.conflictSummary(display.conflicts())
            ));
        }
        return lines.toArray(new Component[0]);
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
