package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Observes one real entity loot-table consumer without scanning the world or changing its output.
 * Only the same, unchanged stack which entered a successful addFreshEntity call is attributable.
 * Splitting, later functions, copies, nested entity-loot calls and ambiguous ownership conservatively fail.
 */
public final class EntityLootProvenance {
    private static final int MAX_OUTPUTS = 64;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    public record Verified(ItemEntity entity, ItemStack stack, ItemStack snapshot) {}
    private static final class Candidate {
        final ItemStack snapshot;
        boolean ambiguous, consumed;
        Candidate(ItemStack snapshot) { this.snapshot = snapshot; }
    }
    private static final class Pending {
        final ItemStack stack;
        final Candidate candidate;
        int seen;
        @Nullable ItemEntity item;
        Pending(ItemStack stack, Candidate candidate) { this.stack = stack; this.candidate = candidate; }
    }
    public static final class Scope {
        private final @Nullable Scope previous;
        private final ServerLevel level;
        private final ServerPlayer owner;
        private final LivingEntity victim;
        private final DamageSource source;
        private final Vec3 position;
        private final BlockPos origin;
        private final Map<ItemStack, Candidate> outputs = new IdentityHashMap<>();
        private final List<Verified> spawned = new ArrayList<>();
        private @Nullable Pending pending;
        private boolean ambiguous, overflow;
        private Scope(@Nullable Scope previous, ServerLevel level, ServerPlayer owner, LivingEntity victim, DamageSource source) {
            this.previous = previous; this.level = level; this.owner = owner; this.victim = victim; this.source = source;
            this.position = victim.position(); this.origin = victim.blockPosition().immutable();
            this.ambiguous = previous != null;
        }
        public BlockPos origin() { return origin; }
    }
    private EntityLootProvenance() {}

    public static Scope open(ServerLevel level, ServerPlayer owner, LivingEntity victim, DamageSource source) {
        Scope previous = CURRENT.get();
        if (previous != null) previous.ambiguous = true;
        Scope scope = new Scope(previous, level, owner, victim, source);
        CURRENT.set(scope); return scope;
    }
    public static boolean current(@Nullable Scope scope) { return scope != null && CURRENT.get() == scope; }
    public static boolean canCapture(@Nullable LootContext context) {
        Scope scope = CURRENT.get();
        return scope != null && context != null && context.getLevel() == scope.level
            && context.getOptionalParameter(LootContextParams.THIS_ENTITY) == scope.victim
            && context.getOptionalParameter(LootContextParams.ATTACKING_ENTITY) == scope.owner
            && context.getOptionalParameter(LootContextParams.DAMAGE_SOURCE) == scope.source;
    }
    /** A later unknown function invalidates attribution even if it restores the old count. */
    public static void invalidate(LootContext context, ItemStack stack) {
        if (stack == null || !canCapture(context)) return;
        Candidate candidate = CURRENT.get().outputs.get(stack);
        if (candidate != null) candidate.ambiguous = true;
    }
    /** Called only after a source-specific observer proves this actual function increased the stack. */
    public static void recordIncrease(LootContext context, ItemStack output, ItemStack beforeSnapshot, int before) {
        if (!canCapture(context) || output == null || beforeSnapshot == null || before < 1 || output.isEmpty()
            || output.getCount() <= before || !ItemStack.isSameItemSameComponents(beforeSnapshot, output)) return;
        Scope scope = CURRENT.get(); Candidate previous = scope.outputs.get(output);
        if (previous != null) { previous.ambiguous = true; return; }
        if (scope.outputs.size() >= MAX_OUTPUTS) { scope.overflow = true; return; }
        scope.outputs.put(output, new Candidate(output.copy()));
    }
    /** A proven, necessary pool condition owns its actual output even after entry-level functions. */
    public static void recordPoolResult(LootContext context, ItemStack output) {
        if (!canCapture(context) || output == null || output.isEmpty()) return;
        Scope scope = CURRENT.get(); Candidate previous = scope.outputs.get(output);
        if (previous != null && previous.consumed) { scope.ambiguous = true; return; }
        if (previous == null && scope.outputs.size() >= MAX_OUTPUTS) { scope.overflow = true; return; }
        scope.outputs.put(output, new Candidate(output.copy()));
    }
    /** Wrap the original consumer; call it once with exactly the original object. */
    public static void consume(Scope scope, ItemStack stack, Consumer<ItemStack> original) {
        if (!current(scope) || stack == null) { original.accept(stack); return; }
        Candidate candidate = scope.outputs.get(stack);
        if (candidate == null || candidate.ambiguous || !ItemStack.matches(stack, candidate.snapshot)) {
            original.accept(stack); return;
        }
        if (candidate.consumed || scope.pending != null) {
            scope.ambiguous = true; original.accept(stack); return;
        }
        candidate.consumed = true; Pending pending = new Pending(stack, candidate); scope.pending = pending;
        boolean completed = false;
        try { original.accept(stack); completed = true; }
        finally {
            scope.pending = null;
            if (!completed || !current(scope) || pending.seen != 1 || pending.item == null
                || pending.item.getItem() != stack || !ItemStack.matches(stack, candidate.snapshot)) {
                candidate.ambiguous = true;
            } else if (scope.spawned.size() >= MAX_OUTPUTS) scope.overflow = true;
            else scope.spawned.add(new Verified(pending.item, stack, candidate.snapshot.copy()));
        }
    }
    /** Observe only a successfully registered item created inside this exact loot consumer. */
    public static void added(ServerLevel level, ItemEntity item) {
        Scope scope = CURRENT.get(); Pending pending = scope == null ? null : scope.pending;
        if (pending == null || level != scope.level || item.getItem() != pending.stack) return;
        pending.seen++;
        DroppedItemOwnerAccess metadata = (DroppedItemOwnerAccess) (Object) item;
        var thrower = metadata.ef$throwerReference();
        if (pending.seen == 1 && item.isAlive() && item.level() == level && level.getEntity(item.getUUID()) == item
            && item.position().distanceToSqr(scope.position) <= 0.0625D
            && ItemStack.matches(pending.stack, pending.candidate.snapshot)
            && HarvestDropCapture.ownerCompatible(scope.owner.getUUID(), thrower == null ? null : thrower.getUUID(), metadata.ef$pickupTarget())) {
            pending.item = item;
        } else pending.item = null;
    }
    /** Restore an enclosing scope even when the vanilla loot callback throws. */
    public static List<Verified> close(@Nullable Scope scope, boolean completed) {
        if (scope == null) return List.of();
        if (!current(scope)) {
            CURRENT.remove(); if (scope.previous != null) scope.previous.ambiguous = true;
            return List.of();
        }
        if (scope.previous == null) CURRENT.remove(); else CURRENT.set(scope.previous);
        if (!completed || scope.ambiguous || scope.overflow || scope.pending != null) return List.of();
        List<Verified> verified = new ArrayList<>();
        for (Verified drop : scope.spawned) {
            Candidate candidate = scope.outputs.get(drop.stack());
            if (candidate != null && !candidate.ambiguous && candidate.consumed && drop.entity().isAlive()
                && drop.entity().level() == scope.level && scope.level.getEntity(drop.entity().getUUID()) == drop.entity()
                && drop.entity().getItem() == drop.stack() && ItemStack.matches(drop.stack(), drop.snapshot())) verified.add(drop);
        }
        return List.copyOf(verified);
    }
    public static void clear() { CURRENT.remove(); }
}
