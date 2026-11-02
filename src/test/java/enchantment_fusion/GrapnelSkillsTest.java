package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelDefense;
import enchantment_fusion.mechanics.signatures.GrapnelDurability;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GrapnelSkillsTest {
    @Test void defaultWildcardCoversExplicitActionAdaptersButNotUnimplementedSources() {
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
            for (String ranged : new String[]{GrapnelSkills.POWER, GrapnelSkills.FLAME, GrapnelSkills.PUNCH,
                GrapnelSkills.MULTISHOT, GrapnelSkills.INFINITY, GrapnelSkills.PIERCING, GrapnelSkills.QUICK}) {
                FusedPair pair = FusedPair.clean(ranged, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            for (String defense : new String[]{GrapnelDefense.PROTECTION, GrapnelDefense.FIRE, GrapnelDefense.BLAST, GrapnelDefense.PROJECTILE, GrapnelDefense.FEATHER}) {
                FusedPair pair = FusedPair.clean(defense, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            for (String durable : new String[]{GrapnelDurability.MENDING, GrapnelDurability.UNBREAKING}) {
                FusedPair pair = FusedPair.clean(durable, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            for (String impact : new String[]{GrapnelImpact.KNOCKBACK, GrapnelImpact.BREACH, GrapnelImpact.DENSITY}) {
                FusedPair pair = FusedPair.clean(impact, 3, GrapnelSkills.LURE, 2);
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
                assertTrue(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.MACE)));
                assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.BOW)));
                assertEquals(!GrapnelImpact.DENSITY.equals(impact), GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.SWORD)));
            }
            assertTrue(GrapnelSkills.matches(FusedPair.clean("minecraft:lunge", 1, GrapnelSkills.LURE, 1)));
            assertTrue(GrapnelSkills.matches(FusedPair.clean("minecraft:frost_walker", 1, GrapnelSkills.LURE, 1)));
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
                  "custom_power":{"primary":"minecraft:power","secondary":"minecraft:lure"},
                  "custom_protection":{"primary":"minecraft:protection","secondary":"minecraft:lure"},
                  "custom_mending":{"primary":"minecraft:mending","secondary":"minecraft:lure"},
                  "custom_knockback":{"primary":"minecraft:knockback","secondary":"minecraft:lure"}
                }}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(MeleeAttackThemes.SMITE, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelSkills.SILK, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelSkills.POWER, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelDefense.PROTECTION, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelDurability.MENDING, 1, GrapnelSkills.LURE, 1)));
            assertFalse(GrapnelSkills.matches(FusedPair.clean(GrapnelImpact.KNOCKBACK, 1, GrapnelSkills.LURE, 1)));
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
        FusedPair punch = FusedPair.clean(GrapnelSkills.PUNCH, 2, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(punch, Set.of(FusionCarrier.BOW)));
        assertTrue(GrapnelSkills.supportsCarrier(punch, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(punch, Set.of(FusionCarrier.SWORD)));
        FusedPair multishot = FusedPair.clean(GrapnelSkills.MULTISHOT, 1, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(multishot, Set.of(FusionCarrier.BOW)));
        assertTrue(GrapnelSkills.supportsCarrier(multishot, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(multishot, Set.of(FusionCarrier.SWORD)));
        FusedPair infinity = FusedPair.clean(GrapnelSkills.INFINITY, 1, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(infinity, Set.of(FusionCarrier.BOW)));
        assertTrue(GrapnelSkills.supportsCarrier(infinity, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(infinity, Set.of(FusionCarrier.SWORD)));
        FusedPair piercing = FusedPair.clean(GrapnelSkills.PIERCING, 2, GrapnelSkills.LURE, 1);
        assertFalse(GrapnelSkills.supportsCarrier(piercing, Set.of(FusionCarrier.BOW)));
        assertTrue(GrapnelSkills.supportsCarrier(piercing, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(piercing, Set.of(FusionCarrier.SWORD)));
        FusedPair quick = FusedPair.clean(GrapnelSkills.QUICK, 2, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(quick, Set.of(FusionCarrier.CROSSBOW)));
        assertFalse(GrapnelSkills.supportsCarrier(quick, Set.of(FusionCarrier.BOW)));
        for (String id : new String[]{GrapnelDefense.PROTECTION, GrapnelDefense.BLAST, GrapnelDefense.PROJECTILE}) {
            FusedPair defense = FusedPair.clean(id, 2, GrapnelSkills.LURE, 1);
            assertTrue(GrapnelSkills.supportsCarrier(defense, Set.of(FusionCarrier.CHEST_ARMOR)));
            assertTrue(GrapnelSkills.supportsCarrier(defense, Set.of(FusionCarrier.SHIELD)));
            assertFalse(GrapnelSkills.supportsCarrier(defense, Set.of(FusionCarrier.SWORD)));
        }
        FusedPair fireDefense = FusedPair.clean(GrapnelDefense.FIRE, 2, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(fireDefense, Set.of(FusionCarrier.CHEST_ARMOR)));
        assertFalse(GrapnelSkills.supportsCarrier(fireDefense, Set.of(FusionCarrier.SHIELD)));
        FusedPair feather = FusedPair.clean(GrapnelDefense.FEATHER, 2, GrapnelSkills.LURE, 1);
        assertTrue(GrapnelSkills.supportsCarrier(feather, Set.of(FusionCarrier.FOOT_ARMOR)));
        assertFalse(GrapnelSkills.supportsCarrier(feather, Set.of(FusionCarrier.CHEST_ARMOR)));
        assertFalse(GrapnelSkills.supportsCarrier(feather, Set.of(FusionCarrier.SHIELD)));
    }
}
