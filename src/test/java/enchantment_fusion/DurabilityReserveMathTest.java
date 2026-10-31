package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.DurabilityReserveMath;
import enchantment_fusion.mechanics.signatures.DurabilityReserveSkills;
import enchantment_fusion.mechanics.signatures.EchoPathSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DurabilityReserveMathTest {
    private static final FusedPair ECHO = FusedPair.clean("minecraft:infinity", 1, "minecraft:unbreaking", 3);
    private static final FusedPair RESERVE = ECHO.flipped();

    @Test void orderedDirectionsAreDistinctAndEchoRequiresARealLauncher() {
        assertTrue(EchoPathSkills.matches(ECHO, EchoPathSkills.SIGNATURE));
        assertFalse(EchoPathSkills.matches(RESERVE, EchoPathSkills.SIGNATURE));
        assertTrue(DurabilityReserveSkills.matches(RESERVE, DurabilityReserveSkills.SIGNATURE));
        assertFalse(DurabilityReserveSkills.matches(ECHO, DurabilityReserveSkills.SIGNATURE));
        assertTrue(EchoPathSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertTrue(EchoPathSkills.supportsCarrier(Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(EchoPathSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void onlyObservedNetPreventedWearCounts() {
        assertEquals(3, DurabilityReserveMath.savedByEffect(5.0F, 2.0F));
        assertEquals(0, DurabilityReserveMath.savedByEffect(1.0F, 1.0F));
        assertEquals(0, DurabilityReserveMath.savedByEffect(Float.NaN, 0.0F));
        assertEquals(1, DurabilityReserveMath.acceptedSaved(3, 5, 4));
        assertEquals(0, DurabilityReserveMath.acceptedSaved(3, 5, 7));
        assertEquals(3, DurabilityReserveMath.acceptedSaved(3, 5, 0));
    }
    @Test void bankIsBoundedEvenForExtremeRequests() {
        assertEquals(3, DurabilityReserveMath.add(1, 2, 4, 3));
        assertEquals(12, DurabilityReserveMath.add(10, Integer.MAX_VALUE, 4, 3));
        assertEquals(1, DurabilityReserveMath.add(-1, 1, 4, 3));
        assertEquals(640, DurabilityReserveMath.add(0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
