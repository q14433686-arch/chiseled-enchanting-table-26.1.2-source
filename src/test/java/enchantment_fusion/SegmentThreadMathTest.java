package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.SegmentThreadMath;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SegmentThreadMathTest {
    @Test void trippingRequiresRealBoundedMovementOrAJump() {
        assertFalse(SegmentThreadMath.trips(0, 0, 0));
        assertFalse(SegmentThreadMath.trips(0, -0.5, 0));
        assertTrue(SegmentThreadMath.trips(0.09, 0, 0));
        assertTrue(SegmentThreadMath.trips(0, 0.13, 0));
        assertFalse(SegmentThreadMath.trips(4.1, 0, 0));
        assertFalse(SegmentThreadMath.trips(Double.NaN, 0, 0));
    }
    @Test void onlyPlayerCirclingSweepsTheLine() {
        assertTrue(SegmentThreadMath.circles(4, 1, 0, 0, 0, 2));
        assertFalse(SegmentThreadMath.circles(4, 1, 0, 0, 0, 0));
        assertFalse(SegmentThreadMath.circles(4, 0, 0, 0, 1, 0));
        assertFalse(SegmentThreadMath.circles(4, 1, 0, 0, 0, 5));
        assertFalse(SegmentThreadMath.circles(4, 1, 0, 0, Double.NaN, 2));
    }
    @Test void sweptTriangleRespectsWidthAndHeight() {
        assertTrue(SegmentThreadMath.swept(4, 1, 0, 0, 0, 2, 2, 1, 0));
        assertFalse(SegmentThreadMath.swept(4, 1, 0, 0, 0, 2, 8, 8, 0.5));
        assertTrue(SegmentThreadMath.swept(4, 1, 0, 0, 0, 2, -0.1, 1, 0.2));
        assertFalse(SegmentThreadMath.swept(0, 0, 1, 0, 2, 0, 1, 0, 1));
        assertFalse(SegmentThreadMath.swept(4, 1, 0, 0, 0, 2, 2, 1, Double.NaN));
        assertEquals(2, SegmentThreadMath.sweptHeight(4, 4, 1, 0, 0, 0, 0, 0, 2, 2, 1), 1.0E-9);
        assertEquals(0, SegmentThreadMath.sweptHeight(4, 4, 1, 0, 0, 0, 0, 0, 2, -0.1, 1), 1.0E-9);
    }
    @Test void identitiesAndCarriersAreDirectional() {
        FusedPair pair = FusedPair.clean("minecraft:sharpness", 5, "minecraft:bane_of_arthropods", 5);
        assertTrue(SegmentThreadSkills.matches(pair, SegmentThreadSkills.TRIP));
        assertTrue(SegmentThreadSkills.matches(pair.flipped(), SegmentThreadSkills.THREAD));
        assertFalse(SegmentThreadSkills.matches(pair.flipped(), SegmentThreadSkills.TRIP));
        assertTrue(SegmentThreadSkills.supportsCarrier(Set.of(FusionCarrier.AXE)));
        assertTrue(SegmentThreadSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(SegmentThreadSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertTrue(SegmentThreadMath.openingBonus(5, 5) > SegmentThreadMath.openingBonus(1, 1));
        assertTrue(SegmentThreadMath.threadDamage(5, 5) > SegmentThreadMath.threadDamage(1, 1));
    }
}
