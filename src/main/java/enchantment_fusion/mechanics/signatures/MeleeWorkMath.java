package enchantment_fusion.mechanics.signatures;

/** Bounded, side-effect-free arithmetic shared by the two melee/mining templates. */
public final class MeleeWorkMath {
    public static final int MAX_CUTS = 3;
    public static final long CUT_TICKS = 200, BOOST_TICKS = 200;
    private MeleeWorkMath() {}

    public static double speedMultiplier(int efficiencyLevel, int streak) {
        if (streak < MeleeWearMath.MIN_STREAK) return 1.0D;
        return 1.0D + 0.20D * Math.clamp(efficiencyLevel, 1, 10)
            + 0.10D * (Math.clamp(streak, MeleeWearMath.MIN_STREAK, MeleeWearMath.MAX_STREAK) - MeleeWearMath.MIN_STREAK);
    }

    public static int addCut(int current) { return (int) Math.min(MAX_CUTS, Math.max(0L, (long) current) + 1L); }

    public static float bladeDamage(int workLevel, int attackLevel, int cuts) {
        if (cuts <= 0) return 0.0F;
        return 1.25F + 0.25F * Math.clamp(workLevel, 1, 10) + 0.30F * Math.clamp(attackLevel, 1, 10)
            + 0.40F * Math.clamp(cuts, 1, MAX_CUTS);
    }
}
