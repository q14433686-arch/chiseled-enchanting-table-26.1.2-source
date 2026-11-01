package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedSneakMath;
import enchantment_fusion.mechanics.signatures.RangedSneakSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedSneakMathTest {
    @Test void boundedCreditComposesWithUseTimesWithoutChangingUnrelatedItems() {
        assertEquals(3, RangedSneakMath.credit(1)); assertEquals(9, RangedSneakMath.credit(Integer.MAX_VALUE));
        assertEquals(23, RangedSneakMath.bowTicks(20, 3)); assertEquals(72000, RangedSneakMath.bowTicks(Integer.MAX_VALUE, 9));
        assertEquals(-3, RangedSneakMath.bowTicks(-3, 0)); assertEquals(0, RangedSneakMath.crossbowDuration(0, 0));
        assertEquals(22, RangedSneakMath.crossbowDuration(25, 3)); assertEquals(1, RangedSneakMath.crossbowDuration(5, 9));
        assertEquals(.56D, RangedSneakMath.retreat(1), 1e-9); assertEquals(.8D, RangedSneakMath.retreat(Integer.MAX_VALUE), 1e-9);
    }
    @Test void sixDirectionsUseActualBowOrLegsCarrier() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair shooting = FusedPair.clean(theme, 3, RangedSneakSkills.SWIFT, 2), sneak = shooting.flipped();
            assertTrue(RangedSneakSkills.matches(shooting)); assertTrue(RangedSneakSkills.matches(sneak));
            for (var role : Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
                assertTrue(RangedSneakSkills.supportsCarrier(shooting, Set.of(role)));
                assertFalse(RangedSneakSkills.supportsCarrier(sneak, Set.of(role)));
            }
            assertFalse(RangedSneakSkills.supportsCarrier(shooting, Set.of(FusionCarrier.LEG_ARMOR)));
            assertTrue(RangedSneakSkills.supportsCarrier(sneak, Set.of(FusionCarrier.LEG_ARMOR)));
            assertFalse(RangedSneakSkills.supportsCarrier(sneak, Set.of(FusionCarrier.FOOT_ARMOR)));
        }
        assertFalse(RangedSneakSkills.matches(FusedPair.clean("minecraft:multishot", 1, RangedSneakSkills.SWIFT, 1)));
    }
    @Test void exactAndWildcardSignaturesRemainPrior() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:swift_sneak"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedSneakSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedSneakSkills.SWIFT, 1)));
            assertFalse(RangedSneakSkills.matches(FusedPair.clean(RangedSneakSkills.SWIFT, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedSneakSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedSneakSkills.SWIFT, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
