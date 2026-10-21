package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.compat.EnchantQueryIndex;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * One JEI row: "apply this enchanted book to (any of) these items with these materials".
 *
 * <p>Not a vanilla {@code Recipe}: this mod has no recipe registry entry for the table (the menu
 * resolves everything server-side), and JEI happily accepts plain objects as long as the category's
 * recipe type is declared with the same class.</p>
 *
 * <p>{@link #targetItems()} is a <b>list</b> on purpose: every item the enchantment supports shares
 * the same book/level/cost, so one row with N item alternatives beats N identical rows. It is also
 * what makes the reverse lookup work — focusing any of those items finds this row.</p>
 */
public record EnchantingRecipe(
    Identifier enchantmentId,
    int level,
    int maxLevel,
    List<ItemStack> targetItems,
    ItemStack book,
    List<ItemStack> costItems,
    int xpLevelCost,
    List<Identifier> conflictingEnchantments
) {
    /**
     * Builds one row from the shared index. {@code holder} is needed to stamp the enchantment onto
     * the display book and the preview result; the index itself only carries the id.
     */
    public static EnchantingRecipe of(EnchantQueryIndex.Entry entry, Holder<Enchantment> holder) {
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, entry.level()));
        return new EnchantingRecipe(
            entry.enchantmentId(),
            entry.level(),
            entry.maxLevel(),
            entry.supportedItems(),
            book,
            entry.itemCosts(),
            entry.xpLevelCost(),
            entry.conflicts()
        );
    }

    /** Preview of the result for the first target item (JEI shows one representative output). */
    public ItemStack resultFor(ItemStack target, Holder<Enchantment> holder) {
        ItemStack result = target.copy();
        ItemStack source = result.is(net.minecraft.world.item.Items.BOOK)
            ? new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK)
            : result;
        EnchantmentHelper.updateEnchantments(source, mutable -> mutable.set(holder, level));
        return source;
    }

    public String enchantmentNameKey() {
        return "enchantment." + enchantmentId.getNamespace() + "." + enchantmentId.getPath();
    }
}
