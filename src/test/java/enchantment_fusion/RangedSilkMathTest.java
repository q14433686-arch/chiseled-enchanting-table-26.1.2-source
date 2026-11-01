package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedSilkMath;
import enchantment_fusion.mechanics.signatures.RangedSilkSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedSilkMathTest {
    @Test void focusedEffectsAndRecoveryRangeAreFiniteAndBounded() {
        assertEquals(.8F, RangedSilkMath.damage(1), 1e-6F);
        assertEquals(2.3F, RangedSilkMath.damage(Integer.MAX_VALUE), 1e-6F);
        assertEquals(.45F, RangedSilkMath.knockback(0), 1e-6F);
        assertEquals(1.45F, RangedSilkMath.knockback(Integer.MAX_VALUE), 1e-6F);
        assertEquals(40, RangedSilkMath.fireBonusTicks(1));
        assertEquals(90, RangedSilkMath.fireBonusTicks(Integer.MAX_VALUE));
        assertTrue(RangedSilkMath.inRange(0)); assertTrue(RangedSilkMath.inRange(16 * 16));
        assertFalse(RangedSilkMath.inRange(16 * 16 + .001)); assertFalse(RangedSilkMath.inRange(-1));
        assertFalse(RangedSilkMath.inRange(Double.NaN)); assertFalse(RangedSilkMath.inRange(Double.POSITIVE_INFINITY));
    }
    @Test void sixDirectionsAreRestrictedToBowOrCrossbowAndKeepDirection() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair forward = FusedPair.clean(theme, 3, RangedSilkSkills.SILK, 1), reverse = forward.flipped();
            assertTrue(RangedSilkSkills.matches(forward)); assertTrue(RangedSilkSkills.matches(reverse));
            assertTrue(RangedSilkSkills.shootingFirst(forward)); assertFalse(RangedSilkSkills.silkFirst(forward));
            assertTrue(RangedSilkSkills.silkFirst(reverse)); assertFalse(RangedSilkSkills.shootingFirst(reverse));
        }
        assertTrue(RangedSilkSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertTrue(RangedSilkSkills.supportsCarrier(Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(RangedSilkSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
        assertFalse(RangedSilkSkills.matches(FusedPair.clean("minecraft:infinity", 1, RangedSilkSkills.SILK, 1)));
    }
    @Test void dataPackExactAndWildcardOverridesStillTakePriority() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var data = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:silk_touch"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(data));
            assertFalse(RangedSilkSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedSilkSkills.SILK, 1)));
            assertFalse(RangedSilkSkills.matches(FusedPair.clean(RangedSilkSkills.SILK, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedSilkSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedSilkSkills.SILK, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
