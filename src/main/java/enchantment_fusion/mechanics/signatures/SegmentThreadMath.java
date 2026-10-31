package enchantment_fusion.mechanics.signatures;

/** Bounded horizontal movement and swept-thread geometry, independent of game state. */
public final class SegmentThreadMath {
    private SegmentThreadMath() {}

    public static boolean trips(double dx, double dy, double dz) {
        double distance = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(distance) && distance <= 16.0D
            && (dx * dx + dz * dz >= 0.08D * 0.08D || dy >= 0.12D);
    }

    /** Only rotation caused by the player's own bounded movement can sweep the tether. */
    public static boolean circles(double ax, double az, double oldX, double oldZ, double x, double z) {
        double vx = oldX - ax, vz = oldZ - az, wx = x - ax, wz = z - az;
        double a = vx * vx + vz * vz, b = wx * wx + wz * wz;
        double dx = x - oldX, dz = z - oldZ, step = dx * dx + dz * dz;
        double cross = vx * wz - vz * wx;
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(step) && Double.isFinite(cross)
            && a >= 0.25D && b >= 0.25D && step >= 0.04D * 0.04D && step <= 16.0D
            && cross * cross >= 0.0004D * a * b;
    }

    /** Point inside the swept triangle, or close enough to an edge for the victim's width. */
    public static boolean swept(double ax, double az, double bx, double bz, double cx, double cz,
                                double px, double pz, double radius) {
        if (!Double.isFinite(ax) || !Double.isFinite(az) || !Double.isFinite(bx) || !Double.isFinite(bz)
            || !Double.isFinite(cx) || !Double.isFinite(cz) || !Double.isFinite(px) || !Double.isFinite(pz)
            || !Double.isFinite(radius) || radius < 0 || radius > 2.5D) return false;
        double area = cross(ax, az, bx, bz, cx, cz);
        if (!Double.isFinite(area) || Math.abs(area) < 1.0E-8D) return false;
        double u = cross(ax, az, bx, bz, px, pz), v = cross(bx, bz, cx, cz, px, pz), w = cross(cx, cz, ax, az, px, pz);
        if ((u >= 0 && v >= 0 && w >= 0) || (u <= 0 && v <= 0 && w <= 0)) return true;
        double r2 = radius * radius;
        return distanceToSegmentSq(px, pz, ax, az, bx, bz) <= r2
            || distanceToSegmentSq(px, pz, bx, bz, cx, cz) <= r2
            || distanceToSegmentSq(px, pz, cx, cz, ax, az) <= r2;
    }

    /** Height on the triangle, or its nearest edge when the victim's width reaches outside it. */
    public static double sweptHeight(double ax, double ay, double az, double bx, double by, double bz,
                                     double cx, double cy, double cz, double px, double pz) {
        double area = cross(ax, az, bx, bz, cx, cz);
        if (!Double.isFinite(area) || Math.abs(area) < 1.0E-8D || !Double.isFinite(ay)
            || !Double.isFinite(by) || !Double.isFinite(cy)) return Double.NaN;
        double a = cross(bx, bz, cx, cz, px, pz) / area;
        double b = cross(cx, cz, ax, az, px, pz) / area;
        double c = cross(ax, az, bx, bz, px, pz) / area;
        if (a >= 0 && b >= 0 && c >= 0) return a * ay + b * by + c * cy;
        double ab = distanceToSegmentSq(px, pz, ax, az, bx, bz);
        double bc = distanceToSegmentSq(px, pz, bx, bz, cx, cz);
        double ca = distanceToSegmentSq(px, pz, cx, cz, ax, az);
        if (ab <= bc && ab <= ca) return edgeHeight(px, pz, ax, ay, az, bx, by, bz);
        if (bc <= ca) return edgeHeight(px, pz, bx, by, bz, cx, cy, cz);
        return edgeHeight(px, pz, cx, cy, cz, ax, ay, az);
    }
    private static double edgeHeight(double px, double pz, double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dz = bz - az, denominator = dx * dx + dz * dz;
        if (denominator < 1.0E-12D) return ay;
        double t = Math.clamp(((px - ax) * dx + (pz - az) * dz) / denominator, 0.0D, 1.0D);
        return ay + t * (by - ay);
    }

    public static float openingBonus(int sharpness, int bane) {
        return 2.0F + 0.35F * Math.clamp(sharpness, 1, 10) + 0.45F * Math.clamp(bane, 1, 10);
    }
    public static float threadDamage(int bane, int sharpness) {
        return 1.5F + 0.35F * Math.clamp(bane, 1, 10) + 0.25F * Math.clamp(sharpness, 1, 10);
    }
    private static double cross(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }
    private static double distanceToSegmentSq(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, denominator = dx * dx + dz * dz;
        if (denominator < 1.0E-12D) return (px - ax) * (px - ax) + (pz - az) * (pz - az);
        double t = Math.clamp(((px - ax) * dx + (pz - az) * dz) / denominator, 0.0D, 1.0D);
        double x = px - (ax + t * dx), z = pz - (az + t * dz);
        return x * x + z * z;
    }
}
