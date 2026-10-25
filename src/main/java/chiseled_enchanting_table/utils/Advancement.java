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
    public static final String DISENCHANT_SACRIFICE = "sacrifice_depleted_tomes";
    public static final String HOW_MUCH_LONGER = "how_much_longer";
    public static final int DISENCHANT_SACRIFICE_REQUIRED = chiseled_enchanting_table.BundledDefaults.SACRIFICE_REQUIRED;
    public static final int TOME_PAGE_REQUIRED_SHELVES = chiseled_enchanting_table.BundledDefaults.TOME_PAGE_REQUIRED_SHELVES;

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

    public static boolean hasCompleted(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var server = serverPlayer.level().getServer();
        if (server == null) return false;
        var advancement = server.getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        return advancement != null && serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Number of criteria already awarded for {@code name}; {@code 0} when unavailable. */
    public static int completedCriteria(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return 0;
        var server = serverPlayer.level().getServer();
        if (server == null) return 0;
        var advancement = server.getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        if (advancement == null) return 0;
        var progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
        int total = advancement.value().criteria().size();
        int remaining = 0;
        for (var ignored : progress.getRemainingCriteria()) {
            remaining++;
        }
        return Math.max(0, Math.min(total, total - remaining));
    }

    /** Total criteria declared by the advancement (datapacks may add more), or 0 when absent. */
    public static int totalCriteria(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return 0;
        var server = serverPlayer.level().getServer();
        if (server == null) return 0;
        var advancement = server.getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        return advancement == null ? 0 : advancement.value().criteria().size();
    }

    /** Awards exactly one remaining criterion. Returns {@code true} when one was awarded. */
    public static boolean awardNextCriterion(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var server = serverPlayer.level().getServer();
        if (server == null) return false;
        var advancement = server.getAdvancements().get(ChiseledEnchantingTable.identifier(name));
        if (advancement == null) return false;
        var progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return false;
        for (var criterion : progress.getRemainingCriteria()) {
            return serverPlayer.getAdvancements().award(advancement, criterion);
        }
        return false;
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
