package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GrapnelSkillsTest {
    @Test void defaultWildcardCoversOnlyThreeAttackFirstDirections() {
        var old = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{"spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject()));
            for (String attack : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
                FusedPair pair = FusedPair.clean(attack, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            assertFalse(GrapnelSkills.matches(FusedPair.clean("minecraft:efficiency", 1, GrapnelSkills.LURE, 1)));
        } finally { SignatureCatalog.publish(old); }
    }
    @Test void exactAndDifferentWildcardKeepTheirPriority() {
        var old = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{
                  "spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"},
                  "custom_exact":{"primary":"minecraft:smite","secondary":"minecraft:lure"}
                }}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(MeleeAttackThemes.SMITE, 1, GrapnelSkills.LURE, 1)));
            assertTrue(GrapnelSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, GrapnelSkills.LURE, 1)));
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{"custom_wildcard":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(MeleeAttackThemes.BANE, 1, GrapnelSkills.LURE, 1)));
        } finally { SignatureCatalog.publish(old); }
    }
    @Test void newCarriersNeedRealMeleeCapability() {
        assertTrue(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
        assertTrue(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.AXE)));
        assertTrue(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.SPEAR)));
        assertTrue(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.MACE)));
        assertFalse(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
        assertFalse(GrapnelSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
        assertFalse(GrapnelSkills.supportsCarrier(Set.of()));
    }
}
