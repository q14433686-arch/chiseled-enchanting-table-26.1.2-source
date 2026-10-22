package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Bidirectional REI display for Chiseled Enchanting Table item transformations.
 */
public final class ReiTransformationDisplay implements Display {
    private final TransformationQueryIndex.Entry entry;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public ReiTransformationDisplay(TransformationQueryIndex.Entry entry) {
        this.entry = entry;
        List<EntryIngredient> in = new ArrayList<>(4);
        in.add(EntryIngredient.of(item(entry.inputStack())));
        if (!entry.primaryCostAlternatives().isEmpty()) {
            in.add(EntryIngredient.of(stacks(entry.primaryCostAlternatives())));
        }
        if (!entry.secondaryCostAt(0).isEmpty()) {
            in.add(EntryIngredient.of(stacks(entry.secondaryCostAt(0))));
        }
        if (!entry.secondaryCostAt(1).isEmpty()) {
            in.add(EntryIngredient.of(stacks(entry.secondaryCostAt(1))));
        }
        this.inputs = List.copyOf(in);
        this.outputs = List.of(EntryIngredient.of(item(entry.outputStack())));
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return ReiTransformationCategory.ID;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(ChiseledEnchantingTable.identifier(
            "transformation/" + entry.id().getNamespace() + "/" + entry.id().getPath()
        ));
    }

    @Override
    @Nullable
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }

    public XpRequirement xpRequirement() {
        return entry.xpRequirement();
    }

    public boolean serverCosts() {
        return entry.serverCosts();
    }

    private static EntryStack<ItemStack> item(ItemStack stack) {
        return EntryStack.of(VanillaEntryTypes.ITEM, stack);
    }

    private static List<EntryStack<ItemStack>> stacks(List<ItemStack> items) {
        List<EntryStack<ItemStack>> out = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            out.add(item(stack));
        }
        return out;
    }
}
