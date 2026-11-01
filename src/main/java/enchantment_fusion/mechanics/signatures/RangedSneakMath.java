package enchantment_fusion.mechanics.signatures;

/** Bounded draw acceleration and retreat impulse; neither creates a projectile or teleports. */
public final class RangedSneakMath {
    private RangedSneakMath() {}
    public static int credit(int secondaryLevel) { return Math.min(9, 2 + Math.clamp(secondaryLevel, 1, 7)); }
    public static int bowTicks(int elapsed, int credit) {
        return credit <= 0 ? elapsed : Math.min(72000, Math.clamp(elapsed, 0, 72000) + Math.clamp(credit, 0, 9));
    }
    public static int crossbowDuration(int original, int credit) {
        return credit <= 0 ? original : Math.max(1, original - Math.clamp(credit, 0, 9));
    }
    public static double retreat(int secondaryLevel) { return .52D + .04D * Math.clamp(secondaryLevel, 1, 7); }
}
