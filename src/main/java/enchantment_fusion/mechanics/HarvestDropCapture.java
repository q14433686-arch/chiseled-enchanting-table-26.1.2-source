package enchantment_fusion.mechanics;

import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
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
import net.minecraft.world.phys.AABB;

/**
 * Per-player, per-break provenance for newly spawned drops. Only entities created inside the
 * actual playerDestroy call are captured; nearby existing entities and ambiguous pickup targets
 * are never scanned. Nested breaks receive independent frames and transformed drops are claimed
 * once. Off-thread/late/custom drop production is intentionally left untouched.
 */
public final class HarvestDropCapture {
    private static final int MAX_ENTITIES = 256;
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();

    private static final class Frame {
        final ServerLevel level;
        final UUID player;
        final ServerPlayer breaker;
        final BlockPos pos;
        final List<ItemEntity> items = new ArrayList<>();
        final List<ExperienceOrb> orbs = new ArrayList<>();
        final Map<Entity, AABB> spawnBounds = new IdentityHashMap<>();
        final Set<ItemEntity> transformed = Collections.newSetFromMap(new IdentityHashMap<>());
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

    /** The wrapper only enters this region for the target block's actual playerDestroy call. */
    public static void enterPlayerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Frame frame = current(level, pos);
        if (frame != null && frame.player.equals(player.getUUID())) frame.collecting++;
    }

    public static void leavePlayerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Frame frame = current(level, pos);
        if (frame != null && frame.player.equals(player.getUUID()) && frame.collecting > 0) frame.collecting--;
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
            frame.breaker.displayClientMessage(Component.translatable("message.enchantment_fusion.harvest_limit"), true);
        }
    }

    /** Called when an output cannot fit safely in one entity; the input remains intact. */
    public static void warnSmeltOverflow(ServerLevel level) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty()) return;
        Frame frame = frames.peekFirst();
        if (frame.level == level && !frame.smeltOverflowWarned) {
            frame.smeltOverflowWarned = true;
            frame.breaker.displayClientMessage(Component.translatable("message.enchantment_fusion.smelt_overflow"), true);
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
