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

    public static AvailableOption disenchantAll(Identifier id) {
        return new AvailableOption(OptionType.DISENCHANT_ALL, id, 0);
    }

    public static AvailableOption downgradeEnchantment(Identifier id, int targetLevel) {
        return new AvailableOption(OptionType.DOWNGRADE_ENCHANTMENT, id, targetLevel);
    }

    public static AvailableOption stripSingleEnchantment(Identifier id, int currentLevel) {
        return new AvailableOption(OptionType.STRIP_SINGLE_ENCHANTMENT, id, currentLevel);
    }

    public static AvailableOption purgeCurse(Identifier id, int currentLevel) {
        return new AvailableOption(OptionType.PURGE_CURSE, id, currentLevel);
    }

    public boolean isTransformation() {
        return type == OptionType.TRANSFORMATION;
    }

    public boolean isTreasureEpiphany() {
        return type == OptionType.TREASURE_EPIPHANY;
    }

    public boolean isCleanseOperation() {
        return type == OptionType.DISENCHANT_ALL
            || type == OptionType.DOWNGRADE_ENCHANTMENT
            || type == OptionType.STRIP_SINGLE_ENCHANTMENT
            || type == OptionType.PURGE_CURSE;
    }

    public boolean isDisenchantAll() {
        return type == OptionType.DISENCHANT_ALL;
    }

    public boolean isDowngrade() {
        return type == OptionType.DOWNGRADE_ENCHANTMENT;
    }

    public boolean isStripSingle() {
        return type == OptionType.STRIP_SINGLE_ENCHANTMENT;
    }

    public boolean isPurgeCurse() {
        return type == OptionType.PURGE_CURSE;
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
