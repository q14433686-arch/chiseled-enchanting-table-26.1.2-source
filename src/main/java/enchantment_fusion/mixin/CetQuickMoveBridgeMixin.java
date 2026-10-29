package enchantment_fusion.mixin;

import enchantment_fusion.compat.CetCleanseServerBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "chiseled_enchanting_table.menu.MenuQuickMove", remap = false)
public abstract class CetQuickMoveBridgeMixin {
    @Inject(
        method = "handle(Lchiseled_enchanting_table/menu/MenuQuickMove$Access;Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void enchantmentFusion$onQuickMove(
        @Coerce Object access,
        Player owner,
        int slotIndex,
        ContainerInput actionType,
        Player player,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (actionType == ContainerInput.QUICK_MOVE
            && CetCleanseServerBridge.tryQuickMoveFusionItemIntoTargetSlot(access, slotIndex, player)) {
            cir.setReturnValue(true);
        }
    }
}
