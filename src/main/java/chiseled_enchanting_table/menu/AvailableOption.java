package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import net.minecraft.resources.Identifier;

/**
 * A single, explicitly-typed option shown in the chiseled enchanting list.
 *
 * <p>Architecture note (P18): this replaces the previous practice of overloading
 * {@link EnchantmentWithLevel} to represent both a real enchantment and a transformation
 * pseudo-option in one {@code Set}. The available *enchantment* set
 * ({@code AvailableEnchantmentPayload.unlocked_enchantements}) is now strictly real enchantments;
 * transformations arrive on their own synced channel
 * ({@code AvailableEnchantmentPayload.transformations}). Client code unifies the two into a typed
 * stream of {@code AvailableOption} so each row knows exactly what it is — no more guessing via a
 * data lookup on the id.</p>
 *
 * @param type  enchantment vs transformation
 * @param id    enchantment id, or transformation id
 * @param level enchantment level, or the transformation's defined level
 */
public record AvailableOption(OptionType type, Identifier id, int level) {

    public static AvailableOption enchantment(Identifier id, int level) {
        return new AvailableOption(OptionType.ENCHANTMENT, id, level);
    }

    public static AvailableOption enchantment(EnchantmentWithLevel e) {
        return new AvailableOption(OptionType.ENCHANTMENT, e.enchantment_id(), e.enchantment_level());
    }

    public static AvailableOption transformation(Identifier id, int level) {
        return new AvailableOption(OptionType.TRANSFORMATION, id, level);
    }

    public static AvailableOption bookCombineUpgrade(Identifier id, int targetLevel) {
        return new AvailableOption(OptionType.BOOK_COMBINE_UPGRADE, id, targetLevel);
    }

    public static AvailableOption bookResearchUpgrade(Identifier id, int targetLevel) {
        return new AvailableOption(OptionType.BOOK_RESEARCH_UPGRADE, id, targetLevel);
    }

    public boolean isTransformation() {
        return type == OptionType.TRANSFORMATION;
    }

    public boolean isBookUpgrade() {
        return type == OptionType.BOOK_COMBINE_UPGRADE || type == OptionType.BOOK_RESEARCH_UPGRADE;
    }

    public boolean isBookCombineUpgrade() {
        return type == OptionType.BOOK_COMBINE_UPGRADE;
    }

    public boolean isBookResearchUpgrade() {
        return type == OptionType.BOOK_RESEARCH_UPGRADE;
    }

    /** Adapter back to the legacy record where cost-resolution helpers still take it. */
    public EnchantmentWithLevel asEnchantmentWithLevel() {
        return new EnchantmentWithLevel(id, level);
    }
}
