package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.LootingFireSeedMath;
import enchantment_fusion.mechanics.signatures.LootingFireSeedSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LootingFireSeedMathTest {
    @Test void ringGeometryIsFiniteAndLimitedToItsEdge() {
        double radius = LootingFireSeedMath.radius(2);
        assertEquals(1.9D, radius, 1.0E-9D);
        assertTrue(LootingFireSeedMath.onRing(radius, 0, 0, radius));
        assertTrue(LootingFireSeedMath.onRing(0, 2.0D, radius + 0.64D, radius));
        assertFalse(LootingFireSeedMath.onRing(0, 0, 0, radius));
        assertFalse(LootingFireSeedMath.onRing(radius + 0.66D, 0, 0, radius));
        assertFalse(LootingFireSeedMath.onRing(radius, 2.01D, 0, radius));
        assertFalse(LootingFireSeedMath.onRing(Double.NaN, 0, 0, radius));
        assertFalse(LootingFireSeedMath.onRing(1, 0, 0, Double.POSITIVE_INFINITY));
        assertFalse(LootingFireSeedMath.onRing(1, 0, 0, -1));
    }
    @Test void extremeLevelsAreBounded() {
        assertEquals(LootingFireSeedMath.radius(1), LootingFireSeedMath.radius(Integer.MIN_VALUE));
        assertEquals(3.5D, LootingFireSeedMath.radius(Integer.MAX_VALUE));
        assertEquals(LootingFireSeedMath.damage(10), LootingFireSeedMath.damage(Integer.MAX_VALUE));
        assertEquals(7, LootingFireSeedMath.burnSeconds(Integer.MAX_VALUE));
        assertTrue(LootingFireSeedMath.damage(3) > LootingFireSeedMath.damage(1));
    }
    @Test void onlyTheSelectedOrderedSignatureClaimsTheSharedMeleeCarriers() {
        SignatureCatalog.reloadFromJson(null);
        FusedPair pair = FusedPair.clean(LootingFireSeedSkills.LOOTING, 3, LootingFireSeedSkills.FIRE, 2);
        try {
            assertTrue(LootingFireSeedSkills.isPair(pair));
            assertFalse(LootingFireSeedSkills.isPair(pair.flipped()));
            assertTrue(LootingFireSeedSkills.selectedSignature(SignatureCatalog.resolve(pair).orElseThrow()));
            for (FusionCarrier carrier : new FusionCarrier[]{FusionCarrier.SWORD, FusionCarrier.AXE,
                FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT})
                assertTrue(LootingFireSeedSkills.supportsCarrier(Set.of(carrier)));
            assertFalse(LootingFireSeedSkills.supportsCarrier(Set.of(FusionCarrier.BOW)));
            assertFalse(LootingFireSeedSkills.supportsCarrier(Set.of(FusionCarrier.PICKAXE)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:looting","secondary":"minecraft:fire_aspect"}}}
                """).getAsJsonObject());
            assertFalse(LootingFireSeedSkills.isPair(pair));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
}
