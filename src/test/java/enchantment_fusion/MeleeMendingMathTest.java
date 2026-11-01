package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeMendingMath;
import enchantment_fusion.mechanics.signatures.MeleeMendingSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import org.junit.jupiter.api.Test;

final class MeleeMendingMathTest {
    @Test void diversionAndRepairAreBoundedAndDependOnRealLoss() {
        assertTrue(MeleeMendingMath.divertedDamage(10, 2) < 10);
        assertEquals(0, MeleeMendingMath.repair(0, 2, 100));
        assertEquals(0, MeleeMendingMath.repair(Float.NaN, 2, 100));
        assertEquals(0, MeleeMendingMath.repair(10, 2, 0));
        assertTrue(MeleeMendingMath.repair(20, 3, 100) > MeleeMendingMath.repair(5, 3, 100));
        assertEquals(1, MeleeMendingMath.repair(100, 3, 1));
        assertEquals(8, MeleeMendingMath.repair(Float.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(MeleeMendingMath.fraction(10), MeleeMendingMath.fraction(Integer.MAX_VALUE));
    }
    @Test void realRepairChargesAndBladeCannotOverflow() {
        assertEquals(0, MeleeMendingMath.addForge(-10, -10));
        assertEquals(8, MeleeMendingMath.addForge(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, MeleeMendingMath.spend(-1)); assertEquals(4, MeleeMendingMath.spend(Integer.MAX_VALUE));
        assertEquals(0, MeleeMendingMath.bladeDamage(1, 1, 0));
        assertTrue(MeleeMendingMath.bladeDamage(4, 3, 4) > MeleeMendingMath.bladeDamage(1, 1, 1));
        assertEquals(MeleeMendingMath.bladeDamage(10, 10, 4), MeleeMendingMath.bladeDamage(255, 255, 255));
    }
    @Test void promotedTemplateDoesNotReplaceTheExplicitSharpnessPair() {
        for (String attack : new String[]{MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            FusedPair pair = FusedPair.clean(attack, 3, DurabilityEconomy.MENDING, 1);
            assertTrue(MeleeMendingSkills.matches(pair)); assertTrue(MeleeMendingSkills.matches(pair.flipped()));
        }
        FusedPair explicit = FusedPair.clean(MeleeAttackThemes.SHARP, 3, DurabilityEconomy.MENDING, 1);
        assertFalse(MeleeMendingSkills.matches(explicit)); assertFalse(MeleeMendingSkills.matches(explicit.flipped()));
    }
    @Test void dataPackSignaturesTakePrecedence() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "exact": {"primary":"minecraft:smite", "secondary":"minecraft:mending"},
                  "wild": {"primary":"*", "secondary":"minecraft:bane_of_arthropods"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(MeleeMendingSkills.matches(FusedPair.clean(MeleeAttackThemes.SMITE, 1, DurabilityEconomy.MENDING, 1)));
            assertFalse(MeleeMendingSkills.matches(FusedPair.clean(DurabilityEconomy.MENDING, 1, MeleeAttackThemes.BANE, 1)));
            assertTrue(MeleeMendingSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, DurabilityEconomy.MENDING, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }
}
