package chiseled_enchanting_table.menu;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.OptionType;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Read-only economic inputs. No container lifecycle, packets, slot setters or mutable Player. */
public interface MenuPricingAccess {
    net.minecraft.world.level.Level world();
    net.minecraft.world.item.ItemStack getEnchantableItem();
    net.minecraft.world.item.ItemStack getCostItem();
    int regularBookshelves();
    int xpDiscountPercent();
    String playerUuid();
    boolean creative();
    int playerLevel();
    int resolveEnchantmentMaxLevel(EnchantmentWithLevel e);
    boolean usesSaltedSingleMaterial();
    boolean usesClassicTransformations();
    boolean usesIncrementalCredit();
    boolean usesClassicFullPointXp();
    BookshelfProgression.Config shelfGatesConfig();
    LibraryResonance.Config libraryResonanceConfig();
    BookshelfProgression.DepletedTomeTier depletedTomeTier();
    int depletedTomeMaxBatchSize();
    int transformationMaxBatchSize();
    int currentTargetEnchantLevel(Identifier id);
    int storedMasterLevel(Identifier id);
    int countAvailableIncludingCostSlot(ItemStack stack);
    int countAvailableInInventory(ItemStack stack);
    int syncedTransformationLevel(Identifier id);
    int syncedTransformationMinPlayerLevel(Identifier id);
    int syncedTransformationDeductedLevels(Identifier id);
    int effectiveXpDiscountPercent(Identifier id, OptionType type);
    double disenchantRefundScale();
    double disenchantExternalRefundScale();
    boolean isMasterTomeBacked(EnchantmentWithLevel e, OptionType type);
    default List<List<ItemStack>> getEnchantmentItemCostOptions(EnchantmentWithLevel e, OptionType type) { return MenuPricing.getEnchantmentItemCostOptions(this, e, type); }
    default int getBaseEnchantmentXpLevelCost(EnchantmentWithLevel e, OptionType type) { return MenuPricing.getBaseEnchantmentXpLevelCost(this, e, type); }
    default int resolveTransformationBatchCount(Identifier id) { return MenuPricing.resolveTransformationBatchCount(this, id); }
    default XpCostConversion.XpRequirement resolveEffectiveXpRequirement(EnchantmentWithLevel e, OptionType type) {
        return MenuPricing.resolveEffectiveXpRequirement(this, e, type, type == OptionType.BOOK_RESEARCH_UPGRADE || type == OptionType.BOOK_COMBINE_UPGRADE);
    }
}
