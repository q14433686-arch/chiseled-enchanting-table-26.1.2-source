package chiseled_enchanting_table.utils;

import java.util.stream.Stream;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;

public class EnchantmentFinder {
    public static Stream<EnchantmentWithLevel> streamAllEnchantements(ChiseledBookShelfBlockEntity cbsbe, Level level) {
        return Stream.iterate(0, i -> i + 1)
            .limit(cbsbe.getContainerSize())
            .map(cbsbe::getItem)
            .flatMap(stack -> {
                ItemEnchantments itemEnchantmentsComponent = stack.get(DataComponents.STORED_ENCHANTMENTS);
                if (itemEnchantmentsComponent == null) return Stream.empty();
                return itemEnchantmentsComponent.entrySet().stream()
                    .map(e -> new EnchantmentWithLevel(
                        ChiseledEnchantingTableScreenHandler.EnchantmentToIdentifier(e.getKey()),
                        e.getIntValue()
                    ));
            });
    }
}
