package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeGuardMath;
import enchantment_fusion.mechanics.signatures.MeleeGuardSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeGuardMathTest {
    @Test void marginalMitigationAccountsForTheVanillaProtectionCap() {
        assertEquals(1.28F, MeleeGuardMath.contribution(8, 4, 4), 1.0E-6F);
        assertEquals(0.64F, MeleeGuardMath.contribution(8, 22, 4), 1.0E-6F);
        assertEquals(0, MeleeGuardMath.contribution(8, 25, 4));
        assertEquals(0, MeleeGuardMath.contribution(8, 4, 5));
        assertEquals(0, MeleeGuardMath.contribution(Float.NaN, 4, 4));
        assertEquals(0, MeleeGuardMath.contribution(8, Float.POSITIVE_INFINITY, 4));
        assertEquals(0, MeleeGuardMath.contribution(-1, 4, 4));
    }

    @Test void frontMustActuallyApproachAndImpactFromTheFacingSide() {
        assertTrue(MeleeGuardMath.front(1, 0, 0, -2, 0, 0, 0.3, 0, 0));
        assertFalse(MeleeGuardMath.front(1, 0, 0, 2, 0, 0, 0.3, 0, 0));
        assertFalse(MeleeGuardMath.front(1, 0, 0, -2, 0, 0, -0.3, 0, 0));
        assertFalse(MeleeGuardMath.front(1, 0, 0, 0, 0, 0, 0.3, 0, 0));
        assertFalse(MeleeGuardMath.front(1, 0, 0, -2, 0, 0, 4, 0, 0));
        assertFalse(MeleeGuardMath.front(Double.NaN, 0, 0, -2, 0, 0, 0.3, 0, 0));
    }

    @Test void sixDirectionsHaveDirectionalCarrierRules() {
        for (String id : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
            FusedPair attack = FusedPair.clean(id, 4, MeleeGuardSkills.PROTECTION, 2), defense = attack.flipped();
            assertTrue(MeleeGuardSkills.matches(attack)); assertTrue(MeleeGuardSkills.matches(defense));
            assertTrue(MeleeGuardSkills.supportsCarrier(attack, Set.of(FusionCarrier.SWORD)));
            assertFalse(MeleeGuardSkills.supportsCarrier(attack, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertTrue(MeleeGuardSkills.supportsCarrier(defense, Set.of(FusionCarrier.SHIELD)));
            assertTrue(MeleeGuardSkills.supportsCarrier(defense, Set.of(FusionCarrier.CHEST_ARMOR)));
            assertFalse(MeleeGuardSkills.supportsCarrier(defense, Set.of(FusionCarrier.SWORD)));
        }
    }

    @Test void dataPackExactAndWildcardSignaturesTakePrecedence() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            var source = JsonParser.parseString("""
                {"signatures": {
                  "custom_exact": {"primary":"minecraft:sharpness", "secondary":"minecraft:protection"},
                  "custom_wild": {"primary":"*", "secondary":"minecraft:smite"}
                }}
                """).getAsJsonObject();
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(source));
            assertFalse(MeleeGuardSkills.matches(FusedPair.clean(MeleeAttackThemes.SHARP, 1, MeleeGuardSkills.PROTECTION, 1)));
            assertFalse(MeleeGuardSkills.matches(FusedPair.clean(MeleeGuardSkills.PROTECTION, 1, MeleeAttackThemes.SMITE, 1)));
            assertTrue(MeleeGuardSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, MeleeGuardSkills.PROTECTION, 1)));
        } finally { SignatureCatalog.publish(previous); }
    }

    @Test void counterUsesBoundedLevelsAndRealCredit() {
        assertEquals(0, MeleeGuardMath.counterDamage(1, 1, 0));
        assertTrue(MeleeGuardMath.counterDamage(4, 3, 4) > MeleeGuardMath.counterDamage(1, 1, 2));
        assertEquals(MeleeGuardMath.counterDamage(10, 10, 4), MeleeGuardMath.counterDamage(255, 255, 200));
    }
}
