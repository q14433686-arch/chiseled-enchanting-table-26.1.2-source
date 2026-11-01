package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearMath;
import enchantment_fusion.mechanics.signatures.MeleeWorkMath;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeWorkMathTest {
    @Test void speedAndBladeHaveBoundedLevelsAndRequireRealProgress() {
        assertEquals(1.0, MeleeWorkMath.speedMultiplier(5, 2));
        assertTrue(MeleeWorkMath.speedMultiplier(4, 3) > MeleeWorkMath.speedMultiplier(1, 3));
        assertTrue(MeleeWorkMath.speedMultiplier(4, 5) > MeleeWorkMath.speedMultiplier(4, 3));
        assertEquals(MeleeWorkMath.speedMultiplier(10, 5), MeleeWorkMath.speedMultiplier(255, 255));
        assertEquals(0, MeleeWorkMath.bladeDamage(1, 1, 0));
        assertTrue(MeleeWorkMath.bladeDamage(4, 4, 3) > MeleeWorkMath.bladeDamage(1, 1, 1));
        assertEquals(MeleeWorkMath.bladeDamage(10, 10, 3), MeleeWorkMath.bladeDamage(255, 255, 255));
        assertEquals(1, MeleeWorkMath.addCut(0)); assertEquals(MeleeWorkMath.MAX_CUTS, MeleeWorkMath.addCut(Integer.MAX_VALUE));
        assertEquals(1, MeleeWearMath.nextStreak(4, 100, 181));
    }

    @Test void twelveDirectionsUseTheActualActionCarrier() {
        for (String attack : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            for (String work : new String[]{MeleeWorkSkills.EFFICIENCY, MeleeWorkSkills.AQUA}) {
                FusedPair forward = FusedPair.clean(attack, 4, work, 2), reverse = forward.flipped();
                assertTrue(MeleeWorkSkills.matches(forward)); assertTrue(MeleeWorkSkills.matches(reverse));
                assertTrue(MeleeWorkSkills.supportsCarrier(forward, Set.of(FusionCarrier.SWORD)));
                assertFalse(MeleeWorkSkills.supportsCarrier(forward, Set.of(FusionCarrier.HEAD_ARMOR)));
                assertEquals(work.equals(MeleeWorkSkills.AQUA), MeleeWorkSkills.supportsCarrier(reverse, Set.of(FusionCarrier.HEAD_ARMOR)));
                assertEquals(work.equals(MeleeWorkSkills.EFFICIENCY), MeleeWorkSkills.supportsCarrier(reverse, Set.of(FusionCarrier.PICKAXE)));
                assertFalse(MeleeWorkSkills.supportsCarrier(reverse, Set.of(FusionCarrier.BOW)));
            }
        }
    }

    @Test void datapackExactAndSecondaryWildcardWinOverTheseTemplates() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:efficiency"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(MeleeWorkSkills.matches(FusedPair.clean(MeleeAttackThemes.SHARP, 1, MeleeWorkSkills.EFFICIENCY, 1)));
            assertFalse(MeleeWorkSkills.matches(FusedPair.clean(MeleeWorkSkills.AQUA, 1, MeleeAttackThemes.SMITE, 1)));
            assertTrue(MeleeWorkSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, MeleeWorkSkills.AQUA, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
