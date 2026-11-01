package enchantment_fusion;

import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath;
import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Box;
import enchantment_fusion.mechanics.signatures.GrapnelPiercingMath.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrapnelPiercingMathTest {
    private static final Point X = new Point(1, 0, 0);
    private static final Box TARGET = new Box(3.7, 0, -0.3, 4.3, 2, 0.3);
    @Test void onlyAContinuousForwardStepPastTheWholeTargetQualifies() {
        Point start = new Point(1, 1, 0), hit = new Point(3.7, 1, 0), end = new Point(5, 1, 0);
        assertTrue(GrapnelPiercingMath.validStep(start, end, new Point(4, 0, 0), X));
        assertTrue(GrapnelPiercingMath.containsImpact(start, end, hit, X));
        assertTrue(GrapnelPiercingMath.passed(TARGET, end, X));
        assertFalse(GrapnelPiercingMath.passed(TARGET, new Point(4.2, 1, 0), X));
    }
    @Test void rejectedDiscontinuitiesCannotPretendToBePiercing() {
        Point start = new Point(1, 1, 0), hit = new Point(3.7, 1, 0);
        assertFalse(GrapnelPiercingMath.validStep(start, new Point(20, 1, 0), new Point(4, 0, 0), X));
        assertFalse(GrapnelPiercingMath.validStep(start, new Point(0, 1, 0), new Point(4, 0, 0), X));
        assertFalse(GrapnelPiercingMath.validStep(start, new Point(5, 1, 2), new Point(5, 0, 0), X));
        assertFalse(GrapnelPiercingMath.containsImpact(start, new Point(5, 1, 0), new Point(hit.x(), 1, 1), X));
        assertFalse(GrapnelPiercingMath.continuous(new Point(4, 1, 0), new Point(5, 1, 0)));
        assertFalse(GrapnelPiercingMath.passed(TARGET, new Point(Double.NaN, 1, 0), X));
    }
    @Test void aSlowArrowMayFinishPassingOnTheFollowingContinuousStep() {
        Point firstEnd = new Point(4.1, 1, 0), nextStart = new Point(4.1, 1, 0), nextEnd = new Point(4.6, 1, 0);
        assertFalse(GrapnelPiercingMath.passed(TARGET, firstEnd, X));
        assertTrue(GrapnelPiercingMath.continuous(firstEnd, nextStart));
        assertTrue(GrapnelPiercingMath.validStep(nextStart, nextEnd, new Point(0.5, 0, 0), X));
        assertTrue(GrapnelPiercingMath.passed(TARGET, nextEnd, X));
    }
    @Test void supportPlaneHandlesDiagonalAndReverseShots() {
        double k = Math.sqrt(0.5D); Point diagonal = new Point(k, 0, k);
        Box box = new Box(3.7, 0, 3.7, 4.3, 2, 4.3);
        assertFalse(GrapnelPiercingMath.passed(box, new Point(4.2, 1, 4.2), diagonal));
        assertTrue(GrapnelPiercingMath.passed(box, new Point(4.4, 1, 4.4), diagonal));
        assertTrue(GrapnelPiercingMath.passed(TARGET, new Point(3.6, 1, 0), new Point(-1, 0, 0)));
    }
}
