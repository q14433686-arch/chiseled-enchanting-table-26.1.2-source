package enchantment_fusion.mechanics.signatures;

/** Bounded geometry and strength for a virtual ring; independent of world mutation. */
public final class LootingFireSeedMath {
    private LootingFireSeedMath() {}
    public static int level(int fireLevel) { return Math.clamp(fireLevel, 1, 10); }
    public static double radius(int fireLevel) { return Math.min(3.5D, 1.5D + 0.2D * level(fireLevel)); }
    public static float damage(int fireLevel) { return 1.0F + 0.35F * level(fireLevel); }
    public static int burnSeconds(int fireLevel) { return 2 + Math.min(5, level(fireLevel)); }
    public static boolean onRing(double dx, double dy, double dz, double radius) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz) || !Double.isFinite(radius)
            || radius < 0 || Math.abs(dy) > 2.0D) return false;
        return Math.abs(Math.hypot(dx, dz) - radius) <= 0.65D;
    }
}
