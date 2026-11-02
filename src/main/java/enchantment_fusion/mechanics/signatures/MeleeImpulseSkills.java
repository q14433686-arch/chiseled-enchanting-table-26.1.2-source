package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.SignaturePairs.SignatureEntry;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Shared real knockback and collision-clipped movement for 42.5 and the selected 08 pursuit. */
public final class MeleeImpulseSkills {
    public static final String KNOCKBACK = "minecraft:knockback";
    public static final String LOOTING = "minecraft:looting", PURSUIT = "concussive_plunder";
    private static final int MAX_MARKS = 256, MAX_PER_OWNER = 4, MAX_CONTACTS = 32;
    private static final long LIFETIME = 60;
    static record Gear(ItemStack stack, FusedPair pair) {}
    static final class Mark {
        final UUID owner, target;
        final ResourceKey<Level> dimension;
        final Gear gear;
        final Vec3 direction;
        final long created, expires;
        Vec3 lastTarget, lastOwner, lastMotion = Vec3.ZERO;
        final List<Step> targetSteps = new ArrayList<>(), ownerSteps = new ArrayList<>();
        long motionTick = Long.MIN_VALUE;
        double ownerTravel;
        Mark(ServerPlayer owner, LivingEntity target, Gear gear, Vec3 direction, long now) {
            this.owner = owner.getUUID(); this.target = target.getUUID(); this.dimension = owner.level().dimension();
            this.gear = gear; this.direction = direction; this.created = now; this.expires = now + LIFETIME;
            this.lastTarget = target.position(); this.lastOwner = owner.position();
        }
    }
    /** A claim is settled only after the complete real attack returns successfully. */
    static record Pursuit(Mark mark, LivingEntity target, Vec3 movement, double available) {}
    private record Contact(LivingEntity entity, double time) {}
    /** Null bounds mean the candidate only appeared after the observed movement. */
    private record Candidate(LivingEntity entity, @Nullable AABB bounds) {}
    private record Step(Vec3 from, Vec3 to, AABB box, Vec3 velocity, List<Candidate> candidates) {}
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final Map<UUID, Integer> TRACKED = new HashMap<>();
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Move> MOVING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PRIMARY_REPLAY = ThreadLocal.withInitial(() -> false);
    private MeleeImpulseSkills() {}

    public static void init() {
        ConfirmedMelee.register(MeleeImpulseSkills::onHit);
        MeleeAttackThemes.registerPrimaryTemplate("melee_impulse", (pair, stack) -> isAttackFirst(pair) && applies(pair, stack));
    }
    public static boolean isAttackFirst(@Nullable FusedPair pair) {
        return pair != null && MeleeAttackThemes.isAttack(pair.primaryId()) && KNOCKBACK.equals(pair.secondaryId());
    }
    public static boolean isKnockbackFirst(@Nullable FusedPair pair) {
        return pair != null && KNOCKBACK.equals(pair.primaryId()) && MeleeAttackThemes.isAttack(pair.secondaryId());
    }
    public static boolean pursuitPair(@Nullable FusedPair pair) {
        return pair != null && KNOCKBACK.equals(pair.primaryId()) && LOOTING.equals(pair.secondaryId());
    }
    public static boolean pursuitSignature(@Nullable SignatureEntry entry) {
        return entry != null && PURSUIT.equals(entry.id()) && KNOCKBACK.equals(entry.primaryId())
            && LOOTING.equals(entry.secondaryId());
    }
    /** Data-pack replacements keep their own signature; only the chosen old signature is redesigned. */
    public static boolean isPursuit(@Nullable FusedPair pair) {
        return pursuitPair(pair) && SignatureCatalog.resolve(pair).map(MeleeImpulseSkills::pursuitSignature).orElse(false);
    }
    public static boolean matches(@Nullable FusedPair pair) {
        return isPursuit(pair) || (isAttackFirst(pair) || isKnockbackFirst(pair)) && SignatureCatalog.resolve(pair).isEmpty();
    }
    public static boolean supportsCarrier(Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack) {
        return matches(pair) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    private static @Nullable Gear selected(@Nullable ItemStack stack, boolean forward) {
        if (stack == null || stack.isEmpty()) return null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((forward ? isAttackFirst(pair) : isKnockbackFirst(pair) || isPursuit(pair)) && applies(pair, stack)) return new Gear(stack, pair);
        return null;
    }
    static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isSpectator(); }
    static boolean owns(ServerPlayer owner, Gear gear) {
        if (gear.stack().isEmpty() || !gear.stack().getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY)
            .pairs().contains(gear.pair()) || !applies(gear.pair(), gear.stack())) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i) == gear.stack()) return true;
        return false;
    }
    private static boolean direct(ServerPlayer owner, DamageSource source) {
        return source.getEntity() == owner && source.getDirectEntity() == owner
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
    }
    private static String attackId(Gear gear) {
        return isPursuit(gear.pair()) ? MeleeAttackThemes.SHARP
            : isAttackFirst(gear.pair()) ? gear.pair().primaryId() : gear.pair().secondaryId();
    }
    public static final class Attack {
        final @Nullable Attack previous;
        final Player player;
        final ItemStack weapon;
        final Entity target;
        final @Nullable Gear forward, reverse;
        @Nullable DamageSource confirmed, replayed, beforeSource;
        @Nullable Vec3 beforeVelocity;
        @Nullable Pursuit pursuit;
        boolean ambiguous;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.target = target;
            this.forward = slot == EquipmentSlot.MAINHAND ? selected(weapon, true) : null;
            this.reverse = slot == EquipmentSlot.MAINHAND ? selected(weapon, false) : null;
        }
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
        Attack previous = CURRENT.get();
        if (previous != null) previous.ambiguous = true;
        Attack frame = new Attack(previous, player, weapon, target, slot);
        frame.ambiguous = previous != null;
        CURRENT.set(frame); return frame;
    }
    /** Legacy callers do not prove the enclosing action completed, so they cannot settle pursuit. */
    public static void endAttack(Attack frame) { endAttack(frame, false); }
    public static void endAttack(Attack frame, boolean completed) {
        if (CURRENT.get() != frame) {
            CURRENT.remove();
            if (frame.previous != null) frame.previous.ambiguous = true;
            return;
        }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        if (completed && !frame.ambiguous && frame.pursuit != null) PursuitPlunderSkills.settle(frame);
    }
    /** Snapshot before the real hurt call, so the second hit's own knockback cannot be stolen. */
    public static void beforeDamage(@Nullable ConfirmedMelee.Observation observation) {
        Attack frame = CURRENT.get();
        if (observation == null || frame == null || frame.player != observation.player() || frame.target != observation.target()
            || frame.weapon != observation.weapon() || frame.reverse == null || !isPursuit(frame.reverse.pair())) return;
        if (frame.beforeSource != null) { frame.ambiguous = true; return; }
        frame.beforeSource = observation.source();
        frame.beforeVelocity = observation.target().getDeltaMovement();
    }
    private static void addMark(Mark mark) {
        MARKS.add(mark);
        TRACKED.merge(mark.owner, 1, Integer::sum); TRACKED.merge(mark.target, 1, Integer::sum);
    }
    private static void decrement(UUID id) {
        TRACKED.computeIfPresent(id, (key, count) -> count <= 1 ? null : count - 1);
    }
    static void removeMark(Mark mark) {
        if (MARKS.remove(mark)) { decrement(mark.owner); decrement(mark.target); }
    }
    private static void removeMarks(Predicate<Mark> predicate) {
        for (Mark mark : List.copyOf(MARKS)) if (predicate.test(mark)) removeMark(mark);
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Attack frame = CURRENT.get(); ServerPlayer owner = hit.player();
        if (frame == null || frame.player != owner || frame.weapon != hit.weapon() || frame.target != hit.target()
            || hit.slot() != EquipmentSlot.MAINHAND || owner.getMainHandItem() != frame.weapon || !hit.eligibleBefore()
            || !ready(owner) || !direct(owner, hit.source())) return;
        boolean forward = frame.forward != null && owns(owner, frame.forward)
            && MeleeAttackThemes.matchesTarget(attackId(frame.forward), hit.target());
        boolean reverse = frame.reverse != null && owns(owner, frame.reverse)
            && MeleeAttackThemes.matchesTarget(attackId(frame.reverse), hit.target());
        if (!forward && !reverse) return;
        frame.confirmed = hit.source();
        if (reverse && isPursuit(frame.reverse.pair())) {
            PursuitPlunderSkills.prepare(frame, owner, hit.target(), frame.reverse);
            return;
        }
        if (!forward) return;
        long now = owner.level().getGameTime();
        Mark mark = find(owner, hit.target(), frame.forward);
        if (mark == null || now >= mark.expires || now <= mark.created || mark.motionTick == Long.MIN_VALUE || mark.motionTick > now
            || !mark.ownerSteps.isEmpty() || !mark.targetSteps.isEmpty()
            || !close(mark.lastOwner, owner.position()) || !close(mark.lastTarget, hit.target().position())
            || now - mark.motionTick > 2 || mark.ownerTravel < 0.30D || owner.distanceToSqr(hit.target()) > 9.0D
            || !MeleeImpulseMath.trackedMotion(mark.lastMotion.x, mark.lastMotion.z, mark.direction.x, mark.direction.z)) return;
        removeMark(mark);
        FusedPair pair = frame.forward.pair();
        VirtualBlade.cast(owner, owner.getLookAngle(), hit.target(), 5.0D,
            MeleeImpulseMath.bladeDamage(pair.primaryLevel(), pair.secondaryLevel()), 8,
            victim -> MeleeAttackThemes.eligible(owner, victim, pair.primaryId()), ParticleTypes.SWEEP_ATTACK);
        CompendiumDiscoveryTracker.recordDiscovery(owner, pair.primaryId(), pair.secondaryId());
    }
    /** Preserve data-pack primary damage effects on a carrier that cannot project Knockback natively. */
    public static float nonNativePrimaryDamage(ServerLevel level, ItemStack weapon, Entity target, DamageSource source, float vanilla) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || MatrixEffectExecutor.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())
            || !(source.getEntity() instanceof ServerPlayer owner) || source.getDirectEntity() != owner
            || owner.getMainHandItem() != weapon) return vanilla;
        Gear gear = selected(weapon, false);
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), KNOCKBACK).orElse(null);
        if (gear == null || primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return vanilla;
        MutableFloat value = new MutableFloat(vanilla); PRIMARY_REPLAY.set(true);
        try { primary.value().modifyDamage(level, gear.pair().primaryLevel(), weapon, target, source, value); }
        finally { PRIMARY_REPLAY.remove(); }
        return Float.isFinite(value.floatValue()) ? value.floatValue() : vanilla;
    }
    public static void nonNativePostAttack(ServerLevel level, Player player, ItemStack weapon, Entity target, DamageSource source) {
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || MatrixEffectExecutor.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get())
            || !(player instanceof ServerPlayer owner) || owner.getMainHandItem() != weapon
            || source.getEntity() != owner || source.getDirectEntity() != owner) return;
        Gear gear = selected(weapon, false);
        Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(level.registryAccess(), KNOCKBACK).orElse(null);
        if (gear == null || primary == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(primary) > 0) return;
        PRIMARY_REPLAY.set(true);
        try { primary.value().doPostAttack(level, gear.pair().primaryLevel(), new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner),
            EnchantmentTarget.ATTACKER, target, source); }
        finally { PRIMARY_REPLAY.remove(); }
    }
    /** Keep real Knockback once. Attack-first uses its secondary only for a confirmed eligible hit. */
    public static float replayKnockback(Player player, Entity target, DamageSource source, float vanilla) {
        Attack frame = CURRENT.get();
        if (Boolean.TRUE.equals(PRIMARY_REPLAY.get()) || frame == null || frame.player != player || frame.target != target || !(player instanceof ServerPlayer owner)
            || owner.getMainHandItem() != frame.weapon || !Float.isFinite(vanilla)
            || source.getEntity() != owner || source.getDirectEntity() != owner) return vanilla;
        Gear gear = frame.reverse != null ? frame.reverse : frame.forward;
        if (gear == null || !owns(owner, gear) || (!isKnockbackFirst(gear.pair()) && !isPursuit(gear.pair()) && frame.confirmed != source)
            || WindSweepSkills.alreadyReplayed(player, frame.weapon, target, source)) return vanilla;
        ServerLevel level = owner.level();
        Holder<Enchantment> knockback = FusionStationOperations.lookupEnchantment(level.registryAccess(), KNOCKBACK).orElse(null);
        if (knockback == null || frame.weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(knockback) > 0) return vanilla;
        int effectLevel = isKnockbackFirst(gear.pair()) || isPursuit(gear.pair())
            ? gear.pair().primaryLevel() : gear.pair().secondaryLevel();
        MutableFloat value = new MutableFloat(vanilla * 2.0F); PRIMARY_REPLAY.set(true);
        try { knockback.value().modifyKnockback(level, effectLevel, frame.weapon, target, source, value); }
        finally { PRIMARY_REPLAY.remove(); }
        float result = value.floatValue() / 2.0F;
        if (!Float.isFinite(result)) return vanilla;
        frame.replayed = source; return result;
    }
    public static boolean alreadyReplayed(Player player, ItemStack weapon, Entity target, DamageSource source) {
        Attack frame = CURRENT.get();
        return frame != null && frame.player == player && frame.weapon == weapon && frame.target == target && frame.replayed == source;
    }
    /** Invoked around the actual LivingEntity.knockback, after resistance and before player packet restoration. */
    public static void afterKnockback(Player player, LivingEntity target, Vec3 before) {
        Attack frame = CURRENT.get();
        if (frame == null || frame.player != player || frame.target != target || frame.confirmed == null
            || !(player instanceof ServerPlayer owner) || !ready(owner) || owner.getMainHandItem() != frame.weapon
            || !target.isAlive() || !direct(owner, frame.confirmed)) return;
        Vec3 delta = target.getDeltaMovement().subtract(before);
        if (!Double.isFinite(delta.x) || !Double.isFinite(delta.z) || delta.horizontalDistanceSqr() < 0.0001D) return;
        Vec3 direction = new Vec3(delta.x, 0, delta.z).normalize();
        for (Gear gear : new Gear[]{frame.forward, frame.reverse}) {
            if (gear == null || !owns(owner, gear) || !MeleeAttackThemes.eligible(owner, target, attackId(gear))
                || (isPursuit(gear.pair()) && (frame.ambiguous || frame.pursuit != null))) continue;
            removeMarks(mark -> mark.owner.equals(owner.getUUID()) && mark.target.equals(target.getUUID())
                && mark.gear.stack() == gear.stack() && mark.gear.pair().equals(gear.pair()));
            List<Mark> owned = MARKS.stream().filter(mark -> mark.owner.equals(owner.getUUID())).toList();
            if (owned.size() >= MAX_PER_OWNER) removeMark(owned.getFirst());
            if (MARKS.size() >= MAX_MARKS) continue;
            long now = owner.level().getGameTime(); addMark(new Mark(owner, target, gear, direction, now));
            owner.level().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 5, 0.15D, 0.2D, 0.15D, 0.01D);
        }
    }
    static @Nullable Mark find(ServerPlayer owner, LivingEntity target, Gear gear) {
        for (Mark mark : MARKS) if (mark.owner.equals(owner.getUUID()) && mark.target.equals(target.getUUID())
            && mark.dimension.equals(owner.level().dimension()) && mark.gear.stack() == gear.stack()
            && mark.gear.pair().equals(gear.pair()) && owns(owner, gear)) return mark;
        return null;
    }
    private static MeleeImpulseMath.Box box(AABB b) {
        return new MeleeImpulseMath.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    /** A bounded snapshot around a real Entity.move, not a position/teleport event. */
    public static final class Move {
        final @Nullable Move previous;
        final Entity entity;
        final Vec3 from, velocity;
        final AABB box;
        final boolean suppressed, captureContacts;
        final List<Candidate> candidates = new ArrayList<>();
        boolean ambiguous;
        Move(@Nullable Move previous, Entity entity, boolean suppressed, boolean captureContacts) {
            this.previous = previous; this.entity = entity; this.suppressed = suppressed; this.captureContacts = captureContacts;
            this.from = entity.position(); this.velocity = entity.getDeltaMovement(); this.box = entity.getBoundingBox();
        }
    }
    private static List<LivingEntity> candidates(Entity source, AABB bounds) {
        return ((ServerLevel) source.level()).getEntitiesOfClass(LivingEntity.class, bounds,
            other -> other != source && other.isAlive() && !other.isSpectator());
    }
    public static @Nullable Move beginMove(Entity entity, MoverType type, Vec3 requested) {
        Move previous = MOVING.get();
        // A nested living-entity move can make the outer contact time ambiguous.
        if (previous != null && previous.entity != entity && entity instanceof LivingEntity) previous.ambiguous = true;
        if (!(entity instanceof LivingEntity) || !(entity.level() instanceof ServerLevel) || TRACKED.isEmpty()
            || !TRACKED.containsKey(entity.getUUID()) || entity.noPhysics
            || (type != MoverType.SELF && type != MoverType.PLAYER)) return null;
        boolean nested = false;
        for (Move parent = previous; parent != null; parent = parent.previous) {
            if (parent.entity == entity) { parent.ambiguous = true; nested = true; }
        }
        boolean observe = MARKS.stream().anyMatch(mark -> mark.target.equals(entity.getUUID()) && isKnockbackFirst(mark.gear.pair()));
        Move move = new Move(previous, entity, nested, observe); MOVING.set(move);
        if (observe) {
            if (!finite(requested) || requested.lengthSqr() > 4.0D) move.ambiguous = true;
            else {
                List<LivingEntity> nearby = candidates(entity, move.box.expandTowards(requested).inflate(0.001D));
                if (nearby.size() > MAX_CONTACTS) move.ambiguous = true;
                else for (LivingEntity candidate : nearby) move.candidates.add(new Candidate(candidate, candidate.getBoundingBox()));
            }
        }
        return move;
    }
    public static void endMove(@Nullable Move move, boolean completed) {
        if (move == null || MOVING.get() != move) return;
        if (move.previous == null) MOVING.remove(); else MOVING.set(move.previous);
        UUID id = move.entity.getUUID();
        if (!completed || move.ambiguous || move.suppressed) {
            removeMarks(mark -> mark.owner.equals(id) || mark.target.equals(id)); return;
        }
        Vec3 end = move.entity.position(), motion = end.subtract(move.from);
        AABB next = move.entity.getBoundingBox();
        if (!finite(move.from) || !finite(end) || !finite(motion) || !finite(move.velocity) || motion.lengthSqr() > 4.0D
            || Math.abs(next.getXsize() - move.box.getXsize()) > 1.0E-5D
            || Math.abs(next.getYsize() - move.box.getYsize()) > 1.0E-5D
            || Math.abs(next.getZsize() - move.box.getZsize()) > 1.0E-5D
            || Math.abs(next.minX - move.box.minX - motion.x) > 1.0E-5D
            || Math.abs(next.minY - move.box.minY - motion.y) > 1.0E-5D
            || Math.abs(next.minZ - move.box.minZ - motion.z) > 1.0E-5D) {
            removeMarks(mark -> mark.owner.equals(id) || mark.target.equals(id)); return;
        }
        if (motion.lengthSqr() < 1.0E-10D) return;
        if (move.captureContacts) {
            List<LivingEntity> after = candidates(move.entity, move.box.expandTowards(motion).inflate(0.001D));
            for (LivingEntity candidate : after)
                if (move.candidates.stream().noneMatch(earlier -> earlier.entity() == candidate))
                    move.candidates.add(new Candidate(candidate, null));
            if (move.candidates.size() > MAX_CONTACTS) {
                removeMarks(mark -> mark.owner.equals(id) || mark.target.equals(id)); return;
            }
        }
        Step step = new Step(move.from, end, move.box, move.velocity, List.copyOf(move.candidates));
        for (Mark mark : List.copyOf(MARKS)) {
            List<Step> pending = mark.owner.equals(id) ? mark.ownerSteps : mark.target.equals(id) ? mark.targetSteps : null;
            if (pending == null) continue;
            if (pending.size() >= 4) { removeMark(mark); continue; }
            pending.add(step);
        }
    }
    static boolean close(Vec3 first, Vec3 second) {
        return finite(first) && finite(second) && first.distanceToSqr(second) <= 1.0E-6D;
    }
    static boolean hasPendingSteps(Mark mark) { return !mark.ownerSteps.isEmpty() || !mark.targetSteps.isEmpty(); }
    /** Reject teleport, correction or interrupted movement before using any captured step. */
    private static boolean validSteps(Vec3 initial, Vec3 actual, List<Step> steps) {
        Vec3 expected = initial;
        for (Step step : steps) {
            if (!close(expected, step.from())) return false;
            expected = step.to();
        }
        return close(expected, actual);
    }
    /** Settle captured movement only after the server tick; a protected first contact blocks transfer. */
    public static void tickServer(MinecraftServer server) {
        for (Mark mark : List.copyOf(MARKS)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(mark.owner);
            ServerLevel level = server.getLevel(mark.dimension);
            Entity entity = level == null ? null : level.getEntity(mark.target);
            long now = level == null ? Long.MAX_VALUE : level.getGameTime();
            if (owner == null || !ready(owner) || level == null || owner.level() != level || now >= mark.expires
                || !(entity instanceof LivingEntity target) || !MeleeAttackThemes.eligible(owner, target, attackId(mark.gear))
                || !owns(owner, mark.gear) || !validSteps(mark.lastOwner, owner.position(), mark.ownerSteps)
                || !validSteps(mark.lastTarget, target.position(), mark.targetSteps)) { removeMark(mark); continue; }
            for (Step step : mark.ownerSteps) {
                Vec3 movement = step.to().subtract(step.from());
                if (movement.lengthSqr() > 2.25D) { removeMark(mark); break; }
                mark.ownerTravel = Math.min(16.0D, mark.ownerTravel + movement.horizontalDistance());
            }
            if (!MARKS.contains(mark)) continue;
            mark.lastOwner = owner.position(); mark.lastTarget = target.position();
            mark.ownerSteps.clear();
            boolean moving = false;
            for (Step step : mark.targetSteps) {
                Vec3 motion = step.to().subtract(step.from());
                if (!MeleeImpulseMath.trackedMotion(motion.x, motion.z, mark.direction.x, mark.direction.z)
                    || (!(target instanceof ServerPlayer)
                        && step.velocity().x * mark.direction.x + step.velocity().z * mark.direction.z <= 0.01D)) continue;
                moving = true; mark.lastMotion = motion; mark.motionTick = now;
                if (isKnockbackFirst(mark.gear.pair())) contact(owner, target, mark, step, motion);
                if (!MARKS.contains(mark)) break;
            }
            mark.targetSteps.clear();
            if (!moving && (mark.motionTick == Long.MIN_VALUE || now < mark.motionTick || now - mark.motionTick > 1)) {
                mark.lastMotion = Vec3.ZERO; mark.motionTick = Long.MIN_VALUE;
            }
        }
    }
    private static boolean sameBox(AABB first, AABB second) {
        return Math.abs(first.minX - second.minX) <= 1.0E-6D && Math.abs(first.maxX - second.maxX) <= 1.0E-6D
            && Math.abs(first.minY - second.minY) <= 1.0E-6D && Math.abs(first.maxY - second.maxY) <= 1.0E-6D
            && Math.abs(first.minZ - second.minZ) <= 1.0E-6D && Math.abs(first.maxZ - second.maxZ) <= 1.0E-6D;
    }
    private static void contact(ServerPlayer owner, LivingEntity source, Mark mark, Step step, Vec3 motion) {
        ServerLevel level = owner.level(); AABB from = step.box(); Vec3 previous = step.from();
        List<Contact> contacts = new ArrayList<>();
        for (Candidate candidate : step.candidates()) {
            LivingEntity other = candidate.entity(); AABB before = candidate.bounds(), current = other.getBoundingBox();
            double atStart = before == null ? Double.POSITIVE_INFINITY
                : MeleeImpulseMath.contact(box(from), motion.x, motion.y, motion.z, box(before));
            double atEnd = MeleeImpulseMath.contact(box(from), motion.x, motion.y, motion.z, box(current));
            boolean overlapping = before != null && from.intersects(before) || from.intersects(current);
            if (!overlapping && !Double.isFinite(atStart) && !Double.isFinite(atEnd)) continue;
            // Existing overlap, changing/late bounds or movement anywhere this tick cannot prove contact time.
            if (overlapping || before == null || !sameBox(before, current) || other.level() != level
                || !other.isAlive() || other.position().distanceToSqr(other.oldPosition()) > 1.0E-6D) {
                removeMark(mark); return;
            }
            if (Double.isFinite(atStart)) contacts.add(new Contact(other, atStart));
        }
        if (contacts.isEmpty()) return;
        contacts.sort(Comparator.comparingDouble(Contact::time));
        Contact earliest = contacts.getFirst(); LivingEntity first = earliest.entity(); removeMark(mark);
        // An equally early protected or fast-moving entity makes the first contact ambiguous.
        for (Contact hit : contacts) {
            if (hit.time() > earliest.time() + 1.0E-6D) break;
            if (!MeleeAttackThemes.eligible(owner, hit.entity(), attackId(mark.gear))
                || hit.entity().getDeltaMovement().lengthSqr() > 0.04D) return;
        }
        if (!VirtualCombatSight.clear(level, owner, previous.add(0, source.getBbHeight() * 0.5D, 0), first.getBoundingBox().getCenter())) return;
        float strength = MeleeImpulseMath.transferStrength(motion.horizontalDistance(), mark.gear.pair().primaryLevel());
        if (strength <= 0) return;
        Vec3 direction = new Vec3(motion.x, 0, motion.z).normalize(); Vec3 before = first.getDeltaMovement();
        first.knockback(strength, -direction.x, -direction.z);
        Vec3 change = first.getDeltaMovement().subtract(before);
        if (!finite(change) || change.horizontalDistanceSqr() < 1.0E-6D) return;
        syncMotion(first);
        Vec3 velocity = source.getDeltaMovement();
        if (finite(velocity) && velocity.x * direction.x + velocity.z * direction.z > 0) {
            source.setDeltaMovement(velocity.x * 0.65D, velocity.y, velocity.z * 0.65D); syncMotion(source);
        }
        level.sendParticles(ParticleTypes.CRIT, first.getX(), first.getY(0.5D), first.getZ(), 8, 0.18D, 0.2D, 0.18D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, mark.gear.pair().primaryId(), mark.gear.pair().secondaryId());
    }
    private static void syncMotion(LivingEntity target) {
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
    public static void clearPlayer(UUID id) { if (id != null) removeMarks(mark -> mark.owner.equals(id) || mark.target.equals(id)); }
    public static void clearServer() { MARKS.clear(); TRACKED.clear(); CURRENT.remove(); MOVING.remove(); PRIMARY_REPLAY.remove(); }
}
