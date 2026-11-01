package enchantment_fusion.mechanics.signatures;

/** Bounded arithmetic for real-hit diversion and real-orb-repair charges. */
public final class MeleeMendingMath {
    public static final int MAX_FORGE = 8, MAX_SPEND = 4;
    public static final long MARK_TICKS = 160, FORGE_TICKS = 600, ARMED_TICKS = 100;
    private MeleeMendingMath() {}
    public static float fraction(int mendingLevel) { return Math.min(0.45F, 0.20F + 0.05F * Math.clamp(mendingLevel, 1, 10)); }
    public static float divertedDamage(float rawDamage, int mendingLevel) {
        return Float.isFinite(rawDamage) && rawDamage > 0 ? rawDamage * (1.0F - fraction(mendingLevel)) : rawDamage;
    }
    /** Approximation based on the actual accepted net loss, not a second damage/loot call. */
    public static int repair(float actualLoss, int mendingLevel, int missing) {
        if (!Float.isFinite(actualLoss) || actualLoss <= 0 || missing <= 0) return 0;
        double share = fraction(mendingLevel);
        return (int) Math.min(Math.min(8L, (long) missing), (long) Math.floor(actualLoss * share / (1.0 - share)));
    }
    public static int addForge(int current, int repaired) {
        return (int) Math.min(MAX_FORGE, Math.max(0L, (long) current) + Math.max(0L, (long) repaired));
    }
    public static int spend(int current) { return Math.clamp(current, 0, MAX_SPEND); }
    public static float bladeDamage(int attackLevel, int mendingLevel, int spent) {
        if (spent <= 0) return 0;
        return 1.25F + 0.30F * Math.clamp(attackLevel, 1, 10) + 0.25F * Math.clamp(mendingLevel, 1, 10)
            + 0.35F * Math.clamp(spent, 1, MAX_SPEND);
    }
}
