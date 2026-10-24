package chiseled_enchanting_table.mixin.anvil;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
 *
 * <p>Audit MX-04/M-07: the count normalisation used to be a HEAD/RETURN inject pair mutating the
 * real input stack — any exception or third-party cancellation inside vanilla
 * {@code createResult} skipped the RETURN restore and permanently shrank the player's stack to 1.
 * It is now a single {@code @WrapMethod} with a Java {@code try/finally}, and the saved count is a
 * method-local instead of a cross-tick field. The {@code onTake} remainder capture no longer
 * depends on {@code onlyRenaming} alone: a non-empty right slot that contributes no enchantment
 * cost can still make vanilla report {@code onlyRenaming == true} while its {@code onTake}
 * empties the left slot, which used to lose the stacked remainder.</p>
 */
@Mixin(AnvilMenu.class)
public abstract class RemoveEnchantMerge extends ItemCombinerMenu {
    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private boolean onlyRenaming;

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

    @WrapMethod(method = "createResult")
    private void cet$wrapCreateResult(Operation<Void> original) {
        ItemStack left = this.inputSlots.getItem(0);
        ItemStack right = this.inputSlots.getItem(1);
        int savedLeftCount = 0;
        if (!right.isEmpty() && left.is(Items.ENCHANTED_BOOK) && left.getCount() > 1) {
            savedLeftCount = left.getCount();
            left.setCount(1);
        }
        try {
            original.call();
        } finally {
            if (savedLeftCount > 1) {
                ItemStack current = this.inputSlots.getItem(0);
                if (current == left && current.is(Items.ENCHANTED_BOOK)) {
                    current.setCount(savedLeftCount);
                }
            }
        }
        cet$filterSameLevelMerge();
    }

    @Unique
    private void cet$filterSameLevelMerge() {
        ItemStack lockedLeft = this.inputSlots.getItem(0);
        ItemStack lockedRight = this.inputSlots.getItem(1);
        if (chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(lockedLeft)
            || chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(lockedRight)) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
            this.broadcastChanges();
            return;
        }
        boolean clientSide = this.player != null && this.player.level() != null && this.player.level().isClientSide();
        var runtimeCfg = chiseled_enchanting_table.config.ModRuntimeConfig.forAnvilSide(clientSide);
        if (runtimeCfg == null || !runtimeCfg.anvilBlockSameBookMerge()) {
            return;
        }
        ItemStack left = this.inputSlots.getItem(0);
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

        // Audit M-07: onlyRenaming can be true even with a non-empty right slot (the right
        // book contributes no enchantment cost), and vanilla onTake still empties the left
        // slot in that case. Capture whenever a take could consume the slot.
        ItemStack right = this.inputSlots.getItem(1);
        ItemStack left = this.inputSlots.getItem(0);
        if ((!this.onlyRenaming || !right.isEmpty()) && left.is(Items.ENCHANTED_BOOK) && left.getCount() > 1) {
            this.cet$leftBookRemainder = left.copyWithCount(left.getCount() - 1);
        }

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
