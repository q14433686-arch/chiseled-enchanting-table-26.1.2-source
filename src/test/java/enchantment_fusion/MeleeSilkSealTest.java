package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeSilkSealSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeSilkSealTest {
    @Test void chosenSharpnessExceptionAndTwoTemplatesKeepDirectionAndHostBoundaries() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String attack : new String[] {MeleeAttackThemes.SHARP, MeleeAttackThemes.SMITE, MeleeAttackThemes.BANE}) {
                FusedPair silkFirst = FusedPair.clean(MeleeSilkSealSkills.SILK, 1, attack, 4);
                assertTrue(MeleeSilkSealSkills.matches(silkFirst), attack);
                assertFalse(MeleeSilkSealSkills.matches(silkFirst.flipped()), attack);
            }
            assertTrue(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
            assertTrue(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.AXE)));
            assertTrue(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.SPEAR)));
            assertTrue(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.MACE)));
            assertFalse(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
            assertFalse(MeleeSilkSealSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void customExactSignatureTakesPriorityOverSharedTemplate() {
        SignatureCatalog.reloadFromJson(JsonParser.parseString("""
            {"signatures":{"custom":{"primary":"minecraft:silk_touch","secondary":"minecraft:smite"}}}
            """).getAsJsonObject());
        try {
            assertFalse(MeleeSilkSealSkills.matches(FusedPair.clean(MeleeSilkSealSkills.SILK, 1, MeleeAttackThemes.SMITE, 4)));
            assertTrue(MeleeSilkSealSkills.matches(FusedPair.clean(MeleeSilkSealSkills.SILK, 1, MeleeAttackThemes.BANE, 4)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"pristine_trophy":{"primary":"minecraft:silk_touch","secondary":"minecraft:smite"}}}
                """).getAsJsonObject());
            assertFalse(MeleeSilkSealSkills.matches(FusedPair.clean(MeleeSilkSealSkills.SILK, 1, MeleeAttackThemes.SMITE, 4)),
                "An unrelated datapack entry reusing the old id is not the selected Sharpness exception");
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void releaseCombinesOnceAndNeverOverflowsFiniteDamage() {
        assertEquals(10.0F, MeleeSilkSealSkills.combinedDamage(4.0F, 6.0F));
        assertEquals(Float.MAX_VALUE, MeleeSilkSealSkills.combinedDamage(Float.MAX_VALUE, Float.MAX_VALUE));
        assertEquals(4.0F, MeleeSilkSealSkills.combinedDamage(4.0F, Float.NaN));
        assertEquals(4.0F, MeleeSilkSealSkills.combinedDamage(4.0F, Float.POSITIVE_INFINITY));
        assertEquals(0.0F, MeleeSilkSealSkills.combinedDamage(0.0F, 10.0F));
    }
}
