package chiseled_enchanting_table.utils;

import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

public class EnchantmentFinder {
    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

    /**
     * Extracts qualifying (non-curse, {@code level > 0}) stored enchantments from a single
     * unlocked Master Tome stack, returning an empty list when empty or effectively locked.
     */
    public static List<EnchantmentWithLevel> extractUnlockedSlotEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty() || TomeLockData.isEffectivelyLocked(stack)) {
            return List.of();
        }
        ItemEnchantments itemEnchantmentsComponent = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (itemEnchantmentsComponent == null || itemEnchantmentsComponent.isEmpty()) {
            return List.of();
        }
        List<EnchantmentWithLevel> out = new ArrayList<>(itemEnchantmentsComponent.size());
        for (var e : itemEnchantmentsComponent.entrySet()) {
            if (!e.getKey().is(CURSE_TAG) && e.getIntValue() > 0 && e.getKey().unwrapKey().isPresent()) {
                out.add(new EnchantmentWithLevel(e.getKey().unwrapKey().orElseThrow().identifier(), e.getIntValue()));
            }
        }
        return out;
    }

    /**
     * Streams every qualifying (non-curse, {@code level > 0}) stored enchantment entry across all
     * books stored inside {@code cbsbe}, ensuring multi-enchant books contribute every qualifying
     * enchantment entry while curses never pollute Master Tome unlocks or Library Resonance.
     */
    public static Stream<EnchantmentWithLevel> streamAllEnchantements(ChiseledBookShelfBlockEntity cbsbe, Level level) {
        return Stream.iterate(0, i -> i + 1)
            .limit(cbsbe.getContainerSize())
            .map(cbsbe::getItem)
            .flatMap(stack -> extractUnlockedSlotEnchantments(stack).stream());
    }
}
