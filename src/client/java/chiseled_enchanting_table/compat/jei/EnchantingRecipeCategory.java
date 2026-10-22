package chiseled_enchanting_table.compat.jei;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.EnchantQueryIndex;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * Bidirectional category:
 * {@code [target item] + [enchanted book] + [primary material] + [optional secondary material] -> [enchanted item]}.
 */
public class EnchantingRecipeCategory extends AbstractRecipeCategory<EnchantingRecipe> {
    public static final Identifier UID = ChiseledEnchantingTable.identifier("enchanting");

    private static final int WIDTH = 128;
    private static final int HEIGHT = 44;

    private static final int SLOT_Y = 5;
    private static final int SLOT_TARGET_X = 4;
    private static final int SLOT_BOOK_X = 22;
    private static final int SLOT_COST_1_X = 40;
    private static final int SLOT_COST_2_X = 58;
    private static final int ARROW_X = 80;
    private static final int SLOT_OUTPUT_X = 104;

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
        builder.addInputSlot(SLOT_TARGET_X, SLOT_Y).addItemStacks(recipe.targetItems());
        builder.addInputSlot(SLOT_BOOK_X, SLOT_Y).add(recipe.book());

        List<ItemStack> primaries = recipe.primaryCostAlternatives();
        List<ItemStack> secondaries = recipe.secondaryCostAlternatives();
        if (primaries.isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        } else {
            builder.addInputSlot(SLOT_COST_1_X, SLOT_Y).addItemStacks(primaries);
            if (!secondaries.isEmpty()) {
                builder.addInputSlot(SLOT_COST_2_X, SLOT_Y).addItemStacks(secondaries);
            }
        }

        ItemStack output = outputStack(recipe);
        builder.addOutputSlot(SLOT_OUTPUT_X, SLOT_Y).add(output);
    }

    @Override
    public void draw(
        EnchantingRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphicsExtractor guiGraphics,
        double mouseX,
        double mouseY
    ) {
        var font = Minecraft.getInstance().font;
        guiGraphics.text(font, "\u2192", ARROW_X, SLOT_Y + 4, 0xFF404040, false);
        var xpReq = chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.forMasterTomeEnchant(
            recipe.level(), recipe.maxLevel(), recipe.xpLevelCost(), 0
        );
        Identifier orb = chiseled_enchanting_table.gui.row.RowRenderSupport.orbSprite(xpReq.deductedLevels());
        guiGraphics.blit(orb, SLOT_TARGET_X, 27, SLOT_TARGET_X + 10, 37, 0.0f, 1.0f, 0.0f, 1.0f);
        Component xpText = Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.xp_vanilla",
            xpReq.requiredLevel(),
            xpReq.deductedLevels()
        );
        guiGraphics.text(font, xpText, SLOT_TARGET_X + 13, 29, 0xFF2E5B27, false);
    }

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
        var xpReq = chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.forMasterTomeEnchant(
            recipe.level(), recipe.maxLevel(), recipe.xpLevelCost(), 0
        );
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
        tooltip.add(Component.translatable(
            "jei.chiseled_enchanting_table.tooltip.bookshelf"
        ));
        if (!recipe.conflictingEnchantments().isEmpty()) {
            tooltip.add(Component.translatable(
                "jei.chiseled_enchanting_table.tooltip.conflicts",
                EnchantQueryIndex.conflictSummary(recipe.conflictingEnchantments())
            ));
        }
    }
}
