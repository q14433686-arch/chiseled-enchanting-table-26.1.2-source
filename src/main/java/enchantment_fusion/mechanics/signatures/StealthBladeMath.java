package enchantment_fusion.mechanics.signatures;

/** Pure movement bounds and scaling shared by the six melee/sneak template directions. */
public final class StealthBladeMath {
    public static final double READY_DISTANCE = 2.0D;
    private StealthBladeMath() {}

    /** A server-observed crouch step, not turning in place or a long teleport. */
    public static double step(double dx, double dy, double dz) {
        double horizontal = dx * dx + dz * dz, total = horizontal + dy * dy;
        if (!Double.isFinite(total) || horizontal < 0.02D * 0.02D || total > 16.0D) return 0;
        return Math.sqrt(horizontal);
    }

    /** A moving player's segment comes close to the stationary imprint at its own height. */
    public static boolean crosses(double ax, double ay, double az, double bx, double by, double bz,
                                  double mx, double my, double mz) {
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)
            || !Double.isFinite(bx) || !Double.isFinite(by) || !Double.isFinite(bz)
            || !Double.isFinite(mx) || !Double.isFinite(my) || !Double.isFinite(mz)
            || step(bx - ax, by - ay, bz - az) == 0) return false;
        double dx = bx - ax, dz = bz - az, denominator = dx * dx + dz * dz;
        double t = Math.clamp(((mx - ax) * dx + (mz - az) * dz) / denominator, 0.0D, 1.0D);
        double x = ax + dx * t - mx, z = az + dz * t - mz, y = ay + (by - ay) * t;
        return x * x + z * z <= 1.15D * 1.15D && Math.abs(y - my) <= 1.5D;
    }

    public static float damage(int attackLevel, int sneakLevel) {
        return 2.0F + 0.35F * Math.clamp(attackLevel, 1, 10) + 0.30F * Math.clamp(sneakLevel, 1, 10);
    }
    public static double range(int sneakLevel) { return 5.0D + 0.3D * (Math.clamp(sneakLevel, 1, 10) - 1); }
}
