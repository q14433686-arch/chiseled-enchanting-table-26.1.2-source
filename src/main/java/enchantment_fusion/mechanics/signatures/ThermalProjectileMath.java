package enchantment_fusion.mechanics.signatures;

import net.minecraft.world.phys.Vec3;

/** Closest synchronized point of two bounded one-tick projectile segments. */
public final class ThermalProjectileMath {
    private ThermalProjectileMath() {}
    public static double approachTime(Vec3 relativePosition, Vec3 relativeMovement) {
        double speed = relativeMovement.lengthSqr();
        double approach = relativePosition.dot(relativeMovement);
        if (!Double.isFinite(speed) || !Double.isFinite(approach) || speed < 1.0E-12D || approach >= 0.0D) return -1.0D;
        return Math.clamp(-approach / speed, 0.0D, 1.0D);
    }
}
