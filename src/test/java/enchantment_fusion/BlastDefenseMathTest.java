package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.BlastDefenseMath;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BlastDefenseMathTest {
    @Test
    void orderedPairsAcceptActualArmorNotWeaponOrShield() {
        FusedPair counter = FusedPair.clean("minecraft:blast_protection", 2, "minecraft:protection", 3);
        FusedPair guard = counter.flipped();
        assertTrue(BlastDefenseSkills.isCounter(counter));
        assertFalse(BlastDefenseSkills.isCounter(guard));
        assertTrue(BlastDefenseSkills.isGuard(guard));
        assertTrue(BlastDefenseSkills.matches(counter, "kinetic_payback"));
        assertFalse(BlastDefenseSkills.matches(counter, "reactive_plate"));
        for (FusionCarrier armor : Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR,
            FusionCarrier.LEG_ARMOR, FusionCarrier.FOOT_ARMOR)) assertTrue(BlastDefenseSkills.supportsCarrier(Set.of(armor)));
        assertFalse(BlastDefenseSkills.supportsCarrier(Set.of(FusionCarrier.SHIELD)));
        assertFalse(BlastDefenseSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
    }

    @Test
    void counterScalesWithBothLevelsAndInverseHealthUntilConfigurableCap() {
        assertEquals(3.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 1, 1, 20, 20), 1.0e-6);
        assertEquals(6.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 1, 1, 10, 20), 1.0e-6);
        assertEquals(6.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 2, 2, 20, 20), 1.0e-6);
        assertEquals(12.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 2, 2, 10, 20), 1.0e-6);
        assertEquals(64.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 255, 255, 1, 20), 1.0e-6);
        assertEquals(8.0F, BlastDefenseMath.counterDamage(4.0F, 100.0F, 1, 1, 10, 20), 1.0e-6F);
        assertEquals(100.0F, BlastDefenseMath.counterDamage(4.0F, 100.0F, 4, 4, 1, 20), 1.0e-6F);
        assertEquals(0.0D, BlastDefenseMath.counterRadius(3.0D, 64.0D, 1, 1, Float.NaN, 20), 1.0e-6);
    }

    @Test
    void nativeKnockbackResistanceIsNotAddedTwiceAndActualProtectionIsConverted() {
        assertEquals(0.6D, BlastDefenseMath.knockbackResistance(0.6D, 4, 15), 1.0e-6);
        assertEquals(0.6D, BlastDefenseMath.knockbackResistance(0.2D, 4, 15), 1.0e-6);
        assertEquals(1.0D, BlastDefenseMath.knockbackResistance(0.1D, 255, 15), 1.0e-6);
        assertEquals(2.0F, BlastDefenseMath.convertedProtection(4.0F, 50), 1.0e-6F);
        assertEquals(0.0F, BlastDefenseMath.convertedProtection(-4.0F, 50), 1.0e-6F);
    }
}
