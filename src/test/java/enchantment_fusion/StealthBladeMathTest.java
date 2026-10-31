package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.signatures.StealthBladeMath;
import enchantment_fusion.mechanics.signatures.StealthBladeSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class StealthBladeMathTest {
    @Test void boundedStepsRejectIdleAndTeleport() {
        assertEquals(0, StealthBladeMath.step(0, 0, 0));
        assertEquals(0, StealthBladeMath.step(0, 1, 0));
        assertEquals(0, StealthBladeMath.step(5, 0, 0));
        assertEquals(0, StealthBladeMath.step(Double.NaN, 0, 0));
        assertEquals(0.1D, StealthBladeMath.step(0.1D, 0.01D, 0), 1.0E-9D);
    }
    @Test void crossingUsesSweptMovementAndHeight() {
        assertTrue(StealthBladeMath.crosses(-2, 0, 0, 2, 0, 0, 0, 0, 0));
        assertTrue(StealthBladeMath.crosses(-2, 0, 1, 2, 0, 1, 0, 0, 0));
        assertFalse(StealthBladeMath.crosses(-2, 0, 2, 2, 0, 2, 0, 0, 0));
        assertFalse(StealthBladeMath.crosses(-2, 3, 0, 2, 3, 0, 0, 0, 0));
        assertFalse(StealthBladeMath.crosses(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertFalse(StealthBladeMath.crosses(-4, 0, 0, 4, 0, 0, 0, 0, 0));
        assertFalse(StealthBladeMath.crosses(0, 0, 0, Double.NaN, 0, 0, 0, 0, 0));
    }
    @Test void scalingIsFiniteMonotonicAndClamped() {
        assertTrue(StealthBladeMath.damage(5, 3) > StealthBladeMath.damage(1, 1));
        assertEquals(StealthBladeMath.damage(10, 10), StealthBladeMath.damage(255, 255));
        assertEquals(5.0D, StealthBladeMath.range(Integer.MIN_VALUE));
        assertEquals(7.7D, StealthBladeMath.range(255), 1.0E-9D);
    }
    @Test void sixDirectionsHaveDistinctCarriersAndNoOtherThemes() {
        for (String id : new String[]{StealthBladeSkills.SHARP, StealthBladeSkills.SMITE, StealthBladeSkills.BANE}) {
            FusedPair attack = FusedPair.clean(id, 3, StealthBladeSkills.SNEAK, 2), sneak = attack.flipped();
            assertTrue(StealthBladeSkills.matches(attack)); assertTrue(StealthBladeSkills.matches(sneak));
            assertTrue(StealthBladeSkills.supportsCarrier(attack, Set.of(FusionCarrier.SWORD)));
            assertTrue(StealthBladeSkills.supportsCarrier(attack, Set.of(FusionCarrier.TRIDENT)));
            assertFalse(StealthBladeSkills.supportsCarrier(attack, Set.of(FusionCarrier.LEG_ARMOR)));
            assertTrue(StealthBladeSkills.supportsCarrier(sneak, Set.of(FusionCarrier.LEG_ARMOR)));
            assertFalse(StealthBladeSkills.supportsCarrier(sneak, Set.of(FusionCarrier.SWORD)));
        }
        assertFalse(StealthBladeSkills.matches(FusedPair.clean("minecraft:soul_speed", 1, StealthBladeSkills.SNEAK, 1)));
        // The independent aberration remains on the pair; it does not erase the ordered template.
        assertTrue(StealthBladeSkills.matches(new FusedPair(StealthBladeSkills.SHARP, 1, StealthBladeSkills.SNEAK, 1, "aberrant")));
    }
    @Test void exactAndWildcardDataPackMappingsTakePriorityOverTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:swift_sneak"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(StealthBladeSkills.matches(FusedPair.clean(StealthBladeSkills.SHARP, 1, StealthBladeSkills.SNEAK, 1)));
            assertFalse(StealthBladeSkills.matches(FusedPair.clean(StealthBladeSkills.SNEAK, 1, StealthBladeSkills.SMITE, 1)));
            assertTrue(StealthBladeSkills.matches(FusedPair.clean(StealthBladeSkills.BANE, 1, StealthBladeSkills.SNEAK, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
