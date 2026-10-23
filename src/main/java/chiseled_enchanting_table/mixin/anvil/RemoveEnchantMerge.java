package chiseled_enchanting_table.mixin.anvil;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil safety &amp; progression enforcement for stackable {@code Enchanted Book}s:
 * <ol>
 *   <li>Blocks same-level {@code book + book -> level + 1} merges when configured so book tier
 *       escalation goes through the Chiseled Enchanting Table.</li>
 *   <li>Evaluates non-renaming left-slot {@code Enchanted Book} stacks ({@code count > 1}) as a single
 *       book ({@code count == 1}) during {@code createResult}, preventing Creative 64× duplication and
 *       the vanilla multi-item {@code cost = 40} lock while preserving {@code count - 1} remainders
 *       in both slot 0 and slot 1 upon {@code onTake}.</li>
 * </ol>
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveEnchantMerge extends ItemCombinerMenu {
    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private boolean onlyRenaming;

    @Unique
    private int cet$savedLeftBookCount = 0;

    @Unique
    private ItemStack cet$leftBookRemainder = ItemStack.EMPTY;

    @Unique
    private ItemStack cet$rightBookRemainder = ItemStack.EMPTY;

    public RemoveEnchantMerge(
        @Nullable MenuType<?> type,
        int syncId,
        Inventory playerInventory,
        ContainerLevelAccess context,
        ItemCombinerMenuSlotDefinition forgingSlotsManager
    ) {
        super(type, syncId, playerInventory, context, forgingSlotsManager);
    }

    @Inject(method = "createResult", at = @At("HEAD"))
    private void cet$normalizeStackedLeftBookHead(CallbackInfo ci) {
        this.cet$savedLeftBookCount = 0;
        ItemStack left = this.inputSlots.getItem(0);
        ItemStack right = this.inputSlots.getItem(1);
        if (!right.isEmpty() && left.is(Items.ENCHANTED_BOOK) && left.getCount() > 1) {
            this.cet$savedLeftBookCount = left.getCount();
            left.setCount(1);
        }
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void cet$restoreStackedLeftBookAndFilterSameLevelMerge(CallbackInfo ci) {
        ItemStack left = this.inputSlots.getItem(0);
        if (this.cet$savedLeftBookCount > 1 && left.is(Items.ENCHANTED_BOOK)) {
            left.setCount(this.cet$savedLeftBookCount);
        }
        this.cet$savedLeftBookCount = 0;

        if (!ChiseledEnchantingTableData.anvilBlockSameBookMerge()) {
            return;
        }
        ItemStack right = this.inputSlots.getItem(1);
        if (!left.is(Items.ENCHANTED_BOOK) || !right.is(Items.ENCHANTED_BOOK)) {
            return;
        }
        ItemStack result = this.resultSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }

        ItemEnchantments leftEnchants = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnchants = EnchantmentHelper.getEnchantmentsForCrafting(right);
        ItemEnchantments resultEnchants = EnchantmentHelper.getEnchantmentsForCrafting(result);

        for (var entry : rightEnchants.entrySet()) {
            var enchant = entry.getKey();
            int rightLevel = entry.getIntValue();
            int leftLevel = leftEnchants.getLevel(enchant);
            if (leftLevel > 0 && leftLevel == rightLevel && resultEnchants.getLevel(enchant) > leftLevel) {
                this.resultSlots.setItem(0, ItemStack.EMPTY);
                this.cost.set(0);
                this.broadcastChanges();
                return;
            }
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void cet$captureStackedBookRemainders(Player player, ItemStack carried, CallbackInfo ci) {
        this.cet$leftBookRemainder = ItemStack.EMPTY;
        this.cet$rightBookRemainder = ItemStack.EMPTY;

        ItemStack left = this.inputSlots.getItem(0);
        if (!this.onlyRenaming && left.is(Items.ENCHANTED_BOOK) && left.getCount() > 1) {
            this.cet$leftBookRemainder = left.copyWithCount(left.getCount() - 1);
        }

        ItemStack right = this.inputSlots.getItem(1);
        if (right.is(Items.ENCHANTED_BOOK) && right.getCount() > 1) {
            this.cet$rightBookRemainder = right.copyWithCount(right.getCount() - 1);
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void cet$restoreStackedBookRemainders(Player player, ItemStack carried, CallbackInfo ci) {
        boolean restored = false;
        if (!this.cet$leftBookRemainder.isEmpty() && this.inputSlots.getItem(0).isEmpty()) {
            this.inputSlots.setItem(0, this.cet$leftBookRemainder);
            restored = true;
        }
        if (!this.cet$rightBookRemainder.isEmpty() && this.inputSlots.getItem(1).isEmpty()) {
            this.inputSlots.setItem(1, this.cet$rightBookRemainder);
            restored = true;
        }
        this.cet$leftBookRemainder = ItemStack.EMPTY;
        this.cet$rightBookRemainder = ItemStack.EMPTY;
        if (restored) {
            this.broadcastChanges();
        }
    }
}
