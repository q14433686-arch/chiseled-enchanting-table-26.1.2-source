package enchantment_fusion.mechanics.signatures;

/** Bounded water-drag values, independent of whether the impulse came from air or swimming. */
public final class RangedWaterMath {
    private RangedWaterMath() {}

    /** An inertia nearer one means less drag; never slow a projectile that was already faster. */
    public static float inertia(float vanilla, int primaryLevel, int secondaryLevel) {
        if (!Float.isFinite(vanilla) || vanilla < 0.0F || vanilla > 1.0F) return vanilla;
        float desired = Math.min(0.96F, 0.82F + 0.015F * Math.clamp(primaryLevel, 1, 6)
            + 0.01F * Math.clamp(secondaryLevel, 1, 5));
        return Math.max(vanilla, desired);
    }
}
