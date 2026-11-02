package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Riptide -> Channeling: two position anchors from one real spin, discharged by a later real release. */
public final class ThunderRailSkills {
    public static final String RIPTIDE = "minecraft:riptide", CHANNELING = "minecraft:channeling", SIGNATURE = "thunder_avatar";
    private static final int LIMIT = 256, MAX_STEPS = 64, MAX_VICTIMS = 8;
    private static final long TRACK_TICKS = 25L, ANCHOR_TICKS = 160L;
    private static final double MAX_PATH = 32.0D, MAX_RAIL = 24.0D, MIN_RAIL = 1.0D;
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final Map<UUID, Rail> RAILS = new HashMap<>();
    private static boolean initialized;

    private static final class Flight {
        final ItemStack stack; final FusedPair pair; final EquipmentSlot slot; final ResourceKey<Level> dimension;
        final long expires; final Vec3 start, impulse; Vec3 last; double traveled; int steps;
        Flight(ServerPlayer owner, ItemStack stack, FusedPair pair, EquipmentSlot slot, Vec3 impulse) {
            this.stack = stack; this.pair = pair; this.slot = slot; dimension = owner.level().dimension();
            expires = owner.level().getGameTime() + TRACK_TICKS; start = last = owner.position(); this.impulse = impulse;
        }
    }
    private record Rail(ItemStack stack, FusedPair pair, EquipmentSlot slot, ResourceKey<Level> dimension,
                        Vec3 start, Vec3 end, long expires) {}
    private ThunderRailSkills() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        ConfirmedPlayerMove.register(new ConfirmedPlayerMove.Observer() {
            @Override public boolean interested(ServerPlayer owner) { return FLIGHTS.containsKey(owner.getUUID()); }
            @Override public double maxStep() { return 12.0D; }
            @Override public @Nullable Object before(ServerPlayer owner, Vec3 requested) {
                Flight flight = FLIGHTS.get(owner.getUUID());
                if (flight == null) return null;
                if (!valid(owner, flight)) { FLIGHTS.remove(owner.getUUID(), flight); return null; }
                if (!owner.isAutoSpinAttack()) {
                    settle(owner, flight); return null;
                }
                return owner.getWeaponItem() == flight.stack ? flight : null;
            }
            @Override public void moved(ServerPlayer owner, ConfirmedPlayerMove.Step step, @Nullable Object evidence) {
                Flight flight = FLIGHTS.get(owner.getUUID());
                if (flight == null) return;
                if (evidence != flight || !valid(owner, flight) || !near(flight.last, step.from())
                    || flight.steps >= MAX_STEPS || (flight.steps == 0 && !GrapnelRiptide.matchesDirection(step.to().subtract(step.from()), flight.impulse))) {
                    FLIGHTS.remove(owner.getUUID(), flight); return;
                }
                double traveled = flight.traveled + step.from().distanceTo(step.to());
                if (!Double.isFinite(traveled) || traveled > MAX_PATH || flight.start.distanceToSqr(step.to()) > MAX_RAIL * MAX_RAIL) {
                    FLIGHTS.remove(owner.getUUID(), flight); return;
                }
                flight.last = step.to(); flight.traveled = traveled; flight.steps++;
            }
            @Override public void invalid(ServerPlayer owner) { FLIGHTS.remove(owner.getUUID()); }
        });
    }
    public static boolean selectedSignature(@Nullable SignatureEntry entry) {
        return entry != null && SIGNATURE.equals(entry.id()) && RIPTIDE.equals(entry.primaryId()) && CHANNELING.equals(entry.secondaryId());
    }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && RIPTIDE.equals(pair.primaryId()) && CHANNELING.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return isPair(pair) && SignatureCatalog.resolve(pair).map(ThunderRailSkills::selectedSignature).orElse(false);
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return roles != null && roles.contains(FusionCarrier.TRIDENT); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.isBroken()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    private static boolean ready(ServerPlayer owner) {
        return owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && !owner.isPassenger()
            && !owner.getAbilities().flying && !owner.isFallFlying();
    }
    private static boolean equipped(ServerPlayer owner, ItemStack stack, FusedPair pair, EquipmentSlot slot, ResourceKey<Level> dimension) {
        return ready(owner) && owner.level().dimension().equals(dimension) && owner.getItemBySlot(slot) == stack
            && pair.equals(selected(stack)) && stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(pair);
    }
    private static boolean valid(ServerPlayer owner, Flight flight) {
        return equipped(owner, flight.stack, flight.pair, flight.slot, flight.dimension) && owner.level().getGameTime() < flight.expires;
    }
    private static boolean live(ServerPlayer owner, Rail rail) {
        return equipped(owner, rail.stack(), rail.pair(), rail.slot(), rail.dimension()) && owner.level().getGameTime() < rail.expires();
    }
    private static boolean valid(ServerPlayer owner, Rail rail) {
        return live(owner, rail) && owner.position().distanceToSqr(rail.end()) <= MAX_RAIL * MAX_RAIL
            && GrapnelPositionTargets.allowedOrigin(owner, rail.start(), false)
            && GrapnelPositionTargets.allowedOrigin(owner, rail.end(), false)
            && VirtualCombatSight.clear(owner.level(), owner, rail.start().add(0, 0.6D, 0), rail.end().add(0, 0.6D, 0));
    }
    private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) <= 1.0E-6D; }

    /** Called only by the shared attributed Riptide release after its real data-pack value and impulse succeeded. */
    static void released(ServerPlayer owner, ItemStack stack, FusedPair pair, EquipmentSlot slot, Vec3 impulse) {
        if (!equipped(owner, stack, pair, slot, owner.level().dimension())) return;
        // A second release before the first spin finishes replaces its unproven trace;
        // it cannot fabricate an endpoint just because use was pressed again.
        FLIGHTS.remove(owner.getUUID());
        Rail rail = RAILS.get(owner.getUUID());
        if (rail != null) {
            if (rail.stack() == stack && rail.pair().equals(pair) && rail.slot() == slot && valid(owner, rail)
                && RAILS.remove(owner.getUUID(), rail)) discharge(owner, rail);
            else RAILS.remove(owner.getUUID(), rail);
        }
        if (FLIGHTS.size() < LIMIT && GrapnelPositionTargets.allowedOrigin(owner, owner.position(), true))
            FLIGHTS.put(owner.getUUID(), new Flight(owner, stack, pair, slot, impulse));
    }
    private static void settle(ServerPlayer owner, Flight flight) {
        if (!FLIGHTS.remove(owner.getUUID(), flight) || !valid(owner, flight) || flight.steps == 0 || !near(owner.position(), flight.last)) return;
        if (!validRailMetrics(flight.start, flight.last, flight.traveled, flight.steps)
            || (!RAILS.containsKey(owner.getUUID()) && RAILS.size() >= LIMIT)) return;
        Rail rail = new Rail(flight.stack, flight.pair, flight.slot, flight.dimension, flight.start, flight.last,
            owner.level().getGameTime() + ANCHOR_TICKS);
        if (!valid(owner, rail)) return;
        RAILS.put(owner.getUUID(), rail);
        for (Vec3 anchor : List.of(rail.start(), rail.end()))
            owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y + 0.6D, anchor.z, 8, 0.15D, 0.25D, 0.15D, 0.01D);
        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.thunder_rail_marked"), true);
    }
    public static boolean validRailMetrics(@Nullable Vec3 start, @Nullable Vec3 end, double traveled, int steps) {
        if (start == null || end == null || steps < 1 || steps > MAX_STEPS || !Double.isFinite(traveled)
            || traveled <= 0 || traveled > MAX_PATH) return false;
        double distance = start.distanceToSqr(end);
        return Double.isFinite(distance) && distance >= MIN_RAIL * MIN_RAIL && distance <= MAX_RAIL * MAX_RAIL
            && traveled + 1.0E-6D >= Math.sqrt(distance);
    }
    private static boolean target(ServerPlayer owner, LivingEntity target) {
        return target.level() == owner.level() && LoyaltyGuard.eligibleTarget(owner, target)
            && (!(target instanceof Player other) || other.canHarmPlayer(owner))
            && GrapnelPositionTargets.allowedOrigin(owner, target.position(), false);
    }
    private static boolean touches(Vec3 from, Vec3 to, AABB box) {
        AABB widened = box.inflate(0.35D);
        return widened.contains(from) || widened.contains(to) || widened.clip(from, to).isPresent();
    }
    private static void discharge(ServerPlayer owner, Rail rail) {
        Vec3 from = rail.start().add(0, 0.6D, 0), to = rail.end().add(0, 0.6D, 0);
        VirtualArc.show(owner, from, to);
        List<LivingEntity> targets = owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0D),
            entity -> entity != owner && target(owner, entity) && touches(from, to, entity.getBoundingBox()));
        targets.sort(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(from)));
        float damage = Math.min(8.0F, 2.0F + 0.5F * Math.clamp(rail.pair().primaryLevel(), 1, 6)
            + 0.5F * Math.clamp(rail.pair().secondaryLevel(), 1, 6));
        boolean previous = Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
        TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(true);
        try {
            int attempts = 0;
            for (LivingEntity victim : targets) {
                if (attempts++ >= MAX_VICTIMS) break;
                if (!valid(owner, rail)) break;
                Vec3 center = victim.getBoundingBox().getCenter();
                if (!target(owner, victim) || !touches(from, to, victim.getBoundingBox())
                    || !VirtualCombatSight.clear(owner.level(), owner, from, center)) continue;
                victim.hurtServer(owner.level(), owner.level().damageSources().playerAttack(owner), damage);
            }
        } finally { TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.set(previous); }
        CompendiumDiscoveryTracker.recordDiscovery(owner, rail.pair().primaryId(), rail.pair().secondaryId());
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(FLIGHTS.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Flight flight = entry.getValue();
            if (owner == null || !valid(owner, flight)) FLIGHTS.remove(entry.getKey(), flight);
            else if (!near(owner.position(), flight.last)) FLIGHTS.remove(entry.getKey(), flight);
            else if (!owner.isAutoSpinAttack()) settle(owner, flight);
        }
        RAILS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Rail rail = entry.getValue();
            if (owner == null || !live(owner, rail)) return true;
            if (owner.level().getGameTime() % 10 == 0) for (Vec3 anchor : List.of(rail.start(), rail.end())) {
                if (GrapnelPositionTargets.allowedOrigin(owner, anchor, false))
                    owner.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y + 0.6D, anchor.z,
                        2, 0.08D, 0.1D, 0.08D, 0.005D);
            }
            return false;
        });
    }
    public static void clearPlayer(UUID owner) { FLIGHTS.remove(owner); RAILS.remove(owner); }
    public static void clearServer() { FLIGHTS.clear(); RAILS.clear(); }
}
