package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseServerBridge;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.menu.MenuMaterialQuotes", remap = false)
public abstract class CetMaterialQuotesBridgeMixin {
    @Inject(
        method = "getEffectiveItemCostOptions(Lchiseled_enchanting_table/menu/MenuPricingAccess;Lchiseled_enchanting_table/utils/EnchantmentWithLevel;Lchiseled_enchanting_table/menu/ChiseledEnchantingMenuPayloads$OptionType;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onGetEffectiveItemCostOptions(
        @Coerce Object pricingAccess,
        @Coerce Object enchantment,
        @Coerce Object optionType,
        CallbackInfoReturnable<List<List<ItemStack>>> cir
    ) {
        List<List<ItemStack>> overridden = CetCleanseServerBridge.overrideStripMaterialOptionsIfMultiTrait(
            pricingAccess, enchantment, optionType
        );
        if (overridden != null) {
            cir.setReturnValue(overridden);
        }
    }
}
