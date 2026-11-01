package enchantment_fusion.mechanics.signatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Position marks require a continuously observed player-input step with the exact active primary attribute. */
public final class GrapnelMotion {
    private static final int MAX_OWNERS = 256, MAX_STEPS = 4;
    private static final long MARK_INTERVAL = 10;
    private record Candidate(GrapnelAttribute.Gear gear, PrimaryAttributeProjection.Evidence attribute) {}
    private record Capture(ServerLevel level, long tick, Vec3 intent, List<Candidate> candidates) {}
    private static final class Pending {
        final ServerLevel level; final long tick; final Candidate candidate; final Vec3 start;
        final List<ConfirmedPlayerMove.Step> steps = new ArrayList<>();
        Pending(ServerLevel level, long tick, Candidate candidate, Vec3 start) {
            this.level = level; this.tick = tick; this.candidate = candidate; this.start = start;
        }
    }
    private record Throttle(ResourceKey<Level> dimension, long until) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Throttle> THROTTLES = new HashMap<>();
    private static boolean initialized;
    private GrapnelMotion() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ConfirmedPlayerMove.register(new ConfirmedPlayerMove.Observer() {
            @Override public boolean interested(ServerPlayer player) {
                return PENDING.containsKey(player.getUUID()) || GrapnelAttribute.selected(player, EquipmentSlot.LEGS) != null
                    || GrapnelAttribute.selected(player, EquipmentSlot.FEET) != null;
            }
            @Override public @Nullable Object before(ServerPlayer player, Vec3 requested) {
                if (!ready(player)) return null;
                Vec3 intent = player.getLastClientMoveIntent();
                List<Candidate> candidates = new ArrayList<>();
                for (EquipmentSlot slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                    for (GrapnelAttribute.Gear gear : GrapnelAttribute.selectedAll(player, slot)) {
                        if (!environment(player, gear)) continue;
                        var attribute = GrapnelAttribute.evidence(player, gear);
                        if (attribute != null) candidates.add(new Candidate(gear, attribute));
                    }
                }
                return new Capture(player.level(), player.level().getGameTime(), intent, List.copyOf(candidates));
            }
            @Override public void moved(ServerPlayer player, ConfirmedPlayerMove.Step step, @Nullable Object raw) {
                UUID id = player.getUUID(); Pending pending = PENDING.get(id);
                if (!(raw instanceof Capture capture) || capture.level() != player.level() || !ready(player)
                    || !matchesInput(step.to().subtract(step.from()), capture.intent())) { PENDING.remove(id); return; }
                Candidate candidate = null;
                for (Candidate possible : capture.candidates()) {
                    if (pending != null && !pending.candidate.equals(possible)) continue;
                    if (environment(player, possible.gear()) && possible.attribute().equals(GrapnelAttribute.evidence(player, possible.gear()))) {
                        candidate = possible; break;
                    }
                }
                if (candidate == null) { PENDING.remove(id); return; }
                if (pending == null) {
                    if (PENDING.size() >= MAX_OWNERS) return;
                    pending = new Pending(capture.level(), capture.tick(), candidate, step.from()); PENDING.put(id, pending);
                }
                if (pending.level != capture.level() || capture.tick() < pending.tick || capture.tick() - pending.tick > 1
                    || pending.steps.size() >= MAX_STEPS) { PENDING.remove(id); return; }
                pending.steps.add(step);
            }
            @Override public void invalid(ServerPlayer player) { PENDING.remove(player.getUUID()); }
        });
    }
    private static boolean ready(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isPassenger()
            && !player.getAbilities().flying && !player.isFallFlying();
    }
    private static boolean environment(ServerPlayer player, GrapnelAttribute.Gear gear) {
        if (GrapnelAttribute.SWIFT.equals(gear.pair().primaryId()))
            return player.isShiftKeyDown() && !player.isSwimming() && player.getPose() == Pose.CROUCHING;
        if (GrapnelAttribute.SOUL.equals(gear.pair().primaryId()))
            return player.onGround() && player.getBlockStateOn().is(BlockTags.SOUL_SPEED_BLOCKS);
        return GrapnelAttribute.DEPTH.equals(gear.pair().primaryId()) && player.isInWater();
    }
    /** Reject stationary, sideways, backwards, non-finite and incidental unrequested movement. */
    public static boolean matchesInput(@Nullable Vec3 displacement, @Nullable Vec3 intent) {
        if (displacement == null || intent == null || !Double.isFinite(displacement.x) || !Double.isFinite(displacement.z)
            || !Double.isFinite(intent.x) || !Double.isFinite(intent.z)) return false;
        double motion = displacement.horizontalDistanceSqr(), input = intent.horizontalDistanceSqr();
        if (motion < 1.0E-5D || input < 1.0E-8D || !Double.isFinite(motion) || !Double.isFinite(input)) return false;
        double dot = displacement.x * intent.x + displacement.z * intent.z;
        return Double.isFinite(dot) && dot > 0.5D * Math.sqrt(motion * input);
    }
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(PENDING.entrySet())) {
            Pending pending = entry.getValue(); PENDING.remove(entry.getKey(), pending);
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !ready(owner) || owner.level() != pending.level || pending.steps.isEmpty()
                || owner.level().getGameTime() < pending.tick || owner.level().getGameTime() - pending.tick > 1
                || !environment(owner, pending.candidate.gear())
                || !pending.candidate.attribute().equals(GrapnelAttribute.evidence(owner, pending.candidate.gear()))
                || !ConfirmedPlayerMove.connected(pending.start, owner.position(), pending.steps)) continue;
            long now = owner.level().getGameTime(); Throttle throttle = THROTTLES.get(entry.getKey());
            if (throttle != null && throttle.dimension().equals(owner.level().dimension()) && now < throttle.until()) continue;
            var gear = pending.candidate.gear();
            if (GrapnelSkills.tryMarkPosition(owner, gear.slot(), gear.stack(), gear.pair(), owner.position()))
                THROTTLES.put(entry.getKey(), new Throttle(owner.level().dimension(), now + MARK_INTERVAL));
        }
        THROTTLES.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Throttle throttle = entry.getValue();
            return owner == null || !ready(owner) || !owner.level().dimension().equals(throttle.dimension())
                || owner.level().getGameTime() >= throttle.until();
        });
    }
    public static void clearPlayer(UUID id) { PENDING.remove(id); THROTTLES.remove(id); }
    public static void clearServer() { PENDING.clear(); THROTTLES.clear(); }
}
