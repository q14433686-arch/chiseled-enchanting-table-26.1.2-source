package enchantment_fusion.mechanics.transmute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog.ReplenishPolicy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Player-owned progress for the explicitly chosen XP + consumable dual benefit. Samples are
 * identity keys, never inventory items. Splitting a stack cannot clone its progress, and another
 * player's XP never advances this player's attachment. The oldest of at most 32 groups is evicted.
 */
public final class ReplenishTracker {
    private static final int MAX_GROUPS = 32;

    private record Progress(ItemStack sample, int experience) {
        private static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("sample").forGetter(Progress::sample),
            Codec.intRange(0, 10_000).fieldOf("experience").forGetter(Progress::experience)
        ).apply(instance, Progress::new));
    }

    private static final AttachmentType<List<Progress>> PROGRESS = AttachmentRegistry.create(
        EnchantmentFusion.id("replenish_progress"),
        builder -> builder.persistent(Progress.CODEC.listOf(0, MAX_GROUPS)).copyOnDeath()
    );

    private ReplenishTracker() {}

    /** Force registration before player save data is loaded. */
    public static void init() {}

    /** Call exactly once after real positive XP is credited, with the amount actually credited. */
    public static void onExperienceCredited(ServerPlayer player, int gained) {
        if (gained <= 0 || !player.isAlive()) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) continue;
            // Preserve one-hand-at-a-time priority; do not apply the replenish dual-benefit rule
            // to bottle conversion or totem vitality, or let both consume the same observation.
            if (TransmuteCatalog.getTransmutedLevel(held, "mending_xp_bottle") > 0
                || TransmuteCatalog.getTransmutedLevel(held, "mending_vitality") > 0) {
                RelicWorldItemTransmutes.handleSpecialMendingXpGain(player, gained);
                return;
            }
            if (TransmuteCatalog.getTransmutedLevel(held, "mending_replenish") > 0) {
                apply(player, held, gained);
                return;
            }
        }
    }

    private static void apply(ServerPlayer player, ItemStack held, int gained) {
        ReplenishPolicy policy = TransmuteCatalog.replenishPolicy(held);
        Inventory inventory = player.getInventory();
        int total = matchingCount(inventory, held);
        if (total >= policy.groupCap()) return;

        List<Progress> existing = player.getAttached(PROGRESS);
        List<Progress> groups = new ArrayList<>();
        int prior = 0;
        if (existing != null) {
            for (Progress entry : existing) {
                if (entry == null || entry.sample().isEmpty()) continue;
                if (ItemStack.isSameItemSameComponents(entry.sample(), held)) {
                    prior = Math.max(prior, entry.experience());
                } else {
                    groups.add(entry);
                }
            }
        }
        // Bound both arithmetic and payout work even if a command/mod awards enormous XP.
        long bank = Math.min((long) prior + gained,
            (long) policy.xpInterval() * ((long) policy.groupCap() - total + 1));
        boolean blocked = false;
        while (bank >= policy.xpInterval() && total < policy.groupCap()) {
            if (held.getCount() < held.getMaxStackSize()) {
                held.grow(1);
            } else {
                // Unstackable potions and full stacks get a separate identical item, but never
                // drop it in the world or claim success when an inventory cannot actually take it.
                ItemStack copy = held.copyWithCount(1);
                inventory.add(copy);
                if (matchingCount(inventory, held) <= total) {
                    blocked = true;
                    break;
                }
            }
            total++;
            bank -= policy.xpInterval();
        }
        if (total >= policy.groupCap()) bank = Math.min(bank, policy.xpInterval() - 1L);
        else if (blocked) bank = Math.min(bank, policy.xpInterval());
        if (bank > 0) groups.add(new Progress(held.copyWithCount(1), (int) bank));
        while (groups.size() > MAX_GROUPS) groups.removeFirst();
        player.setAttached(PROGRESS, groups.isEmpty() ? null : List.copyOf(groups));
    }

    private static int matchingCount(Inventory inventory, ItemStack sample) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sample)) total += stack.getCount();
        }
        return total;
    }
}
