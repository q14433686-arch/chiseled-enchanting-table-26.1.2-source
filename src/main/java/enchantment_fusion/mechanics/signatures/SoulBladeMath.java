package enchantment_fusion.mechanics.signatures;

/** Position-based weakness: the second attacker must be on the opposite side of the target. */
public final class SoulBladeMath {
    private SoulBladeMath() {}
    public static boolean opposite(double initialX, double initialZ, double nowX, double nowZ) {
        double a = initialX * initialX + initialZ * initialZ, b = nowX * nowX + nowZ * nowZ;
        if (!Double.isFinite(a) || !Double.isFinite(b) || a < 1.0E-8D || b < 1.0E-8D) return false;
        return (initialX * nowX + initialZ * nowZ) / Math.sqrt(a * b) <= -0.5D;
    }
    public static float weaknessBonus(int sharpness, int smite) {
        return 2.0F + 0.3F * Math.clamp(sharpness, 1, 10) + 0.5F * Math.clamp(smite, 1, 10);
    }
    public static float bladeDamage(int stacks, int sharpness) {
        return 2.0F + 0.75F * Math.clamp(stacks, 1, 5) + 0.35F * Math.clamp(sharpness, 1, 10);
    }
}
