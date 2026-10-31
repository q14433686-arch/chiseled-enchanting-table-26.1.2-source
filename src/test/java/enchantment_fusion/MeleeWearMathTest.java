package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearMath;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import enchantment_fusion.mechanics.signatures.RealWearObservation;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeWearMathTest {
    @Test void consecutiveHitsHaveBoundedGapAndCannotOverflow() {
        assertEquals(1, MeleeWearMath.nextStreak(0, 0, 0));
        assertEquals(2, MeleeWearMath.nextStreak(1, 1, 81));
        assertEquals(1, MeleeWearMath.nextStreak(4, 1, 82));
        assertEquals(1, MeleeWearMath.nextStreak(4, 100, 99));
        assertEquals(1, MeleeWearMath.nextStreak(4, Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(5, MeleeWearMath.nextStreak(Integer.MAX_VALUE, 4, 4));
    }
    @Test void guardPreventsOnlyAvailablePositiveWear() {
        assertEquals(0, MeleeWearMath.guardFor(2, 3));
        assertEquals(6, MeleeWearMath.guardFor(3, 3));
        assertEquals(12, MeleeWearMath.guardFor(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, MeleeWearMath.preventedWear(-2, 10));
        assertEquals(0, MeleeWearMath.preventedWear(10, -2));
        assertEquals(3, MeleeWearMath.preventedWear(3, 7));
        assertEquals(7, MeleeWearMath.preventedWear(10, 7));
    }
    @Test void shardsAndBonusAreFiniteBoundedAndMonotonic() {
        assertEquals(0, MeleeWearMath.spendShards(1));
        assertEquals(2, MeleeWearMath.spendShards(2));
        assertEquals(4, MeleeWearMath.spendShards(8));
        assertEquals(8, MeleeWearMath.addShards(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, MeleeWearMath.addShards(-3, -9));
        assertEquals(0.0F, MeleeWearMath.strikeBonus(3, 2, 1));
        assertTrue(MeleeWearMath.strikeBonus(5, 3, 4) > MeleeWearMath.strikeBonus(1, 1, 2));
        assertEquals(MeleeWearMath.strikeBonus(10, 10, 4), MeleeWearMath.strikeBonus(255, 255, 255));
        assertTrue(Float.isFinite(MeleeWearMath.strikeBonus(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }
    @Test void evidenceUsesTheRealEffectAndTheNetOutcome() {
        RealWearObservation sample = new RealWearObservation();
        sample.enterEffect(); sample.record(false, 3, 3); sample.exitEffect();
        sample.enterEffect(); sample.record(true, 3, 1); sample.exitEffect();
        sample.enterEffect(); sample.record(false, 1, 1); sample.exitEffect();
        assertEquals(2, sample.acceptedSaved(3, 1));
        assertEquals(1, sample.acceptedSaved(3, 2));
        assertEquals(0, sample.acceptedSaved(3, 4));
        assertEquals(0, sample.acceptedSaved(0, 0));
        assertEquals(0, sample.acceptedSaved(3, -1));
        assertEquals(0, new RealWearObservation().acceptedSaved(5, 0));
    }
    @Test void ambiguousNestedOrChangedEffectsNeverCreditSavings() {
        RealWearObservation nested = new RealWearObservation();
        nested.enterEffect(); nested.enterEffect(); nested.record(true, 2, 0); nested.exitEffect(); nested.exitEffect();
        assertEquals(0, nested.acceptedSaved(2, 0));
        RealWearObservation childWear = new RealWearObservation();
        childWear.enterEffect(); childWear.nestedWear(); childWear.record(true, 2, 0); childWear.exitEffect();
        assertEquals(0, childWear.acceptedSaved(2, 0));
        RealWearObservation afterChange = new RealWearObservation();
        afterChange.enterEffect(); afterChange.record(true, 2, 0); afterChange.exitEffect();
        afterChange.enterEffect(); afterChange.record(false, 0, 1); afterChange.exitEffect();
        assertEquals(0, afterChange.acceptedSaved(2, 1));
        RealWearObservation twice = new RealWearObservation();
        twice.enterEffect(); twice.record(true, 3, 2); twice.exitEffect();
        twice.enterEffect(); twice.record(true, 2, 1); twice.exitEffect();
        assertEquals(0, twice.acceptedSaved(3, 1));
        RealWearObservation invalid = new RealWearObservation();
        invalid.enterEffect(); invalid.record(true, Float.NaN, 0); invalid.exitEffect();
        assertEquals(0, invalid.acceptedSaved(3, 0));
        RealWearObservation unfinished = new RealWearObservation();
        unfinished.enterEffect(); unfinished.record(true, 1, 0);
        assertEquals(0, unfinished.acceptedSaved(1, 0));
    }
    @Test void allSixDirectionsAreSelectedWithoutClaimingOtherThemes() {
        for (String id : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            FusedPair attack = FusedPair.clean(id, 3, MeleeWearSkills.UNBREAKING, 2), reverse = attack.flipped();
            assertTrue(MeleeWearSkills.matches(attack)); assertTrue(MeleeWearSkills.matches(reverse));
            assertTrue(MeleeWearSkills.isAttackFirst(attack)); assertTrue(MeleeWearSkills.isUnbreakingFirst(reverse));
        }
        assertFalse(MeleeWearSkills.matches(FusedPair.clean("minecraft:fire_aspect", 1, MeleeWearSkills.UNBREAKING, 1)));
        assertTrue(MeleeAttackThemes.supportsWeapon(Set.of(FusionCarrier.SWORD)));
        assertTrue(MeleeAttackThemes.supportsWeapon(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(MeleeAttackThemes.supportsWeapon(Set.of(FusionCarrier.HEAD_ARMOR)));
    }
    @Test void dataPackSignaturesOverrideTheTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:unbreaking"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(MeleeWearSkills.matches(FusedPair.clean(MeleeAttackThemes.SHARP, 1, MeleeWearSkills.UNBREAKING, 1)));
            assertFalse(MeleeWearSkills.matches(FusedPair.clean(MeleeWearSkills.UNBREAKING, 1, MeleeAttackThemes.SMITE, 1)));
            assertTrue(MeleeWearSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, MeleeWearSkills.UNBREAKING, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
