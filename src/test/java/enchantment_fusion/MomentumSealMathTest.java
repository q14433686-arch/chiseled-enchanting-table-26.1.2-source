package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.MomentumSealMath;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class MomentumSealMathTest {
    private static final FusedPair FORWARD = FusedPair.clean("minecraft:knockback", 2, "minecraft:silk_touch", 1);
    @Test void directionAndHostsAreNotSymmetric() {
        assertTrue(MomentumSealSkills.matches(FORWARD, MomentumSealSkills.FORWARD));
        assertFalse(MomentumSealSkills.matches(FORWARD.flipped(), MomentumSealSkills.FORWARD));
        assertTrue(MomentumSealSkills.matches(FORWARD.flipped(), MomentumSealSkills.REVERSE));
        for (FusionCarrier carrier : Set.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE))
            assertTrue(MomentumSealSkills.supportsCarrier(Set.of(carrier)));
        assertFalse(MomentumSealSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
    }
    @Test void releaseUsesOnlyHorizontalDirectionAndKeepsGravity() {
        Vec3 release = MomentumSealMath.release(new Vec3(0, 50, -5), new Vec3(1, 0, 0), 0.75, -0.2);
        assertEquals(0.0, release.x, 1.0e-9);
        assertEquals(-0.2, release.y, 1.0e-9);
        assertEquals(-0.75, release.z, 1.0e-9);
        Vec3 fallback = MomentumSealMath.release(new Vec3(0, 10, 0), new Vec3(3, 0, 0), 99, 0.1);
        assertEquals(8.0, fallback.x, 1.0e-9);
        assertEquals(0.1, fallback.y, 1.0e-9);
    }
    @Test void nonfiniteAndZeroInputsCannotProduceInvalidVelocity() {
        assertEquals(0.0, MomentumSealMath.speed(new Vec3(Double.NaN, 1, 0)));
        Vec3 safe = MomentumSealMath.release(Vec3.ZERO, Vec3.ZERO, Double.POSITIVE_INFINITY, Double.NaN);
        assertEquals(Vec3.ZERO, safe);
    }
}
