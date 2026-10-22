package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.gui.row.RowRenderSupport;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Bidirectional JEI category for Chiseled Enchanting Table item transformations:
 * {@code [input item] + [primary cost] + [optional secondary costs] -> [transformed item]}.
 */
public class TransformationRecipeCategory extends AbstractRecipeCategory<TransformationRecipe> {
    public static final Identifier UID = ChiseledEnchantingTable.identifier("transformation");

    private static final int WIDTH = 128;
    private static final int HEIGHT = 44;

    private static final int SLOT_Y = 5;
    private static final int SLOT_INPUT_X = 4;
    private static final int SLOT_COST_1_X = 22;
    private static final int SLOT_COST_2_X = 40;
    private static final int SLOT_COST_3_X = 58;
    private static final int ARROW_X = 80;
    private static final int SLOT_OUTPUT_X = 104;

    public static final IRecipeType<TransformationRecipe> RECIPE_TYPE =
        IRecipeType.create(UID.getNamespace(), UID.getPath(), TransformationRecipe.class);

    public TransformationRecipeCategory(IGuiHelper guiHelper) {
        super(
            RECIPE_TYPE,
            Component.translatable("jei.chiseled_enchanting_table.category.transformation"),
            guiHelper.createDrawableItemStack(new ItemStack(BlockRegistry.CHISELED_ENCHANTING_TABLE)),
            WIDTH,
            HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TransformationRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(SLOT_INPUT_X, SLOT_Y).add(recipe.inputStack());

        List<ItemStack> primaries = recipe.primaryAlternatives();
        if (!primaries.isEmpty()) {
            builder.addInputSlot(SLOT_COST_1_X, SLOT_Y).addItemStacks(primaries);
        }
        if (!recipe.firstSecondaryAlternatives().isEmpty()) {
            builder.addInputSlot(SLOT_COST_2_X, SLOT_Y).addItemStacks(recipe.firstSecondaryAlternatives());
        }
        if (!recipe.secondSecondaryAlternatives().isEmpty()) {
            builder.addInputSlot(SLOT_COST_3_X, SLOT_Y).addItemStacks(recipe.secondSecondaryAlternatives());
        }

        builder.addOutputSlot(SLOT_OUTPUT_X, SLOT_Y).add(recipe.outputStack());
    }

    @Override
    public void draw(
        TransformationRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor guiGraphics,
        double mouseX,
        double mouseY
    ) {
        var font = Minecraft.getInstance().font;
        guiGraphics.text(font, "\u2192", ARROW_X, SLOT_Y + 4, 0xFF404040, false);
        var xpReq = recipe.xpRequirement();
        Identifier orb = RowRenderSupport.orbSprite(xpReq.deductedLevels());
        guiGraphics.blit(orb, SLOT_INPUT_X, 27, SLOT_INPUT_X + 10, 37, 0.0f, 1.0f, 0.0f, 1.0f);
        Component xpText = Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.xp_vanilla",
            xpReq.requiredLevel(),
            xpReq.deductedLevels()
        );
        guiGraphics.text(font, xpText, SLOT_INPUT_X + 13, 29, 0xFF2E5B27, false);
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        TransformationRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        var xpReq = recipe.xpRequirement();
        tooltip.add(Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.xp_vanilla",
            xpReq.requiredLevel(),
            xpReq.deductedLevels()
        ));
        tooltip.add(Component.translatable(
            recipe.serverCosts()
                ? "jei.chiseled_enchanting_table.tooltip.cost_synced"
                : "jei.chiseled_enchanting_table.tooltip.cost_hint"
        ));
    }
}
