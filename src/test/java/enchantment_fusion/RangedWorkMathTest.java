package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.RangedWorkMath;
import enchantment_fusion.mechanics.signatures.RangedWorkSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedWorkMathTest {
    @Test void costsAndScalingAreBounded() {
        assertEquals(1, RangedWorkMath.addEdge(-100));
        assertEquals(3, RangedWorkMath.addEdge(Integer.MAX_VALUE));
        assertEquals(1.0D, RangedWorkMath.speed(5, 2));
        assertTrue(RangedWorkMath.speed(5, 3) > RangedWorkMath.speed(1, 3));
        assertEquals(0.0F, RangedWorkMath.bladeDamage(5, 5, 0, true));
        assertTrue(RangedWorkMath.bladeDamage(5, 5, 3, true) > RangedWorkMath.bladeDamage(1, 1, 1, true));
        assertTrue(RangedWorkMath.bladeDamage(5, 5, 3, true) > RangedWorkMath.bladeDamage(5, 5, 3, false));
        assertEquals(RangedWorkMath.bladeDamage(10, 10, 3, true), RangedWorkMath.bladeDamage(1000, 1000, 1000, true));
        assertEquals(32.0D, RangedWorkMath.BLADE_RANGE);
        assertTrue(RangedWorkMath.BLADE_WIDTH <= 1.0D);
    }
    @Test void sixDirectionsHaveDifferentActualCarriers() {
        for (String id : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair forward = FusedPair.clean(id, 3, RangedWorkSkills.EFFICIENCY, 4), reverse = forward.flipped();
            assertTrue(RangedWorkSkills.matches(forward)); assertTrue(RangedWorkSkills.matches(reverse));
            assertTrue(RangedWorkSkills.shootingFirst(forward)); assertTrue(RangedWorkSkills.miningFirst(reverse));
            for (var role : Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
                assertTrue(RangedWorkSkills.supportsCarrier(forward, Set.of(role)));
                assertFalse(RangedWorkSkills.supportsCarrier(reverse, Set.of(role)));
            }
            for (var role : Set.of(FusionCarrier.PICKAXE, FusionCarrier.AXE, FusionCarrier.SHOVEL, FusionCarrier.HOE)) {
                assertFalse(RangedWorkSkills.supportsCarrier(forward, Set.of(role)));
                assertTrue(RangedWorkSkills.supportsCarrier(reverse, Set.of(role)));
            }
            assertFalse(RangedWorkSkills.supportsCarrier(reverse, Set.of(FusionCarrier.SWORD)));
        }
        assertFalse(RangedWorkSkills.matches(FusedPair.clean("minecraft:multishot", 1, RangedWorkSkills.EFFICIENCY, 1)));
        assertFalse(RangedWorkSkills.matches(FusedPair.clean(RangedWorkSkills.EFFICIENCY, 1, "minecraft:multishot", 1)));
    }
    @Test void explicitAndWildcardSignaturesWinOverNewTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:efficiency"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedWorkSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedWorkSkills.EFFICIENCY, 1)));
            assertFalse(RangedWorkSkills.matches(FusedPair.clean(RangedWorkSkills.EFFICIENCY, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedWorkSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedWorkSkills.EFFICIENCY, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
