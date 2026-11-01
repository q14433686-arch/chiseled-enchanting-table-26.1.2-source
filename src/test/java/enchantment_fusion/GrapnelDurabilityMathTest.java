package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.GrapnelDurabilityMath;
import enchantment_fusion.mechanics.signatures.RealWearObservation;
import org.junit.jupiter.api.Test;

final class GrapnelDurabilityMathTest {
    @Test void onlyAnObservedCompleteWearAvoidanceQualifies() {
        RealWearObservation observation = new RealWearObservation();
        observation.enterEffect(); observation.record(true, 1, 0); observation.exitEffect();
        assertTrue(GrapnelDurabilityMath.fullySaved(1, 0, observation.acceptedSaved(1, 0)));
        assertFalse(GrapnelDurabilityMath.fullySaved(0, 0, 0));
        assertFalse(GrapnelDurabilityMath.fullySaved(2, 0, 1));
        assertFalse(GrapnelDurabilityMath.fullySaved(2, 1, 2));
        assertFalse(GrapnelDurabilityMath.fullySaved(1, 0, 0));
    }
    @Test void inactiveOrReducingRepairEffectsCannotClaimTheOutcome() {
        assertTrue(GrapnelDurabilityMath.improvedRepair(4, 8));
        assertFalse(GrapnelDurabilityMath.improvedRepair(4, 4));
        assertFalse(GrapnelDurabilityMath.improvedRepair(4, 2));
        assertFalse(GrapnelDurabilityMath.improvedRepair(0, 8));
        assertFalse(GrapnelDurabilityMath.improvedRepair(-2, 8));
    }
    @Test void duplicateOrNestedRollsRemainUnattributable() {
        RealWearObservation sample = new RealWearObservation();
        sample.enterEffect(); sample.record(true, 2, 1); sample.exitEffect();
        sample.enterEffect(); sample.record(true, 1, 0); sample.exitEffect();
        assertFalse(GrapnelDurabilityMath.fullySaved(2, 0, sample.acceptedSaved(2, 0)));
        sample = new RealWearObservation();
        sample.enterEffect(); sample.nestedWear(); sample.record(true, 1, 0); sample.exitEffect();
        assertFalse(GrapnelDurabilityMath.fullySaved(1, 0, sample.acceptedSaved(1, 0)));
    }
}
