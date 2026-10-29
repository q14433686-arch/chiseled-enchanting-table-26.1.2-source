package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseServerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.menu.MenuXpQuotes", remap = false)
public abstract class CetXpQuotesBridgeMixin {
    @Inject(
        method = "resolveEffectiveXpRequirement(Lchiseled_enchanting_table/menu/MenuPricingAccess;Lchiseled_enchanting_table/utils/EnchantmentWithLevel;Lchiseled_enchanting_table/menu/ChiseledEnchantingMenuPayloads$OptionType;)Lchiseled_enchanting_table/mechanics/enchanting/XpCostConversion$XpRequirement;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onResolveEffectiveXpRequirement(
        @Coerce Object pricingAccess,
        @Coerce Object enchantment,
        @Coerce Object optionType,
        CallbackInfoReturnable<Object> cir
    ) {
        Object overridden = CetCleanseServerBridge.overrideDowngradeXpRequirementIfTransmute(
            pricingAccess, enchantment, optionType
        );
        if (overridden != null) {
            cir.setReturnValue(overridden);
        }
    }
}
