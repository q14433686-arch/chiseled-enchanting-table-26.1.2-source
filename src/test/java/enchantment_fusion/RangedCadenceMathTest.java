package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.RangedCadenceMath;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedCadenceMathTest {
    private static final FusedPair BOW = FusedPair.clean("minecraft:power", 5, "minecraft:quick_charge", 3);
    private static final FusedPair CROSSBOW = BOW.flipped();

    @Test void directionsRequireTheirRealWeaponActions() {
        assertTrue(RangedCadenceSkills.matches(BOW, RangedCadenceSkills.BOW));
        assertTrue(RangedCadenceSkills.matches(CROSSBOW, RangedCadenceSkills.CROSSBOW));
        assertFalse(RangedCadenceSkills.matches(BOW, RangedCadenceSkills.CROSSBOW));
        assertTrue(RangedCadenceSkills.supportsCarrier(BOW, Set.of(FusionCarrier.BOW)));
        assertFalse(RangedCadenceSkills.supportsCarrier(BOW, Set.of(FusionCarrier.CROSSBOW)));
        assertTrue(RangedCadenceSkills.supportsCarrier(CROSSBOW, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(RangedCadenceSkills.supportsCarrier(CROSSBOW, Set.of(FusionCarrier.BOW)));
    }
    @Test void rewardsAreBoundedAndNeverProduceAProjectile() {
        assertEquals(6, RangedCadenceMath.drawCredit(3, 2, 12));
        assertEquals(12, RangedCadenceMath.drawCredit(100, 10, 12));
        assertEquals(0, RangedCadenceMath.drawCredit(3, 0, 12));
        assertEquals(1, RangedCadenceMath.nextLoad(0, 3));
        assertEquals(3, RangedCadenceMath.nextLoad(3, 3));
        assertEquals(2.2D, RangedCadenceMath.arrowMultiplier(3, 5, 8), 0.00001D);
        assertEquals(1.0D, RangedCadenceMath.arrowMultiplier(0, 5, 8), 0.00001D);
        assertEquals(5.0D, RangedCadenceMath.arrowMultiplier(100, 100, 100), 0.00001D);
    }
    @Test void synchronizedDrawViewCarriesHandPhaseAndGeneration() {
        int pending = RangedCadenceMath.drawView(6, true, false, 34);
        int active = RangedCadenceMath.drawView(6, true, true, 34);
        assertEquals(6, RangedCadenceMath.viewTicks(pending));
        assertEquals(34, RangedCadenceMath.viewGeneration(active));
        assertTrue(RangedCadenceMath.viewOffhand(pending));
        assertFalse(RangedCadenceMath.viewActive(pending));
        assertTrue(RangedCadenceMath.viewActive(active));
        assertEquals(0, RangedCadenceMath.drawView(0, false, false, 1));
        assertEquals(0, RangedCadenceMath.viewTicks(0));
        assertEquals(0, RangedCadenceMath.viewTicks(-1));
    }
}
