package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.EmberHarvestCapture;
import enchantment_fusion.mechanics.signatures.EmberHarvestSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class EmberHarvestTest {
    @Test void bonusIsBoundedByRealCountAndLevel() {
        assertEquals(0, EmberHarvestCapture.bonusCount(3, 0));
        assertEquals(0, EmberHarvestCapture.bonusCount(3, -10));
        assertEquals(1, EmberHarvestCapture.bonusCount(1, 64));
        assertEquals(2, EmberHarvestCapture.bonusCount(3, 64));
        assertEquals(1, EmberHarvestCapture.bonusCount(6, 1));
        assertEquals(3, EmberHarvestCapture.bonusCount(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(1, EmberHarvestCapture.bonusCount(Integer.MIN_VALUE, 64));
    }
    @Test void exactDirectionAndDataPackReplacementStayDistinct() {
        SignatureCatalog.reloadFromJson(null);
        FusedPair pair = FusedPair.clean(EmberHarvestSkills.FIRE, 2, EmberHarvestSkills.LOOTING, 3);
        try {
            assertTrue(EmberHarvestSkills.isPair(pair));
            assertFalse(EmberHarvestSkills.isPair(pair.flipped()));
            assertTrue(EmberHarvestSkills.selectedSignature(SignatureCatalog.resolve(pair).orElseThrow()));
            for (FusionCarrier carrier : new FusionCarrier[]{FusionCarrier.SWORD, FusionCarrier.AXE,
                FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT})
                assertTrue(EmberHarvestSkills.supportsCarrier(Set.of(carrier)));
            assertFalse(EmberHarvestSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
            assertFalse(EmberHarvestSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:fire_aspect","secondary":"minecraft:looting"}}}
                """).getAsJsonObject());
            assertFalse(EmberHarvestSkills.isPair(pair));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
}
