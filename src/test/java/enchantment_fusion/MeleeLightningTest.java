package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeLightningTest {
    @Test void fourDirectionsKeepDistinctWeaponsAndExactExceptions() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String id : new String[]{"minecraft:smite", "minecraft:bane_of_arthropods"}) {
                FusedPair forward = FusedPair.clean(id, 3, MeleeLightningSkills.CHANNELING, 1), reverse = forward.flipped();
                assertTrue(MeleeLightningSkills.matches(forward)); assertTrue(MeleeLightningSkills.matches(reverse));
                assertTrue(MeleeLightningSkills.supportsCarrier(forward, Set.of(FusionCarrier.AXE)));
                assertFalse(MeleeLightningSkills.supportsCarrier(forward, Set.of(FusionCarrier.TRIDENT)));
                assertTrue(MeleeLightningSkills.supportsCarrier(reverse, Set.of(FusionCarrier.TRIDENT)));
                assertFalse(MeleeLightningSkills.supportsCarrier(reverse, Set.of(FusionCarrier.AXE)));
            }
            assertFalse(MeleeLightningSkills.matches(FusedPair.clean("minecraft:sharpness", 1, MeleeLightningSkills.CHANNELING, 1)));
            assertFalse(MeleeLightningSkills.matches(FusedPair.clean(MeleeLightningSkills.CHANNELING, 1, "minecraft:sharpness", 1)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:smite","secondary":"minecraft:channeling"}}}
                """).getAsJsonObject());
            assertFalse(MeleeLightningSkills.matches(FusedPair.clean("minecraft:smite", 1, MeleeLightningSkills.CHANNELING, 1)));
            assertTrue(MeleeLightningSkills.matches(FusedPair.clean(MeleeLightningSkills.CHANNELING, 1, "minecraft:smite", 1)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void addedDamageNeverReducesTheOriginal() {
        assertEquals(12.5F, MeleeLightningSkills.traceDamage(10, 1), 0.0001F);
        assertEquals(2000, MeleeLightningSkills.traceDamage(2000, 10));
        assertEquals(0, MeleeLightningSkills.traceDamage(0, 1));
        assertTrue(Float.isNaN(MeleeLightningSkills.traceDamage(Float.NaN, 1)));
    }
}
