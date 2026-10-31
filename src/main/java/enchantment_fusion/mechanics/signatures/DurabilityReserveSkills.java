package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.config.FusionConfigStore;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Unbreaking ▷ Infinity: observe the actual Unbreaking effect and bank its prevented wear. */
public final class DurabilityReserveSkills {
    public static final String SIGNATURE = "tenacious_reserve";
    public static final String UNBREAKING = "minecraft:unbreaking", INFINITY = "minecraft:infinity";
    private static final int MAX_ITEMS = 8;
    private static final EquipmentSlot[] ACTIVE_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final class Reserve {
        final ItemStack stack;
        final FusedPair pair;
        final ResourceKey<Level> dimension;
        int points;
        long expires;
        boolean armed;
        Reserve(ItemStack stack, FusedPair pair, ResourceKey<Level> dimension) {
            this.stack = stack; this.pair = pair; this.dimension = dimension;
        }
    }
    public static final class WearFrame {
        private final @Nullable WearFrame previous;
        private final @Nullable ServerPlayer player;
        private final ItemStack stack;
        private final @Nullable FusedPair pair;
        private int prevented;
        private WearFrame(@Nullable WearFrame previous, @Nullable ServerPlayer player, ItemStack stack, @Nullable FusedPair pair) {
            this.previous = previous; this.player = player; this.stack = stack; this.pair = pair;
        }
    }
    private static final Map<UUID, List<Reserve>> RESERVES = new HashMap<>();
    private static final Map<UUID, ItemStack> LAST_SELECTED = new HashMap<>();
    private static final ThreadLocal<WearFrame> WEAR = new ThreadLocal<>();
    private DurabilityReserveSkills() {}

    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return pair != null && UNBREAKING.equals(pair.primaryId()) && INFINITY.equals(pair.secondaryId())
            && SIGNATURE.equals(signature);
    }
    public static boolean supportsCarrier(@Nullable ItemStack stack) { return DurabilityEconomy.supportsCarrier(stack); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return matches(pair, signature) && supportsCarrier(stack);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (!supportsCarrier(stack)) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> matches(pair, entry.id())).orElse(false)) return pair;
        }
        return null;
    }
    private static int cost() { return FusionConfigStore.signatureInt(SIGNATURE, "saved_wear_per_charge", 4, 1, 64); }
    private static int capacity() { return FusionConfigStore.signatureInt(SIGNATURE, "charge_capacity", 3, 1, 10); }
    private static int lifetime() { return FusionConfigStore.signatureInt(SIGNATURE, "reserve_lifetime_ticks", 1200, 20, 12000); }
    private static boolean eligible(@Nullable ServerPlayer player) { return player != null && player.isAlive() && !player.isSpectator(); }
    private static boolean owns(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i) == stack) return true;
        return false;
    }
    private static boolean stillSame(ServerPlayer player, Reserve reserve) {
        return !reserve.stack.isEmpty() && owns(player, reserve.stack) && player.level().dimension().equals(reserve.dimension)
            && reserve.pair.equals(selected(reserve.stack));
    }
    private static @Nullable Reserve find(ServerPlayer player, ItemStack stack) {
        List<Reserve> entries = RESERVES.get(player.getUUID());
        if (entries != null) for (Reserve reserve : entries) if (reserve.stack == stack) return reserve;
        return null;
    }
    private static @Nullable Holder<Enchantment> unbreaking(ServerLevel level) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), UNBREAKING).orElse(null);
    }

    /** Mask nested damage operations too, even if the nested item has no relevant fusion. */
    public static WearFrame begin(ItemStack stack, @Nullable ServerPlayer player) {
        FusedPair pair = eligible(player) && DurabilityEconomy.actuallyEquipped(player, stack) ? selected(stack) : null;
        WearFrame frame = new WearFrame(WEAR.get(), player, stack, pair);
        WEAR.set(frame);
        return frame;
    }
    public static void abort(WearFrame frame) {
        if (WEAR.get() != frame) return;
        if (frame.previous == null) WEAR.remove(); else WEAR.set(frame.previous);
    }
    /** Called once around the actual Unbreaking enchantment effect, not a rerolled approximation. */
    public static void observed(ServerLevel level, Enchantment enchantment, ItemStack stack, float before, float after) {
        WearFrame frame = WEAR.get();
        if (frame == null || frame.stack != stack || frame.pair == null || !eligible(frame.player)) return;
        Holder<Enchantment> unbreaking = unbreaking(level);
        if (unbreaking == null || unbreaking.value() != enchantment) return;
        int saved = DurabilityReserveMath.savedByEffect(before, after);
        frame.prevented = (int) Math.min(Integer.MAX_VALUE, (long) frame.prevented + saved);
    }
    /** On an otherwise valid but non-native carrier, use the real datapack effect exactly once. */
    public static int applyNonNative(ServerLevel level, ItemStack stack, int amount, @Nullable ServerPlayer player) {
        WearFrame frame = WEAR.get();
        if (frame == null || frame.stack != stack || frame.pair == null || frame.player != player || amount <= 0) return amount;
        Holder<Enchantment> unbreaking = unbreaking(level);
        if (unbreaking == null || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(unbreaking) > 0
            || DurabilityEconomy.selected(stack, false) != null) return amount; // 05 already applied its non-native effect above.
        MutableFloat modified = new MutableFloat(amount);
        unbreaking.value().modifyDurabilityChange(level, frame.pair.primaryLevel(), stack, modified);
        return modified.intValue();
    }
    /** Return final ordinary wear or consume an explicitly armed reservation for this one operation. */
    public static int finish(WearFrame frame, int requested, int result) {
        if (WEAR.get() != frame) return result;
        abort(frame);
        ServerPlayer player = frame.player;
        if (!eligible(player) || frame.pair == null || requested <= 0 || !DurabilityEconomy.actuallyEquipped(player, frame.stack)
            || !frame.pair.equals(selected(frame.stack))) return result;
        long now = player.level().getGameTime();
        int saved = DurabilityReserveMath.acceptedSaved(frame.prevented, requested, result);
        Reserve reserve = find(player, frame.stack);
        if (reserve != null && (now >= reserve.expires || !stillSame(player, reserve))) {
            List<Reserve> entries = RESERVES.get(player.getUUID());
            if (entries != null) entries.remove(reserve);
            reserve = null;
        }
        if (saved > 0) {
            if (reserve == null) {
                List<Reserve> entries = RESERVES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
                if (entries.size() >= MAX_ITEMS) entries.removeFirst();
                reserve = new Reserve(frame.stack, frame.pair, player.level().dimension()); entries.add(reserve);
            }
            int beforeCharges = reserve.points / cost();
            reserve.points = DurabilityReserveMath.add(reserve.points, saved, cost(), capacity());
            reserve.expires = now + lifetime();
            if (reserve.points / cost() > beforeCharges) {
                player.sendSystemMessage(Component.translatable("message.enchantment_fusion.reserve_ready",
                    frame.stack.getHoverName(), reserve.points / cost(), capacity()), true);
            }
        }
        if (reserve != null && reserve.armed && result > 0 && reserve.points >= cost()) {
            reserve.points -= cost();
            reserve.armed = false;
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.reserve_spent", frame.stack.getHoverName()), true);
            CompendiumDiscoveryTracker.recordDiscovery(player, UNBREAKING, INFINITY);
            return 0;
        }
        return result;
    }
    public static boolean hasEquipped(ServerPlayer player) {
        for (EquipmentSlot slot : ACTIVE_SLOTS) if (selected(player.getItemBySlot(slot)) != null) return true;
        return false;
    }
    /** Repeated activation cycles all ready held/worn items, toggling the selected item's protection. */
    public static boolean activate(ServerPlayer player) {
        if (!eligible(player)) return false;
        long now = player.level().getGameTime();
        List<Reserve> ready = new ArrayList<>();
        for (EquipmentSlot slot : ACTIVE_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (selected(stack) == null) continue;
            Reserve reserve = find(player, stack);
            if (reserve != null && now < reserve.expires && stillSame(player, reserve)
                && reserve.points >= cost() && !ready.contains(reserve)) ready.add(reserve);
        }
        if (ready.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.enchantment_fusion.reserve_not_ready"), true);
            return false;
        }
        ItemStack previous = LAST_SELECTED.get(player.getUUID());
        int index = -1;
        for (int i = 0; i < ready.size(); i++) if (ready.get(i).stack == previous) { index = i; break; }
        Reserve reserve = ready.get((index + 1) % ready.size());
        LAST_SELECTED.put(player.getUUID(), reserve.stack);
        reserve.armed = !reserve.armed;
        player.sendSystemMessage(Component.translatable(reserve.armed
            ? "message.enchantment_fusion.reserve_armed" : "message.enchantment_fusion.reserve_disarmed",
            reserve.stack.getHoverName()), true);
        return true;
    }
    public static void tickPlayer(ServerPlayer player) {
        if (!eligible(player)) { onDisconnect(player.getUUID()); return; }
        List<Reserve> entries = RESERVES.get(player.getUUID());
        if (entries == null) return;
        long now = player.level().getGameTime();
        entries.removeIf(reserve -> now >= reserve.expires || !stillSame(player, reserve));
        ItemStack last = LAST_SELECTED.get(player.getUUID());
        if (last != null && entries.stream().noneMatch(reserve -> reserve.stack == last)) LAST_SELECTED.remove(player.getUUID());
        if (entries.isEmpty()) RESERVES.remove(player.getUUID());
    }
    public static void onDisconnect(UUID id) { RESERVES.remove(id); LAST_SELECTED.remove(id); }
    public static void clear() { RESERVES.clear(); LAST_SELECTED.clear(); WEAR.remove(); }
}
