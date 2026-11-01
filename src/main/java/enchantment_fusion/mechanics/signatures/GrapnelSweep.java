package enchantment_fusion.mechanics.signatures;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A sword sweep must use the primary attribute and actually damage a separate eligible victim. */
public final class GrapnelSweep {
    private static final int MAX_PENDING = 256;
    private record Pending(GrapnelAttribute.Gear gear, UUID target, DamageSource source, ResourceKey<Level> dimension, long tick) {}
    public static final class Sweep {
        final @Nullable Sweep previous; final @Nullable ServerPlayer owner; final @Nullable Pending pending;
        final @Nullable PrimaryAttributeProjection.Evidence attribute;
        boolean ambiguous, marked; int depth;
        Sweep(@Nullable Sweep previous, @Nullable ServerPlayer owner, @Nullable Pending pending,
              @Nullable PrimaryAttributeProjection.Evidence attribute) {
            this.previous = previous; this.owner = owner; this.pending = pending; this.attribute = attribute;
        }
    }
    public static final class Victim {
        final Sweep sweep; final LivingEntity target; final float health, absorption; final boolean eligible;
        Victim(Sweep sweep, LivingEntity target, boolean eligible) {
            this.sweep = sweep; this.target = target; this.eligible = eligible;
            this.health = target.getHealth(); this.absorption = target.getAbsorptionAmount();
        }
    }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final ThreadLocal<Sweep> CURRENT = new ThreadLocal<>();
    private static boolean initialized;
    private GrapnelSweep() {}

    public static void init() {
        if (initialized) return;
        initialized = true; ConfirmedMelee.register(GrapnelSweep::onHit);
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isCreative() && !owner.isSpectator(); }
    private static void onHit(ConfirmedMelee.Hit hit) {
        ServerPlayer owner = hit.player();
        if (!ready(owner) || !hit.full() || !hit.eligibleBefore() || hit.slot() != EquipmentSlot.MAINHAND
            || owner.getMainHandItem() != hit.weapon()) return;
        GrapnelAttribute.Gear gear = GrapnelAttribute.selected(owner, EquipmentSlot.MAINHAND);
        if (gear == null || !GrapnelAttribute.SWEEP.equals(gear.pair().primaryId())
            || (!PENDING.containsKey(owner.getUUID()) && PENDING.size() >= MAX_PENDING)) return;
        PENDING.put(owner.getUUID(), new Pending(gear, hit.target().getUUID(), hit.source(), owner.level().dimension(), owner.level().getGameTime()));
    }
    /** The comparison uses the exact float arithmetic of the real sweep's initial damage. */
    public static boolean improvesSweep(float baseDamage, float charge, double ratio, double without) {
        if (!Float.isFinite(baseDamage) || !Float.isFinite(charge) || baseDamage <= 0 || charge <= 0
            || !Double.isFinite(ratio) || !Double.isFinite(without)) return false;
        float current = 1.0F + (float) ratio * baseDamage, baseline = 1.0F + (float) without * baseDamage;
        return Float.isFinite(current) && Float.isFinite(baseline) && current > baseline;
    }
    /** Wraps the actual doSweepAttack; the pending direct hit must be the one that caused this sweep. */
    public static Sweep begin(Player player, Entity originalTarget, float baseDamage, DamageSource source, float charge) {
        Sweep previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        ServerPlayer owner = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        Pending pending = owner == null ? null : PENDING.remove(owner.getUUID());
        PrimaryAttributeProjection.Evidence proof = null;
        if (owner != null && pending != null && ready(owner) && pending.source() == source
            && pending.target().equals(originalTarget.getUUID()) && pending.dimension().equals(owner.level().dimension())
            && pending.tick() == owner.level().getGameTime() && GrapnelAttribute.stillEquipped(owner, pending.gear())) {
            proof = GrapnelAttribute.evidence(owner, pending.gear());
            if (proof != null && !improvesSweep(baseDamage, charge, proof.value(), proof.withoutPrimary())) proof = null;
        }
        Sweep sweep = new Sweep(previous, owner, pending, proof); CURRENT.set(sweep); return sweep;
    }
    public static void end(Sweep sweep) {
        if (CURRENT.get() != sweep) { CURRENT.remove(); return; }
        if (sweep.previous == null) CURRENT.remove(); else CURRENT.set(sweep.previous);
    }
    public static @Nullable Victim before(Player player, LivingEntity target, DamageSource source) {
        Sweep sweep = CURRENT.get();
        if (sweep == null || sweep.owner != player || sweep.pending == null || sweep.attribute == null) return null;
        if (sweep.depth++ > 0) sweep.ambiguous = true;
        ServerPlayer owner = sweep.owner;
        boolean eligible = !sweep.marked && target.isAlive() && !sweep.pending.target().equals(target.getUUID())
            && source == sweep.pending.source() && source.getEntity() == owner && source.getDirectEntity() == owner
            && GrapnelSkills.eligibleTarget(owner, target, sweep.pending.gear().pair());
        return new Victim(sweep, target, eligible);
    }
    public static void after(@Nullable Victim victim, boolean completed, boolean accepted) {
        if (victim == null) return;
        Sweep sweep = victim.sweep;
        if (CURRENT.get() != sweep || sweep.depth != 1 || !completed) sweep.ambiguous = true;
        if (sweep.depth > 0) sweep.depth--;
        ServerPlayer owner = sweep.owner; Pending pending = sweep.pending;
        if (sweep.ambiguous || sweep.marked || !accepted || !victim.eligible || owner == null || pending == null
            || !ready(owner) || owner.level() != victim.target.level() || !victim.target.isAlive()
            || owner.level().getGameTime() != pending.tick() || !pending.dimension().equals(owner.level().dimension())
            || !GrapnelAttribute.stillEquipped(owner, pending.gear())
            || !sweep.attribute.equals(GrapnelAttribute.evidence(owner, pending.gear()))
            || ConfirmedMeleeMath.actualLoss(victim.health, victim.absorption, victim.target.getHealth(), victim.target.getAbsorptionAmount()) <= 0) return;
        sweep.marked = true;
        GrapnelSkills.markImpact(owner, victim.target, pending.gear().stack(), pending.gear().pair());
    }
    public static void tickServer(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey()); Pending pending = entry.getValue();
            return owner == null || !ready(owner) || !owner.level().dimension().equals(pending.dimension())
                || owner.level().getGameTime() > pending.tick() || !GrapnelAttribute.stillEquipped(owner, pending.gear());
        });
    }
    public static void clearPlayer(UUID id) { PENDING.remove(id); }
    public static void clearServer() { PENDING.clear(); CURRENT.remove(); }
}
