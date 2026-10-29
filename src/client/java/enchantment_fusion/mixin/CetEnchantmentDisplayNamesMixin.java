package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseClientBridge;
import enchantment_fusion.compat.CetCleanseIds;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.gui.EnchantmentDisplayNames", remap = false)
public abstract class CetEnchantmentDisplayNamesMixin {
    @Inject(
        method = "identifierToEnchantment(Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/core/Holder;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onIdentifierToEnchantment(
        Identifier id,
        Player player,
        CallbackInfoReturnable<Holder<Enchantment>> cir
    ) {
        if (CetCleanseIds.isSyntheticCleanseId(id)) {
            Holder<Enchantment> h = CetCleanseClientBridge.resolveSyntheticHolder(id, player);
            if (h != null) {
                cir.setReturnValue(h);
            }
        }
    }

    @Inject(
        method = "displayName(Lchiseled_enchanting_table/menu/ChiseledEnchantingTableScreenHandler;Lnet/minecraft/world/entity/player/Player;Lchiseled_enchanting_table/menu/ChiseledEnchantingMenuPayloads$AvailableOption;)Ljava/lang/String;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onDisplayName(
        @Coerce Object handler,
        Player player,
        @Coerce Object option,
        CallbackInfoReturnable<String> cir
    ) {
        String custom = CetCleanseClientBridge.resolveSyntheticDisplayName(handler, player, option);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
