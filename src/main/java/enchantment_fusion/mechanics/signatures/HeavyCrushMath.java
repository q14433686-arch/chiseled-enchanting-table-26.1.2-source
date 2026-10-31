package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.model.FusedPair;
import org.jetbrains.annotations.Nullable;

/** Pure order-independent formulas for the user-selected Density/Breach exception. */
public final class HeavyCrushMath {
    public static final String DENSITY = "minecraft:density";
    public static final String BREACH = "minecraft:breach";
    public record Levels(int density, int breach) {}
    private HeavyCrushMath() {}

    public static @Nullable Levels levels(@Nullable FusedPair pair) {
        if (pair == null) return null;
        if (DENSITY.equals(pair.primaryId()) && BREACH.equals(pair.secondaryId())) {
            return new Levels(pair.primaryLevel(), pair.secondaryLevel());
        }
        if (BREACH.equals(pair.primaryId()) && DENSITY.equals(pair.secondaryId())) {
            return new Levels(pair.secondaryLevel(), pair.primaryLevel());
        }
        return null;
    }

    /** The actual Breach datapack effect is separate; Density supplies additional pressure. */
    public static float densityArmorReduction(int density) {
        return 0.025F * Math.clamp(density, 1, 20);
    }

    /** Full swing plus actual fall or forward movement; never use a negative or non-finite input. */
    public static double meleeImpact(float charge, double fallDistance, double speed) {
        double c = Float.isFinite(charge) ? Math.clamp(charge, 0.0F, 1.0F) : 0.0D;
        double fall = Double.isFinite(fallDistance) ? Math.clamp(fallDistance, 0.0D, 8.0D) : 0.0D;
        double motion = Double.isFinite(speed) ? Math.clamp(speed, 0.0D, 4.0D) : 0.0D;
        return 4.0D * c + fall + 2.0D * motion;
    }

    public static double projectileImpact(double speed) {
        return Double.isFinite(speed) ? 2.0D * Math.clamp(speed, 0.0D, 8.0D) : 0.0D;
    }

    public static boolean qualifies(float actualDamage, double impact, double minDamage, double minImpact) {
        return Float.isFinite(actualDamage) && Double.isFinite(impact)
            && actualDamage >= minDamage && impact >= minImpact;
    }

    public static int slowedUseTicks(int elapsed) {
        return (int) Math.floor(Math.max(0, elapsed) / 1.5D);
    }

    public static int slowedChargeDuration(int original) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(0, original) * 1.5D));
    }
}
