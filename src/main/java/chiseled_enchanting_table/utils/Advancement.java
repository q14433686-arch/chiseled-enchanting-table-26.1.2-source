package chiseled_enchanting_table.utils;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public class Advancement {
    public static void give(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var advancement = serverPlayer.level().getServer().getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        if (advancement == null) return;
        var progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (var criterion : progress.getRemainingCriteria()) {
            serverPlayer.getAdvancements().award(advancement, criterion);
        }
    }

    /** Whether the (server) player already owns the named advancement. */
    public static boolean has(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var advancement = serverPlayer.level().getServer().getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        return advancement != null && serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    public static void checkBookMilestones(Player player, Level level, ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return;
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) return;
        for (var entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            if (entry.getIntValue() >= enchantment.getMaxLevel()) {
                give(player, "max_level_book");
                break;
            }
        }
        long totalKnownEnchantments = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .listElements()
            .filter(holder -> holder.value().getMaxLevel() > 0 && !holder.is(net.minecraft.tags.EnchantmentTags.CURSE))
            .count();
        long nonCurseOnBook = enchantments.keySet().stream()
            .filter(holder -> !holder.is(net.minecraft.tags.EnchantmentTags.CURSE))
            .count();
        if (totalKnownEnchantments > 0 && nonCurseOnBook >= totalKnownEnchantments) {
            give(player, "all_enchant_book");
        }
    }

    public static void checkAllEnchantBook(Player player, Level level, ItemStack enchantableItem) {
        checkBookMilestones(player, level, enchantableItem);
    }
}
