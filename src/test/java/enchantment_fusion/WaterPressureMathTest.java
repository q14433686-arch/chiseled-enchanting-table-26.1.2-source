package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.signatures.WaterPressureMath;
import enchantment_fusion.mechanics.signatures.WaterPressureSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WaterPressureMathTest {
    @Test void airPaymentKeepsReserveAndHandlesSmallPools() {
        assertEquals(40, WaterPressureMath.cost(300)); assertEquals(20, WaterPressureMath.reserve(300));
        assertFalse(WaterPressureMath.canPay(59, 300)); assertTrue(WaterPressureMath.canPay(60, 300));
        assertFalse(WaterPressureMath.canPay(1, 1)); assertTrue(WaterPressureMath.canPay(2, 1));
        assertEquals(1, WaterPressureMath.cost(Integer.MIN_VALUE));
    }
    @Test void pressureScalingIsBounded() {
        assertTrue(WaterPressureMath.damage(5, 3) > WaterPressureMath.damage(1, 1));
        assertEquals(WaterPressureMath.damage(10, 10), WaterPressureMath.damage(255, 255));
        assertEquals(2.0D, WaterPressureMath.radius(Integer.MIN_VALUE));
        assertEquals(3.8D, WaterPressureMath.radius(Integer.MAX_VALUE), 1.0E-9D);
    }
    @Test void sixDirectionsHaveDifferentHostsAndPreserveAberration() {
        for (String id : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            FusedPair attack = FusedPair.clean(id, 3, WaterPressureSkills.BREATH, 2), breath = attack.flipped();
            assertTrue(WaterPressureSkills.matches(attack)); assertTrue(WaterPressureSkills.matches(breath));
            assertTrue(WaterPressureSkills.supportsCarrier(attack, Set.of(FusionCarrier.TRIDENT)));
            assertFalse(WaterPressureSkills.supportsCarrier(attack, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertTrue(WaterPressureSkills.supportsCarrier(breath, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertFalse(WaterPressureSkills.supportsCarrier(breath, Set.of(FusionCarrier.SWORD)));
        }
        assertFalse(WaterPressureSkills.matches(FusedPair.clean("minecraft:efficiency", 1, WaterPressureSkills.BREATH, 1)));
        assertTrue(WaterPressureSkills.matches(new FusedPair(MeleeAttackThemes.SHARP, 1, WaterPressureSkills.BREATH, 1, "aberrant")));
    }
    @Test void exactAndWildcardMappingsOverrideTheFamily() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:respiration"},
                  "wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(WaterPressureSkills.matches(FusedPair.clean(MeleeAttackThemes.SHARP, 1, WaterPressureSkills.BREATH, 1)));
            assertFalse(WaterPressureSkills.matches(FusedPair.clean(WaterPressureSkills.BREATH, 1, MeleeAttackThemes.SMITE, 1)));
            assertTrue(WaterPressureSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, WaterPressureSkills.BREATH, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
