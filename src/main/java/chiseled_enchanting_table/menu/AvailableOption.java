package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import net.minecraft.resources.Identifier;

/**
 * A single, explicitly-typed option shown in the chiseled enchanting list.
 */
public record AvailableOption(OptionType type, Identifier id, int level) {

    public static AvailableOption enchantment(Identifier id, int level) {
        return new AvailableOption(OptionType.ENCHANTMENT, id, level);
    }

    public static AvailableOption enchantment(EnchantmentWithLevel e) {
        return new AvailableOption(OptionType.ENCHANTMENT, e.enchantment_id(), e.enchantment_level());
    }

    public static AvailableOption treasureEpiphany(Identifier id, int level) {
        return new AvailableOption(OptionType.TREASURE_EPIPHANY, id, level);
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

    public boolean isTreasureEpiphany() {
        return type == OptionType.TREASURE_EPIPHANY;
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

    public EnchantmentWithLevel asEnchantmentWithLevel() {
        return new EnchantmentWithLevel(id, level);
    }
}
