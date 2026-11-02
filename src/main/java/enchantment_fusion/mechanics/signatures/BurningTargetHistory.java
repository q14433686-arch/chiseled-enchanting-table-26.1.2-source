package enchantment_fusion.mechanics.signatures;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Bounded observation of consecutive real living-entity fire ticks; never ignites or edits an entity. */
public final class BurningTargetHistory {
    private static final int MAX_OBSERVED = 4096;
    private record Entry(LivingEntity entity, ResourceKey<Level> dimension, long first, long last) {}
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private BurningTargetHistory() {}
    /** Same-tick callbacks must not advance the duration; gaps start a new observation. */
    public static long firstAfter(long first, long last, long now) {
        return now == last || last >= 0 && last < Long.MAX_VALUE && now == last + 1 ? first : now;
    }
    public static boolean longEnough(long first, long last, long now, int ticks) {
        return ticks > 0 && first >= 0 && first <= last && now >= last && now - last <= 1 && last - first >= ticks - 1L;
    }
    public static void observe(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        UUID id = target.getUUID();
        if (!target.isAlive() || !target.isOnFire() || target.getRemainingFireTicks() <= 0) {
            ENTRIES.remove(id); return;
        }
        long now = level.getGameTime(); Entry previous = ENTRIES.get(id);
        if (previous == null && ENTRIES.size() >= MAX_OBSERVED) return;
        long first = previous != null && previous.entity() == target && previous.dimension().equals(level.dimension())
            ? firstAfter(previous.first(), previous.last(), now) : now;
        ENTRIES.put(id, new Entry(target, level.dimension(), first, now));
    }
    public static boolean observedFor(LivingEntity target, int ticks) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || !target.isOnFire()
            || target.getRemainingFireTicks() <= 0) return false;
        Entry entry = ENTRIES.get(target.getUUID());
        return entry != null && entry.entity() == target && entry.dimension().equals(level.dimension())
            && longEnough(entry.first(), entry.last(), level.getGameTime(), ticks);
    }
    public static void forget(LivingEntity target) { ENTRIES.remove(target.getUUID()); }
    public static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        ENTRIES.entrySet().removeIf(entry -> {
            Entry record = entry.getValue(); ServerLevel level = server.getLevel(record.dimension());
            return level == null || record.entity().level() != level || level.getEntity(entry.getKey()) != record.entity()
                || level.getGameTime() < record.last() || level.getGameTime() - record.last() > 2;
        });
    }
    public static void clear() { ENTRIES.clear(); }
}
