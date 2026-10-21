package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * The single bidirectional category: {@code [target item] + [enchanted book] + [material] -> [enchanted item]}.
 *
 * <p>Because both the item and the book are real input slots, JEI derives both queries from it:
 * pressing {@code U} on an enchanted book lists every item it can enchant, and pressing {@code U} on
 * a sword lists every book that works on it. That is the whole point of modelling the mechanic as a
 * two-input recipe instead of two separate lookup screens.</p>
 */
public class EnchantingRecipeCategory extends AbstractRecipeCategory<EnchantingRecipe> {
    public static final Identifier UID = ChiseledEnchantingTable.identifier("enchanting");

    private static final int WIDTH = 100;
    private static final int HEIGHT = 42;

    private static final int SLOT_ITEM = 1;
    private static final int SLOT_BOOK = 21;
    private static final int SLOT_COST_X = 27;
    private static final int SLOT_OUTPUT_X = 74;

    public static final IRecipeType<EnchantingRecipe> RECIPE_TYPE =
        IRecipeType.create(UID.getNamespace(), UID.getPath(), EnchantingRecipe.class);

    public EnchantingRecipeCategory(IGuiHelper guiHelper) {
        super(
            RECIPE_TYPE,
            Component.translatable("jei.chiseled_enchanting_table.category.enchanting"),
            guiHelper.createDrawableItemStack(new ItemStack(Blocks.CHISELED_BOOKSHELF)),
            WIDTH,
            HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnchantingRecipe recipe, IFocusGroup focuses) {
        // Target item: all items the enchantment supports share this row, so they are alternatives
        // in one slot (this is what makes "item -> which books" work).
        builder.addInputSlot(SLOT_ITEM, 1).addItemStacks(recipe.targetItems());
        builder.addInputSlot(SLOT_ITEM, SLOT_BOOK).addItemStack(recipe.book());

        if (recipe.costItems().isEmpty()) {
            // Enchantments with no material cost still need a stable layout; keep the slot but
            // hide it from focus so an empty slot cannot be "focused".
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        } else {
            builder.addInputSlot(SLOT_COST_X, 11).addItemStacks(recipe.costItems());
        }

        ItemStack output = outputStack(recipe);
        builder.addOutputSlot(SLOT_OUTPUT_X, 11).addItemStack(output);
    }

    /**
     * Representative output: the most "recognisable" supported item with the enchantment applied.
     * A book target becomes an enchanted book (that is what the table actually produces), which is
     * also why the row is still meaningful when the only supported items are books.
     */
    private static ItemStack outputStack(EnchantingRecipe recipe) {
        Holder<Enchantment> holder = Minecraft.getInstance().level == null
            ? null
            : Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, recipe.enchantmentId()))
                .map(reference -> (Holder<Enchantment>) reference)
                .orElse(null);
        ItemStack target = recipe.targetItems().isEmpty()
            ? new ItemStack(net.minecraft.world.item.Items.BOOK)
            : recipe.targetItems().getFirst();
        if (holder == null) return target.copy();
        return recipe.resultFor(target, holder);
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        EnchantingRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        tooltip.add(Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.xp", recipe.xpLevelCost()
        ));
        tooltip.add(Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.cost_hint"
        ));
        tooltip.add(Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.bookshelf"
        ));
        if (!recipe.conflictingEnchantments().isEmpty()) {
            MutableComponent conflicts = Component.empty();
            boolean first = true;
            for (Identifier id : recipe.conflictingEnchantments()) {
                if (!first) conflicts.append(Component.literal(", "));
                conflicts.append(Component.translatable(
                    "enchantment." + id.getNamespace() + "." + id.getPath()
                ));
                first = false;
            }
            tooltip.add(Component.translatable(
                "jei.chiseled_enchanting_table.tooltip.conflicts", conflicts
            ));
        }
    }
}
