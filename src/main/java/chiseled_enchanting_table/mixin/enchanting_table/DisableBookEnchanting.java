package chiseled_enchanting_table.mixin.enchanting_table;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class DisableBookEnchanting {
    @Unique
    private static final TagKey<Enchantment> EPIPHANY_EXCLUSIVE_TAG =
        TagKey.create(Registries.ENCHANTMENT, ChiseledEnchantingTable.identifier("epiphany_exclusive"));

    @Inject(method = "getEnchantmentList", at = @At("HEAD"), cancellable = true)
    private void chiseledEnchantingTable$filterVanillaTableHead(
        RegistryAccess registryManager,
        ItemStack stack,
        int slot,
        int level,
        CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        var mode = ChiseledEnchantingTableData.shelfGatesConfig().vanillaTableMode();
        if (mode == BookshelfProgression.VanillaTableMode.NONE) {
            return;
        }
        if (mode == BookshelfProgression.VanillaTableMode.ALL || stack.is(Items.BOOK)) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void chiseledEnchantingTable$alignVanillaGearCaps(
        RegistryAccess registryManager,
        ItemStack stack,
        int slot,
        int level,
        CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        if (ChiseledEnchantingTableData.shelfGatesConfig().vanillaTableMode()
            != BookshelfProgression.VanillaTableMode.ALIGN_CAPS) {
            return;
        }
        List<EnchantmentInstance> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        List<EnchantmentInstance> clamped = new ArrayList<>(original.size());
        for (EnchantmentInstance inst : original) {
            if (inst.enchantment().is(EPIPHANY_EXCLUSIVE_TAG)) {
                continue;
            }
            int maxLvl = Math.max(1, inst.enchantment().value().getMaxLevel());
            int cap = Math.max(1, (maxLvl + 1) / 2);
            clamped.add(new EnchantmentInstance(inst.enchantment(), Math.min(inst.level(), cap)));
        }
        cir.setReturnValue(List.copyOf(clamped));
    }
}
