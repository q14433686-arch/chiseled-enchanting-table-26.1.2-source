package enchantment_fusion.mechanics.signatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Reuses collision-clipped input movement for both swimming charge and current contact. */
final class RangedTideMovement {
    private static final int MAX_OWNERS = 256, MAX_STEPS = 4;
    private static final long FLOW_TICKS = 200;
    private record Capture(ServerLevel level, long tick, Vec3 intent, @Nullable RangedTideSkills.Gear gear,
                           @Nullable RangedTideTrail.Current current) {}
    private static final class Pending {
        final ServerLevel level; final long tick; final Vec3 start;
        final @Nullable RangedTideSkills.Gear gear; final @Nullable RangedTideTrail.Current current;
        final List<ConfirmedPlayerMove.Step> steps = new ArrayList<>();
        Pending(Capture capture, Vec3 start) {
            level = capture.level(); tick = capture.tick(); gear = capture.gear(); current = capture.current(); this.start = start;
        }
    }
    private static final class Flow {
        final RangedTideSkills.Gear gear; final ResourceKey<Level> dimension;
        double distance; long born, until;
        Flow(RangedTideSkills.Gear gear, ResourceKey<Level> dimension) { this.gear = gear; this.dimension = dimension; }
    }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Flow> FLOWS = new HashMap<>();
    private static boolean initialized;
    private RangedTideMovement() {}
    static void init() {
        if (initialized) return; initialized = true;
        ConfirmedPlayerMove.register(new ConfirmedPlayerMove.Observer() {
            @Override public boolean interested(ServerPlayer owner) {
                return PENDING.containsKey(owner.getUUID()) || RangedTideSkills.worn(owner) != null || RangedTideTrail.current(owner) != null;
            }
            @Override public @Nullable Object before(ServerPlayer owner, Vec3 requested) {
                if (!ready(owner) || !owner.isInWater()) return null;
                RangedTideSkills.Gear gear = RangedTideSkills.worn(owner); RangedTideTrail.Current current = RangedTideTrail.current(owner);
                return gear == null && current == null ? null : new Capture(owner.level(), owner.level().getGameTime(),
                    owner.getLastClientMoveIntent(), gear, current);
            }
            @Override public void moved(ServerPlayer owner, ConfirmedPlayerMove.Step step, @Nullable Object raw) {
                UUID id = owner.getUUID(); Pending pending = PENDING.get(id);
                if (!(raw instanceof Capture capture) || capture.level() != owner.level() || !ready(owner) || !owner.isInWater()
                    || !GrapnelMotion.matchesInput(step.to().subtract(step.from()), capture.intent())
                    || (capture.gear() != null && !RangedTideSkills.wearing(owner, capture.gear()))
                    || (capture.current() != null && RangedTideTrail.current(owner) != capture.current())) { PENDING.remove(id); return; }
                if (pending == null) {
                    if (PENDING.size() >= MAX_OWNERS) return;
                    pending = new Pending(capture, step.from()); PENDING.put(id, pending);
                }
                if (pending.level != capture.level() || capture.tick() < pending.tick || capture.tick() - pending.tick > 1
                    || !RangedTideSkills.same(pending.gear, capture.gear()) || pending.current != capture.current()
                    || pending.steps.size() >= MAX_STEPS) { PENDING.remove(id); return; }
                pending.steps.add(step);
            }
            @Override public void invalid(ServerPlayer owner) { PENDING.remove(owner.getUUID()); FLOWS.remove(owner.getUUID()); }
        });
    }
    static boolean ready(ServerPlayer owner) {
        return owner.isAlive() && !owner.isSpectator() && !owner.isCreative() && !owner.isPassenger()
            && !owner.getAbilities().flying && !owner.isFallFlying();
    }
    private static boolean valid(ServerPlayer owner, @Nullable Flow flow) {
        if (flow == null || !ready(owner) || !owner.level().dimension().equals(flow.dimension)
            || !RangedTideSkills.wearing(owner, flow.gear)) return false;
        long now = owner.level().getGameTime(); return now >= flow.born && now < flow.until;
    }
    static boolean take(ServerPlayer owner, RangedTideSkills.Gear gear) {
        Flow flow = FLOWS.get(owner.getUUID());
        if (!valid(owner, flow) || !RangedTideSkills.same(flow.gear, gear) || flow.distance < RangedTideMath.CHARGE_DISTANCE) return false;
        FLOWS.remove(owner.getUUID(), flow); return true;
    }
    static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(PENDING.entrySet())) {
            Pending pending = entry.getValue(); PENDING.remove(entry.getKey(), pending);
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !ready(owner) || !owner.isInWater() || owner.level() != pending.level || pending.steps.isEmpty()
                || owner.level().getGameTime() < pending.tick || owner.level().getGameTime() - pending.tick > 1
                || !ConfirmedPlayerMove.connected(pending.start, owner.position(), pending.steps)) {
                FLOWS.remove(entry.getKey()); continue;
            }
            if (pending.gear != null && RangedTideSkills.wearing(owner, pending.gear)) {
                Flow flow = FLOWS.get(entry.getKey());
                if (!valid(owner, flow) || !RangedTideSkills.same(flow.gear, pending.gear)) {
                    if (flow != null) FLOWS.remove(entry.getKey(), flow);
                    if (FLOWS.size() < MAX_OWNERS) {
                        flow = new Flow(pending.gear, owner.level().dimension()); FLOWS.put(entry.getKey(), flow);
                    } else flow = null;
                }
                if (flow != null) {
                    boolean wasReady = flow.distance >= RangedTideMath.CHARGE_DISTANCE;
                    for (ConfirmedPlayerMove.Step step : pending.steps)
                        flow.distance = RangedTideMath.addDistance(flow.distance, step.to().subtract(step.from()).horizontalDistance());
                    flow.born = owner.level().getGameTime(); flow.until = flow.born + FLOW_TICKS;
                    if (!wasReady && flow.distance >= RangedTideMath.CHARGE_DISTANCE)
                        owner.sendSystemMessage(Component.translatable("message.enchantment_fusion.ranged_tide_ready"), true);
                }
            }
            RangedTideTrail.ride(owner, pending.current, pending.steps);
        }
        FLOWS.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); return owner == null || !valid(owner, entry.getValue());
        });
    }
    static void clearPlayer(UUID id) { PENDING.remove(id); FLOWS.remove(id); }
    static void clearServer() { PENDING.clear(); FLOWS.clear(); }
}
