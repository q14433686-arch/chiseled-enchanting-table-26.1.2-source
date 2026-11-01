package enchantment_fusion.mechanics.signatures;

/** Target lock and short-range original-arrow recovery never change unrelated arrows. */
public final class RangedSilkMath {
    public static final long MARK_TICKS = 160L, ARROW_TICKS = 200L;
    public static final double RECOVERY_RANGE = 16.0D;
    private RangedSilkMath() {}
    public static float damage(int secondary) { return .5F + .3F * Math.clamp(secondary, 1, 6); }
    public static float knockback(int secondary) { return .25F + .2F * Math.clamp(secondary, 1, 6); }
    public static int fireBonusTicks(int secondary) { return 30 + 10 * Math.clamp(secondary, 1, 6); }
    public static boolean inRange(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= RECOVERY_RANGE * RECOVERY_RANGE;
    }
}
