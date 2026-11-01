package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.RealRepairObservation;
import org.junit.jupiter.api.Test;

final class RealRepairObservationTest {
    private static RealRepairObservation one(float before, float after) {
        RealRepairObservation observation = new RealRepairObservation();
        observation.enter(); observation.record(true, 1, before, after, true); observation.exit(); return observation;
    }
    @Test void requiresTheActualSinglePositiveMatchingMendingEffect() {
        assertTrue(one(4, 8).proved(1, 4, 8));
        assertFalse(one(4, 4).proved(1, 4, 4));
        assertFalse(one(4, 8).proved(2, 4, 8));
        assertFalse(one(4, 8).proved(1, 3, 8));
        assertFalse(one(4, 8).proved(1, 4, 9));
        assertFalse(one(0, 2).proved(1, 0, 2));
        assertFalse(one(4, Float.NaN).proved(1, 4, 8));
    }
    @Test void foreignChangedDuplicateAndNestedEffectsCannotBeClaimed() {
        RealRepairObservation other = one(4, 8);
        other.enter(); other.record(false, 1, 8, 9, true); other.exit();
        assertFalse(other.proved(1, 4, 9));
        RealRepairObservation duplicate = one(4, 8);
        duplicate.enter(); duplicate.record(true, 1, 8, 16, true); duplicate.exit();
        assertFalse(duplicate.proved(1, 4, 16));
        RealRepairObservation nested = new RealRepairObservation();
        nested.enter(); nested.enter(); nested.record(true, 1, 4, 8, true); nested.exit(); nested.exit();
        assertFalse(nested.proved(1, 4, 8));
        RealRepairObservation failed = new RealRepairObservation();
        failed.enter(); failed.record(true, 1, 4, 8, false); failed.exit();
        assertFalse(failed.proved(1, 4, 8));
    }
    @Test void unrelatedNoopEffectDoesNotInvalidateARealRepair() {
        RealRepairObservation observation = new RealRepairObservation();
        observation.enter(); observation.record(false, 4, 4, 4, true); observation.exit();
        observation.enter(); observation.record(true, 1, 4, 8, true); observation.exit();
        assertTrue(observation.proved(1, 4, 8));
    }
}
