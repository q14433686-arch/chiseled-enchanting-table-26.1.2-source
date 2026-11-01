package enchantment_fusion.mechanics.signatures;

/** Pure, saturating bounds for real-arrow streaks and observed wear credits. */
public final class RangedWearMath {
    public static final int MIN_HITS = 3, MAX_HITS = 5, MAX_GUARD = 12, MAX_CHARGES = 4;
    public static final long STREAK_TICKS = 200L, GUARD_TICKS = 400L, CHARGE_TICKS = 600L;
    private RangedWearMath() {}
    public static boolean expiredStreak(long last, long now) {
        long elapsed = now - last;
        return now < last || elapsed < 0 || elapsed > STREAK_TICKS;
    }
    public static int nextStreak(int current, long last, long now) {
        int prior = expiredStreak(last, now) ? 0 : Math.clamp(current, 0, MAX_HITS);
        return Math.min(MAX_HITS, prior + 1);
    }
    public static int guard(int streak, int rank) {
        return streak < MIN_HITS ? 0 : Math.min(MAX_GUARD, Math.clamp(streak, 0, MAX_HITS) + Math.clamp(rank, 1, 10));
    }
    public static int addCharges(int current, int saved) {
        return (int) Math.min(MAX_CHARGES, Math.max(0L, (long) current) + Math.max(0L, (long) saved));
    }
    public static float echoDamage(boolean power, int rank) {
        int level = Math.clamp(rank, 1, 10);
        return power ? 3.0F + 0.5F * level : 2.5F + 0.25F * level;
    }
}
