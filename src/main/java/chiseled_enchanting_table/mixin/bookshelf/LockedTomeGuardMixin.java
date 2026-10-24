package chiseled_enchanting_table.mixin.bookshelf;

import chiseled_enchanting_table.mechanics.resonance.TomeLock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extraction guard for player-locked template books («纂刻的书» page): every vanilla
 * removal channel of a chiseled bookshelf funnels through the {@code Container}
 * removal method — the empty-hand take-book interaction and hoppers alike — so
 * refusing locked stacks here seals all of them without touching interaction code.
 * In-place mutation (wear/regen via {@code setItem}) is unaffected by design.
 * ({@code removeItemNoUpdate} is not declared by the 26.3 bookshelf entity and no
 * extraction path uses it, so it is deliberately not injected.)
 */
@Mixin(ChiseledBookShelfBlockEntity.class)
public abstract class LockedTomeGuardMixin {

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void cet$guardLockedRemove(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        ChiseledBookShelfBlockEntity self = (ChiseledBookShelfBlockEntity) (Object) this;
        if (slot >= 0 && slot < self.getContainerSize()
            && TomeLock.isEffectivelyLocked(self.getItem(slot))) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

}
