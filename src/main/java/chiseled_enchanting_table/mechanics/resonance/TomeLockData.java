package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.utils.CetCustomData;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item-data layer for player-owned Master Tome locks.
 *
 * <p>The lock lives on the book stack itself ({@code CUSTOM_DATA}), so it survives shelf
 * movement attempts, world reloads and stack copies without any new saved-data schema.
 * Timed locks are expressed in the same unit as regeneration: remaining <em>wear</em>.
 * A {@code NEXT_CHARGE} lock created at wear {@code n} targets {@code n - 1}; a
 * {@code FULL_CHARGE} lock targets {@code 0}; a {@code PERMANENT} lock has no target.
 * Once the current wear reaches the target, the lock is expired and no longer blocks
 * anything; server code that observes the stack clears the stale keys (and notifies
 * the owner when regeneration caused the expiry).</p>
 */
public final class TomeLockData {
    public static final String NBT_LOCK_OWNER_KEY = "cet_lock_owner";
    public static final String NBT_LOCK_OWNER_NAME_KEY = "cet_lock_owner_name";
    public static final String NBT_LOCK_MODE_KEY = "cet_lock_mode";
    public static final String NBT_LOCK_TARGET_WEAR_KEY = "cet_lock_target_wear";

    private TomeLockData() {}

    public enum Mode {
        NEXT_CHARGE("next_charge"),
        FULL_CHARGE("full_charge"),
        PERMANENT("permanent");

        private final String token;

        Mode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public boolean timed() {
            return this != PERMANENT;
        }

        public static Mode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return PERMANENT;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "next", "next_charge", "+1", "plus_one" -> NEXT_CHARGE;
                case "full", "full_charge", "max" -> FULL_CHARGE;
                default -> PERMANENT;
            };
        }
    }

    /** Immutable view of one lock; {@code targetWear} is {@code -1} for permanent locks. */
    public record Lock(UUID owner, String ownerName, Mode mode, int targetWear) {
        public Lock {
            ownerName = ownerName == null ? "" : ownerName;
            mode = mode == null ? Mode.PERMANENT : mode;
            targetWear = mode.timed() ? Math.max(0, targetWear) : -1;
        }

        public boolean ownedBy(@Nullable UUID playerId) {
            return playerId != null && playerId.equals(owner);
        }

        public boolean expiredAt(int currentWear) {
            return isExpired(mode, targetWear, currentWear);
        }
    }

    // ------------------------------------------------------------------
    // Pure lock math (headless-testable without ItemStack)
    // ------------------------------------------------------------------

    /** Wear at which a lock created at {@code currentWear} releases. */
    public static int targetWearFor(Mode mode, int currentWear) {
        int wear = Math.max(0, currentWear);
        Mode safe = mode == null ? Mode.PERMANENT : mode;
        return switch (safe) {
            case NEXT_CHARGE -> Math.max(0, wear - 1);
            case FULL_CHARGE -> 0;
            case PERMANENT -> -1;
        };
    }

    public static boolean isExpired(Mode mode, int targetWear, int currentWear) {
        Mode safe = mode == null ? Mode.PERMANENT : mode;
        return safe.timed() && Math.max(0, currentWear) <= Math.max(0, targetWear);
    }

    /** Remaining ticks until the next +1 charge, or {@code -1} when not charging. */
    public static int ticksToNextCharge(int wearUsed, long progressTicks, int intervalTicks) {
        if (wearUsed <= 0 || intervalTicks <= 0 || intervalTicks == Integer.MAX_VALUE) {
            return -1;
        }
        long progress = Math.max(0L, Math.min((long) intervalTicks - 1L, progressTicks));
        long remaining = (long) intervalTicks - progress;
        return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    /** Remaining ticks until wear reaches zero, or {@code -1} when not charging. */
    public static int ticksToFullCharge(int wearUsed, long progressTicks, int intervalTicks) {
        int next = ticksToNextCharge(wearUsed, progressTicks, intervalTicks);
        if (next < 0) {
            return -1;
        }
        long full = (long) next + (long) Math.max(0, wearUsed - 1) * (long) intervalTicks;
        return full > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) full;
    }

    // ------------------------------------------------------------------
    // CUSTOM_DATA accessors
    // ------------------------------------------------------------------

    @Nullable
    public static Lock getLock(ItemStack stack) {
        CompoundTag tag = tagOf(stack);
        if (tag == null) {
            return null;
        }
        String ownerRaw = tag.getStringOr(NBT_LOCK_OWNER_KEY, "");
        if (ownerRaw.isBlank()) {
            return null;
        }
        UUID owner;
        try {
            owner = UUID.fromString(ownerRaw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Mode mode = Mode.fromToken(tag.getStringOr(NBT_LOCK_MODE_KEY, Mode.PERMANENT.token()));
        int target = mode.timed() ? Math.max(0, tag.getIntOr(NBT_LOCK_TARGET_WEAR_KEY, 0)) : -1;
        return new Lock(owner, tag.getStringOr(NBT_LOCK_OWNER_NAME_KEY, ""), mode, target);
    }

    public static boolean hasLock(ItemStack stack) {
        return getLock(stack) != null;
    }

    /** True while a non-expired lock is present. Expired timed locks never block. */
    public static boolean isEffectivelyLocked(ItemStack stack) {
        Lock lock = getLock(stack);
        return lock != null && !lock.expiredAt(TomeWearData.getWearUsed(stack));
    }


    /**
     * Applies a lock. Returns {@code false} for timed modes whose target has already
     * been reached (notably a full-durability book), leaving the stack untouched.
     */
    public static boolean lock(ItemStack stack, UUID owner, String ownerName, Mode mode) {
        if (stack == null || stack.isEmpty() || owner == null) {
            return false;
        }
        Mode safeMode = mode == null ? Mode.PERMANENT : mode;
        int wear = TomeWearData.getWearUsed(stack);
        int target = targetWearFor(safeMode, wear);
        if (safeMode.timed() && isExpired(safeMode, target, wear)) {
            return false;
        }
        CetCustomData.update(stack, tag -> {
            tag.putString(NBT_LOCK_OWNER_KEY, owner.toString());
            tag.putString(NBT_LOCK_OWNER_NAME_KEY, ownerName == null ? "" : ownerName);
            tag.putString(NBT_LOCK_MODE_KEY, safeMode.token());
            if (safeMode.timed()) {
                tag.putInt(NBT_LOCK_TARGET_WEAR_KEY, target);
            } else {
                tag.remove(NBT_LOCK_TARGET_WEAR_KEY);
            }
        });
        return true;
    }

    /** Removes every lock key; returns whether a lock record was present. */
    public static boolean unlock(ItemStack stack) {
        CompoundTag probe = CetCustomData.read(stack).orElse(null);
        if (probe == null) {
            return false;
        }
        boolean had = probe.contains(NBT_LOCK_OWNER_KEY) || probe.contains(NBT_LOCK_MODE_KEY);
        if (!had) {
            return false;
        }
        CetCustomData.update(stack, tag -> {
            tag.remove(NBT_LOCK_OWNER_KEY);
            tag.remove(NBT_LOCK_OWNER_NAME_KEY);
            tag.remove(NBT_LOCK_MODE_KEY);
            tag.remove(NBT_LOCK_TARGET_WEAR_KEY);
        });
        return true;
    }

    /**
     * Clears an expired timed lock in place. Returns the expired lock so the caller can
     * notify its owner exactly once (the keys are gone afterwards), or {@code null}.
     */
    @Nullable
    public static Lock clearExpired(ItemStack stack) {
        Lock lock = getLock(stack);
        if (lock == null || !lock.expiredAt(TomeWearData.getWearUsed(stack))) {
            return null;
        }
        unlock(stack);
        return lock;
    }

    @Nullable
    private static CompoundTag tagOf(ItemStack stack) {
        return CetCustomData.read(stack).orElse(null);
    }
}
