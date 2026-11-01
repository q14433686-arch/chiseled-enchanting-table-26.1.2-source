package enchantment_fusion.mechanics.signatures;

/** Bounded virtual screen dimensions and actual-mitigation charge. */
public final class RangedGuardMath {
    private RangedGuardMath() {}
    public static double radius(int level) { return Math.min(3.5D, 2.0D + .25D * Math.clamp(level, 1, 6)); }
    public static int blocks(int level) { return Math.min(6, 2 + Math.clamp(level, 1, 6)); }
    public static float charge(float previous, float prevented) {
        if (!Float.isFinite(previous) || previous < 0) previous = 0;
        if (!Float.isFinite(prevented) || prevented <= 0) return Math.min(12.0F, previous);
        return Math.min(12.0F, previous + prevented);
    }
}
