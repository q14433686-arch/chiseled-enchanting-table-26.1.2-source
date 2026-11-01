package enchantment_fusion.mechanics.signatures;

import net.minecraft.world.phys.Vec3;

/** Bounded line-segment geometry and swim accumulation, without predicted teleports. */
public final class RangedTideMath {
    public static final double CHARGE_DISTANCE = 3.0D, RIDE_RADIUS = 1.1D;
    private RangedTideMath() {}
    private static double clamp(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }
    public static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    public static Vec3 closest(Vec3 start, Vec3 end, Vec3 point) {
        if (!finite(start) || !finite(end) || !finite(point)) return start;
        Vec3 delta = end.subtract(start); double length = delta.lengthSqr();
        return length < 1.0E-10D ? start : start.add(delta.scale(clamp(point.subtract(start).dot(delta) / length)));
    }
    /** Exact closest distance of two finite segments, including parallel and zero-length cases. */
    public static double distanceSquared(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) {
        if (!finite(a0) || !finite(a1) || !finite(b0) || !finite(b1)) return Double.POSITIVE_INFINITY;
        Vec3 u = a1.subtract(a0), v = b1.subtract(b0), w = a0.subtract(b0);
        double a = u.dot(u), b = u.dot(v), c = v.dot(v), d = u.dot(w), e = v.dot(w), s, t;
        if (a < 1.0E-10D && c < 1.0E-10D) return a0.distanceToSqr(b0);
        if (a < 1.0E-10D) { s = 0.0D; t = clamp(e / c); }
        else if (c < 1.0E-10D) { t = 0.0D; s = clamp(-d / a); }
        else {
            double denominator = a * c - b * b;
            s = denominator > 1.0E-10D ? clamp((b * e - c * d) / denominator) : 0.0D;
            t = (b * s + e) / c;
            if (t < 0.0D) { t = 0.0D; s = clamp(-d / a); }
            else if (t > 1.0D) { t = 1.0D; s = clamp((b - d) / a); }
        }
        return a0.add(u.scale(s)).distanceToSqr(b0.add(v.scale(t)));
    }
    public static double addDistance(double previous, double moved) {
        if (!Double.isFinite(previous) || previous < 0) previous = 0;
        if (!Double.isFinite(moved) || moved <= 0) return Math.min(CHARGE_DISTANCE, previous);
        return Math.min(CHARGE_DISTANCE, previous + Math.min(2.0D, moved));
    }
}
