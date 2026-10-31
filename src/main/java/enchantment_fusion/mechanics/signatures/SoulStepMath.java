package enchantment_fusion.mechanics.signatures;

/** Pure resource and motion rules for the two Soul Speed/Swift Sneak directions. */
public final class SoulStepMath {
    private SoulStepMath() {}

    public static boolean actualStep(double distance) {
        // Reject stationary input and large position jumps; ordinary collision still controls movement.
        return Double.isFinite(distance) && distance >= 0.005D && distance <= 1.5D;
    }

    public static int energy(int current, int capacity, boolean collect, boolean spend) {
        int limit = Math.clamp(capacity, 1, 1200);
        int value = Math.clamp(current, 0, limit);
        if (collect) return Math.min(limit, value + 1);
        if (spend && value > 0) return value - 1;
        return value;
    }

    public static double fastSneak(int swiftLevel) {
        return Math.min(1.0D, 0.3D + 0.15D * Math.clamp(swiftLevel, 1, 6));
    }

    public static double fastCrawl(int soulLevel) {
        return Math.min(0.9D, 0.55D + 0.05D * Math.clamp(soulLevel, 1, 7));
    }
}
