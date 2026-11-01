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
    @Test void defaultWildcardCoversOnlyTheEightExplicitActionAdapters() {
        var old = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{"spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject()));
            for (String attack : new String[]{MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
                FusedPair pair = FusedPair.clean(attack, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            for (String work : new String[]{GrapnelSkills.EFFICIENCY, GrapnelSkills.SILK, GrapnelSkills.FORTUNE}) {
                FusedPair pair = FusedPair.clean(work, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            for (String ranged : new String[]{GrapnelSkills.POWER, GrapnelSkills.FLAME}) {
                FusedPair pair = FusedPair.clean(ranged, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            assertFalse(GrapnelSkills.matches(FusedPair.clean("minecraft:infinity", 1, GrapnelSkills.LURE, 1)));
        } finally { SignatureCatalog.publish(old); }
    }
    @Test void exactAndDifferentWildcardKeepTheirPriority() {
        var old = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{
                  "spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"},
                  "custom_exact":{"primary":"minecraft:smite","secondary":"minecraft:lure"},
                  "custom_silk":{"primary":"minecraft:silk_touch","secondary":"minecraft:lure"},
                  "custom_power":{"primary":"minecraft:power","secondary":"minecraft:lure"}
                }}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(MeleeAttackThemes.SMITE, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelSkills.SILK, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelSkills.POWER, 1, GrapnelSkills.LURE, 1)));
            assertTrue(GrapnelSkills.matches(FusedPair.clean(GrapnelSkills.FORTUNE, 1, GrapnelSkills.LURE, 1)));
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
        FusedPair tool = FusedPair.clean(GrapnelSkills.EFFICIENCY, 1, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(tool, Set.of(FusionCarrier.PICKAXE)));
        assertTrue(GrapnelSkills.supportsCarrier(tool, Set.of(FusionCarrier.AXE)));
        assertFalse(GrapnelSkills.supportsCarrier(tool, Set.of(FusionCarrier.SWORD)));
        assertFalse(GrapnelSkills.supportsCarrier(tool, Set.of(FusionCarrier.BOW)));
        FusedPair power = FusedPair.clean(GrapnelSkills.POWER, 1, GrapnelSkills.LURE, 1);
        FusedPair flame = FusedPair.clean(GrapnelSkills.FLAME, 1, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(power, Set.of(FusionCarrier.BOW)));
        assertFalse(GrapnelSkills.supportsCarrier(power, Set.of(FusionCarrier.CROSSBOW)));
        assertTrue(GrapnelSkills.supportsCarrier(flame, Set.of(FusionCarrier.BOW)));
        assertTrue(GrapnelSkills.supportsCarrier(flame, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(flame, Set.of(FusionCarrier.SWORD)));
    }
}
