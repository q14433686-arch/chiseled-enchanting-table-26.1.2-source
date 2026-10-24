package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import java.util.List;
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
 *
 * <p>Both injections are {@code require = 0}: this is an auxiliary economy
 * guard, while {@code ContainerHelper} sits on every block-entity load/save
 * path. A descriptor drift here must degrade the guard silently — it must
 * never again fail the {@code ContainerHelper} transformation and cascade
 * into world I/O errors (field report 2026-10-24: 49 repeated apply failures
 * breaking block-entity load/save and packet handling on an integrated
 * server whose jar predated the {@code List}-descriptor fix in {@code 25a19ef}).</p>
 */
@Mixin(ContainerHelper.class)
public abstract class LockedBookContainerMixin {
    @Inject(
        method = "removeItem(Ljava/util/List;II)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void cet$blockLockedRemove(
        List<ItemStack> items,
        int slot,
        int amount,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (slot >= 0 && slot < items.size() && TomeLockData.isEffectivelyLocked(items.get(slot))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
        method = "takeItem(Ljava/util/List;I)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void cet$blockLockedTakeItem(
        List<ItemStack> items,
        int slot,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (slot >= 0 && slot < items.size() && TomeLockData.isEffectivelyLocked(items.get(slot))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
