package enchantment_fusion.mechanics.signatures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import enchantment_fusion.EnchantmentFusion;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;

/** Player-owned, bounded escrow of item units removed from actual spawned drops. */
final class FortuneBankLedger {
    private static final int MAX_TYPES = 16, MAX_STORED = 256;
    private record Credit(ItemStack sample, int count) {
        private static final Codec<Credit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("sample").forGetter(Credit::sample),
            Codec.intRange(1, MAX_STORED).fieldOf("count").forGetter(Credit::count)
        ).apply(instance, Credit::new));
    }
    private static final AttachmentType<List<Credit>> BANK = AttachmentRegistry.create(
        EnchantmentFusion.id("fortune_ore_bank"),
        builder -> builder.persistent(Credit.CODEC.listOf(0, MAX_TYPES)).copyOnDeath());
    private FortuneBankLedger() {}
    static void init() {}
    private static List<Credit> entries(ServerPlayer player) {
        List<Credit> current = player.getAttached(BANK);
        return current == null ? List.of() : current;
    }
    /** Duplicate or malformed entries are refused rather than minting or silently discarding resources. */
    private static int index(List<Credit> credits, ItemStack item) {
        if (credits.size() > MAX_TYPES) return -2;
        int found = -1;
        for (int i = 0; i < credits.size(); i++) {
            Credit credit = credits.get(i);
            if (credit == null || credit.sample() == null || credit.sample().isEmpty() || credit.sample().getCount() != 1
                || credit.count() < 1 || credit.count() > MAX_STORED) return -2;
            if (ItemStack.isSameItemSameComponents(credit.sample(), item)) {
                if (found >= 0) return -2;
                found = i;
            }
        }
        return found;
    }
    static int amount(ServerPlayer player, ItemStack item) {
        List<Credit> credits = entries(player); int index = index(credits, item);
        return index < 0 ? 0 : credits.get(index).count();
    }
    static int availableCapacity(ServerPlayer player, ItemStack item, int configuredCap) {
        List<Credit> credits = entries(player); int limit = Math.clamp(configuredCap, 1, MAX_STORED), index = index(credits, item);
        if (index == -2) return 0;
        if (index >= 0) return Math.max(0, limit - credits.get(index).count());
        return credits.size() >= MAX_TYPES ? 0 : limit;
    }
    /** The caller has verified entity identity, provenance, amount, and the exact original stack. */
    static boolean deposit(ServerPlayer player, ItemEntity entity, ItemStack expected, int amount, int cap) {
        if (amount <= 0 || !entity.isAlive() || entity.getItem() != expected || expected.getCount() <= amount
            || availableCapacity(player, expected, cap) < amount) return false;
        List<Credit> next = new ArrayList<>(); boolean found = false;
        for (Credit credit : entries(player)) {
            if (!credit.sample().isEmpty() && ItemStack.isSameItemSameComponents(credit.sample(), expected)) {
                next.add(new Credit(credit.sample(), credit.count() + amount)); found = true;
            } else next.add(credit);
        }
        if (!found) next.add(new Credit(expected.copyWithCount(1), amount));
        return commit(player, entity, expected, expected.copyWithCount(expected.getCount() - amount), next);
    }
    static boolean payout(ServerPlayer player, ItemEntity entity, ItemStack expected, int amount) {
        if (amount <= 0 || !entity.isAlive() || entity.getItem() != expected || amount > expected.getMaxStackSize() - expected.getCount()
            || amount(player, expected) < amount) return false;
        List<Credit> next = new ArrayList<>(); boolean found = false;
        for (Credit credit : entries(player)) {
            if (!credit.sample().isEmpty() && ItemStack.isSameItemSameComponents(credit.sample(), expected)) {
                int remainder = credit.count() - amount;
                if (remainder > 0) next.add(new Credit(credit.sample(), remainder));
                found = true;
            } else next.add(credit);
        }
        return found && commit(player, entity, expected, expected.copyWithCount(expected.getCount() + amount), next);
    }
    record Withdrawal(ItemStack item, int count, int remaining) {}
    /** Fallback cash-out keeps both the inventory and ledger in the player's save record. */
    static @Nullable Withdrawal withdrawToOffhand(ServerPlayer player) {
        List<Credit> credits = entries(player); ItemStack before = player.getOffhandItem();
        if (credits.isEmpty()) return null;
        Credit first = credits.getFirst();
        if (first == null) return null;
        ItemStack sample = before.isEmpty() ? first.sample() : before;
        if (sample == null || sample.isEmpty() || !sample.isItemEnabled(player.level().enabledFeatures())) return null;
        int at = index(credits, sample); if (at < 0) return null;
        Credit credit = credits.get(at);
        int room = before.isEmpty() ? sample.getMaxStackSize() : before.getMaxStackSize() - before.getCount();
        int amount = Math.min(room, credit.count()); if (amount <= 0) return null;
        ItemStack after = before.isEmpty() ? sample.copyWithCount(amount) : before.copyWithCount(before.getCount() + amount);
        int remaining = credit.count() - amount;
        List<Credit> next = new ArrayList<>(credits);
        if (remaining == 0) next.remove(at); else next.set(at, new Credit(credit.sample(), remaining));
        player.setItemSlot(EquipmentSlot.OFFHAND, after);
        try {
            player.setAttached(BANK, next.isEmpty() ? null : List.copyOf(next));
            return new Withdrawal(after, amount, remaining);
        } catch (RuntimeException exception) {
            player.setItemSlot(EquipmentSlot.OFFHAND, before);
            EnchantmentFusion.LOGGER.warn("Could not save a Fortune reserve withdrawal; offhand restored", exception);
            return null;
        }
    }
    private static boolean commit(ServerPlayer player, ItemEntity entity, ItemStack before, ItemStack after, List<Credit> next) {
        // The live drop is changed first. If storing the attachment fails synchronously, restore it.
        entity.setItem(after);
        try {
            player.setAttached(BANK, next.isEmpty() ? null : List.copyOf(next));
            return true;
        } catch (RuntimeException exception) {
            entity.setItem(before);
            EnchantmentFusion.LOGGER.warn("Could not save a Fortune escrow transaction; original drop restored", exception);
            return false;
        }
    }
}
