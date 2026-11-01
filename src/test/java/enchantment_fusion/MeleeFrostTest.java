package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeFrostSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeFrostTest {
    @Test void sharedSemanticsRespectDirectionAndSignaturePriority() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String id : new String[]{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"}) {
                FusedPair forward = FusedPair.clean(id, 3, MeleeFrostSkills.FROST, 2);
                FusedPair reverse = forward.flipped();
                assertTrue(MeleeFrostSkills.matches(forward)); assertTrue(MeleeFrostSkills.matches(reverse));
                assertTrue(MeleeFrostSkills.supportsCarrier(forward, Set.of(FusionCarrier.AXE)));
                assertFalse(MeleeFrostSkills.supportsCarrier(forward, Set.of(FusionCarrier.FOOT_ARMOR)));
                assertTrue(MeleeFrostSkills.supportsCarrier(reverse, Set.of(FusionCarrier.FOOT_ARMOR)));
                assertFalse(MeleeFrostSkills.supportsCarrier(reverse, Set.of(FusionCarrier.AXE)));
            }
            assertFalse(MeleeFrostSkills.matches(FusedPair.clean(MeleeFrostSkills.FROST, 1, "minecraft:fire_aspect", 1)));
            assertFalse(MeleeFrostSkills.matches(FusedPair.clean(MeleeFrostSkills.FROST, 1, "minecraft:feather_falling", 1)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:smite","secondary":"minecraft:frost_walker"}}}
                """).getAsJsonObject());
            assertFalse(MeleeFrostSkills.matches(FusedPair.clean("minecraft:smite", 1, MeleeFrostSkills.FROST, 1)));
            assertTrue(MeleeFrostSkills.matches(FusedPair.clean(MeleeFrostSkills.FROST, 1, "minecraft:smite", 1)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void frostBonusIsBoundedAndNeverReducesRealDamage() {
        assertEquals(12, MeleeFrostSkills.markedDamage(10, 1), 0.0001F);
        assertEquals(2000, MeleeFrostSkills.markedDamage(2000, 10));
        assertEquals(0, MeleeFrostSkills.markedDamage(0, 1));
        assertTrue(Float.isNaN(MeleeFrostSkills.markedDamage(Float.NaN, 2)));
    }
}
