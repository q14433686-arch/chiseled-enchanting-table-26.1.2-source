package chiseled_enchanting_table.mixin.anvil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1) Blocks the anvil's same-level enchanted-book merge path (Sharpness I + Sharpness I ->
 *    Sharpness II) so players use the Chiseled Enchanting Table's own upgrade mechanics,
 *    while allowing book-to-item application and non-upgrading book combinations.
 * 2) Preserves `count - 1` enchanted books when a stack (`max_stack_size = 64`) is placed
 *    into the right anvil slot (`slot 1`), since vanilla assumes unstackable books (`count == 1`)
 *    and otherwise wipes the entire stack in `AnvilMenu#onTake`.
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveEnchantMerge extends ItemCombinerMenu {

	@Shadow
	@Final
	private DataSlot cost;

	@Unique
	private ItemStack cet$rightBookRemainder = ItemStack.EMPTY;

	protected RemoveEnchantMerge(
		MenuType<?> type,
		int containerId,
		net.minecraft.world.entity.player.Inventory playerInventory,
		net.minecraft.world.inventory.ContainerLevelAccess access,
		ItemCombinerMenuSlotDefinition slotDefinition
	) {
		super(type, containerId, playerInventory, access, slotDefinition);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void cet$blockSameLevelBookMerge(CallbackInfo ci) {
		ItemStack left = this.inputSlots.getItem(0);
		ItemStack right = this.inputSlots.getItem(1);
		if (!left.is(Items.ENCHANTED_BOOK) || !right.is(Items.ENCHANTED_BOOK)) {
			return;
		}
		ItemEnchantments leftEnch = EnchantmentHelper.getEnchantmentsForCrafting(left);
		ItemEnchantments rightEnch = EnchantmentHelper.getEnchantmentsForCrafting(right);
		for (var entry : rightEnch.entrySet()) {
			int rightLevel = entry.getIntValue();
			int leftLevel = leftEnch.getLevel(entry.getKey());
			int maxLevel = entry.getKey().value().getMaxLevel();
			if (leftLevel > 0 && leftLevel == rightLevel && leftLevel < maxLevel) {
				this.resultSlots.setItem(0, ItemStack.EMPTY);
				this.cost.set(0);
				return;
			}
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void cet$captureStackedBookRemainder(Player player, ItemStack carried, CallbackInfo ci) {
		ItemStack right = this.inputSlots.getItem(1);
		if (right.is(Items.ENCHANTED_BOOK) && right.getCount() > 1) {
			this.cet$rightBookRemainder = right.copyWithCount(right.getCount() - 1);
		} else {
			this.cet$rightBookRemainder = ItemStack.EMPTY;
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void cet$restoreStackedBookRemainder(Player player, ItemStack carried, CallbackInfo ci) {
		if (!this.cet$rightBookRemainder.isEmpty() && this.inputSlots.getItem(1).isEmpty()) {
			this.inputSlots.setItem(1, this.cet$rightBookRemainder);
		}
		this.cet$rightBookRemainder = ItemStack.EMPTY;
	}
}
