package enchantment_fusion.mechanics.signatures;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GrapnelLootingSourcesTest {
    @Test void directionHostAndExactSignaturePriorityAreIndependentOfVanillaIntersection() {
        SignatureCatalog.reloadFromJson(null);
        try {
            FusedPair pair = FusedPair.clean(GrapnelLooting.LOOTING, 3, GrapnelSkills.LURE, 2);
            assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            for (FusionCarrier role : List.of(FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE, FusionCarrier.TRIDENT))
                assertTrue(GrapnelSkills.supportsCarrier(pair, Set.of(role)), role.toString());
            for (FusionCarrier role : List.of(FusionCarrier.BOW, FusionCarrier.FISHING_ROD, FusionCarrier.PICKAXE, FusionCarrier.HEAD_ARMOR))
                assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(role)), role.toString());
            assertFalse(GrapnelLooting.supportsCarrier(null));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:looting","secondary":"minecraft:lure"},
                    "spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject());
            assertFalse(GrapnelSkills.matches(pair));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void missingPrimaryUsesHighestSourceWithoutChangingNativeOrWrongSlot() {
        FusedPair one = FusedPair.clean(GrapnelLooting.LOOTING, 1, GrapnelSkills.LURE, 2);
        FusedPair three = FusedPair.clean(GrapnelLooting.LOOTING, 3, GrapnelSkills.LURE, 1);
        FusedPair unrelated = FusedPair.clean("minecraft:sharpness", 5, GrapnelSkills.LURE, 1);
        assertEquals(0, GrapnelLooting.highestLevel(List.of(), List.of()));
        assertEquals(3, GrapnelLooting.highestLevel(List.of(one, three, unrelated), List.of()));
        assertEquals(5, GrapnelLooting.highestLevel(List.of(three), List.of(new RetainedEnchant(GrapnelLooting.LOOTING, 5))));
        assertEquals(0, GrapnelLooting.projectedLevel(0, 3, false));
        assertEquals(3, GrapnelLooting.projectedLevel(0, 3, true));
        assertEquals(2, GrapnelLooting.projectedLevel(2, 5, true));
        assertEquals(0, GrapnelLooting.projectedLevel(0, -5, true));
    }
    @Test void competingOrderedOrRetainedSourcesCannotClaimTheLoot() {
        FusedPair pair = FusedPair.clean(GrapnelLooting.LOOTING, 3, GrapnelSkills.LURE, 2);
        FusedPair otherPrimary = FusedPair.clean(GrapnelLooting.LOOTING, 1, "minecraft:smite", 2);
        FusedPair otherSecondary = FusedPair.clean("minecraft:smite", 2, GrapnelLooting.LOOTING, 1);
        FusedPair unrelated = FusedPair.clean("minecraft:sharpness", 5, GrapnelSkills.LURE, 2);
        assertFalse(GrapnelLooting.unambiguous(List.of(), List.of(), pair));
        assertTrue(GrapnelLooting.unambiguous(List.of(pair, unrelated), List.of(), pair));
        assertFalse(GrapnelLooting.unambiguous(List.of(pair, pair), List.of(), pair));
        assertFalse(GrapnelLooting.unambiguous(List.of(pair, otherPrimary), List.of(), pair));
        assertFalse(GrapnelLooting.unambiguous(List.of(pair, otherSecondary), List.of(), pair));
        assertFalse(GrapnelLooting.unambiguous(List.of(pair), List.of(new RetainedEnchant(GrapnelLooting.LOOTING, 1)), pair));
        assertTrue(GrapnelLooting.unambiguous(List.of(pair), List.of(new RetainedEnchant("minecraft:unbreaking", 1)), pair));
    }
    @Test void actualDeathNeedsOneAcceptedPositiveLossAndNoNesting() {
        GrapnelLooting.Evidence real = new GrapnelLooting.Evidence();
        real.damage(2); real.death(); assertTrue(real.confirmed(true)); assertFalse(real.confirmed(false));
        GrapnelLooting.Evidence totem = new GrapnelLooting.Evidence(); totem.damage(2); assertFalse(totem.confirmed(true));
        GrapnelLooting.Evidence noLoss = new GrapnelLooting.Evidence(); noLoss.damage(0); noLoss.death(); assertFalse(noLoss.confirmed(true));
        GrapnelLooting.Evidence invalid = new GrapnelLooting.Evidence(); invalid.damage(Float.NaN); invalid.death(); assertFalse(invalid.confirmed(true));
        GrapnelLooting.Evidence duplicated = new GrapnelLooting.Evidence(); duplicated.damage(1); duplicated.damage(1); duplicated.death(); assertFalse(duplicated.confirmed(true));
        GrapnelLooting.Evidence doubleDeath = new GrapnelLooting.Evidence(); doubleDeath.damage(1); doubleDeath.death(); doubleDeath.death(); assertFalse(doubleDeath.confirmed(true));
        GrapnelLooting.Evidence nested = new GrapnelLooting.Evidence(); nested.damage(1); nested.death(); nested.nested(); assertFalse(nested.confirmed(true));
    }
    @Test void countRequiresOneMatchingQueryAndARealPositiveIncrease() {
        assertTrue(GrapnelLooting.positiveIncrease(1, 4, 1, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(0, 4, 1, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(1, 1, 1, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(2, 1, 1, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(1, 4, 0, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(1, 4, 2, 3, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(1, 4, 1, 5, 3, false));
        assertFalse(GrapnelLooting.positiveIncrease(1, 4, 1, 3, 3, true));
    }
    @Test void probabilityUsesTheSameRollAndNeverCreditsBaselineSuccess() {
        assertTrue(GrapnelLootChance.savedByPrimary(0.025F, 0.025F, 0.055F));
        assertTrue(GrapnelLootChance.savedByPrimary(0.05F, 0.025F, 0.055F));
        assertFalse(GrapnelLootChance.savedByPrimary(0.024F, 0.025F, 0.055F));
        assertFalse(GrapnelLootChance.savedByPrimary(0.055F, 0.025F, 0.055F));
        assertFalse(GrapnelLootChance.savedByPrimary(0.5F, 1.0F, 1.0F));
        assertFalse(GrapnelLootChance.savedByPrimary(-0.1F, 0, 1));
        assertFalse(GrapnelLootChance.savedByPrimary(1, 0, 1));
        assertFalse(GrapnelLootChance.savedByPrimary(Float.NaN, 0, 1));
        assertFalse(GrapnelLootChance.savedByPrimary(0.1F, Float.NaN, 1));
        assertFalse(GrapnelLootChance.savedByPrimary(0.1F, 0, Float.POSITIVE_INFINITY));
    }
}
