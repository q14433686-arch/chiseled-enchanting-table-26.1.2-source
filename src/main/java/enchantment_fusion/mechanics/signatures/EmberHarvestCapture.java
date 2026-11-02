package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.EntityLootProvenance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Scope a marked victim's own final ordinary loot, then settle after the full real attack. */
public final class EmberHarvestCapture {
    private static final TagKey<Item> ORDINARY = TagKey.create(Registries.ITEM,
        Identifier.fromNamespaceAndPath("enchantment_fusion", "ember_harvest_ordinary"));
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    public static final class Frame {
        final @Nullable Frame previous; final @Nullable Evidence evidence;
        boolean ambiguous, inDeath; int lootCalls, deaths;
        @Nullable EntityLootProvenance.Scope loot;
        Frame(@Nullable Frame previous, @Nullable Evidence evidence) { this.previous = previous; this.evidence = evidence; }
    }
    private static final class Evidence implements Runnable {
        final ServerPlayer owner; final LivingEntity victim; final ServerLevel level; final DamageSource source;
        final ItemStack weapon; final OwnedTargetMarks.Mark mark;
        final Vec3 origin; boolean accepted, confirmed, completed, ambiguous; int deaths, lootCalls;
        List<EntityLootProvenance.Verified> drops = List.of();
        Evidence(ServerPlayer owner, LivingEntity victim, ServerLevel level, DamageSource source, ItemStack weapon, OwnedTargetMarks.Mark mark) {
            this.owner = owner; this.victim = victim; this.level = level; this.source = source; this.weapon = weapon; this.mark = mark;
            this.origin = victim.position();
        }
        @Override public void run() {
            try { settle(this); }
            catch (RuntimeException error) { EnchantmentFusion.LOGGER.error("Unable to settle ordinary ember-harvest loot", error); }
        }
    }
    private EmberHarvestCapture() {}
    /** The reward cannot exceed the observed ordinary stack or three items. */
    public static int bonusCount(int lootingLevel, int actualCount) {
        if (actualCount <= 0) return 0;
        int cap = 1 + (Math.clamp(lootingLevel, 1, 6) - 1) / 2;
        return Math.min(actualCount, cap);
    }
    /** Data-pack tag plus exact default components; never copy container/custom/unique components. */
    public static boolean ordinary(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ORDINARY) && stack.getCount() <= stack.getMaxStackSize()
            && stack.getMaxStackSize() > 1 && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }
    public static @Nullable Frame beginDamage(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        if (source.getEntity() instanceof ServerPlayer owner && source.getDirectEntity() == owner && owner.level() == level
            && victim.level() == level && owner.isAlive() && victim.isAlive()) {
            MeleeImpulseSkills.Attack attack = MeleeImpulseSkills.currentAttack(); ItemStack weapon = owner.getMainHandItem();
            OwnedTargetMarks.Mark mark = EmberHarvestSkills.mark(owner, victim, weapon);
            if (attack != null && !attack.ambiguous && attack.player == owner && attack.target == victim
                && attack.weapon == weapon && mark != null) {
                if (!attack.completions.isEmpty()) { attack.ambiguous = true; }
                else {
                    Evidence evidence = new Evidence(owner, victim, level, source, weapon, mark);
                    attack.completions.add(evidence); Frame frame = new Frame(previous, evidence);
                    frame.ambiguous = previous != null; CURRENT.set(frame); return frame;
                }
            }
        }
        if (previous == null) return null;
        Frame sentinel = new Frame(previous, null); sentinel.ambiguous = true; CURRENT.set(sentinel); return sentinel;
    }
    public static void endDamage(@Nullable Frame frame, boolean accepted) {
        if (frame == null) return;
        if (CURRENT.get() != frame) {
            CURRENT.remove(); if (frame.previous != null) frame.previous.ambiguous = true;
            if (frame.evidence != null) frame.evidence.ambiguous = true; return;
        }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
        Evidence evidence = frame.evidence;
        if (evidence != null) {
            evidence.accepted = accepted; evidence.completed = true; evidence.deaths = frame.deaths; evidence.lootCalls = frame.lootCalls;
            evidence.ambiguous |= frame.ambiguous || frame.inDeath || frame.loot != null;
        }
    }
    /** Called only after the enclosing hurtOrSimulate returned true and actual loss was observed. */
    static void confirmedHit(ConfirmedMelee.Hit hit) {
        MeleeImpulseSkills.Attack attack = MeleeImpulseSkills.currentAttack();
        if (attack == null || attack.ambiguous || !hit.eligibleBefore()) return;
        for (Runnable task : attack.completions) if (task instanceof Evidence evidence && evidence.owner == hit.player()
            && evidence.victim == hit.target() && evidence.weapon == hit.weapon() && evidence.source == hit.source()
            && hit.target().isDeadOrDying()) evidence.confirmed = true;
    }
    public static void afterDeath(LivingEntity victim, DamageSource source) {
        Frame frame = CURRENT.get();
        if (frame != null && frame.evidence != null && frame.evidence.victim == victim && frame.evidence.source == source) frame.deaths++;
    }
    public static @Nullable Frame beginDeath(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame frame = CURRENT.get(); if (frame == null) return null;
        if (frame.evidence == null || frame.evidence.victim != victim || frame.evidence.level != level
            || frame.evidence.source != source || frame.inDeath) { frame.ambiguous = true; return null; }
        frame.inDeath = true; return frame;
    }
    public static void endDeath(@Nullable Frame frame) {
        if (frame == null) return;
        if (CURRENT.get() != frame || !frame.inDeath) frame.ambiguous = true;
        frame.inDeath = false;
    }
    public static @Nullable EntityLootProvenance.Scope beginLoot(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame frame = CURRENT.get(); Evidence evidence = frame == null ? null : frame.evidence;
        if (evidence == null || !frame.inDeath || frame.ambiguous || evidence.victim != victim || evidence.level != level
            || evidence.source != source || EmberHarvestSkills.mark(evidence.owner, victim, evidence.weapon) != evidence.mark) return null;
        if (++frame.lootCalls != 1 || frame.loot != null) { frame.ambiguous = true; return null; }
        frame.loot = EntityLootProvenance.open(level, evidence.owner, victim, source); return frame.loot;
    }
    public static void endLoot(@Nullable EntityLootProvenance.Scope scope, boolean completed) {
        if (scope == null) return;
        Frame frame = CURRENT.get(); List<EntityLootProvenance.Verified> drops = EntityLootProvenance.close(scope, completed);
        if (frame == null || frame.loot != scope || frame.evidence == null) {
            if (frame != null) frame.ambiguous = true; return;
        }
        frame.loot = null; frame.evidence.drops = drops;
    }
    private static void settle(Evidence evidence) {
        ServerPlayer owner = evidence.owner; LivingEntity victim = evidence.victim; ServerLevel level = evidence.level;
        BlockPos origin = BlockPos.containing(evidence.origin);
        if (!evidence.completed || !evidence.accepted || !evidence.confirmed || evidence.ambiguous || evidence.deaths != 1
            || evidence.lootCalls != 1 || evidence.drops.isEmpty() || !owner.isAlive() || owner.isSpectator() || owner.level() != level
            || victim.level() != level || !victim.isDeadOrDying() || owner.distanceToSqr(victim) > 36.0D
            || EmberHarvestSkills.mark(owner, victim, evidence.weapon) != evidence.mark || !level.hasChunkAt(origin)
            || !level.isInWorldBounds(origin) || !level.mayInteract(owner, origin)) return;
        for (EntityLootProvenance.Verified drop : evidence.drops) {
            if (!ordinary(drop.stack()) || !ordinary(drop.snapshot())
                || !GrapnelDropTargets.unchanged(owner, drop.entity(), drop.stack(), drop.snapshot())) continue;
            int count = bonusCount(evidence.mark.pair().secondaryLevel(), drop.stack().getCount());
            if (count <= 0 || !EmberHarvestSkills.consume(owner, evidence.mark)) return;
            // Create a bounded new plain item only from the verified actual output; the original remains untouched.
            ItemStack bonus = new ItemStack(drop.stack().getItem(), count);
            ItemEntity reward = new ItemEntity(level, evidence.origin.x, evidence.origin.y + 0.15D, evidence.origin.z, bonus);
            reward.setDefaultPickUpDelay();
            if (level.addFreshEntity(reward) && reward.isAlive() && level.getEntity(reward.getUUID()) == reward)
                CompendiumDiscoveryTracker.recordDiscovery(owner, EmberHarvestSkills.FIRE, EmberHarvestSkills.LOOTING);
            return; // spend at most one mark, even if another mod rejects or moves the new item
        }
    }
    public static void clear() { CURRENT.remove(); }
}
