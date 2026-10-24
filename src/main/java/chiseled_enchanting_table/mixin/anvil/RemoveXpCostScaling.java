package chiseled_enchanting_table.mixin.anvil;

import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the anvil's prior-work penalty (`DataComponents.REPAIR_COST`) from scaling
 * exponentially (`2 * cost + 1`), keeping repeated anvil repairs and book applications
 * flat instead of hitting "Too Expensive!".
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveXpCostScaling extends ItemCombinerMenu {
	@Unique
	private static final ThreadLocal<Boolean> CET_CLIENT_ANVIL_SIDE = new ThreadLocal<>();

	public RemoveXpCostScaling(
		@Nullable MenuType<?> type,
		int syncId,
		Inventory playerInventory,
		ContainerLevelAccess context,
		ItemCombinerMenuSlotDefinition forgingSlotsManager
	) {
		super(type, syncId, playerInventory, context, forgingSlotsManager);
	}

	@WrapMethod(method = "createResult")
	private void cet$trackAnvilSide(Operation<Void> original) {
		Boolean previous = CET_CLIENT_ANVIL_SIDE.get();
		boolean clientSide = this.player != null && this.player.level() != null && this.player.level().isClientSide();
		CET_CLIENT_ANVIL_SIDE.set(clientSide);
		try {
			original.call();
		} finally {
			if (previous == null) {
				CET_CLIENT_ANVIL_SIDE.remove();
			} else {
				CET_CLIENT_ANVIL_SIDE.set(previous);
			}
		}
	}

	@Unique
	private static final java.util.concurrent.atomic.AtomicBoolean CET_LOGGED_UNSCOPED_ANVIL_CALL = new java.util.concurrent.atomic.AtomicBoolean(false);

	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void cet$keepFlatRepairCost(int oldRepairCost, CallbackInfoReturnable<Integer> cir) {
		Boolean clientSide = CET_CLIENT_ANVIL_SIDE.get();
		if (clientSide == null && CET_LOGGED_UNSCOPED_ANVIL_CALL.compareAndSet(false, true)) {
			chiseled_enchanting_table.ChiseledEnchantingTable.LOGGER.debug(
				"AnvilMenu.calculateIncreasedRepairCost invoked outside AnvilMenu.createResult; falling back to server runtimeConfiguration()"
			);
		}
		ModRuntimeConfig cfg = clientSide != null
			? ModRuntimeConfig.forAnvilSide(clientSide)
			: ChiseledEnchantingTableData.runtimeConfiguration();
		if (cfg != null && cfg.anvilFlatRepairCost()) {
			cir.setReturnValue(oldRepairCost);
		}
	}
}
