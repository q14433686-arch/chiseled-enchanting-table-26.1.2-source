package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.model.FusedPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** Exact death-XP award evidence; merged, nested, foreign, or unverified XP is left entirely vanilla. */
public final class RangedMendingCapture {
    private static final int MAX_ORBS = 64, MAX_TRACKED = 512, MAX_PER_OWNER = 64;
    private static final long ROUTE_TICKS = 200L;
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final Map<ExperienceOrb, Route> ROUTES = new HashMap<>();
    public static final class Frame {
        final @Nullable Frame previous; final @Nullable ConfirmedArrow.DamageEvidence damage;
        final @Nullable DamageSource source; final @Nullable FusedPair pair;
        final List<ExperienceOrb> orbs = new ArrayList<>();
        boolean ambiguous, inDeath, inAward; int deathCalls, awardCalls, loopCalls, loopDepth, addDepth, awardAmount;
        @Nullable Vec3 awardPosition;
        Frame(@Nullable Frame previous, @Nullable ConfirmedArrow.DamageEvidence damage,
              @Nullable DamageSource source, @Nullable FusedPair pair) {
            this.previous = previous; this.damage = damage; this.source = source; this.pair = pair;
        }
    }
    private static final class Route {
        final UUID owner; final ResourceKey<Level> dimension; final ItemStack launcher; final FusedPair pair;
        final List<Vec3> path; final long born; final int value; int index; Vec3 previous;
        Route(ServerPlayer owner, ItemStack launcher, FusedPair pair, List<Vec3> path, ExperienceOrb orb) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.launcher = launcher; this.pair = pair;
            this.path = path; this.born = owner.level().getGameTime(); this.value = orb.getValue();
            this.index = path.size() - 1; this.previous = orb.position();
        }
    }
    private RangedMendingCapture() {}
    private static int count(ExperienceOrb orb) { return ((ExperienceOrbCountAccess) (Object) orb).ef$pickupCount(); }
    public static @Nullable Frame begin(@Nullable ConfirmedArrow.Damage hit, DamageSource source) {
        Frame previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        ConfirmedArrow.DamageEvidence evidence = ConfirmedArrow.experienceEvidence(hit); FusedPair selected = null;
        if (evidence != null && evidence.target() instanceof Mob && source.getDirectEntity() == evidence.arrow()
            && source.getEntity() == evidence.owner() && RangedMendingSkills.ready(evidence.owner())) {
            for (FusedPair pair : evidence.pairs()) if (RangedMendingSkills.shootingFirst(pair)
                && RangedMendingSkills.applies(pair, evidence.launcher())
                && (selected == null || pair.secondaryLevel() > selected.secondaryLevel())) selected = pair;
        }
        if (selected == null && previous == null) { CURRENT.remove(); return null; }
        Frame frame = new Frame(previous, selected == null ? null : evidence, selected == null ? null : source, selected);
        CURRENT.set(frame); return frame;
    }
    /** A nested or unrelated death invalidates the enclosing attribution, rather than stealing its XP. */
    public static boolean beginDeath(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame frame = CURRENT.get(); if (frame == null) return false;
        if (frame.damage == null || frame.pair == null || frame.damage.target() != victim || frame.damage.owner().level() != level
            || frame.source != source || frame.inDeath) { frame.ambiguous = true; return false; }
        frame.deathCalls++; frame.inDeath = true; if (frame.deathCalls != 1) frame.ambiguous = true; return true;
    }
    public static void endDeath(boolean entered) {
        Frame frame = CURRENT.get();
        if (!entered) return;
        if (frame == null || !frame.inDeath || frame.inAward || frame.loopDepth != 0 || frame.addDepth != 0) {
            if (frame != null) frame.ambiguous = true; return;
        }
        frame.inDeath = false;
    }
    /** Wrap vanilla's exact award call; never inspect existing nearby orbs or replace the award. */
    public static boolean beginAward(LivingEntity victim, ServerLevel level, Vec3 position, int amount) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.damage == null || !frame.inDeath || frame.damage.target() != victim
            || frame.damage.owner().level() != level) return false;
        frame.awardCalls++;
        if (frame.inAward || frame.awardCalls != 1 || amount <= 0 || !RangedTideMath.finite(position)
            || position.distanceToSqr(victim.position()) > 1.0E-6D) { frame.ambiguous = true; return false; }
        frame.inAward = true; frame.awardAmount = amount; frame.awardPosition = position; return true;
    }
    public static void endAward(boolean entered, boolean completed) {
        Frame frame = CURRENT.get(); if (!entered) return;
        if (frame == null || !frame.inAward) { if (frame != null) frame.ambiguous = true; return; }
        if (!completed || frame.loopDepth != 0 || frame.addDepth != 0) frame.ambiguous = true;
        frame.inAward = false;
    }
    /** A nested award, including one made before an insertion, cannot borrow this death scope. */
    public static @Nullable Frame beginAwardLoop(ServerLevel level, Vec3 position, int amount) {
        Frame frame = CURRENT.get(); if (frame == null || !frame.inAward) return null;
        frame.loopDepth++; frame.loopCalls++;
        if (frame.damage == null || frame.damage.owner().level() != level || frame.loopDepth != 1
            || frame.loopCalls != 1 || amount != frame.awardAmount || frame.awardPosition == null
            || !frame.awardPosition.equals(position)) frame.ambiguous = true;
        return frame;
    }
    public static void endAwardLoop(@Nullable Frame frame, boolean completed) {
        if (frame == null) return;
        if (CURRENT.get() != frame || frame.loopDepth <= 0 || !completed || frame.addDepth != 0) frame.ambiguous = true;
        if (frame.loopDepth > 0) frame.loopDepth--;
    }
    /** Nested world insertions during an award make all attribution ambiguous. */
    public static @Nullable Frame beginAdd(ServerLevel level) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.damage == null || !frame.inAward || frame.loopDepth != 1
            || frame.damage.owner().level() != level) return null;
        frame.addDepth++; if (frame.addDepth != 1) frame.ambiguous = true; return frame;
    }
    public static void endAdd(@Nullable Frame frame) {
        if (frame == null) return;
        if (CURRENT.get() != frame || frame.addDepth <= 0) { frame.ambiguous = true; return; }
        frame.addDepth--;
    }
    /** Called at successful addFreshEntity return, not from a neighborhood scan. */
    public static void added(ServerLevel level, ExperienceOrb orb) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.damage == null || !frame.inAward || frame.loopDepth != 1 || frame.addDepth != 1
            || frame.damage.owner().level() != level || frame.awardPosition == null) return;
        if (frame.orbs.size() >= MAX_ORBS || !orb.isAlive() || orb.level() != level || level.getEntity(orb.getUUID()) != orb
            || count(orb) != 1 || orb.getValue() <= 0 || orb.position().distanceToSqr(frame.awardPosition) > 1.0E-6D
            || frame.orbs.contains(orb)) { frame.ambiguous = true; return; }
        frame.orbs.add(orb);
    }
    /** Real accepted damage, one death and the original completed award must all agree. */
    public static void end(@Nullable Frame frame, boolean completed, boolean accepted) {
        if (frame == null) return;
        if (CURRENT.get() != frame) { CURRENT.remove(); if (frame.previous != null) frame.previous.ambiguous = true; return; }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        ConfirmedArrow.DamageEvidence hit = frame.damage; FusedPair pair = frame.pair;
        if (!completed || !accepted || frame.ambiguous || frame.inDeath || frame.inAward || frame.loopDepth != 0 || frame.addDepth != 0
            || frame.deathCalls != 1 || frame.awardCalls != 1 || frame.loopCalls != 1 || frame.awardAmount <= 0 || hit == null || pair == null
            || frame.awardPosition == null || !hit.target().isDeadOrDying() || hit.owner().level() != hit.target().level()
            || !RangedMendingSkills.owns(hit.owner(), hit.launcher(), pair)
            || ConfirmedMeleeMath.actualLoss(hit.beforeHealth(), hit.beforeAbsorption(), hit.target().getHealth(), hit.target().getAbsorptionAmount()) <= 0
            || hit.path().isEmpty() || frame.awardPosition.distanceToSqr(hit.path().getLast()) > 16.0D
            || !VirtualCombatSight.clear(hit.owner().level(), hit.owner(), frame.awardPosition, hit.path().getLast())) return;
        long total = 0;
        for (ExperienceOrb orb : frame.orbs) {
            if (!orb.isAlive() || orb.level() != hit.owner().level() || hit.owner().level().getEntity(orb.getUUID()) != orb
                || count(orb) != 1 || orb.getValue() <= 0 || orb.position().distanceToSqr(frame.awardPosition) > 1.0E-6D) continue;
            total += orb.getValue();
        }
        if (total <= 0 || total > frame.awardAmount) return;
        int ownerCount = (int) ROUTES.values().stream().filter(route -> route.owner.equals(hit.owner().getUUID())).count();
        int beforeCount = ownerCount;
        for (ExperienceOrb orb : frame.orbs) {
            if (ROUTES.size() >= MAX_TRACKED || ownerCount >= MAX_PER_OWNER) break;
            if (!orb.isAlive() || orb.level() != hit.owner().level() || hit.owner().level().getEntity(orb.getUUID()) != orb
                || count(orb) != 1 || orb.getValue() <= 0 || orb.position().distanceToSqr(frame.awardPosition) > 1.0E-6D) continue;
            ROUTES.put(orb, new Route(hit.owner(), hit.launcher(), pair, hit.path(), orb)); ownerCount++;
        }
        if (ownerCount > beforeCount)
            CompendiumDiscoveryTracker.recordDiscovery(hit.owner(), pair.primaryId(), pair.secondaryId());
    }
    private static boolean valid(ServerPlayer owner, ExperienceOrb orb, Route route) {
        long now = owner.level().getGameTime();
        return route.owner.equals(owner.getUUID()) && route.dimension.equals(owner.level().dimension())
            && now >= route.born && now - route.born < ROUTE_TICKS && RangedMendingSkills.owns(owner, route.launcher, route.pair)
            && orb.isAlive() && orb.level() == owner.level() && owner.level().getEntity(orb.getUUID()) == orb
            && count(orb) == 1 && orb.getValue() == route.value && RangedTideMath.finite(orb.position());
    }
    /** Apply only a small velocity to the same orb; walls and other players retain normal collision/pickup. */
    public static void tickServer(MinecraftServer server) {
        for (var entry : List.copyOf(ROUTES.entrySet())) {
            ExperienceOrb orb = entry.getKey(); Route route = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(route.owner);
            if (owner == null || !valid(owner, orb, route) || orb.position().distanceToSqr(route.previous) > 4.0D) {
                ROUTES.remove(orb, route); continue;
            }
            route.previous = orb.position();
            while (route.index >= 0 && orb.position().distanceToSqr(route.path.get(route.index)) < .64D) route.index--;
            Vec3 destination;
            if (route.index >= 0) destination = route.path.get(route.index);
            else {
                if (owner.position().distanceToSqr(route.path.getFirst()) > 64.0D) { ROUTES.remove(orb, route); continue; }
                destination = owner.position().add(0, .8D, 0);
            }
            if (!RangedTideMath.finite(destination) || !VirtualCombatSight.clear(owner.level(), owner, orb.position(), destination)) {
                ROUTES.remove(orb, route); continue;
            }
            Vec3 delta = destination.subtract(orb.position());
            if (delta.lengthSqr() > .04D) { orb.setDeltaMovement(delta.normalize().scale(Math.min(.55D, delta.length()))); orb.hurtMarked = true; }
        }
    }
    /** A verified one-count orb first repairs the exact launcher; remainder flows through normal giveExperiencePoints. */
    public static OptionalInt claim(ExperienceOrb orb, ServerPlayer owner, int amount) {
        Route route = ROUTES.remove(orb); if (route == null || !valid(owner, orb, route) || amount != route.value || amount <= 0) return OptionalInt.empty();
        ItemStack launcher = route.launcher; ServerLevel level = owner.level();
        Holder<Enchantment> mending = FusionStationOperations.lookupEnchantment(level.registryAccess(), RangedMendingSkills.MENDING).orElse(null);
        if (mending == null || !mending.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) return OptionalInt.empty();
        int nativeLevel = launcher.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(mending);
        if (nativeLevel > 0 && nativeLevel < route.pair.secondaryLevel()) return OptionalInt.empty();
        if (!launcher.isDamaged()) return OptionalInt.of(amount);
        GrapnelDurability.RepairFrame frame = GrapnelDurability.beginRepair(owner);
        try {
            GrapnelDurability.RepairFrame calculation = GrapnelDurability.beginRepairCalculation(owner, level, launcher);
            int vanilla = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, launcher, amount);
            MutableFloat value = new MutableFloat(vanilla);
            if (nativeLevel <= 0) mending.value().modifyDurabilityToRepairFromXp(level, route.pair.secondaryLevel(), launcher, value);
            if (!Float.isFinite(value.floatValue())) return OptionalInt.empty();
            int capacity = Math.max(0, value.intValue());
            GrapnelDurability.finishRepairCalculation(calculation, nativeLevel > 0 ? amount : vanilla, capacity);
            int before = launcher.getDamageValue(), planned = Math.min(before, capacity);
            if (planned <= 0 || !valid(owner, orb, route)) return OptionalInt.of(amount);
            launcher.setDamageValue(before - planned);
            int repaired = Math.clamp(before - launcher.getDamageValue(), 0, planned);
            if (repaired <= 0) return OptionalInt.of(amount);
            GrapnelDurability.afterOrbRepair(owner, launcher, before, repaired, nativeLevel <= 0);
            DurabilityEconomy.afterOrbRepair(owner, launcher, repaired);
            MeleeMendingSkills.afterOrbRepair(owner, launcher, repaired);
            RangedMendingSkills.afterOrbRepair(owner, launcher, before, repaired, nativeLevel <= 0);
            return OptionalInt.of(RangedMendingMath.remainingXp(amount, capacity, repaired));
        } finally { GrapnelDurability.endRepair(frame); }
    }
    public static void clearPlayer(UUID owner) { ROUTES.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)); }
    public static void clearServer() { ROUTES.clear(); CURRENT.remove(); }
}
