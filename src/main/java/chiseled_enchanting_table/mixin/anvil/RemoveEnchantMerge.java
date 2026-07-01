package chiseled_enchanting_table.mixin.anvil;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the upstream balance rule for enchanted books, but deliberately keeps the ported patch
 * narrower than the old redirect implementation: only book+book same-level upgrades are blocked.
 *
 * <p>Vanilla's anvil upgrades an enchantment when the left and right levels are equal. For this mod
 * that lets players bypass the chiseled-table progression by repeatedly combining two equal-level
 * enchanted books into a higher-level book. After vanilla has produced the result, this mixin clears
 * the output if both inputs are enchanted books and any overlapping enchantment would have been
 * upgraded by the equal-level rule.</p>
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveEnchantMerge extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;

    private RemoveEnchantMerge(
        MenuType<?> menuType,
        int containerId,
        Inventory inventory,
        ContainerLevelAccess access,
        ItemCombinerMenuSlotDefinition slotDefinition
    ) {
        super(menuType, containerId, inventory, access, slotDefinition);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void cet$removeSameLevelBookMerge(CallbackInfo ci) {
        if (!hasSameLevelBookUpgrade(this.inputSlots)) return;

        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.cost.set(0);
        this.broadcastChanges();
    }

    private static boolean hasSameLevelBookUpgrade(Container inputSlots) {
        ItemStack left = inputSlots.getItem(0);
        ItemStack right = inputSlots.getItem(1);
        if (!left.is(Items.ENCHANTED_BOOK) || !right.is(Items.ENCHANTED_BOOK)) return false;

        ItemEnchantments leftEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
        if (leftEnchantments.isEmpty() || rightEnchantments.isEmpty()) return false;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int leftLevel = leftEnchantments.getLevel(enchantment);
            int rightLevel = entry.getIntValue();
            if (leftLevel > 0 && leftLevel == rightLevel && leftLevel < enchantment.value().getMaxLevel()) {
                return true;
            }
        }
        return false;
    }
}
