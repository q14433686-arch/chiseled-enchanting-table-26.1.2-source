package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks container extraction of effectively locked tomes. This covers chiseled
 * bookshelf removal paths and hopper/automation transfers, which both resolve
 * through {@link ContainerHelper}; expired timed locks are ignored by
 * {@link TomeLockData#isEffectivelyLocked(ItemStack)}.
 */
@Mixin(ContainerHelper.class)
public abstract class LockedBookContainerMixin {
    @Inject(
        method = "removeItem(Lnet/minecraft/core/NonNullList;II)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void cet$blockLockedRemove(
        NonNullList<ItemStack> items,
        int slot,
        int amount,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (slot >= 0 && slot < items.size() && TomeLockData.isEffectivelyLocked(items.get(slot))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
        method = "takeItem(Lnet/minecraft/core/NonNullList;I)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void cet$blockLockedTakeItem(
        NonNullList<ItemStack> items,
        int slot,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (slot >= 0 && slot < items.size() && TomeLockData.isEffectivelyLocked(items.get(slot))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
