package chiseled_enchanting_table.utils;

import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import chiseled_enchanting_table.menu.ChiseledEnchantingTableScreenHandler;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

public class EnchantmentFinder {
    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    /**
     * Streams every qualifying (non-curse, {@code level > 0}) stored enchantment entry across all
     * books stored inside {@code cbsbe}, ensuring multi-enchant books contribute every qualifying
     * enchantment entry while curses never pollute Master Tome unlocks or Library Resonance.
     */
    public static Stream<EnchantmentWithLevel> streamAllEnchantements(ChiseledBookShelfBlockEntity cbsbe, Level level) {
        return Stream.iterate(0, i -> i + 1)
            .limit(cbsbe.getContainerSize())
            .map(cbsbe::getItem)
            .filter(stack -> !TomeLockData.isEffectivelyLocked(stack))
            .flatMap(stack -> {
                ItemEnchantments itemEnchantmentsComponent = stack.get(DataComponents.STORED_ENCHANTMENTS);
                if (itemEnchantmentsComponent == null) return Stream.empty();
                return itemEnchantmentsComponent.entrySet().stream()
                    .filter(e -> !e.getKey().is(CURSE_TAG) && e.getIntValue() > 0 && e.getKey().unwrapKey().isPresent())
                    .map(e -> new EnchantmentWithLevel(
                        e.getKey().unwrapKey().orElseThrow().identifier(),
                        e.getIntValue()
                    ));
            });
    }
}
