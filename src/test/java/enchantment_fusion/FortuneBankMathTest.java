package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.FortuneBankMath;
import org.junit.jupiter.api.Test;

final class FortuneBankMathTest {
    @Test void depositCannotExceedTheRealIncrementOrCapacity() {
        assertEquals(0, FortuneBankMath.deposit(1, 1, 64));
        assertEquals(1, FortuneBankMath.deposit(1, 2, 64));
        assertEquals(2, FortuneBankMath.deposit(1, 4, 64));
        assertEquals(1, FortuneBankMath.deposit(1, 4, 1));
        assertEquals(0, FortuneBankMath.deposit(1, 4, 0));
        assertEquals(0, FortuneBankMath.deposit(0, 4, 64));
        assertEquals(0, FortuneBankMath.deposit(4, 1, 64));
        assertEquals(64, FortuneBankMath.deposit(1, Integer.MAX_VALUE, 64));
    }
    @Test void payoutUsesOnlyStoredUnitsAndFreeSpace() {
        assertEquals(3, FortuneBankMath.payout(1, 64, 12, 3));
        assertEquals(1, FortuneBankMath.payout(63, 64, 12, 3));
        assertEquals(2, FortuneBankMath.payout(1, 64, 2, 3));
        assertEquals(0, FortuneBankMath.payout(64, 64, 2, 3));
        assertEquals(0, FortuneBankMath.payout(0, 64, 2, 3));
        assertEquals(0, FortuneBankMath.payout(1, 64, 0, 3));
    }
}
