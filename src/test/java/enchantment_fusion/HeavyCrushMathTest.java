package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.HeavyCrushMath;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class HeavyCrushMathTest {
    @Test
    void bothOrdersUseEnchantmentIdentityNotSlotForLevels() {
        FusedPair forward = FusedPair.clean("minecraft:density", 5, "minecraft:breach", 2);
        FusedPair reverse = forward.flipped();
        assertEquals(new HeavyCrushMath.Levels(5, 2), HeavyCrushMath.levels(forward));
        assertEquals(HeavyCrushMath.levels(forward), HeavyCrushMath.levels(reverse));
        assertTrue(HeavyCrushSkill.matches(forward, "armor_shatter"));
        assertTrue(HeavyCrushSkill.matches(reverse, "armor_pressure"));
        assertFalse(HeavyCrushSkill.matches(reverse, "armor_shatter"));
        assertNull(HeavyCrushMath.levels(FusedPair.clean("minecraft:density", 5, "minecraft:mending", 2)));
        assertEquals(0.125F, HeavyCrushMath.densityArmorReduction(5), 0.00001F);
    }

    @Test
    void supportedWeaponsAreNotTheVanillaEnchantmentIntersection() {
        for (FusionCarrier role : Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR,
            FusionCarrier.MACE, FusionCarrier.TRIDENT, FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
            assertTrue(HeavyCrushSkill.supportsCarrier(Set.of(role)), role.name());
        }
        assertFalse(HeavyCrushSkill.supportsCarrier(Set.of(FusionCarrier.SHIELD)));
        assertFalse(HeavyCrushSkill.supportsCarrier(Set.of(FusionCarrier.CHEST_ARMOR)));
    }

    @Test
    void craterRequiresBothActualDamageAndRealImpact() {
        assertTrue(HeavyCrushMath.qualifies(6.0F, 5.0D, 6.0D, 5.0D));
        assertFalse(HeavyCrushMath.qualifies(5.99F, 20.0D, 6.0D, 5.0D));
        assertFalse(HeavyCrushMath.qualifies(20.0F, 4.99D, 6.0D, 5.0D));
        assertFalse(HeavyCrushMath.qualifies(Float.NaN, 5.0D, 6.0D, 5.0D));
        assertEquals(6.0D, HeavyCrushMath.meleeImpact(1.0F, 2.0D, 0.0D), 0.000001D);
        assertEquals(6.0D, HeavyCrushMath.projectileImpact(3.0D), 0.000001D);
    }

    @Test
    void chargePenaltiesAreMonotonicAndBounded() {
        assertEquals(20, HeavyCrushMath.slowedUseTicks(30));
        assertEquals(10, HeavyCrushMath.slowedUseTicks(15));
        assertEquals(0, HeavyCrushMath.slowedUseTicks(-5));
        assertEquals(38, HeavyCrushMath.slowedChargeDuration(25));
        assertEquals(Integer.MAX_VALUE, HeavyCrushMath.slowedChargeDuration(Integer.MAX_VALUE));
    }
}
