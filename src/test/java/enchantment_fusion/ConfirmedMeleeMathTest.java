package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.ConfirmedMeleeMath;
import org.junit.jupiter.api.Test;

final class ConfirmedMeleeMathTest {
    @Test void realLossIncludesHealthAndAbsorption() {
        assertEquals(6, ConfirmedMeleeMath.actualLoss(20, 4, 16, 2));
        assertEquals(3, ConfirmedMeleeMath.actualLoss(20, 4, 20, 1));
        assertEquals(0, ConfirmedMeleeMath.actualLoss(20, 4, 20, 4));
    }
    @Test void healingAndAbsorptionGainedAreNetConservative() {
        assertEquals(0, ConfirmedMeleeMath.actualLoss(10, 2, 11, 1));
        assertEquals(0, ConfirmedMeleeMath.actualLoss(2, 0, 1, 4));
        assertEquals(1, ConfirmedMeleeMath.actualLoss(10, 3, 11, 1));
    }
    @Test void invalidOrOverflowingMeasurementsAreRejected() {
        assertEquals(0, ConfirmedMeleeMath.actualLoss(Float.NaN, 0, 1, 0));
        assertEquals(0, ConfirmedMeleeMath.actualLoss(1, Float.POSITIVE_INFINITY, 1, 0));
        assertEquals(0, ConfirmedMeleeMath.actualLoss(1, 0, -1, 0));
        assertEquals(0, ConfirmedMeleeMath.actualLoss(Float.MAX_VALUE, Float.MAX_VALUE, 0, 0));
    }
}
