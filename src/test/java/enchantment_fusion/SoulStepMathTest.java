package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.SoulStepMath;
import enchantment_fusion.mechanics.signatures.SoulStepSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SoulStepMathTest {
    private static final FusedPair BOOTS = FusedPair.clean("minecraft:soul_speed", 3, "minecraft:swift_sneak", 2);
    private static final FusedPair LEGS = BOOTS.flipped();

    @Test void orderedActionsHaveDifferentCarriersAndNames() {
        assertTrue(SoulStepSkills.matches(BOOTS, SoulStepSkills.FORWARD));
        assertTrue(SoulStepSkills.matches(LEGS, SoulStepSkills.REVERSE));
        assertFalse(SoulStepSkills.matches(BOOTS, SoulStepSkills.REVERSE));
        assertTrue(SoulStepSkills.supportsCarrier(BOOTS, Set.of(FusionCarrier.FOOT_ARMOR)));
        assertFalse(SoulStepSkills.supportsCarrier(BOOTS, Set.of(FusionCarrier.LEG_ARMOR)));
        assertTrue(SoulStepSkills.supportsCarrier(LEGS, Set.of(FusionCarrier.LEG_ARMOR)));
        assertFalse(SoulStepSkills.supportsCarrier(LEGS, Set.of(FusionCarrier.FOOT_ARMOR)));
    }
    @Test void energyOnlyChangesWhenActuallyCollectedOrSpent() {
        assertEquals(1, SoulStepMath.energy(0, 200, true, false));
        assertEquals(200, SoulStepMath.energy(200, 200, true, false));
        assertEquals(2, SoulStepMath.energy(2, 200, false, false));
        assertEquals(1, SoulStepMath.energy(2, 200, false, true));
        assertEquals(0, SoulStepMath.energy(0, 200, false, true));
        assertTrue(SoulStepMath.actualStep(0.006D));
        assertFalse(SoulStepMath.actualStep(0.0D));
        assertFalse(SoulStepMath.actualStep(10.0D));
        assertFalse(SoulStepMath.actualStep(Double.NaN));
    }
    @Test void levelSpeedIsBoundedAndDoesNotMutateEquipment() {
        assertEquals(0.6D, SoulStepMath.fastSneak(2), 0.00001D);
        assertEquals(1.0D, SoulStepMath.fastSneak(100), 0.00001D);
        assertEquals(0.7D, SoulStepMath.fastCrawl(3), 0.00001D);
        assertEquals(0.9D, SoulStepMath.fastCrawl(100), 0.00001D);
    }
}
