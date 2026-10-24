package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scoped container guard on {@link ChiseledBookShelfBlockEntity} (rather than global
 * {@code ContainerHelper}): blocks extraction of effectively locked Master Tomes when
 * {@code locked_book_extraction_blocked} is enabled, and clears any residual lock metadata
 * whenever a book is legitimately extracted from a chiseled bookshelf slot.
 */
@Mixin(ChiseledBookShelfBlockEntity.class)
public abstract class LockedBookContainerMixin {
    @Inject(
        method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cet$blockLockedShelfRemove(
        int slot,
        int amount,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        ChiseledBookShelfBlockEntity self = (ChiseledBookShelfBlockEntity) (Object) this;
        if (slot < 0 || slot >= self.getContainerSize()) {
            return;
        }
        boolean clientSide = self.getLevel() != null && self.getLevel().isClientSide();
        if (!LibraryResonance.configForSide(clientSide).lockedBookExtractionBlocked()) {
            return;
        }
        ItemStack current = self.getItem(slot);
        if (!current.isEmpty() && TomeLockData.isEffectivelyLocked(current)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
        method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN")
    )
    private void cet$clearLockOnShelfExit(
        int slot,
        int amount,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack removed = cir.getReturnValue();
        if (removed != null && !removed.isEmpty()) {
            TomeLockData.unlock(removed);
        }
    }

    @Inject(
        method = "removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cet$blockLockedShelfRemoveNoUpdate(
        int slot,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        ChiseledBookShelfBlockEntity self = (ChiseledBookShelfBlockEntity) (Object) this;
        if (slot < 0 || slot >= self.getContainerSize()) {
            return;
        }
        boolean clientSide = self.getLevel() != null && self.getLevel().isClientSide();
        if (!LibraryResonance.configForSide(clientSide).lockedBookExtractionBlocked()) {
            return;
        }
        ItemStack current = self.getItem(slot);
        if (!current.isEmpty() && TomeLockData.isEffectivelyLocked(current)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
        method = "removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN")
    )
    private void cet$clearLockOnShelfExitNoUpdate(
        int slot,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack removed = cir.getReturnValue();
        if (removed != null && !removed.isEmpty()) {
            TomeLockData.unlock(removed);
        }
    }
}
