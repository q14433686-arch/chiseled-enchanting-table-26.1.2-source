package chiseled_enchanting_table.mechanics.enchanting;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared plain-book -> enchanted-book conversion (audit M-05).
 *
 * <p>Previously every conversion site minted a fresh {@link Items#ENCHANTED_BOOK} stack and
 * copied only {@link DataComponents#CUSTOM_NAME}, silently dropping lore, custom data and any
 * third-party components the source book carried (the ritual preview kept custom data, the
 * actual completion did not). All production and preview paths now funnel through here: the
 * source's component patch is carried over wholesale, then the direct-enchantments component
 * is cleared because enchantments on a book live in stored enchantments, which the caller
 * writes via {@code EnchantmentHelper.updateEnchantments} right after conversion.</p>
 */
public final class BookConversion {
    private BookConversion() {}

    /** Returns {@code source} converted to a single enchanted book, preserving its components. */
    public static ItemStack toEnchantedBook(ItemStack source) {
        if (source == null || source.isEmpty() || source.is(Items.ENCHANTED_BOOK)) {
            return source == null ? ItemStack.EMPTY : source;
        }
        ItemStack converted = new ItemStack(Items.ENCHANTED_BOOK, 1);
        converted.applyComponents(source.getComponentsPatch());
        converted.remove(DataComponents.ENCHANTMENTS);
        return converted;
    }
}
