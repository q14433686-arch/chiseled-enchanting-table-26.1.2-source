package chiseled_enchanting_table.mixin.enchanting_table;

import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable vanilla Enchanting Table integration governed by {@code vanilla_table_mode}:
 * <ul>
 *   <li>{@code BOOKS} (default): blocks {@code minecraft:book} so Master Tomes are curated on the Chiseled Enchanting Table.</li>
 *   <li>{@code ALIGN_CAPS}: blocks {@code minecraft:book} AND clamps gear offers to {@link BookshelfProgression#maxBlankWoodenTier}
 *       so a 15-shelf vanilla table cannot bypass the configured Chiseled wooden-shelf progression cap.</li>
 *   <li>{@code ALL}: disables all vanilla Enchanting Table offers.</li>
 *   <li>{@code NONE}: leaves the vanilla Enchanting Table completely untouched.</li>
 * </ul>
 */
@Mixin(EnchantmentMenu.class)
public abstract class DisableBookEnchanting extends AbstractContainerMenu {
    @Shadow
    @Final
    private Container enchantSlots;

    @Shadow
    @Final
    public int[] costs;

    @Shadow
    @Final
    public int[] enchantClue;

    @Shadow
    @Final
    public int[] levelClue;

    @Shadow
    @Final
    private DataSlot enchantmentSeed;

    protected DisableBookEnchanting(@Nullable MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void cet$blockVanillaBookEnchanting(Container inventory, CallbackInfo ci) {
        if (inventory != this.enchantSlots) {
            return;
        }
        VanillaTableMode mode = ChiseledEnchantingTableData.vanillaEnchantingMode();
        if (mode == VanillaTableMode.NONE) {
            return;
        }
        ItemStack stack = inventory.getItem(0);
        if (mode == VanillaTableMode.ALL || stack.is(Items.BOOK)) {
            for (int i = 0; i < 3; i++) {
                this.costs[i] = 0;
                this.enchantClue[i] = -1;
                this.levelClue[i] = -1;
            }
            this.broadcastChanges();
            ci.cancel();
        }
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void cet$alignVanillaGearCaps(
        net.minecraft.core.RegistryAccess registryAccess,
        ItemStack stack,
        int slot,
        int cost,
        CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        if (ChiseledEnchantingTableData.vanillaEnchantingMode() != VanillaTableMode.ALIGN_CAPS) {
            return;
        }
        List<EnchantmentInstance> raw = cir.getReturnValue();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        BookshelfProgression.Config sgCfg = ChiseledEnchantingTableData.shelfGatesConfig();
        List<EnchantmentInstance> clamped = new ArrayList<>(raw.size());
        for (EnchantmentInstance inst : raw) {
            int maxLvl = Math.max(1, inst.enchantment().value().getMaxLevel());
            Identifier enchId = inst.enchantment().unwrapKey()
                .map(rk -> rk.identifier())
                .orElse(null);
            int cap = BookshelfProgression.maxBlankWoodenTier(
                sgCfg,
                enchId,
                maxLvl,
                sgCfg.maxDirectBookshelves()
            );
            int newLvl = Math.max(1, Math.min(inst.level(), cap));
            clamped.add(new EnchantmentInstance(inst.enchantment(), newLvl));
        }
        cir.setReturnValue(clamped);
    }
}
