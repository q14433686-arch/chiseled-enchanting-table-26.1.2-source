package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.compat.TransformationQueryIndex;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Bidirectional JEI recipe wrapper for Chiseled Enchanting Table item transformations.
 */
public record TransformationRecipe(
    Identifier id,
    ItemStack inputStack,
    List<ItemStack> primaryAlternatives,
    List<ItemStack> firstSecondaryAlternatives,
    List<ItemStack> secondSecondaryAlternatives,
    ItemStack outputStack,
    XpRequirement xpRequirement,
    boolean serverCosts
) {
    public static TransformationRecipe of(TransformationQueryIndex.Entry entry) {
        return new TransformationRecipe(
            entry.id(),
            entry.inputStack(),
            entry.primaryCostAlternatives(),
            entry.secondaryCostAt(0),
            entry.secondaryCostAt(1),
            entry.outputStack(),
            entry.xpRequirement(),
            entry.serverCosts()
        );
    }
}
