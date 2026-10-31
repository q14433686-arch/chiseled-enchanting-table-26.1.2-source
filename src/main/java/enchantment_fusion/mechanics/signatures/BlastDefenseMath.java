package enchantment_fusion.mechanics.signatures;

/** Pure bounded formulas for the two protection/blast-protection directions. */
public final class BlastDefenseMath {
    private BlastDefenseMath() {}

    /** Both enchantment identities matter; health is bounded below by half a heart. */
    public static double inverseHealthScale(int blastLevel, int protectionLevel, float health, float maxHealth) {
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0.0F) return 0.0D;
        double levels = (Math.clamp(blastLevel, 1, 255) + Math.clamp(protectionLevel, 1, 255)) / 2.0D;
        return levels * Math.max(1.0D, maxHealth) / Math.max(1.0D, Math.min(health, maxHealth));
    }

    public static double counterRadius(double base, double maximum, int blastLevel, int protectionLevel,
                                       float health, float maxHealth) {
        if (!Double.isFinite(base) || !Double.isFinite(maximum) || base <= 0.0D || maximum <= 0.0D) return 0.0D;
        return Math.min(maximum, base * inverseHealthScale(blastLevel, protectionLevel, health, maxHealth));
    }

    public static float counterDamage(float base, float maximum, int blastLevel, int protectionLevel,
                                      float health, float maxHealth) {
        if (!Float.isFinite(base) || !Float.isFinite(maximum) || base <= 0.0F || maximum <= 0.0F) return 0.0F;
        return (float) Math.min(maximum, base * inverseHealthScale(blastLevel, protectionLevel, health, maxHealth));
    }

    /** A selected blast-primary item guarantees a minimum without double-counting native resistance. */
    public static double knockbackResistance(double nativeResistance, int blastLevel, int percentPerLevel) {
        double safeNative = Double.isFinite(nativeResistance) ? Math.clamp(nativeResistance, 0.0D, 1.0D) : 0.0D;
        double minimum = Math.clamp(blastLevel, 1, 255) * Math.clamp(percentPerLevel, 0, 100) / 100.0D;
        return Math.max(safeNative, Math.min(1.0D, minimum));
    }

    public static float convertedProtection(float actualMainContribution, int percent) {
        if (!Float.isFinite(actualMainContribution) || actualMainContribution <= 0.0F) return 0.0F;
        return actualMainContribution * Math.clamp(percent, 0, 100) / 100.0F;
    }
}
