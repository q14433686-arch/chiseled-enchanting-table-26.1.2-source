package chiseled_enchanting_table.mechanics.enchanting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Server-neutral translatable "墨污" (Ink Stain) lore stamp applied when an overloaded
 * {@code Infinity} Master Tome cross-lines an enchantment onto an item, and automatically washed
 * off when the item is cleansed in the Cleanse tab.
 */
public final class InkStainLore {
    public static final String LORE_KEY = "item.chiseled_enchanting_table.ink_stain_lore";

    private InkStainLore() {}

    public static Component createLine() {
        return Component.translatable(LORE_KEY)
            .withStyle(style -> style.withColor(ChatFormatting.DARK_PURPLE).withItalic(true));
    }

    public static boolean isInkStainComponent(Component c) {
        return c != null
            && c.getContents() instanceof TranslatableContents tc
            && LORE_KEY.equals(tc.getKey());
    }

    public static boolean hasInkStain(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return false;
        }
        for (Component line : lore.lines()) {
            if (isInkStainComponent(line)) {
                return true;
            }
        }
        return false;
    }

    public static void applyInkStain(ItemStack stack) {
        if (stack == null || stack.isEmpty() || hasInkStain(stack)) {
            return;
        }
        ItemLore existing = stack.get(DataComponents.LORE);
        List<Component> lines = new ArrayList<>();
        if (existing != null) {
            lines.addAll(existing.lines());
        }
        lines.add(createLine());
        stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
    }

    public static void removeInkStain(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing == null || existing.lines().isEmpty()) {
            return;
        }
        List<Component> kept = new ArrayList<>(existing.lines().size());
        boolean removedAny = false;
        for (Component line : existing.lines()) {
            if (isInkStainComponent(line)) {
                removedAny = true;
            } else {
                kept.add(line);
            }
        }
        if (!removedAny) {
            return;
        }
        if (kept.isEmpty()) {
            stack.remove(DataComponents.LORE);
        } else {
            stack.set(DataComponents.LORE, new ItemLore(List.copyOf(kept)));
        }
    }

    /**
     * Washes off the ink stain after a Cleanse removal action ({@code DISENCHANT_ALL},
     * {@code STRIP_SINGLE_ENCHANTMENT}, or {@code PURGE_CURSE}) once no unusable or curse traits
     * remain on gear (or immediately when stripping/purging on a book).
     */
    public static void reconcileAfterRemovalCleanse(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !hasInkStain(stack)) {
            return;
        }
        if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) {
            removeInkStain(stack);
            return;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            var holder = entry.getKey();
            if (holder.is(EnchantmentTags.CURSE) || !holder.value().canEnchant(stack)) {
                return;
            }
        }
        removeInkStain(stack);
    }
}
