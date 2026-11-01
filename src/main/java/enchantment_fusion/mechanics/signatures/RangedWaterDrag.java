package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedPair;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A bounded, non-copying water-drag lease on the exact real arrow inserted into the world. */
public final class RangedWaterDrag {
    @FunctionalInterface public interface Source {
        boolean valid(ServerPlayer owner, EquipmentSlot slot, ItemStack item, FusedPair pair);
    }
    private record Flight(UUID owner, ResourceKey<Level> dimension, EquipmentSlot slot, ItemStack item,
                          FusedPair pair, long born, Source source) {}
    private static final int MAX_FLIGHTS = 1024;
    private static final long LIFETIME = 200;
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private RangedWaterDrag() {}

    /** Called only from the confirmed central-arrow insertion callback, never from projectile creation. */
    public static boolean arm(ServerPlayer owner, AbstractArrow arrow, EquipmentSlot slot, ItemStack item,
                              FusedPair pair, Source source) {
        if (owner == null || arrow == null || slot == null || item == null || pair == null || source == null
            || FLIGHTS.size() >= MAX_FLIGHTS || FLIGHTS.containsKey(arrow) || !owner.isAlive() || owner.isSpectator()
            || owner.isCreative() || arrow.level() != owner.level() || arrow.getOwner() != owner || !arrow.isAlive()
            || arrow.noPhysics || PhantomVolley.isGhost(arrow) || owner.level().getEntity(arrow.getUUID()) != arrow
            || !source.valid(owner, slot, item, pair)) return false;
        FLIGHTS.put(arrow, new Flight(owner.getUUID(), owner.level().dimension(), slot, item, pair,
            owner.level().getGameTime(), source));
        return true;
    }
    private static boolean valid(AbstractArrow arrow, Flight flight) {
        if (!(arrow.level() instanceof ServerLevel level) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || !owner.getUUID().equals(flight.owner()) || owner.level() != level || !level.dimension().equals(flight.dimension())
            || !owner.isAlive() || owner.isSpectator() || owner.isCreative() || !arrow.isAlive() || arrow.noPhysics
            || PhantomVolley.isGhost(arrow) || level.getEntity(arrow.getUUID()) != arrow) return false;
        long now = level.getGameTime();
        return now >= flight.born() && now - flight.born() < LIFETIME
            && flight.source().valid(owner, flight.slot(), flight.item(), flight.pair());
    }
    /** Invoked at vanilla's actual water-inertia calculation, without changing blocks or air physics. */
    public static float inertia(AbstractArrow arrow, float vanilla) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null) return vanilla;
        if (!valid(arrow, flight)) { FLIGHTS.remove(arrow); return vanilla; }
        BlockPos pos = BlockPos.containing(arrow.position());
        if (!arrow.isInWater() || !arrow.level().hasChunkAt(pos) || !arrow.level().getFluidState(pos).is(FluidTags.WATER)) return vanilla;
        float result = RangedWaterMath.inertia(vanilla, flight.pair().primaryLevel(), flight.pair().secondaryLevel());
        if (result > vanilla) arrow.hurtMarked = true; // The server sends the changed velocity to tracking clients.
        return result;
    }
    public static void tickServer(MinecraftServer server) {
        FLIGHTS.entrySet().removeIf(entry -> !valid(entry.getKey(), entry.getValue()));
    }
    public static void clearPlayer(UUID id) { FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner().equals(id)); }
    public static void clearServer() { FLIGHTS.clear(); }
}
