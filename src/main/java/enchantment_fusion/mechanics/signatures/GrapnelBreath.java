package enchantment_fusion.mechanics.signatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A real underwater air decrement, observed with its original random draw and attribute. */
public final class GrapnelBreath {
    private static final long MARK_INTERVAL = 20;
    private static final int MAX_OWNERS = 256;
    private record Throttle(ResourceKey<Level> dimension, long until) {}
    private static final Map<UUID, Throttle> THROTTLES = new HashMap<>();
    private static final ThreadLocal<Tick> CURRENT = new ThreadLocal<>();
    public static final class Tick {
        final @Nullable Tick previous; final ServerPlayer owner; final ServerLevel level; final long time;
        final GrapnelAttribute.Gear gear; final PrimaryAttributeProjection.Evidence evidence; final Vec3 position;
        final int air; int calculations, rolls, input, result; double roll = Double.NaN;
        boolean calculating, completed, ambiguous;
        Tick(@Nullable Tick previous, ServerPlayer owner, GrapnelAttribute.Gear gear, PrimaryAttributeProjection.Evidence evidence) {
            this.previous = previous; this.owner = owner; this.level = owner.level(); this.time = level.getGameTime();
            this.gear = gear; this.evidence = evidence; this.position = owner.position(); this.air = owner.getAirSupply();
        }
    }
    private GrapnelBreath() {}
    private static boolean ready(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && player.isEyeInFluid(FluidTags.WATER);
    }
    public static @Nullable Tick beginTick(LivingEntity entity) {
        Tick previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        if (!(entity instanceof ServerPlayer owner) || !ready(owner)) return null;
        for (GrapnelAttribute.Gear gear : GrapnelAttribute.selectedAll(owner, EquipmentSlot.HEAD)) {
            if (!GrapnelAttribute.RESPIRATION.equals(gear.pair().primaryId())) continue;
            var evidence = GrapnelAttribute.evidence(owner, gear);
            if (evidence == null) return null;
            Tick tick = new Tick(previous, owner, gear, evidence); CURRENT.set(tick); return tick;
        }
        return null;
    }
    public static @Nullable Tick beginCalculation(LivingEntity entity, int input) {
        Tick tick = CURRENT.get();
        if (tick == null || tick.owner != entity) return null;
        if (++tick.calculations != 1 || tick.calculating) tick.ambiguous = true;
        tick.calculating = true; tick.input = input; return tick;
    }
    public static void recordRoll(LivingEntity entity, double roll) {
        Tick tick = CURRENT.get();
        if (tick == null || tick.owner != entity || !tick.calculating) return;
        if (++tick.rolls != 1) tick.ambiguous = true;
        tick.roll = roll;
    }
    public static void endCalculation(@Nullable Tick tick, int result, boolean completed) {
        if (tick == null) return;
        if (CURRENT.get() != tick || !tick.calculating || !completed) tick.ambiguous = true;
        tick.calculating = false; tick.result = result; tick.completed = completed;
    }
    /** The exact same vanilla random draw would consume air without this one modifier. */
    public static boolean savedByPrimary(double roll, double with, double without) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1 || !Double.isFinite(with) || !Double.isFinite(without)
            || with <= 0 || without < 0 || with <= without) return false;
        return roll >= 1.0D / (with + 1.0D) && (without == 0 || roll < 1.0D / (without + 1.0D));
    }
    public static void endTick(@Nullable Tick tick, boolean completed) {
        if (tick == null) return;
        if (CURRENT.get() != tick) { CURRENT.remove(); return; }
        if (tick.previous == null) CURRENT.remove(); else CURRENT.set(tick.previous);
        ServerPlayer owner = tick.owner;
        if (!completed || tick.ambiguous || tick.calculating || !tick.completed || tick.calculations != 1 || tick.rolls != 1
            || tick.input != tick.air || tick.result != tick.air || owner.getAirSupply() != tick.air
            || !ready(owner) || owner.level() != tick.level || tick.level.getGameTime() != tick.time
            || owner.position().distanceToSqr(tick.position) > 0.0001D
            || !tick.evidence.equals(GrapnelAttribute.evidence(owner, tick.gear))
            || !savedByPrimary(tick.roll, tick.evidence.value(), tick.evidence.withoutPrimary())) return;
        Throttle throttle = THROTTLES.get(owner.getUUID());
        if (throttle != null && throttle.dimension().equals(tick.level.dimension()) && tick.time < throttle.until()) return;
        if (!THROTTLES.containsKey(owner.getUUID()) && THROTTLES.size() >= MAX_OWNERS) return;
        if (GrapnelSkills.tryMarkPosition(owner, tick.gear.slot(), tick.gear.stack(), tick.gear.pair(), owner.position()))
            THROTTLES.put(owner.getUUID(), new Throttle(tick.level.dimension(), tick.time + MARK_INTERVAL));
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(THROTTLES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey()); Throttle throttle = entry.getValue();
            if (player == null || !ready(player) || !player.level().dimension().equals(throttle.dimension())
                || player.level().getGameTime() >= throttle.until()) THROTTLES.remove(entry.getKey(), throttle);
        }
    }
    public static void clearPlayer(UUID id) { THROTTLES.remove(id); }
    public static void clearServer() { THROTTLES.clear(); CURRENT.remove(); }
}
