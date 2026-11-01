package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.RangedMendingMath;
import enchantment_fusion.mechanics.signatures.RangedMendingSkills;
import enchantment_fusion.mechanics.signatures.RangedWearSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RangedMendingMathTest {
    @Test void repairCreditAndOneArrowEffectsStayFiniteAndBounded() {
        assertEquals(0, RangedMendingMath.addForge(-7, -5));
        assertEquals(5, RangedMendingMath.addForge(3, 2));
        assertEquals(12, RangedMendingMath.addForge(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, RangedMendingMath.spend(-1)); assertEquals(4, RangedMendingMath.spend(100));
        assertEquals(0, RangedMendingMath.damageBonus(3, 0));
        assertEquals(3.75F, RangedMendingMath.damageBonus(6, 4), 1e-6F);
        assertEquals(2.1F, RangedMendingMath.knockbackBonus(Integer.MAX_VALUE, Integer.MAX_VALUE), 1e-6F);
        assertEquals(7.5F, RangedMendingMath.fireSeconds(Integer.MAX_VALUE, Integer.MAX_VALUE), 1e-6F);
    }
    @Test void realOrbRemainderNeverCreatesAdditionalExperience() {
        assertEquals(0, RangedMendingMath.remainingXp(0, 2, 1));
        assertEquals(7, RangedMendingMath.remainingXp(7, 0, 2));
        assertEquals(7, RangedMendingMath.remainingXp(7, 14, 0));
        assertEquals(4, RangedMendingMath.remainingXp(7, 14, 6));
        assertEquals(0, RangedMendingMath.remainingXp(7, 14, 14));
        assertEquals(0, RangedMendingMath.remainingXp(7, 14, 999));
        assertEquals(0, RangedMendingMath.remainingXp(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void sixDirectionsNeedDamageableBowOrCrossbowAndExcludeOtherThemes() {
        for (String theme : new String[]{RangedWearSkills.POWER, RangedWearSkills.PUNCH, RangedWearSkills.FLAME}) {
            FusedPair forward = FusedPair.clean(theme, 3, RangedMendingSkills.MENDING, 1), reverse = forward.flipped();
            assertTrue(RangedMendingSkills.matches(forward)); assertTrue(RangedMendingSkills.matches(reverse));
            assertTrue(RangedMendingSkills.shootingFirst(forward)); assertTrue(RangedMendingSkills.mendingFirst(reverse));
            assertTrue(RangedMendingSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
            assertTrue(RangedMendingSkills.supportsCarrier(Set.of(FusionCarrier.CROSSBOW)));
            assertFalse(RangedMendingSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
        }
        assertFalse(RangedMendingSkills.matches(FusedPair.clean("minecraft:infinity", 1, RangedMendingSkills.MENDING, 1)));
    }
    @Test void exactAndWildcardDataPackSignaturesStillTakePriority() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var data = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:power", "secondary":"minecraft:mending"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:punch"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(data));
            assertFalse(RangedMendingSkills.matches(FusedPair.clean(RangedWearSkills.POWER, 1, RangedMendingSkills.MENDING, 1)));
            assertFalse(RangedMendingSkills.matches(FusedPair.clean(RangedMendingSkills.MENDING, 1, RangedWearSkills.PUNCH, 1)));
            assertTrue(RangedMendingSkills.matches(FusedPair.clean(RangedWearSkills.FLAME, 1, RangedMendingSkills.MENDING, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
