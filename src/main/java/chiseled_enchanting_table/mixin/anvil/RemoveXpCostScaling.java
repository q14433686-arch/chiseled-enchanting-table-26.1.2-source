package chiseled_enchanting_table.mixin.anvil;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the anvil's prior-work penalty (`DataComponents.REPAIR_COST`) from scaling
 * exponentially (`2 * cost + 1`), keeping repeated anvil repairs and book applications
 * flat instead of hitting "Too Expensive!".
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveXpCostScaling {

	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void cet$keepFlatRepairCost(int oldRepairCost, CallbackInfoReturnable<Integer> cir) {
		if (chiseled_enchanting_table.config.ModRuntimeConfig.effective().anvilFlatRepairCost()) {
			cir.setReturnValue(oldRepairCost);
		}
	}
}
