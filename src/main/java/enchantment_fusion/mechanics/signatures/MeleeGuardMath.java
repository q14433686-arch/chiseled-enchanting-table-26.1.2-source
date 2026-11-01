package enchantment_fusion.mechanics.signatures;

/** Pure arithmetic for attributable protection and a front-facing virtual guard. */
public final class MeleeGuardMath {
    private MeleeGuardMath() {}

    /** Marginal mitigation by one observed additive protection contribution under vanilla's cap. */
    public static float contribution(float input, float total, float own) {
        if (!Float.isFinite(input) || !Float.isFinite(total) || !Float.isFinite(own)
            || input <= 0 || total <= 0 || own <= 0 || own > total) return 0;
        float with = Math.clamp(total, 0, 20), without = Math.clamp(total - own, 0, 20);
        return Math.max(0, input * (with - without) / 25.0F);
    }

    /** Inbound projectile and impact point must both be on the facing side of the guard. */
    public static boolean front(double facingX, double facingY, double facingZ,
                                double velocityX, double velocityY, double velocityZ,
                                double offsetX, double offsetY, double offsetZ) {
        double facingSquared = facingX * facingX + facingY * facingY + facingZ * facingZ;
        double speedSquared = velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ;
        double offsetSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (!Double.isFinite(facingSquared) || !Double.isFinite(speedSquared) || !Double.isFinite(offsetSquared)
            || facingSquared < 1.0E-8D || speedSquared < 1.0E-6D || offsetSquared > 9.0D) return false;
        double impactSide = facingX * offsetX + facingY * offsetY + facingZ * offsetZ;
        double approach = facingX * velocityX + facingY * velocityY + facingZ * velocityZ;
        return impactSide >= -0.05D * Math.sqrt(facingSquared) && approach < -0.15D * Math.sqrt(facingSquared * speedSquared);
    }

    public static float counterDamage(int protectionLevel, int attackLevel, float spent) {
        if (!Float.isFinite(spent) || spent <= 0) return 0;
        return 1.25F + Math.clamp(protectionLevel, 1, 10) * 0.25F
            + Math.clamp(attackLevel, 1, 10) * 0.30F + Math.min(spent, 4.0F) * 0.25F;
    }
}
