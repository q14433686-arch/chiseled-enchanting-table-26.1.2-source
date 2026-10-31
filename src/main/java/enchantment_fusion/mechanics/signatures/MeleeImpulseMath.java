package enchantment_fusion.mechanics.signatures;

/** Conservative motion and swept-box arithmetic; never substitutes a nearby scan for contact. */
public final class MeleeImpulseMath {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean valid() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
                && minX < maxX && minY < maxY && minZ < maxZ;
        }
        boolean overlaps(Box other) {
            return maxX > other.minX && minX < other.maxX && maxY > other.minY && minY < other.maxY
                && maxZ > other.minZ && minZ < other.maxZ;
        }
    }
    private MeleeImpulseMath() {}
    public static boolean trackedMotion(double dx, double dz, double directionX, double directionZ) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(directionX) || !Double.isFinite(directionZ)) return false;
        double speedSquared = dx * dx + dz * dz;
        return speedSquared >= 0.000625D && speedSquared <= 4.0D && dx * directionX + dz * directionZ > 0.01D;
    }
    /** Entry fraction in [0,1]; +infinity means unproven/no contact, including initial overlap. */
    public static double contact(Box moving, double dx, double dy, double dz, Box target) {
        if (moving == null || target == null || !moving.valid() || !target.valid() || moving.overlaps(target)
            || !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return Double.POSITIVE_INFINITY;
        double[] interval = {0.0D, 1.0D};
        if (!axis(moving.minX, moving.maxX, target.minX, target.maxX, dx, interval)
            || !axis(moving.minY, moving.maxY, target.minY, target.maxY, dy, interval)
            || !axis(moving.minZ, moving.maxZ, target.minZ, target.maxZ, dz, interval)) return Double.POSITIVE_INFINITY;
        return interval[1] > 1.0E-9D && interval[0] <= interval[1] && interval[0] >= 0 && interval[0] <= 1
            ? interval[0] : Double.POSITIVE_INFINITY;
    }
    private static boolean axis(double min, double max, double otherMin, double otherMax, double delta, double[] interval) {
        if (Math.abs(delta) < 1.0E-9D) return max > otherMin && min < otherMax;
        double a = (otherMin - max) / delta, b = (otherMax - min) / delta;
        if (a > b) { double swap = a; a = b; b = swap; }
        interval[0] = Math.max(interval[0], a); interval[1] = Math.min(interval[1], b);
        return interval[0] <= interval[1];
    }
    public static float transferStrength(double horizontalDistance, int knockbackLevel) {
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0.025D) return 0.0F;
        return (float) Math.min(1.25D, horizontalDistance * (1.2D + 0.1D * Math.clamp(knockbackLevel, 1, 10)));
    }
    public static float bladeDamage(int attackLevel, int knockbackLevel) {
        return 1.25F + 0.35F * Math.clamp(attackLevel, 1, 10) + 0.30F * Math.clamp(knockbackLevel, 1, 10);
    }
}
