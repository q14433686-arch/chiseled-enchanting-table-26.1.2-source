package enchantment_fusion.mechanics.signatures;

/** Pure bounded arithmetic for the 42.1 melee/durability templates. */
public final class MeleeWearMath {
    public static final int MIN_STREAK = 3, MAX_STREAK = 5, MAX_GUARD = 12;
    public static final int MIN_SHARDS = 2, MAX_SHARDS = 8, MAX_SPEND = 4;
    public static final long STREAK_TICKS = 80, GUARD_TICKS = 200, SHARD_TICKS = 600, ARMED_TICKS = 100;
    private MeleeWearMath() {}

    public static int nextStreak(int current, long last, long now) {
        long elapsed = now - last;
        int previous = now >= last && elapsed >= 0 && elapsed <= STREAK_TICKS ? Math.clamp(current, 0, MAX_STREAK) : 0;
        return Math.min(MAX_STREAK, previous + 1);
    }
    public static int guardFor(int streak, int unbreakingLevel) {
        if (streak < MIN_STREAK) return 0;
        return Math.min(MAX_GUARD, Math.clamp(streak, 0, MAX_STREAK) + Math.clamp(unbreakingLevel, 1, 10));
    }
    public static int preventedWear(int result, int guard) {
        return Math.min(Math.max(0, result), Math.max(0, guard));
    }
    public static int addShards(int current, int saved) {
        return (int) Math.min(MAX_SHARDS, Math.max(0L, (long) current) + Math.max(0L, (long) saved));
    }
    public static int spendShards(int current) {
        return current < MIN_SHARDS ? 0 : Math.min(MAX_SPEND, current);
    }
    public static float strikeBonus(int attackLevel, int unbreakingLevel, int spent) {
        if (spent < MIN_SHARDS) return 0.0F;
        return 0.75F + 0.30F * Math.clamp(attackLevel, 1, 10)
            + 0.25F * Math.clamp(unbreakingLevel, 1, 10) + 0.60F * Math.clamp(spent, MIN_SHARDS, MAX_SPEND);
    }
}
