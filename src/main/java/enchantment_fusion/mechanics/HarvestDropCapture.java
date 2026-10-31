package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import enchantment_fusion.mechanics.signatures.FortuneBankSkills;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player, per-break provenance for newly spawned drops. Only entities created inside the
 * actual playerDestroy call are captured; nearby existing entities and ambiguous pickup targets
 * are never scanned. Nested breaks receive independent frames and transformed drops are claimed
 * once. Off-thread/late/custom drop production is intentionally left untouched.
 */
public final class HarvestDropCapture {
    private static final int MAX_ENTITIES = 256;
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();
    private static final ThreadLocal<Deque<FormulaProbe>> FORMULA_PROBES = new ThreadLocal<>();
    private static final int MAX_LOOT_OUTCOMES = 64;
    private static final class FormulaProbe {
        final int before;
        int calls, after;
        FormulaProbe(int before) { this.before = before; }
    }

    /** Identity and before/after count from one actual Fortune function, never a second loot roll. */
    public record FortuneOutcome(ItemStack output, ItemStack snapshot, int before, int after, boolean ambiguous) {}
    private static final class LootFrame {
        final ItemStack tool; final BlockState state; final boolean sneaking;
        final Map<ItemStack, FortuneOutcome> outcomes = new IdentityHashMap<>();
        boolean overflowed;
        LootFrame(ItemStack tool, BlockState state, boolean sneaking) { this.tool = tool; this.state = state; this.sneaking = sneaking; }
    }

    private static final class Frame {
        final ServerLevel level;
        final UUID player;
        final ServerPlayer breaker;
        final BlockPos pos;
        final List<ItemEntity> items = new ArrayList<>();
        final List<ExperienceOrb> orbs = new ArrayList<>();
        final Map<Entity, AABB> spawnBounds = new IdentityHashMap<>();
        final Set<ItemEntity> transformed = Collections.newSetFromMap(new IdentityHashMap<>());
        final Deque<LootFrame> lootFrames = new ArrayDeque<>();
        int collecting;
        boolean overflowed;
        boolean overflowWarned;
        boolean smeltOverflowWarned;
        Frame(ServerLevel level, ServerPlayer player, BlockPos pos) {
            this.level = level; this.player = player.getUUID(); this.breaker = player; this.pos = pos.immutable();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Frame frame;
        private boolean closed;
        private Scope(Frame frame) { this.frame = frame; }
        @Override public void close() {
            if (closed) return;
            Deque<Frame> frames = FRAMES.get();
            if (frames == null || frames.peekFirst() != frame) throw new IllegalStateException("Unbalanced harvest capture");
            closed = true;
            frames.removeFirst();
            if (frames.isEmpty()) FRAMES.remove();
        }
    }

    private HarvestDropCapture() {}

    public static Scope begin(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Frame frame = new Frame(level, player, pos);
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) { frames = new ArrayDeque<>(); FRAMES.set(frames); }
        frames.addFirst(frame);
        return new Scope(frame);
    }

    /** Legacy/synthetic capture scopes do not supply a verifiable loot tool. */
    public static void enterPlayerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
        enterPlayerDestroy(level, player, pos, ItemStack.EMPTY, level.getBlockState(pos));
    }

    /** The wrapper only enters this region for the target block's actual playerDestroy call. */
    public static void enterPlayerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack lootTool, BlockState state) {
        Frame frame = current(level, pos);
        if (frame != null && frame.player.equals(player.getUUID())) {
            frame.collecting++;
            frame.lootFrames.addFirst(new LootFrame(lootTool, state, player.isShiftKeyDown()));
        }
    }

    public static void leavePlayerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Frame frame = current(level, pos);
        if (frame != null && frame.player.equals(player.getUUID()) && frame.collecting > 0) {
            frame.collecting--;
            frame.lootFrames.pollFirst();
        }
    }

    /** Nested probes prevent unrelated loot calls from making an unsafe formula look safe. */
    public static boolean fortuneFormulaActive() {
        Deque<FormulaProbe> probes = FORMULA_PROBES.get();
        return probes != null && !probes.isEmpty();
    }
    public static void beginFortuneFormula(int before) {
        Deque<FormulaProbe> probes = FORMULA_PROBES.get();
        if (probes == null) { probes = new ArrayDeque<>(); FORMULA_PROBES.set(probes); }
        probes.addFirst(new FormulaProbe(before));
    }
    /** Called only by the two vanilla formulas whose level-zero baseline cannot add units. */
    public static void markSafeFortuneFormula(int before, int level, int after) {
        Deque<FormulaProbe> probes = FORMULA_PROBES.get();
        if (probes == null || probes.isEmpty()) return;
        FormulaProbe probe = probes.peekFirst();
        probe.calls++;
        if (before == probe.before && level > 0 && after >= before) probe.after = after; else probe.after = -1;
    }
    /** Returns the single safe formula's result, or -1 for anything ambiguous. */
    public static int endFortuneFormula() {
        Deque<FormulaProbe> probes = FORMULA_PROBES.get();
        if (probes == null || probes.isEmpty()) return -1;
        FormulaProbe probe = probes.removeFirst();
        if (probes.isEmpty()) FORMULA_PROBES.remove();
        return probe.calls == 1 ? probe.after : -1;
    }

    /** Avoid copying loot stacks when no matching server-side bank action is armed. */
    public static boolean canCaptureFortune(LootContext context) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty() || context == null) return false;
        Frame frame = frames.peekFirst(); LootFrame loot = frame.lootFrames.peekFirst();
        Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        return loot != null && origin != null && frame.pos.equals(BlockPos.containing(origin))
            && frame.collecting > 0 && frame.breaker.isShiftKeyDown() == loot.sneaking
            && context.getLevel() == frame.level && context.getOptionalParameter(LootContextParams.TOOL) == loot.tool
            && context.getOptionalParameter(LootContextParams.THIS_ENTITY) == frame.breaker
            && loot.state.equals(context.getOptionalParameter(LootContextParams.BLOCK_STATE))
            && FortuneBankSkills.observes(frame.breaker, loot.tool);
    }

    /** A subsequent untrusted loot function invalidates attribution even if it restores the same count. */
    public static void invalidateFortuneOutput(LootContext context, ItemStack stack) {
        if (stack == null || !canCaptureFortune(context)) return;
        Deque<Frame> frames = FRAMES.get();
        LootFrame loot = frames.peekFirst().lootFrames.peekFirst();
        FortuneOutcome previous = loot.outcomes.get(stack);
        if (previous != null && !previous.ambiguous())
            loot.outcomes.put(stack, new FortuneOutcome(previous.output(), previous.snapshot(), previous.before(), previous.after(), true));
    }

    /** Capture the before and after of the actual loot function inside this exact block/tool context. */
    public static void recordFortune(LootContext context, ItemStack output, ItemStack beforeSnapshot, int beforeCount) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty() || context == null || output == null || beforeSnapshot == null || !canCaptureFortune(context)) return;
        Frame frame = frames.peekFirst(); LootFrame loot = frame.lootFrames.peekFirst();
        if (loot == null || frame.collecting <= 0 || frame.breaker.isShiftKeyDown() != loot.sneaking || context.getLevel() != frame.level
            || context.getOptionalParameter(LootContextParams.TOOL) != loot.tool
            || context.getOptionalParameter(LootContextParams.THIS_ENTITY) != frame.breaker
            || !loot.state.equals(context.getOptionalParameter(LootContextParams.BLOCK_STATE))) return;
        Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null || !frame.pos.equals(BlockPos.containing(origin)) || beforeCount < 1 || output.isEmpty()
            || !ItemStack.isSameItemSameComponents(beforeSnapshot, output)) return;
        FortuneOutcome previous = loot.outcomes.get(output);
        if (previous != null) {
            loot.outcomes.put(output, new FortuneOutcome(output, output.copy(), beforeCount, output.getCount(), true));
        } else if (loot.outcomes.size() < MAX_LOOT_OUTCOMES) {
            loot.outcomes.put(output, new FortuneOutcome(output, output.copy(), beforeCount, output.getCount(), false));
        } else loot.overflowed = true;
    }

    /** A later copy, changed stack, overflow or multiple applications cannot prove provenance. */
    public static List<FortuneOutcome> fortuneOutcomes(ServerLevel level, BlockPos pos, ItemStack lootTool) {
        Frame frame = current(level, pos); LootFrame loot = frame == null ? null : frame.lootFrames.peekFirst();
        if (frame == null || frame.overflowed || loot == null || loot.tool != lootTool || loot.overflowed
            || frame.breaker.isShiftKeyDown() != loot.sneaking) return List.of();
        return List.copyOf(loot.outcomes.values());
    }

    /** Called only after ServerLevel.addFreshEntity has succeeded. */
    public static void record(ServerLevel level, Entity entity) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty()) return;
        Frame frame = frames.peekFirst();
        if (frame.level != level || frame.collecting <= 0 || entity == null || !entity.isAlive()
            || !new AABB(frame.pos).inflate(3.0D).intersects(entity.getBoundingBox())) return;
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) return;
        if (entity instanceof ItemEntity item && !belongsToFrame(frame, item)) return;
        if (frame.items.size() + frame.orbs.size() >= MAX_ENTITIES) {
            frame.overflowed = true;
            return;
        }
        if (entity instanceof ItemEntity item) frame.items.add(item);
        else frame.orbs.add((ExperienceOrb) entity);
        frame.spawnBounds.put(entity, entity.getBoundingBox());
    }

    public static List<ItemEntity> items(ServerLevel level, BlockPos pos, AABB area) {
        Frame frame = current(level, pos);
        if (frame == null) return List.of();
        warnOverflow(frame);
        return frame.items.stream().filter(e -> e.isAlive() && e.level() == level && belongsToFrame(frame, e)
            && area.intersects(frame.spawnBounds.get(e))).toList();
    }

    public static List<ExperienceOrb> orbs(ServerLevel level, BlockPos pos, AABB area) {
        Frame frame = current(level, pos);
        if (frame == null) return List.of();
        warnOverflow(frame);
        return frame.orbs.stream().filter(e -> e.isAlive() && e.level() == level
            && area.intersects(frame.spawnBounds.get(e))).toList();
    }

    /** Claim only when an actual transformation will be performed. */
    public static boolean claimTransform(ServerLevel level, BlockPos pos, ItemEntity item) {
        Frame frame = current(level, pos);
        return frame != null && item.isAlive() && belongsToFrame(frame, item)
            && frame.items.contains(item) && frame.transformed.add(item);
    }

    /** Undo a claim only when an intended synchronous transformation did not commit. */
    public static void releaseTransform(ServerLevel level, BlockPos pos, ItemEntity item) {
        Frame frame = current(level, pos);
        if (frame != null && frame.items.contains(item)) frame.transformed.remove(item);
    }

    private static boolean belongsToFrame(Frame frame, ItemEntity item) {
        DroppedItemOwnerAccess owner = (DroppedItemOwnerAccess) (Object) item;
        var thrower = owner.ef$throwerReference();
        UUID pickupTarget = owner.ef$pickupTarget();
        return ownerCompatible(frame.player, thrower == null ? null : thrower.getUUID(), pickupTarget);
    }

    private static void warnOverflow(Frame frame) {
        // Ordinary unenchanted mining should not produce a mod warning merely for a large loot table.
        if (frame.overflowed && !frame.overflowWarned) {
            frame.overflowWarned = true;
            frame.breaker.sendSystemMessage(Component.translatable("message.enchantment_fusion.harvest_limit"), true);
        }
    }

    /** Called when an output cannot fit safely in one entity; the input remains intact. */
    public static void warnSmeltOverflow(ServerLevel level) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty()) return;
        Frame frame = frames.peekFirst();
        if (frame.level == level && !frame.smeltOverflowWarned) {
            frame.smeltOverflowWarned = true;
            frame.breaker.sendSystemMessage(Component.translatable("message.enchantment_fusion.smelt_overflow"), true);
        }
    }

    /** Null metadata is acceptable only inside the already captured playerDestroy call. */
    public static boolean ownerCompatible(UUID player, UUID thrower, UUID pickupTarget) {
        return player != null && (thrower == null || player.equals(thrower))
            && (pickupTarget == null || player.equals(pickupTarget));
    }

    private static Frame current(ServerLevel level, BlockPos pos) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty()) return null;
        Frame frame = frames.peekFirst();
        return frame.level == level && frame.pos.equals(pos) ? frame : null;
    }
}
