package chiseled_enchanting_table.mixin.enchanting_table;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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

/**
 * Configurable vanilla Enchanting Table integration governed by {@code vanilla_table_mode}:
 * <ul>
 *   <li>{@code BOOKS} (default): blocks {@code minecraft:book} so Master Tomes are curated on the Chiseled Enchanting Table.</li>
 *   <li>{@code ALIGN_CAPS}: blocks {@code minecraft:book}, excludes {@code #chiseled_enchanting_table:epiphany_exclusive},
 *       and clamps gear offers to {@link BookshelfProgression#maxBlankWoodenTier} so a 15-shelf vanilla table follows
 *       the configured {@code blank_max_tier_mode} ({@code HALF_CEIL} / {@code FULL_CURVE} / {@code LEVEL_ONE_ONLY})
 *       and per-enchantment overrides.</li>
 *   <li>{@code ALL}: disables all vanilla Enchanting Table offers.</li>
 *   <li>{@code NONE}: leaves the vanilla Enchanting Table completely untouched.</li>
 * </ul>
 */
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
        BookshelfProgression.Config sgCfg = ChiseledEnchantingTableData.shelfGatesConfig();
        if (sgCfg.vanillaTableMode() != BookshelfProgression.VanillaTableMode.ALIGN_CAPS) {
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
            Identifier enchId = inst.enchantment().unwrapKey()
                .map(rk -> rk.identifier())
                .orElse(null);
            int cap = BookshelfProgression.maxBlankWoodenTier(
                sgCfg,
                enchId,
                maxLvl,
                sgCfg.maxDirectBookshelves()
            );
            if (cap <= 0) {
                continue;
            }
            clamped.add(new EnchantmentInstance(inst.enchantment(), Math.max(1, Math.min(inst.level(), cap))));
        }
        cir.setReturnValue(List.copyOf(clamped));
    }
}
