package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.MeleeFireSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeleeFireTest {
    @Test void familyUsesBothDirectionsWithoutSeizingSelectedExceptions() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String id : new String[]{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"}) {
                FusedPair forward = FusedPair.clean(id, 3, MeleeFireSkills.FIRE, 2);
                assertTrue(MeleeFireSkills.matches(forward));
                assertTrue(MeleeFireSkills.matches(forward.flipped()));
            }
            assertFalse(MeleeFireSkills.matches(FusedPair.clean(MeleeFireSkills.FIRE, 1, "minecraft:frost_walker", 1)));
            assertFalse(MeleeFireSkills.matches(FusedPair.clean(MeleeFireSkills.FIRE, 1, "minecraft:silk_touch", 1)));
            assertFalse(MeleeFireSkills.matches(FusedPair.clean(MeleeFireSkills.FIRE, 1, "minecraft:sweeping_edge", 1)));
            assertTrue(MeleeFireSkills.supportsCarrier(Set.of(FusionCarrier.AXE)));
            assertTrue(MeleeFireSkills.supportsCarrier(Set.of(FusionCarrier.MACE)));
            assertFalse(MeleeFireSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
            assertFalse(MeleeFireSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:smite","secondary":"minecraft:fire_aspect"}}}
                """).getAsJsonObject());
            assertFalse(MeleeFireSkills.matches(FusedPair.clean("minecraft:smite", 1, MeleeFireSkills.FIRE, 1)));
            assertTrue(MeleeFireSkills.matches(FusedPair.clean(MeleeFireSkills.FIRE, 1, "minecraft:smite", 1)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
}
