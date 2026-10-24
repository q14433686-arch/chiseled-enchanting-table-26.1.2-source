package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.mechanics.resonance.TomeWearData;
import chiseled_enchanting_table.menu.PayloadLimits;
import io.netty.buffer.Unpooled;
import java.util.Random;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic coverage for the 2026-10-24 audit mechanics: generation fade
 * (copy generation advances lineage, capacity fades by it), essence-priced
 * scapegoat overflow, and fuzz-style bounds on the payload count guards.
 * No registry bootstrap: every function under test is static and item-free.
 */
class AuditMechanicsTest {

    // ---------- generation fade ----------

    @Test void generationFadesCapacityWithFloorOfOne() {
        assertEquals(16, TomeWearData.effectiveMaxUses(16, 0));
        assertEquals(15, TomeWearData.effectiveMaxUses(16, 1));
        assertEquals(1, TomeWearData.effectiveMaxUses(16, 15));
        assertEquals(1, TomeWearData.effectiveMaxUses(16, 16));
        assertEquals(1, TomeWearData.effectiveMaxUses(16, 255));
        assertEquals(1, TomeWearData.effectiveMaxUses(0, 0));
        assertEquals(16, TomeWearData.effectiveMaxUses(16, -3));
    }

    @Test void lineageAdvancesOnCopyAndMergesToMoreFadedParent() {
        assertEquals(1, TomeWearData.childGeneration(0));
        assertEquals(8, TomeWearData.childGeneration(7));
        assertEquals(255, TomeWearData.childGeneration(255));
        assertEquals(1, TomeWearData.childGeneration(-1));
        assertEquals(5, TomeWearData.mergedGeneration(2, 5));
        assertEquals(5, TomeWearData.mergedGeneration(5, 2));
        assertEquals(0, TomeWearData.mergedGeneration(-1, -1));
        assertEquals(3, TomeWearData.mergedGeneration(-1, 3));
    }

    @Test void remainingChargesRespectFadedCap() {
        assertEquals(16, TomeWearData.remainingCharges(16, 0, 0));
        assertEquals(12, TomeWearData.remainingCharges(16, 2, 2));
        assertEquals(1, TomeWearData.remainingCharges(16, 0, 15));
        // Wear above a newly stamped lower cap never goes negative on display.
        assertEquals(1, TomeWearData.remainingCharges(16, 14, 15));
    }

    // ---------- scapegoat pricing ----------

    @Test void flatPricingKeepsHistoricalFullReset() {
        assertEquals(0, TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.FLAT, 16, 10, 60));
        assertEquals(0, TomeWearData.computeOverflowNextWear(null, 16, 10, 60));
    }

    @Test void essencePricingRestoresProportionally() {
        // Burning the crown jewel itself restores everything.
        assertEquals(0, TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.ESSENCE, 16, 60, 60));
        // Burning a junk entry (1/6 of the top value) restores ~1/6 of 16 = 3 charges.
        assertEquals(13, TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.ESSENCE, 16, 10, 60));
        // No value burned still restores the guaranteed single charge.
        assertEquals(15, TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.ESSENCE, 16, 0, 60));
        // An unpriceable tome falls back to the flat reset instead of dividing by zero.
        assertEquals(0, TomeWearData.computeOverflowNextWear(
            TomeWearData.ScapegoatPricing.ESSENCE, 16, 10, 0));
    }

    @Test void essenceWearStepOnlyBuysBackWhatWasBurned() {
        // Lv.5 tome, cap 4, wear 3, gear use overflows: step down to Lv.4.
        var flat = MasterTomeWear.computeWearStep(
            MasterTomeWear.ConsumptionMode.WEAR, MasterTomeWear.DepleteBehavior.STEP_DOWN,
            4, 2, 5, 3, false, false, TomeWearData.ScapegoatPricing.FLAT, 0, 0);
        assertEquals(4, flat.nextEnchantLevel());
        assertEquals(0, flat.nextWearUsed());
        assertTrue(flat.steppedDown());

        // Same step, essence pricing, junk burned (1/4 of top): only 1 of 4 charges return.
        var essence = MasterTomeWear.computeWearStep(
            MasterTomeWear.ConsumptionMode.WEAR, MasterTomeWear.DepleteBehavior.STEP_DOWN,
            4, 2, 5, 3, false, false, TomeWearData.ScapegoatPricing.ESSENCE, 10, 40);
        assertEquals(4, essence.nextEnchantLevel());
        assertEquals(3, essence.nextWearUsed());
        assertTrue(essence.steppedDown());

        // CONSUME mode is never priced: it always depletes outright.
        var consumed = MasterTomeWear.computeWearStep(
            MasterTomeWear.ConsumptionMode.CONSUME, MasterTomeWear.DepleteBehavior.STEP_DOWN,
            4, 2, 5, 0, false, false, TomeWearData.ScapegoatPricing.ESSENCE, 40, 40);
        assertEquals(0, consumed.nextEnchantLevel());
        assertTrue(consumed.depletedCompletely());
    }

    @Test void scapegoatTokensRoundTripAndDefaultToEssence() {
        assertEquals(TomeWearData.ScapegoatPricing.ESSENCE,
            TomeWearData.ScapegoatPricing.fromToken("essence"));
        assertEquals(TomeWearData.ScapegoatPricing.FLAT,
            TomeWearData.ScapegoatPricing.fromToken("flat"));
        assertEquals(TomeWearData.ScapegoatPricing.ESSENCE,
            TomeWearData.ScapegoatPricing.fromToken("bogus"));
        assertEquals(TomeWearData.ScapegoatPricing.ESSENCE,
            TomeWearData.ScapegoatPricing.fromToken(null));
        assertEquals("essence", TomeWearData.ScapegoatPricing.ESSENCE.token());
        assertEquals("flat", TomeWearData.ScapegoatPricing.FLAT.token());
    }

    // ---------- payload bounds (fuzz-style) ----------

    @Test void boundedCountsAcceptInRangeAndRejectOutside() {
        Random rng = new Random(20261024L);
        for (int i = 0; i < 512; i++) {
            int max = 1 + rng.nextInt(70_000);
            int ok = rng.nextInt(max + 1);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                PayloadLimits.writeBoundedCount(buf, ok, max);
                assertEquals(ok, PayloadLimits.readBoundedCount(buf, max));
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
            // Outbound rejects both directions of overflow before writing anything.
            RegistryFriendlyByteBuf bad = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                assertThrows(IllegalArgumentException.class,
                    () -> PayloadLimits.writeBoundedCount(bad, max + 1 + rng.nextInt(1_000), max));
                assertThrows(IllegalArgumentException.class,
                    () -> PayloadLimits.writeBoundedCount(bad, -1 - rng.nextInt(1_000), max));
                assertEquals(0, bad.readableBytes());
            } finally {
                bad.release();
            }
            // Inbound: a hostile peer announcing more than the budget is rejected on read.
            RegistryFriendlyByteBuf hostile = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                hostile.writeVarInt(max + 1);
                assertThrows(IllegalArgumentException.class,
                    () -> PayloadLimits.readBoundedCount(hostile, max));
            } finally {
                hostile.release();
            }
        }
        // readCount uses the global collection budget.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            PayloadLimits.writeBoundedCount(buf, PayloadLimits.MAX_COLLECTION_ENTRIES,
                PayloadLimits.MAX_COLLECTION_ENTRIES);
            assertEquals(PayloadLimits.MAX_COLLECTION_ENTRIES, PayloadLimits.readCount(buf));
        } finally {
            buf.release();
        }
    }
}
