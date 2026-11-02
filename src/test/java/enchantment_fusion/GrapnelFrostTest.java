package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.GrapnelFrost;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GrapnelFrostTest {
    @Test void frostGrapnelIsOrderedWornBootsAndKeepsCatalogPriority() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.reloadFromJson(null);
            FusedPair pair = FusedPair.clean(GrapnelFrost.FROST, 2, GrapnelSkills.LURE, 1);
            assertTrue(GrapnelFrost.isPair(pair)); assertFalse(GrapnelFrost.isPair(pair.flipped()));
            assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            assertTrue(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.LEG_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.SWORD)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.FISHING_ROD)));
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{"exact":{"primary":"minecraft:frost_walker","secondary":"minecraft:lure"},
                               "wild":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(pair));
        } finally { SignatureCatalog.publish(previous); }
    }
    @Test void nativeLocationEffectIsNeverReplayedAndSlotConditionsRemainAuthoritative() {
        assertTrue(PrimaryLocationProjection.needsProjection(0, true));
        assertFalse(PrimaryLocationProjection.needsProjection(1, true));
        assertFalse(PrimaryLocationProjection.needsProjection(0, false));
    }
}
