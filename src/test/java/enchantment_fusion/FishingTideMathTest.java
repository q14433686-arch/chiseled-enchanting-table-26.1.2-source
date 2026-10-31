package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.FishingTideMath;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FishingTideMathTest {
    private static final FusedPair CHOICE = FusedPair.clean("minecraft:luck_of_the_sea", 3, "minecraft:lure", 2);
    private static final FusedPair SCHOOL = CHOICE.flipped();

    @Test void bothDirectionsAreRealFishingRodActions() {
        assertTrue(FishingTideSkills.matches(CHOICE, FishingTideSkills.CHOICE));
        assertTrue(FishingTideSkills.matches(SCHOOL, FishingTideSkills.SCHOOL));
        assertFalse(FishingTideSkills.matches(CHOICE, FishingTideSkills.SCHOOL));
        assertTrue(FishingTideSkills.supportsCarrier(Set.of(FusionCarrier.FISHING_ROD)));
        assertFalse(FishingTideSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }
    @Test void waitingImprovesOnlyTheCurrentLootLuckAndHasABound() {
        assertEquals(0.0F, FishingTideMath.waitingLuck(0, 2), 0.00001F);
        assertEquals(0.0F, FishingTideMath.waitingLuck(19, 2), 0.00001F);
        assertEquals(1.0F, FishingTideMath.waitingLuck(20, 2), 0.00001F);
        assertEquals(2.0F, FishingTideMath.waitingLuck(40, 2), 0.00001F);
        assertEquals(6.0F, FishingTideMath.waitingLuck(9999, 9999), 0.00001F);
    }
    @Test void schoolShortensOneWaitWithoutMakingItInstant() {
        assertEquals(70, FishingTideMath.shortenedWait(100, 3, 15, 5, 50));
        assertEquals(50, FishingTideMath.shortenedWait(100, 10, 15, 5, 50));
        assertEquals(100, FishingTideMath.shortenedWait(100, 3, 0, 0, 50));
        assertEquals(1, FishingTideMath.shortenedWait(0, 3, 15, 5, 50));
    }
}
