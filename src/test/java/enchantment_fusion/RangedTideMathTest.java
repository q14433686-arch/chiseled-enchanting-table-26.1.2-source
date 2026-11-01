package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedTideMath;
import enchantment_fusion.mechanics.signatures.RangedTideSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class RangedTideMathTest {
    @Test void realSwimDistanceIsBoundedAndFinite() {
        assertEquals(2.0D, RangedTideMath.addDistance(0, 50));
        assertEquals(3.0D, RangedTideMath.addDistance(2, 1));
        assertEquals(3.0D, RangedTideMath.addDistance(3, 2));
        assertEquals(0.0D, RangedTideMath.addDistance(Double.NaN, Double.NaN));
        assertEquals(0.0D, RangedTideMath.addDistance(-4, -10));
    }
    @Test void closestSegmentDetectsCrossingsAndRejectsSeparatePaths() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(4, 0, 0);
        assertEquals(0, RangedTideMath.distanceSquared(a, b, new Vec3(2, 0, -2), new Vec3(2, 0, 2)), 1e-8);
        assertEquals(4, RangedTideMath.distanceSquared(a, b, new Vec3(0, 2, 0), new Vec3(4, 2, 0)), 1e-8);
        assertEquals(1, RangedTideMath.distanceSquared(a, a, new Vec3(1, 0, 0), new Vec3(1, 0, 0)), 1e-8);
        assertEquals(new Vec3(4, 0, 0), RangedTideMath.closest(a, b, new Vec3(8, 3, 0)));
        assertEquals(Double.POSITIVE_INFINITY, RangedTideMath.distanceSquared(a, b, new Vec3(Double.NaN, 0, 0), b));
    }
    @Test void sixTideDirectionsHaveBowOrBootsCarrier() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair shooting = FusedPair.clean(theme, 3, RangedTideSkills.DEPTH, 2), depth = shooting.flipped();
            assertTrue(RangedTideSkills.matches(shooting)); assertTrue(RangedTideSkills.matches(depth));
            for (var role : Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
                assertTrue(RangedTideSkills.supportsCarrier(shooting, Set.of(role)));
                assertFalse(RangedTideSkills.supportsCarrier(depth, Set.of(role)));
            }
            assertFalse(RangedTideSkills.supportsCarrier(shooting, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertTrue(RangedTideSkills.supportsCarrier(depth, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(RangedTideSkills.supportsCarrier(depth, Set.of(FusionCarrier.HEAD_ARMOR)));
        }
        assertFalse(RangedTideSkills.matches(FusedPair.clean("minecraft:multishot", 1, RangedTideSkills.DEPTH, 1)));
    }
    @Test void exactAndWildcardSignaturesRemainPriorToThisTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:depth_strider"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedTideSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedTideSkills.DEPTH, 1)));
            assertFalse(RangedTideSkills.matches(FusedPair.clean(RangedTideSkills.DEPTH, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedTideSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedTideSkills.DEPTH, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
