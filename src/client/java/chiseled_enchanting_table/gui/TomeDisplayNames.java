package chiseled_enchanting_table.gui;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
/** Stateless tome titles and duration text, independent of list widget ownership. */
final class TomeDisplayNames {
    private TomeDisplayNames() {}
    static String tomeTitle(ChiseledEnchantingMenuPayloads.TomeBookEntry book, Player player) {
        String custom = book.custom_name() == null ? "" : book.custom_name().trim();
        String defaultName = new ItemStack(Items.ENCHANTED_BOOK).getHoverName().getString();
        if (!custom.isEmpty() && !custom.equals(defaultName)) return custom;
        if (book.enchantments().isEmpty()) return custom.isEmpty() ? defaultName : custom;
        var first = book.enchantments().getFirst();
        var holder = EnchantmentDisplayNames.findEnchantment(first.enchantment_id(), player);
        String title = holder.map(h -> Enchantment.getFullname(h, first.enchantment_level()).getString())
            .orElse(first.enchantment_id().toString());
        return book.enchantments().size() > 1 ? title + " +" + (book.enchantments().size() - 1) : title;
    }

    static String formatTomeEta(int ticks) {
        if (ticks < 0) return "--:--";
        long seconds = (ticks + 19L) / 20L;
        long hours = seconds / 3600L;
        long mins = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0
            ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, mins, secs)
            : String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs);
    }
}
