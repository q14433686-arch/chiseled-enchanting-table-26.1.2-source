package enchantment_fusion.mechanics.signatures;

/** Direction-specific evidence of an actually executed primary value effect. */
public final class GrapnelImpactMath {
    private GrapnelImpactMath() {}
    public static boolean increased(float before, float after) {
        return Float.isFinite(before) && Float.isFinite(after) && after > before;
    }
    public static boolean reducedArmor(float before, float after) {
        return Float.isFinite(before) && Float.isFinite(after)
            && Math.clamp(after, 0.0F, 1.0F) < Math.clamp(before, 0.0F, 1.0F);
    }
    public static boolean changedHorizontal(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z) && x * x + z * z >= 0.0001D;
    }
}
