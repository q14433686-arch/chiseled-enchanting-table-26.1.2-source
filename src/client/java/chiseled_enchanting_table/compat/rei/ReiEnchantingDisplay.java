package chiseled_enchanting_table.compat.rei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * REI mirror of the JEI row: one (enchantment, level) with every item it supports as alternatives.
 *
 * <p>Same trick as the JEI side — the target item <i>and</i> the enchanted book are real input
 * slots, so focusing either of them answers both "book → what can it enchant" and "item → which
 * books work on it" from a single registration.</p>
 *
 * <p>Unlike the JEI adapter this class keeps the resolved {@link EnchantQueryIndex.Entry} instead of
 * re-deriving it: REI rebuilds displays on every registry reload, so holding the row avoids a second
 * index build (and a cache that would go stale after joining another world). The display is
 * registered client-side only, so {@link #getSerializer()} returns {@code null} — there is nothing
 * to persist or sync.</p>
 */
public final class ReiEnchantingDisplay implements Display {
    private final EnchantQueryIndex.Entry row;
    private final Holder<Enchantment> enchantment;
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public ReiEnchantingDisplay(EnchantQueryIndex.Entry row, Holder<Enchantment> enchantment) {
        this.row = row;
        this.enchantment = enchantment;

        List<EntryIngredient> inputIngredients = new ArrayList<>(4);
        // Slot 1: every supported item as alternatives in one ingredient — this is what makes the
        // row focusable from the item side.
        inputIngredients.add(EntryIngredient.of(stacks(row.supportedItems())));
        // Slot 2: the book for exactly this level, which makes the row focusable from the book side.
        inputIngredients.add(EntryIngredient.of(item(row.bookStack(enchantment))));
        // Slots 3+: each required material stack in its own slot (not cycled as fake alternatives).
        for (ItemStack costStack : row.itemCosts()) {
            inputIngredients.add(EntryIngredient.of(item(costStack)));
        }
        this.inputs = List.copyOf(inputIngredients);
        this.outputs = List.of(EntryIngredient.of(item(result(enchantment))));
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
        return ReiEnchantingCategory.ID;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.of(ChiseledEnchantingTable.identifier(
            "enchanting/" + row.enchantmentId().getNamespace() + "/" + row.enchantmentId().getPath()
                + "/" + row.level()
        ));
    }

    @Override
    @Nullable
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }

    /** The index row this display was built from. */
    public EnchantQueryIndex.Entry row() {
        return row;
    }

    public int xpLevelCost() {
        return row.xpLevelCost();
    }

    public List<Identifier> conflicts() {
        return row.conflicts();
    }

    /** Whether the costs on this row came from the server's resolved table for this player. */
    public boolean serverCosts() {
        return row.serverCosts();
    }

    /**
     * Representative output: the first supported item with the enchantment applied. A book target
     * becomes an enchanted book, which is what the table actually produces in that case.
     */
    private ItemStack result(Holder<Enchantment> holder) {
        ItemStack target = row.supportedItems().isEmpty()
            ? new ItemStack(Items.BOOK)
            : row.supportedItems().getFirst().copy();
        ItemStack result = target.is(Items.BOOK) ? new ItemStack(Items.ENCHANTED_BOOK) : target;
        int level = row.level();
        EnchantmentHelper.updateEnchantments(result, mutable -> mutable.set(holder, level));
        return result;
    }

    private static List<EntryStack<ItemStack>> stacks(List<ItemStack> items) {
        List<EntryStack<ItemStack>> entries = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            entries.add(item(stack));
        }
        return entries;
    }

    /**
     * Item entry without going through {@code EntryStacks}: that helper has fluid overloads that
     * pull Architectury's fluid types into our compile classpath for no benefit here.
     */
    private static EntryStack<ItemStack> item(ItemStack stack) {
        return EntryStack.of(VanillaEntryTypes.ITEM, stack);
    }
}
