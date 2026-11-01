package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.GrapnelImpactMath;
import org.junit.jupiter.api.Test;

final class GrapnelImpactMathTest {
    @Test void positiveEffectMustActuallyChangeTheValue() {
        assertTrue(GrapnelImpactMath.increased(1, 2));
        assertFalse(GrapnelImpactMath.increased(1, 1));
        assertFalse(GrapnelImpactMath.increased(2, 1));
        assertFalse(GrapnelImpactMath.increased(Float.NaN, 2));
        assertFalse(GrapnelImpactMath.increased(1, Float.POSITIVE_INFINITY));
    }
    @Test void armorReductionHonorsVanillaClamp() {
        assertTrue(GrapnelImpactMath.reducedArmor(0.8F, 0.6F));
        assertFalse(GrapnelImpactMath.reducedArmor(2.0F, 1.5F));
        assertFalse(GrapnelImpactMath.reducedArmor(0, -0.1F));
        assertFalse(GrapnelImpactMath.reducedArmor(Float.NaN, 0));
    }
    @Test void impulseMustChangeFiniteHorizontalVelocity() {
        assertTrue(GrapnelImpactMath.changedHorizontal(0.5, 0));
        assertFalse(GrapnelImpactMath.changedHorizontal(0, 0));
        assertFalse(GrapnelImpactMath.changedHorizontal(Double.NaN, 1));
    }
}
