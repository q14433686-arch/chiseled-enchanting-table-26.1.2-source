package enchantment_fusion.mechanics.signatures;

/** Bounded numbers for the selected underwater melee family. */
public final class WaterPressureMath {
    private WaterPressureMath() {}
    public static int cost(int maximumAir) { return Math.max(1, Math.min(40, Math.max(1, maximumAir) / 5)); }
    public static int reserve(int maximumAir) { return Math.max(1, Math.min(20, Math.max(1, maximumAir) / 10)); }
    public static boolean canPay(int air, int maximumAir) { return air >= cost(maximumAir) + reserve(maximumAir); }
    public static float damage(int attack, int breath) {
        return 1.5F + 0.35F * Math.clamp(attack, 1, 10) + 0.3F * Math.clamp(breath, 1, 10);
    }
    public static double radius(int breath) { return 2.0D + 0.2D * (Math.clamp(breath, 1, 10) - 1); }
}
