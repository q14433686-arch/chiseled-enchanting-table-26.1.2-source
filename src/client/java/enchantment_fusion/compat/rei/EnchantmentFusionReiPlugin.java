package enchantment_fusion.compat.rei;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.registry.FusionBlocks;
import enchantment_fusion.registry.FusionItems;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.network.chat.Component;

/**
 * Optional REI integration registering workstation & item guide pages when REI is present.
 */
public final class EnchantmentFusionReiPlugin implements REIClientPlugin {
    @Override
    public void registerDisplays(DisplayRegistry registry) {
        DefaultInformationDisplay tableInfo = DefaultInformationDisplay.createFromEntries(
            EntryIngredients.of(FusionBlocks.ENCHANTMENT_FUSION_TABLE),
            Component.translatable("block.enchantment_fusion.enchantment_fusion_table")
        );
        tableInfo.line(Component.translatable("compat.enchantment_fusion.jei.table_info"));
        registry.add(tableInfo);

        DefaultInformationDisplay bookInfo = DefaultInformationDisplay.createFromEntries(
            EntryIngredients.of(FusionItems.FUSED_ENCHANTED_BOOK),
            Component.translatable("item.enchantment_fusion.fused_enchanted_book")
        );
        bookInfo.line(Component.translatable("compat.enchantment_fusion.jei.book_info"));
        registry.add(bookInfo);

        DefaultInformationDisplay stabInfo = DefaultInformationDisplay.createFromEntries(
            EntryIngredients.of(FusionItems.RESONANCE_STABILIZER),
            Component.translatable("item.enchantment_fusion.resonance_stabilizer")
        );
        stabInfo.line(Component.translatable("compat.enchantment_fusion.jei.stabilizer_info"));
        registry.add(stabInfo);
    }

    @Override
    public String getPluginProviderName() {
        return EnchantmentFusion.MOD_ID;
    }
}
