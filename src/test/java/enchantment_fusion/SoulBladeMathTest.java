package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.SoulBladeMath;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SoulBladeMathTest {
    @Test void oppositeSideIsMeasuredRelativeToTheTargetNotItsFacing() {
        assertTrue(SoulBladeMath.opposite(1, 0, -1, 0));
        assertTrue(SoulBladeMath.opposite(1, 0, -1, 1));
        assertFalse(SoulBladeMath.opposite(1, 0, 1, 0));
        assertFalse(SoulBladeMath.opposite(1, 0, 0, 1));
        assertFalse(SoulBladeMath.opposite(0, 0, -1, 0));
        assertFalse(SoulBladeMath.opposite(Double.NaN, 0, -1, 0));
    }
    @Test void chargedBladeAndWeaknessHaveBoundedLevels() {
        assertEquals(SoulBladeMath.weaknessBonus(10, 10), SoulBladeMath.weaknessBonus(100, 100));
        assertEquals(SoulBladeMath.bladeDamage(5, 10), SoulBladeMath.bladeDamage(100, 100));
        assertTrue(SoulBladeMath.bladeDamage(5, 3) > SoulBladeMath.bladeDamage(1, 3));
    }
    @Test void bothDirectionsRequireActualMeleeWeapons() {
        FusedPair pair = FusedPair.clean("minecraft:sharpness", 5, "minecraft:smite", 5);
        assertTrue(SoulBladeSkills.matches(pair, SoulBladeSkills.WEAKNESS));
        assertTrue(SoulBladeSkills.matches(pair.flipped(), SoulBladeSkills.BLADE));
        assertTrue(SoulBladeSkills.supportsCarrier(Set.of(FusionCarrier.AXE)));
        assertTrue(SoulBladeSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(SoulBladeSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
    }
}
