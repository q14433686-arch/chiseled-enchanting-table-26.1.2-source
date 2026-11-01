package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedGuardMath;
import enchantment_fusion.mechanics.signatures.RangedGuardSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedGuardMathTest {
    @Test void screenAndMitigationAreBounded() {
        assertEquals(2.25D, RangedGuardMath.radius(1)); assertEquals(3.5D, RangedGuardMath.radius(Integer.MAX_VALUE));
        assertEquals(3, RangedGuardMath.blocks(1)); assertEquals(6, RangedGuardMath.blocks(Integer.MAX_VALUE));
        assertEquals(12.0F, RangedGuardMath.charge(10, 50)); assertEquals(0.0F, RangedGuardMath.charge(Float.NaN, Float.NaN));
        assertEquals(3.0F, RangedGuardMath.charge(3, -1));
    }
    @Test void sixDirectionsUseBowOrRealDefenseCarrier() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair shooting = FusedPair.clean(theme, 3, RangedGuardSkills.PROTECTION, 2), guard = shooting.flipped();
            assertTrue(RangedGuardSkills.matches(shooting)); assertTrue(RangedGuardSkills.matches(guard));
            for (var role : Set.of(FusionCarrier.BOW, FusionCarrier.CROSSBOW)) {
                assertTrue(RangedGuardSkills.supportsCarrier(shooting, Set.of(role)));
                assertFalse(RangedGuardSkills.supportsCarrier(guard, Set.of(role)));
            }
            for (var role : Set.of(FusionCarrier.HEAD_ARMOR, FusionCarrier.CHEST_ARMOR, FusionCarrier.LEG_ARMOR,
                FusionCarrier.FOOT_ARMOR, FusionCarrier.SHIELD)) {
                assertFalse(RangedGuardSkills.supportsCarrier(shooting, Set.of(role)));
                assertTrue(RangedGuardSkills.supportsCarrier(guard, Set.of(role)));
            }
            assertFalse(RangedGuardSkills.supportsCarrier(guard, Set.of(FusionCarrier.SWORD)));
        }
        assertFalse(RangedGuardSkills.matches(FusedPair.clean("minecraft:multishot", 1, RangedGuardSkills.PROTECTION, 1)));
    }
    @Test void exactAndWildcardSignaturesStayAheadOfTemplate() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:protection"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(RangedGuardSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedGuardSkills.PROTECTION, 1)));
            assertFalse(RangedGuardSkills.matches(FusedPair.clean(RangedGuardSkills.PROTECTION, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedGuardSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedGuardSkills.PROTECTION, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
