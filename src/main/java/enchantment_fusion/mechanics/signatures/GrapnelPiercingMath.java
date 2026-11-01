package enchantment_fusion.mechanics.signatures;

/** Pure conservative geometry for a projectile continuing completely past an impact-time box. */
public final class GrapnelPiercingMath {
    public record Point(double x, double y, double z) {
        Point minus(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        double dot(Point other) { return x * other.x + y * other.y + z * other.z; }
        double lengthSquared() { return dot(this); }
        boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean finite() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
                && minX <= maxX && minY <= maxY && minZ <= maxZ;
        }
    }
    private GrapnelPiercingMath() {}
    private static double perpendicularSquared(Point delta, Point unit) {
        double along = delta.dot(unit);
        return Math.max(0.0D, delta.lengthSquared() - along * along);
    }
    public static boolean unit(Point direction) {
        return direction.finite() && Math.abs(direction.lengthSquared() - 1.0D) < 0.0001D;
    }
    /** Reject discontinuities, large teleports, reversal or movement away from the original ray. */
    public static boolean validStep(Point from, Point to, Point velocity, Point direction) {
        if (!from.finite() || !to.finite() || !velocity.finite() || !unit(direction)) return false;
        Point moved = to.minus(from); double projected = moved.dot(direction), length = Math.sqrt(moved.lengthSquared());
        if (projected < -0.0001D || length > Math.sqrt(velocity.lengthSquared()) + 0.15D) return false;
        double allowance = 0.08D + 0.025D * length;
        return perpendicularSquared(moved, direction) <= allowance * allowance;
    }
    /** On the impact step, the recorded collision location must really lie on the observed segment. */
    public static boolean containsImpact(Point from, Point to, Point impact, Point direction) {
        if (!from.finite() || !to.finite() || !impact.finite() || !unit(direction)) return false;
        Point hit = impact.minus(from), moved = to.minus(from);
        double along = hit.dot(direction), total = moved.dot(direction);
        return along >= -0.12D && along <= total + 0.12D && perpendicularSquared(hit, direction) <= 0.04D;
    }
    /** The endpoint must reach beyond the box's most distant support plane. */
    public static boolean passed(Box box, Point end, Point direction) {
        if (!box.finite() || !end.finite() || !unit(direction)) return false;
        double x = direction.x() >= 0 ? box.maxX() : box.minX();
        double y = direction.y() >= 0 ? box.maxY() : box.minY();
        double z = direction.z() >= 0 ? box.maxZ() : box.minZ();
        return end.dot(direction) > x * direction.x() + y * direction.y() + z * direction.z() + 0.01D;
    }
    public static boolean continuous(Point previous, Point next) {
        return previous.finite() && next.finite() && previous.minus(next).lengthSquared() <= 0.0004D;
    }
}
