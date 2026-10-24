package chiseled_enchanting_table.mixin.grindstone;

import chiseled_enchanting_table.registry.BlockRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adapts the vanilla Grindstone to stackable {@code Enchanted Book}s and the Depleted Tome economy:
 * <ul>
 *   <li>Disenchanting an {@code Enchanted Book} in a single Grindstone input slot consumes {@code 1} book
 *       at a time (preserving {@code count - 1} in the input slot) and awards vanilla 1-book XP.</li>
 *   <li>Uncursed {@code Enchanted Book}s produce {@code 1× chiseled_enchanting_table:depleted_tome}
 *       instead of bypassing Depleted Tome restoration into a plain {@code minecraft:book}.</li>
 *   <li>Simultaneous 2-slot book merging in a Grindstone is disallowed so two Enchanted Books cannot
 *       collapse into {@code 2× minecraft:book}.</li>
 * </ul>
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneBookDepleteMixin extends AbstractContainerMenu {
    @Shadow
    @Final
    Container resultSlots;

    @Shadow
    @Final
    Container repairSlots;

    @Shadow
    private ItemStack removeNonCursesFrom(ItemStack item) {
        throw new AssertionError();
    }

    protected GrindstoneBookDepleteMixin(@Nullable MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
        at = @At("RETURN")
    )
    private void cet$wrapGrindstoneResultSlot(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (this.slots.size() <= GrindstoneMenu.RESULT_SLOT) {
            return;
        }
        Slot delegate = this.slots.get(GrindstoneMenu.RESULT_SLOT);
        Container repairRef = this.repairSlots;
        // Audit MX-05: derive the backing container + index from the delegate itself
        // instead of hardcoding the current vanilla layout (container slot 2).
        Slot wrapped = new Slot(delegate.container, delegate.getContainerSlot(), delegate.x, delegate.y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return delegate.mayPlace(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return delegate.mayPickup(player);
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                ItemStack in0 = repairRef.getItem(0);
                ItemStack in1 = repairRef.getItem(1);
                ItemStack rem0 = in0.is(Items.ENCHANTED_BOOK) && in0.getCount() > 1
                    ? in0.copyWithCount(in0.getCount() - 1)
                    : ItemStack.EMPTY;
                ItemStack rem1 = in1.is(Items.ENCHANTED_BOOK) && in1.getCount() > 1
                    ? in1.copyWithCount(in1.getCount() - 1)
                    : ItemStack.EMPTY;

                delegate.onTake(player, carried);

                if (!rem0.isEmpty() && repairRef.getItem(0).isEmpty()) {
                    repairRef.setItem(0, rem0);
                }
                if (!rem1.isEmpty() && repairRef.getItem(1).isEmpty()) {
                    repairRef.setItem(1, rem1);
                }
            }
        };
        wrapped.index = delegate.index;
        this.slots.set(GrindstoneMenu.RESULT_SLOT, wrapped);
    }

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void cet$computeEnchantedBookDeplete(
        ItemStack input,
        ItemStack additional,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!input.is(Items.ENCHANTED_BOOK) && !additional.is(Items.ENCHANTED_BOOK)) {
            return;
        }
        if (!input.isEmpty() && !additional.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack source = !input.isEmpty() ? input : additional;
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(source);
        boolean hasNonCurse = false;
        for (var entry : stored.entrySet()) {
            if (!entry.getKey().is(EnchantmentTags.CURSE) && entry.getIntValue() > 0) {
                hasNonCurse = true;
                break;
            }
        }
        if (!hasNonCurse) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack single = source.copyWithCount(1);
        ItemStack stripped = this.removeNonCursesFrom(single);
        // Audit disposition: the Depleted Tome return is a core economy hook but must
        // be opt-outable. When off, fall through with the vanilla plain-book result;
        // the one-at-a-time stack protection above stays (stackable books need it).
        if (stripped.is(Items.BOOK)
            && chiseled_enchanting_table.config.ModRuntimeConfig.effective().grindstoneDepletedTome()) {
            ItemStack depleted = new ItemStack(BlockRegistry.DEPLETED_TOME, 1);
            Component customName = source.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                depleted.set(DataComponents.CUSTOM_NAME, customName);
            }
            cir.setReturnValue(depleted);
            return;
        }
        stripped.setCount(1);
        cir.setReturnValue(stripped);
    }
}
