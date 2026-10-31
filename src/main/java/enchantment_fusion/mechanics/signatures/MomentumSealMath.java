package enchantment_fusion.mechanics.signatures;

import net.minecraft.world.phys.Vec3;

/** Horizontal force is saved without suppressing gravity or bypassing collision. */
public final class MomentumSealMath {
    private MomentumSealMath() {}
    public static Vec3 horizontal(Vec3 vector) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.z)) return Vec3.ZERO;
        return new Vec3(vector.x, 0.0D, vector.z);
    }
    public static double speed(Vec3 vector) {
        return Math.min(8.0D, horizontal(vector).length());
    }
    public static Vec3 direction(Vec3 vector) {
        Vec3 horizontal = horizontal(vector);
        return horizontal.lengthSqr() < 0.0001D ? Vec3.ZERO : horizontal.normalize();
    }
    public static Vec3 release(Vec3 preferred, Vec3 fallback, double speed, double currentVertical) {
        Vec3 direction = direction(preferred);
        if (direction.lengthSqr() < 0.0001D) direction = direction(fallback);
        double safeSpeed = Double.isFinite(speed) ? Math.clamp(speed, 0.0D, 8.0D) : 0.0D;
        double vertical = Double.isFinite(currentVertical) ? currentVertical : 0.0D;
        return new Vec3(direction.x * safeSpeed, vertical, direction.z * safeSpeed);
    }
}
