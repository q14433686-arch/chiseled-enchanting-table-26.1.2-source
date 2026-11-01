package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedBreathSkills;
import enchantment_fusion.mechanics.signatures.RangedWaterMath;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedWaterMathTest {
    @Test void reducedDragIsBoundedAndNeverSlowsOtherPhysics() {
        assertTrue(RangedWaterMath.inertia(.6F, 1, 1) > .6F);
        assertTrue(RangedWaterMath.inertia(.6F, 5, 3) > RangedWaterMath.inertia(.6F, 1, 1));
        assertEquals(.99F, RangedWaterMath.inertia(.99F, 5, 5));
        assertEquals(RangedWaterMath.inertia(.6F, 6, 5), RangedWaterMath.inertia(.6F, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(RangedWaterMath.inertia(.6F, 1, 1), RangedWaterMath.inertia(.6F, Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertEquals(-1F, RangedWaterMath.inertia(-1F, 5, 5));
        assertEquals(2F, RangedWaterMath.inertia(2F, 5, 5));
        assertTrue(Float.isNaN(RangedWaterMath.inertia(Float.NaN, 5, 5)));
    }
    @Test void sixBreathDirectionsUseRealBowOrHelmetRoles() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair arrow = FusedPair.clean(theme, 3, RangedBreathSkills.RESPIRATION, 2), breath = arrow.flipped();
            assertTrue(RangedBreathSkills.matches(arrow)); assertTrue(RangedBreathSkills.matches(breath));
            assertTrue(RangedBreathSkills.shootingFirst(arrow)); assertTrue(RangedBreathSkills.breathFirst(breath));
            for (var role : Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
                assertTrue(RangedBreathSkills.supportsCarrier(arrow, Set.of(role)));
                assertFalse(RangedBreathSkills.supportsCarrier(breath, Set.of(role)));
            }
            assertFalse(RangedBreathSkills.supportsCarrier(arrow, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertTrue(RangedBreathSkills.supportsCarrier(breath, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertFalse(RangedBreathSkills.supportsCarrier(breath, Set.of(FusionCarrier.FOOT_ARMOR)));
        }
        assertFalse(RangedBreathSkills.matches(FusedPair.clean("minecraft:multishot", 1, RangedBreathSkills.RESPIRATION, 1)));
    }
    @Test void exactAndWildcardSignaturePrecedenceIsNotOverridden() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:respiration"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedBreathSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedBreathSkills.RESPIRATION, 1)));
            assertFalse(RangedBreathSkills.matches(FusedPair.clean(RangedBreathSkills.RESPIRATION, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedBreathSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedBreathSkills.RESPIRATION, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
