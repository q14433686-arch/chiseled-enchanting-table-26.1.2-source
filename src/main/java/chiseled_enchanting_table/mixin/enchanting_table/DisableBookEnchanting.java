package chiseled_enchanting_table.mixin.enchanting_table;

import java.util.Arrays;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Upstream balance rule, remapped for 26.1.2: the vanilla enchanting table must not be a source of
 * freshly-created enchanted books. This keeps enchanted books tied to exploration / chiseled
 * bookshelves / this mod's progression loop instead of allowing players to mass-produce them from
 * ordinary books at the vanilla table.
 */
@Mixin(EnchantmentMenu.class)
public abstract class DisableBookEnchanting {
    @Shadow @Final private Container enchantSlots;
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void cet$disableVanillaBookEnchanting(Container container, CallbackInfo ci) {
        if (container != this.enchantSlots) return;
        if (!container.getItem(0).is(Items.BOOK)) return;

        Arrays.fill(this.costs, 0);
        Arrays.fill(this.enchantClue, -1);
        Arrays.fill(this.levelClue, -1);
        ((EnchantmentMenu) (Object) this).broadcastChanges();
        ci.cancel();
    }
}
