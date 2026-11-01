package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedWearMath;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.RealWearObservation;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedWearMathTest {
    @Test void streakGuardAndChargesAreBoundedAndDoNotOverflow() {
        assertEquals(1, RangedWearMath.nextStreak(0, 0, 1));
        assertEquals(3, RangedWearMath.nextStreak(2, 1, 201));
        assertEquals(1, RangedWearMath.nextStreak(4, 1, 202));
        assertEquals(1, RangedWearMath.nextStreak(4, 100, 99));
        assertEquals(1, RangedWearMath.nextStreak(4, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(5, RangedWearMath.nextStreak(Integer.MAX_VALUE, 100, 100));
        assertEquals(0, RangedWearMath.guard(2, 100));
        assertEquals(6, RangedWearMath.guard(3, 3));
        assertEquals(12, RangedWearMath.guard(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(4, RangedWearMath.addCharges(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, RangedWearMath.addCharges(-1, -5));
        assertTrue(RangedWearMath.echoDamage(true, 5) > RangedWearMath.echoDamage(true, 1));
        assertEquals(RangedWearMath.echoDamage(false, 10), RangedWearMath.echoDamage(false, Integer.MAX_VALUE));
    }
    @Test void onlyObservedNetDurabilitySavingsCanBecomeCharges() {
        RealWearObservation observed = new RealWearObservation();
        assertEquals(0, observed.acceptedSaved(4, 0));
        observed.enterEffect(); observed.record(true, 4.0F, 2.0F); observed.exitEffect();
        assertEquals(2, RangedWearMath.addCharges(0, observed.acceptedSaved(4, 2)));
        assertEquals(1, RangedWearMath.addCharges(0, observed.acceptedSaved(4, 3)));
        assertEquals(0, RangedWearMath.addCharges(0, observed.acceptedSaved(4, 4)));
        observed.enterEffect(); observed.record(false, 2.0F, 3.0F); observed.exitEffect();
        assertEquals(0, observed.acceptedSaved(4, 3));
    }
    @Test void sixDirectionsAndRealLauncherCarriersOnly() {
        for (String id : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair forward = FusedPair.clean(id, 2, RangedWearSkills.UNBREAKING, 3), reverse = forward.flipped();
            assertTrue(RangedWearSkills.matches(forward)); assertTrue(RangedWearSkills.matches(reverse));
            assertTrue(RangedWearSkills.shootingFirst(forward)); assertTrue(RangedWearSkills.unbreakingFirst(reverse));
        }
        assertFalse(RangedWearSkills.matches(FusedPair.clean("minecraft:quick_charge", 1, RangedWearSkills.UNBREAKING, 1)));
        assertFalse(RangedWearSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, "minecraft:lure", 1)));
        assertTrue(RangedWearSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertTrue(RangedWearSkills.supportsCarrier(Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(RangedWearSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void exactAndWildcardDatapackSignaturesRemainPriorToTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:unbreaking"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedWearSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedWearSkills.UNBREAKING, 1)));
            assertFalse(RangedWearSkills.matches(FusedPair.clean(RangedWearSkills.UNBREAKING, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedWearSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedWearSkills.UNBREAKING, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
