package enchantment_fusion.compat.jei;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.gui.EnchantmentFusionScreen;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionItems;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Optional JEI integration registering workstation, book, stabilizer, grimoire, and page info guides,
 * plus exact active bounds of {@link EnchantmentFusionScreen} so JEI's right-side overlay never overlaps.
 */
@JeiPlugin
public final class EnchantmentFusionJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_UID = EnchantmentFusion.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(EnchantmentFusionScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(EnchantmentFusionScreen containerScreen) {
                return List.of(containerScreen.guiBounds());
            }
        });
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
            List.of(new ItemStack(FusionBlocks.ENCHANTMENT_FUSION_TABLE)),
            VanillaTypes.ITEM_STACK,
            Component.translatable("compat.enchantment_fusion.jei.table_info")
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(FusionItems.FUSED_ENCHANTED_BOOK)),
            VanillaTypes.ITEM_STACK,
            Component.translatable("compat.enchantment_fusion.jei.book_info")
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(FusionItems.RESONANCE_STABILIZER)),
            VanillaTypes.ITEM_STACK,
            Component.translatable("compat.enchantment_fusion.jei.stabilizer_info")
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(FusionItems.FUSION_GRIMOIRE)),
            VanillaTypes.ITEM_STACK,
            Component.translatable("item.enchantment_fusion.fusion_grimoire.lore")
        );
        registration.addIngredientInfo(
            List.of(new ItemStack(FusionItems.FUSION_TATTERED_PAGE)),
            VanillaTypes.ITEM_STACK,
            Component.translatable("item.enchantment_fusion.fusion_tattered_page.lore")
        );
    }
}
