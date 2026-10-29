package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.compat.CetCleanseIds;
import enchantment_fusion.compat.CetCleanseIds.Kind;
import enchantment_fusion.compat.CetCleanseIds.ParsedCleanseId;
import enchantment_fusion.compat.CetCleanseOperations;
import enchantment_fusion.model.FusedPair;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class CetCleanseIdsAndMathTest {

    @Test
    void syntheticCleanseIdsRoundTripAcrossAberrantFusedAndTransmute() {
        Identifier abId = CetCleanseIds.aberrantId(1, "minecraft:silk_touch");
        assertTrue(CetCleanseIds.isSyntheticCleanseId(abId));
        ParsedCleanseId parsedAb = CetCleanseIds.parse(abId);
        assertNotNull(parsedAb);
        assertEquals(Kind.ABERRANT, parsedAb.kind());
        assertEquals(1, parsedAb.pairIndex());
        assertEquals("minecraft:silk_touch", parsedAb.underlyingEnchantId());
        assertEquals(Identifier.parse("minecraft:silk_touch"), CetCleanseIds.underlyingEnchantmentIdentifier(abId));

        Identifier fusedId = CetCleanseIds.fusedId(0, "minecraft:channeling");
        assertTrue(CetCleanseIds.isSyntheticCleanseId(fusedId));
        ParsedCleanseId parsedFused = CetCleanseIds.parse(fusedId);
        assertNotNull(parsedFused);
        assertEquals(Kind.FUSED, parsedFused.kind());
        assertEquals(0, parsedFused.pairIndex());
        assertEquals("minecraft:channeling", parsedFused.underlyingEnchantId());

        Identifier transId = CetCleanseIds.transmuteId("minecraft:loyalty");
        assertTrue(CetCleanseIds.isSyntheticCleanseId(transId));
        ParsedCleanseId parsedTrans = CetCleanseIds.parse(transId);
        assertNotNull(parsedTrans);
        assertEquals(Kind.TRANSMUTE, parsedTrans.kind());
        assertEquals(-1, parsedTrans.pairIndex());
        assertEquals("minecraft:loyalty", parsedTrans.underlyingEnchantId());

        assertFalse(CetCleanseIds.isSyntheticCleanseId(Identifier.parse("minecraft:sharpness")));
        assertNull(CetCleanseIds.parse(Identifier.parse("minecraft:sharpness")));
    }

    @Test
    void essenceRefundMathHonorsCleanVsAberrantAndStepDownScaling() {
        FusedPair clean = FusedPair.clean("minecraft:sharpness", 5, "minecraft:frost_walker", 2);
        FusedPair aberrant = new FusedPair("minecraft:silk_touch", 1, "minecraft:fortune", 3, "ab_greedy_abyss");

        int cleanEssence = CetCleanseOperations.essenceForFusedPair(clean, 1.0D);
        assertTrue(cleanEssence >= 40, "Clean high-level fused pair should yield meaningful XP essence");
        assertEquals(0, CetCleanseOperations.essenceForFusedPair(aberrant, 1.0D), "Aberrant curse must yield 0 essence until purified");

        int trans5 = CetCleanseOperations.essenceForTransmuted(5, 1.0D);
        int trans3 = CetCleanseOperations.essenceForTransmuted(3, 1.0D);
        assertTrue(trans5 > trans3);
        assertEquals(trans5 - trans3, CetCleanseOperations.essenceForTransmutedStepDown(5, 3, 1.0D));
        assertEquals(0, CetCleanseOperations.essenceForTransmutedStepDown(2, 2, 1.0D));
        assertEquals(0, CetCleanseOperations.essenceForTransmutedStepDown(1, 0, 1.0D));
    }
}
