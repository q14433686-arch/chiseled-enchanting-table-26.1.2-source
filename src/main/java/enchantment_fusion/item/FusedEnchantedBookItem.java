package enchantment_fusion.item;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import enchantment_fusion.registry.FusionItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Carrier book holding an ordered binary fusion {@code [A(x) ▷ B(y)]}.
 * Included in {@code #minecraft:bookshelf_books} so Chiseled Bookshelves accept it natively.
 */
public final class FusedEnchantedBookItem extends Item {
    public FusedEnchantedBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).isEmpty()
            || super.isFoil(stack);
    }

    public static ItemStack createForPair(FusedPair pair) {
        ItemStack stack = new ItemStack(FusionItems.FUSED_ENCHANTED_BOOK, 1);
        stack.set(FusionDataComponents.FUSED_PAIRS, new FusedEnchantmentList(java.util.List.of(pair)));
        return stack;
    }
}
