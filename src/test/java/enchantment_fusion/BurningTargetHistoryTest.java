package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.BurningTargetHistory;
import org.junit.jupiter.api.Test;

final class BurningTargetHistoryTest {
    @Test void onlyConsecutiveObservedTicksCount() {
        assertEquals(10, BurningTargetHistory.firstAfter(10, 10, 10));
        assertEquals(10, BurningTargetHistory.firstAfter(10, 10, 11));
        assertEquals(12, BurningTargetHistory.firstAfter(10, 10, 12));
        assertEquals(9, BurningTargetHistory.firstAfter(10, 10, 9));
        assertFalse(BurningTargetHistory.longEnough(10, 28, 28, 20));
        assertTrue(BurningTargetHistory.longEnough(10, 29, 29, 20));
        assertTrue(BurningTargetHistory.longEnough(10, 29, 30, 20));
        assertFalse(BurningTargetHistory.longEnough(10, 29, 31, 20));
        assertFalse(BurningTargetHistory.longEnough(10, 29, 28, 20));
        assertFalse(BurningTargetHistory.longEnough(-1, 29, 29, 20));
        assertFalse(BurningTargetHistory.longEnough(0, 1, 1, 0));
        assertEquals(Long.MIN_VALUE, BurningTargetHistory.firstAfter(0, Long.MAX_VALUE, Long.MIN_VALUE));
    }
}
