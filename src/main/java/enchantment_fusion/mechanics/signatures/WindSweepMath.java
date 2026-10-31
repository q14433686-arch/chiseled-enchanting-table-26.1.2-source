package enchantment_fusion.mechanics.signatures;

/** Bounded wind forces; never propagate non-finite modded attributes or velocity. */
public final class WindSweepMath {
    private WindSweepMath() {}
    public static double force(double strength, double resistance, double speedToward, boolean burst) {
        if (!Double.isFinite(strength) || !Double.isFinite(resistance) || !Double.isFinite(speedToward) || strength <= 0) return 0;
        double result = strength * (1.0D - Math.clamp(resistance, 0, 1));
        return burst ? result : Math.min(result, Math.max(0, 0.6D - speedToward));
    }
    public static boolean aimed(double distanceSquared, double lookLengthSquared, double dot) {
        return Double.isFinite(distanceSquared) && Double.isFinite(lookLengthSquared) && Double.isFinite(dot)
            && distanceSquared >= 0.01D && distanceSquared <= 25.0D && lookLengthSquared >= 1.0E-8D && dot >= 0.5D;
    }
}
