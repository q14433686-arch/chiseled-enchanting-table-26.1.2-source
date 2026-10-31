package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.DurabilityEconomyMath;
import enchantment_fusion.model.FusedPair;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class DurabilityEconomyMathTest {
    @Test
    void orderedPairAndConfigurableGrowthAreBounded() {
        FusedPair growth = FusedPair.clean("minecraft:mending", 1, "minecraft:unbreaking", 3);
        FusedPair rescue = growth.flipped();
        assertTrue(DurabilityEconomy.isGrowth(growth));
        assertFalse(DurabilityEconomy.isGrowth(rescue));
        assertTrue(DurabilityEconomy.isRescue(rescue));
        assertTrue(DurabilityEconomy.matches(growth, "overflow_plating"));
        assertFalse(DurabilityEconomy.matches(growth, "self_healing_lattice"));
        assertEquals(2000, DurabilityEconomyMath.growthCap(1000, 200));
        assertEquals(Integer.MAX_VALUE, DurabilityEconomyMath.growthCap(Integer.MAX_VALUE, 500));
        assertEquals(3, DurabilityEconomyMath.growthStep(1000, 3));
        assertEquals(1, DurabilityEconomyMath.growthStep(1, 1));
        assertEquals(30, DurabilityEconomyMath.growthChance(20, 3));
        assertEquals(100, DurabilityEconomyMath.growthChance(20, 255));
    }

    @Test
    void thresholdAndConversionRequireSafeCompletePayment() {
        assertTrue(DurabilityEconomyMath.crossedOnePercent(1000, 989, 990));
        assertTrue(DurabilityEconomyMath.crossedOnePercent(1000, 989, 1000));
        assertFalse(DurabilityEconomyMath.crossedOnePercent(1000, 990, 991));
        assertFalse(DurabilityEconomyMath.crossedOnePercent(1000, 999, 998));
        assertEquals(10, DurabilityEconomyMath.conversionXp(1000, 64));
        assertEquals(64, DurabilityEconomyMath.conversionXp(Integer.MAX_VALUE, 64));
        assertNull(DurabilityEconomyMath.splitWear(4, new int[] {}));
        assertNull(DurabilityEconomyMath.splitWear(4, new int[] {1, 2}));
        assertNull(DurabilityEconomyMath.splitWear(4, new int[] {8, -1}));
        int[] shares = DurabilityEconomyMath.splitWear(11, new int[] {1, 3, 20});
        assertNotNull(shares);
        assertEquals(11, Arrays.stream(shares).sum());
        assertTrue(shares[0] <= 1 && shares[1] <= 3 && shares[2] <= 20);
        var random = new java.util.Random(1729);
        for (int trial = 0; trial < 1000; trial++) {
            int[] capacities = {random.nextInt(50), random.nextInt(50), random.nextInt(50), random.nextInt(50)};
            int total = Arrays.stream(capacities).sum();
            int cost = 1 + random.nextInt(200);
            int[] allocation = DurabilityEconomyMath.splitWear(cost, capacities);
            if (total < cost) assertNull(allocation);
            else {
                assertNotNull(allocation);
                assertEquals(cost, Arrays.stream(allocation).sum());
                for (int i = 0; i < capacities.length; i++) assertTrue(allocation[i] >= 0 && allocation[i] <= capacities[i]);
            }
        }
        assertEquals(4096, DurabilityEconomyMath.rescueTarget(Integer.MAX_VALUE, 10, 4096));
        assertEquals(1, DurabilityEconomyMath.rescueTarget(1, 10, 4096));
    }

    @Test
    void xpBalanceUsesLevelAndProgressNotTotalExperience() {
        assertEquals(0, DurabilityEconomyMath.spendableXp(0, 0.0F, 7));
        assertEquals(315, DurabilityEconomyMath.spendableXp(15, 0.0F, 37));
        assertEquals(352, DurabilityEconomyMath.spendableXp(16, 0.0F, 42));
        assertEquals(1395, DurabilityEconomyMath.spendableXp(30, 0.0F, 112));
        assertEquals(1507, DurabilityEconomyMath.spendableXp(31, 0.0F, 121));
        assertEquals(354, DurabilityEconomyMath.spendableXp(16, 2.0F / 42.0F, 42));
        assertEquals(-1, DurabilityEconomyMath.spendableXp(2, Float.NaN, 11));
        assertEquals(-1, DurabilityEconomyMath.spendableXp(1_000_001, 0.0F, 1));
    }
}
