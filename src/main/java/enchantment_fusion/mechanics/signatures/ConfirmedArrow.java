package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared evidence from the real central bow/crossbow shot. The launcher is the live inventory
 * object, not the arrow's copied weapon. A pending shot is not published until that exact arrow
 * is observed in the server world. No callback creates ammunition or infers a hit from proximity.
 */
public final class ConfirmedArrow {
    public interface Listener {
        boolean applies(FusedPair pair, ItemStack launcher);
        void hit(Hit hit);
        default void missed(ServerPlayer owner, ItemStack launcher, FusedPair pair) {}
    }
    /** Capture is tentative; publication occurs only when this exact arrow is present in the world. */
    public interface InsertionListener {
        @Nullable Object capture(ServerPlayer owner, ItemStack launcher);
        void inserted(ServerPlayer owner, ItemStack launcher, AbstractArrow arrow, Object captured);
    }
    private record Insertion(InsertionListener listener, Object captured) {}
    public record Hit(ServerPlayer owner, ItemStack launcher, FusedPair pair, AbstractArrow arrow,
                      LivingEntity target, Vec3 location, float actualLoss, boolean burning) {}
    private static final int MAX_FLIGHTS = 2048, MAX_OWNERS = 256, MAX_LAUNCHERS = 8, MAX_NODES = 128;
    private static final long LIFETIME = 200L;
    private static final double MAX_STEP_SQUARED = 16.0D * 16.0D;
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<InsertionListener> INSERTION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<AbstractArrow, Flight> FLIGHTS = new WeakHashMap<>();
    private static final Map<UUID, List<Flight>> LATEST = new HashMap<>();
    private static long nextSequence;

    private static final class Flight {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final ItemStack launcher;
        final List<FusedPair> pairs;
        final List<Insertion> insertions;
        final long born, sequence;
        final List<Vec3> nodes = new ArrayList<>();
        boolean invalid, published, resolved;
        Flight(ServerPlayer owner, ItemStack launcher, List<FusedPair> pairs, List<Insertion> insertions) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension();
            this.launcher = launcher; this.pairs = pairs; this.insertions = insertions; this.born = owner.level().getGameTime();
            this.sequence = ++nextSequence;
        }
        void append(Vec3 position) {
            if (invalid || position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)) { invalid = true; return; }
            if (!nodes.isEmpty()) {
                double distance = nodes.getLast().distanceToSqr(position);
                if (distance < 1.0E-7D) return;
                if (distance > MAX_STEP_SQUARED) { invalid = true; return; }
            }
            if (nodes.size() >= MAX_NODES) { invalid = true; return; }
            nodes.add(position);
        }
    }
    public static final class Damage {
        private final Flight flight;
        private final AbstractArrow arrow;
        private final ServerPlayer owner;
        private final LivingEntity target;
        private final Vec3 location;
        private final float health, absorption;
        private final boolean eligible, burning;
        Damage(Flight flight, AbstractArrow arrow, ServerPlayer owner, LivingEntity target, Vec3 location) {
            this.flight = flight; this.arrow = arrow; this.owner = owner; this.target = target; this.location = location;
            this.health = target.getHealth(); this.absorption = target.getAbsorptionAmount();
            this.eligible = LoyaltyGuard.eligibleTarget(owner, target) && owner.level().mayInteract(owner, target.blockPosition());
            this.burning = arrow.isOnFire();
        }
    }
    private ConfirmedArrow() {}
    public static void register(Listener listener) { if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener); }
    public static void registerInsertion(InsertionListener listener) {
        if (listener != null && !INSERTION_LISTENERS.contains(listener)) INSERTION_LISTENERS.add(listener);
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator() && !owner.isCreative(); }
    private static boolean owns(ServerPlayer owner, ItemStack stack) {
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == stack) return true;
        return false;
    }
    private static boolean valid(ServerPlayer owner, Flight flight) {
        long now = owner.level().getGameTime();
        return ready(owner) && owner.getUUID().equals(flight.owner) && owner.level().dimension().equals(flight.dimension)
            && now >= flight.born && now - flight.born < LIFETIME && !flight.launcher.isEmpty() && owns(owner, flight.launcher);
    }
    private static boolean listens(Listener listener, FusedPair pair, ItemStack launcher) {
        try { return listener.applies(pair, launcher); }
        catch (RuntimeException error) {
            EnchantmentFusion.LOGGER.error("Arrow evidence selector failed", error); return false;
        }
    }
    private static boolean contains(Flight flight, FusedPair pair) {
        return flight.pairs.contains(pair) && flight.launcher.getOrDefault(FusionDataComponents.FUSED_PAIRS,
            FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    /** Called inside the scoped, actual launcher method, before the entity is added to the world. */
    public static void launched(Projectile projectile, LivingEntity shooter, int index, @Nullable ItemStack launcher) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner) || index != 0
            || launcher == null || !ready(owner) || arrow.getOwner() != owner || PhantomVolley.isGhost(arrow)
            || (owner.getMainHandItem() != launcher && owner.getOffhandItem() != launcher) || FLIGHTS.size() >= MAX_FLIGHTS) return;
        List<FusedPair> selected = new ArrayList<>();
        for (FusedPair pair : launcher.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (LISTENERS.stream().anyMatch(listener -> listens(listener, pair, launcher))) selected.add(pair);
            if (selected.size() >= 64) break;
        }
        List<Insertion> insertions = new ArrayList<>();
        for (InsertionListener listener : INSERTION_LISTENERS) {
            try {
                Object captured = listener.capture(owner, launcher);
                if (captured != null) insertions.add(new Insertion(listener, captured));
            } catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Arrow insertion selector failed", error); }
        }
        if (selected.isEmpty() && insertions.isEmpty()) return;
        Flight flight = new Flight(owner, launcher, List.copyOf(selected), List.copyOf(insertions));
        flight.append(arrow.position());
        FLIGHTS.put(arrow, flight);
    }
    /** Records only the exact in-world projectile, with bounded path size and discontinuity checks. */
    public static void sample(AbstractArrow arrow) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !(arrow.level() instanceof ServerLevel level) || !(arrow.getOwner() instanceof ServerPlayer owner)
            || level.getEntity(arrow.getUUID()) != arrow || !valid(owner, flight) || owner.level() != level
            || PhantomVolley.isGhost(arrow)) { if (flight != null) FLIGHTS.remove(arrow); return; }
        flight.append(arrow.position());
        if (!flight.published) {
            flight.published = true; // Commit before callbacks; an insertion may be observed more than once.
            if (!flight.invalid) for (Insertion insertion : flight.insertions) {
                try { insertion.listener().inserted(owner, flight.launcher, arrow, insertion.captured()); }
                catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Arrow insertion callback failed", error); }
            }
            if (LATEST.size() >= MAX_OWNERS && !LATEST.containsKey(owner.getUUID())) return;
            List<Flight> latest = LATEST.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>());
            Flight newer = latest.stream().filter(entry -> entry.launcher == flight.launcher && entry.sequence > flight.sequence).findFirst().orElse(null);
            if (newer == null) {
                latest.removeIf(entry -> entry.launcher == flight.launcher);
                if (latest.size() >= MAX_LAUNCHERS) latest.removeFirst();
                latest.add(flight);
            }
        }
    }
    public static @Nullable Damage beforeDamage(AbstractArrow arrow, Entity victim, DamageSource source, Vec3 location) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || flight.resolved || !flight.published || !(victim instanceof LivingEntity target)
            || !(arrow.getOwner() instanceof ServerPlayer owner) || !valid(owner, flight)
            || owner.level() != arrow.level() || target.level() != owner.level() || owner.level().getEntity(arrow.getUUID()) != arrow
            || source.getDirectEntity() != arrow || source.getEntity() != owner || PhantomVolley.isGhost(arrow)
            || MatrixEffectExecutor.isExecuting() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return null;
        return new Damage(flight, arrow, owner, target, location);
    }
    public static void afterDamage(@Nullable Damage damage, boolean accepted) {
        if (damage == null || !accepted || !damage.eligible || damage.flight.resolved || FLIGHTS.get(damage.arrow) != damage.flight
            || damage.arrow.getOwner() != damage.owner || damage.owner.level() != damage.target.level()
            || !valid(damage.owner, damage.flight)) return;
        float loss = ConfirmedMeleeMath.actualLoss(damage.health, damage.absorption,
            damage.target.getHealth(), damage.target.getAbsorptionAmount());
        if (loss <= 0.0F) return;
        damage.flight.resolved = true;
        for (FusedPair pair : damage.flight.pairs) {
            if (!contains(damage.flight, pair)) continue;
            Hit hit = new Hit(damage.owner, damage.flight.launcher, pair, damage.arrow, damage.target,
                damage.location, loss, damage.burning);
            for (Listener listener : LISTENERS) if (listens(listener, pair, damage.flight.launcher)) {
                try { listener.hit(hit); }
                catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Arrow evidence callback failed", error); }
            }
        }
    }
    /** Block/entity impact completes a miss only if the shot did not already produce a confirmed hit. */
    public static void impact(AbstractArrow arrow, HitResult impact) {
        Flight flight = FLIGHTS.get(arrow);
        if (flight == null || !flight.published || !(arrow.getOwner() instanceof ServerPlayer owner) || !valid(owner, flight)
            || owner.level() != arrow.level() || impact.getType() == HitResult.Type.MISS) return;
        flight.append(impact.getLocation());
        if (!flight.resolved) {
            flight.resolved = true;
            for (FusedPair pair : flight.pairs) if (contains(flight, pair)) {
                for (Listener listener : LISTENERS) if (listens(listener, pair, flight.launcher)) {
                    try { listener.missed(owner, flight.launcher, pair); }
                    catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Arrow miss callback failed", error); }
                }
            }
        }
    }
    /** Latest launched, actually inserted arrow only; never falls back to an older equal item. */
    public static List<Vec3> latestPath(ServerPlayer owner, ItemStack launcher, FusedPair pair) {
        List<Flight> flights = LATEST.get(owner.getUUID());
        if (flights == null) return List.of();
        for (Flight flight : flights) if (flight.launcher == launcher && valid(owner, flight) && contains(flight, pair)) {
            return flight.invalid || flight.nodes.size() < 2 ? List.of() : List.copyOf(flight.nodes);
        }
        return List.of();
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(LATEST.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) { LATEST.remove(entry.getKey()); continue; }
            entry.getValue().removeIf(flight -> !valid(owner, flight));
            if (entry.getValue().isEmpty()) LATEST.remove(entry.getKey());
        }
        FLIGHTS.entrySet().removeIf(entry -> {
            Flight flight = entry.getValue(); ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
            return owner == null || !valid(owner, flight) || entry.getKey().level() != owner.level();
        });
    }
    public static void clearPlayer(UUID id) {
        LATEST.remove(id); FLIGHTS.entrySet().removeIf(entry -> entry.getValue().owner.equals(id));
    }
    public static void clearServer() { FLIGHTS.clear(); LATEST.clear(); nextSequence = 0L; }
}
