package chiseled_enchanting_table.mechanics.resonance;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Player-applied freeze on a shelf template book («纂刻的书» page, 2026-10-24 feature).
 *
 * <p>The lock lives in the book's own {@code CUSTOM_DATA} so it travels with the book
 * (a broken shelf drops the book still locked) and needs no extra persisted state.
 * While effectively locked a book cannot be removed from a chiseled bookshelf by any
 * channel (player hand, hopper: see {@code LockedTomeGuardMixin}) and is invisible to
 * every enchanting path ({@link MasterTomeWear} candidate collection skips it, which
 * also removes it from the live charge snapshot and ritual binding).</p>
 *
 * <p>Timed modes expire <em>lazily</em>: the world-regeneration settlement clears the
 * lock the moment its charge target is reached, and {@link #isEffectivelyLocked}
 * additionally treats a timed lock on a full book as already expired so no consumer
 * can observe a stale lock even if the book refilled through a path that did not
 * clear it. Locking itself is free; early unlock is priced in the screen handler
 * (1 XP level + 1 Cleansing Grit, locker only) — this class only stores state and
 * answers pure questions about it.</p>
 */
public final class TomeLock {
    public static final String NBT_LOCK_MODE_KEY = "cet_lock_mode";
    public static final String NBT_LOCK_OWNER_KEY = "cet_lock_owner";

    public enum Mode {
        NONE(0),
        UNTIL_NEXT_CHARGE(1),
        UNTIL_FULL(2),
        PERMANENT(3);

        private final int id;

        Mode(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Mode fromId(int id) {
            return switch (id) {
                case 1 -> UNTIL_NEXT_CHARGE;
                case 2 -> UNTIL_FULL;
                case 3 -> PERMANENT;
                default -> NONE;
            };
        }
    }

    private TomeLock() {}

    // ------------------------------------------------------------------
    // Pure rules (headless-testable)
    // ------------------------------------------------------------------

    /** Does a lock of {@code mode} expire when wear moves {@code before -> after}? */
    public static boolean shouldExpire(Mode mode, int wearBefore, int wearAfter) {
        if (mode == null) {
            return false;
        }
        return switch (mode) {
            case UNTIL_NEXT_CHARGE -> wearAfter < wearBefore || wearAfter <= 0;
            case UNTIL_FULL -> wearAfter <= 0;
            case NONE, PERMANENT -> false;
        };
    }

    /** A timed lock on a full book has nothing left to wait for: already expired. */
    public static boolean isEffectivelyLocked(Mode mode, int wearUsed) {
        if (mode == null || mode == Mode.NONE) {
            return false;
        }
        return mode == Mode.PERMANENT || wearUsed > 0;
    }

    /** Ticks until the next +1 charge; {@code Integer.MAX_VALUE} when regen is off. */
    public static long ticksToNextCharge(int intervalTicks, long progressTicks) {
        if (intervalTicks <= 0 || intervalTicks == Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) intervalTicks - Math.max(0L, progressTicks));
    }

    /** Ticks until every worn charge is restored at a constant interval. */
    public static long ticksToFullCharge(int wearUsed, int intervalTicks, long progressTicks) {
        if (wearUsed <= 0) {
            return 0L;
        }
        long next = ticksToNextCharge(intervalTicks, progressTicks);
        if (next == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return next + (long) (wearUsed - 1) * (long) intervalTicks;
    }

    // ------------------------------------------------------------------
    // Stack state
    // ------------------------------------------------------------------

    public static Mode getMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Mode.NONE;
        }
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Mode.fromId(root.getIntOr(NBT_LOCK_MODE_KEY, 0));
    }

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String raw = root.getStringOr(NBT_LOCK_OWNER_KEY, "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isEffectivelyLocked(ItemStack stack) {
        return isEffectivelyLocked(getMode(stack), MasterTomeWear.getWearUsed(stack));
    }

    public static boolean isLockedBy(ItemStack stack, @Nullable UUID player) {
        UUID owner = getOwner(stack);
        return owner != null && owner.equals(player);
    }

    public static void setLock(ItemStack stack, Mode mode, @Nullable UUID owner) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (mode == null || mode == Mode.NONE) {
            clearLock(stack);
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(NBT_LOCK_MODE_KEY, mode.id());
        if (owner != null) {
            tag.putString(NBT_LOCK_OWNER_KEY, owner.toString());
        } else {
            tag.remove(NBT_LOCK_OWNER_KEY);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearLock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(NBT_LOCK_MODE_KEY) && !tag.contains(NBT_LOCK_OWNER_KEY)) {
            return;
        }
        tag.remove(NBT_LOCK_MODE_KEY);
        tag.remove(NBT_LOCK_OWNER_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
