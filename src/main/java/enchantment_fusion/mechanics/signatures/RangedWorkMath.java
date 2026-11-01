package enchantment_fusion.mechanics.signatures;

/** Saturating arithmetic shared by the six shooting/efficiency directions. */
public final class RangedWorkMath {
    public static final int MAX_EDGES = 3, MAX_VICTIMS = 8;
    public static final long EDGE_TICKS = 200L, PREPARED_TICKS = 200L;
    public static final double BLADE_RANGE = 32.0D, BLADE_WIDTH = 0.85D;
    private RangedWorkMath() {}
    public static boolean expiredStreak(long first, long now) {
        long elapsed = now - first;
        return now < first || elapsed < 0 || elapsed > RangedWearMath.STREAK_TICKS;
    }
    public static int nextStreak(int current, long first, long last, long now) {
        return RangedWearMath.nextStreak(expiredStreak(first, now) ? 0 : current, last, now);
    }
    public static int addEdge(int edges) { return (int) Math.min(MAX_EDGES, Math.max(0L, (long) edges) + 1L); }
    public static double speed(int efficiency, int streak) { return MeleeWorkMath.speedMultiplier(efficiency, streak); }
    public static float bladeDamage(int efficiency, int shooting, int edges, boolean power) {
        if (edges <= 0) return 0.0F;
        return 1.25F + 0.25F * Math.clamp(efficiency, 1, 10) + (power ? 0.5F : 0.25F) * Math.clamp(shooting, 1, 10)
            + 0.4F * Math.clamp(edges, 1, MAX_EDGES);
    }
}
