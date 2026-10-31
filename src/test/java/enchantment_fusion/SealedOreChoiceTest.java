package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.SealedOreChoice;
import org.junit.jupiter.api.Test;

final class SealedOreChoiceTest {
    @Test void oneRealInputCanBecomeOneBoundedMatchingOutput() {
        assertTrue(SealedOreChoice.exchangeable(1, 1, 3, 64, true, true));
        assertTrue(SealedOreChoice.exchangeable(1, 1, 64, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(2, 1, 3, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(0, 1, 3, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(1, 0, 0, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(1, 2, 3, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(1, 1, 65, 64, true, true));
        assertFalse(SealedOreChoice.exchangeable(1, 1, 3, 0, true, true));
        assertFalse(SealedOreChoice.exchangeable(1, 1, 3, 64, false, true));
        assertFalse(SealedOreChoice.exchangeable(1, 1, 3, 64, true, false));
    }
}
