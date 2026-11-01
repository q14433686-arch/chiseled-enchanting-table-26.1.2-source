package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeFortuneMath;
import enchantment_fusion.mechanics.signatures.MeleeFortuneSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeFortuneTest {
    @Test void chanceDamageAndChargeAreFiniteAndBounded() {
        assertEquals(40, MeleeFortuneMath.chance(10, 10, 3));
        assertEquals(90, MeleeFortuneMath.chance(90, 50, Integer.MAX_VALUE));
        assertEquals(0, MeleeFortuneMath.chance(-999, 0, 1));
        assertEquals(13.0F, MeleeFortuneMath.heavyDamage(10, 1), 0.0001F);
        assertEquals(2000.0F, MeleeFortuneMath.heavyDamage(2000, 10));
        assertEquals(0, MeleeFortuneMath.scatterDamage(Float.NaN, 3));
        assertTrue(MeleeFortuneMath.scatterDamage(1000, 10) <= 8);
        assertEquals(5, MeleeFortuneMath.chargeTicks(4.5F));
        assertEquals(0, MeleeFortuneMath.chargeTicks(Float.NaN));
        assertEquals(0, MeleeFortuneMath.chargeTicks(Float.POSITIVE_INFINITY));
    }
    @Test void selectedExceptionAndFourFamilyDirectionsDoNotOverrideDatapacks() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String id : new String[]{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"}) {
                FusedPair forward = FusedPair.clean(id, 3, MeleeFortuneSkills.FORTUNE, 2);
                assertTrue(MeleeFortuneSkills.matches(forward)); assertTrue(MeleeFortuneSkills.matches(forward.flipped()));
            }
            assertTrue(MeleeFortuneSkills.supportsCarrier(Set.of(FusionCarrier.MACE)));
            assertTrue(MeleeFortuneSkills.supportsCarrier(Set.of(FusionCarrier.SWORD)));
            assertFalse(MeleeFortuneSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
            assertFalse(MeleeFortuneSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:smite","secondary":"minecraft:fortune"}}}
                """).getAsJsonObject());
            assertFalse(MeleeFortuneSkills.matches(FusedPair.clean("minecraft:smite", 1, "minecraft:fortune", 1)));
            assertFalse(MeleeFortuneSkills.matches(FusedPair.clean("minecraft:sharpness", 1, "minecraft:fortune", 1)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
}
