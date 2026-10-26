package chiseled_enchanting_table.menu;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
/** Registry queries without retaining the menu coordinator. */
final class MenuEnchantmentLookup {
    private MenuEnchantmentLookup() {}
    static java.util.Optional<Holder.Reference<Enchantment>> findEnchantment(Identifier id, Level world) {
        if (id == null || world == null) {
            return java.util.Optional.empty();
        }
        return world.registryAccess()
            .lookup(Registries.ENCHANTMENT)
            .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, id)));
    }
}
