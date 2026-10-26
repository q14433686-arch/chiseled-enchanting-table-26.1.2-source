package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.mechanics.enchanting.DisenchantmentApplication;
import net.minecraft.world.item.ItemStack;
/** Confirmation presentation policy; no lifecycle or input dispatch. */
final class TableCleansePresentation {
    private TableCleansePresentation() {}
    static boolean requiresDisenchantAllConfirm(ItemStack target) {
        if (target == null || target.isEmpty() || !target.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            return false;
        }
        return DisenchantmentApplication.countNonCurseEnchantments(target) >= 3
            || chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.hasInfinityImmunity(target);
    }
}
