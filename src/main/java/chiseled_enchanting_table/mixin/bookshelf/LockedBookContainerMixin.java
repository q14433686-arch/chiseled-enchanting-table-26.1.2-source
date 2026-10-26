package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import java.util.List;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
public abstract class LockedBookContainerMixin implements net.minecraft.world.Container {
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

    /**
     * Audit MIXIN-01 / MIXIN-02: {@link ChiseledBookShelfBlockEntity} inherits
     * {@code removeItemNoUpdate(int)} as an interface default method rather than declaring it in
     * class bytecode (so {@code @Inject} cannot target it with {@code require = 1}). Overriding
     * {@link net.minecraft.world.Container#removeItemNoUpdate(int)} directly on the target class
     * blocks extraction of locked Master Tomes fail-closed while preserving vanilla's full-slot
     * {@code NoUpdate} contract ({@link ContainerHelper#takeItem(List, int)} removes the entire
     * stack — including 64-stacked Enchanted Books — without triggering {@code setChanged()} or
     * comparator/blockstate churn).
     */
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ChiseledBookShelfBlockEntity self = (ChiseledBookShelfBlockEntity) (Object) this;
        boolean clientSide = self.getLevel() != null && self.getLevel().isClientSide();
        boolean enforceLock = LibraryResonance.configForSide(clientSide).lockedBookExtractionBlocked();
        return cet$takeUnlockedFullSlotNoUpdate(self.getItems(), slot, enforceLock);
    }

    @Unique
    private static ItemStack cet$takeUnlockedFullSlotNoUpdate(
        List<ItemStack> items,
        int slot,
        boolean enforceLock
    ) {
        if (items == null || slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = items.get(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (enforceLock && TomeLockData.isEffectivelyLocked(current)) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            TomeLockData.unlock(removed);
        }
        return removed;
    }
}
