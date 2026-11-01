package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Bounded, session-only target marks shared by direction adapters, never serialized on items. */
final class OwnedTargetMarks {
    record Key(UUID owner, String channel) {}
    record Mark(ItemStack stack, FusedPair pair, EquipmentSlot slot, UUID target, ResourceKey<Level> dimension, long expires) {}
    private final Map<Key, Mark> entries = new HashMap<>();
    private final int limit;
    private final BiPredicate<FusedPair, ItemStack> applies;
    OwnedTargetMarks(int limit, BiPredicate<FusedPair, ItemStack> applies) {
        if (limit < 1 || applies == null) throw new IllegalArgumentException("Invalid target-mark store");
        this.limit = limit; this.applies = applies;
    }
    boolean put(ServerPlayer owner, String channel, EquipmentSlot slot, ItemStack stack, FusedPair pair, LivingEntity target, int lifetime) {
        if (owner == null || channel == null || channel.isBlank() || slot == null || target == null || !target.isAlive()
            || owner.level() != target.level() || lifetime < 1 || lifetime > 1200) return false;
        Key key = new Key(owner.getUUID(), channel);
        if (!entries.containsKey(key) && entries.size() >= limit) return false;
        Mark mark = new Mark(stack, pair, slot, target.getUUID(), owner.level().dimension(), owner.level().getGameTime() + lifetime);
        if (!valid(owner, mark)) return false;
        entries.put(key, mark); return true;
    }
    boolean valid(ServerPlayer owner, @Nullable Mark mark) {
        return mark != null && owner != null && owner.isAlive() && !owner.isSpectator()
            && owner.level().dimension().equals(mark.dimension()) && owner.level().getGameTime() < mark.expires()
            && owner.getItemBySlot(mark.slot()) == mark.stack() && applies.test(mark.pair(), mark.stack())
            && mark.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(mark.pair());
    }
    @Nullable Mark get(ServerPlayer owner, String channel) {
        Key key = new Key(owner.getUUID(), channel); Mark mark = entries.get(key);
        if (!valid(owner, mark)) { if (mark != null) entries.remove(key, mark); return null; }
        return mark;
    }
    boolean remove(ServerPlayer owner, String channel, Mark mark) { return entries.remove(new Key(owner.getUUID(), channel), mark); }
    void clearPlayer(UUID owner) { entries.keySet().removeIf(key -> key.owner().equals(owner)); }
    void tickServer(MinecraftServer server) {
        entries.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().owner()); Mark mark = entry.getValue();
            if (!valid(owner, mark)) return true;
            Entity target = owner.level().getEntity(mark.target());
            return !(target instanceof LivingEntity living) || !living.isAlive();
        });
    }
    void clear() { entries.clear(); }
}
