package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.mechanics.resonance.TomeLockData;
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
        assertEquals(0, TomeWearData.childGeneration(-1)); // sentinel parent clamps to boundary
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

    // ---------- tome locks ----------

    @Test void tomeLockTargetsFollowWear() {
        assertEquals(2, TomeLockData.targetWearFor(TomeLockData.Mode.NEXT_CHARGE, 3));
        assertEquals(0, TomeLockData.targetWearFor(TomeLockData.Mode.NEXT_CHARGE, 0));
        assertEquals(0, TomeLockData.targetWearFor(TomeLockData.Mode.FULL_CHARGE, 3));
        assertEquals(-1, TomeLockData.targetWearFor(TomeLockData.Mode.PERMANENT, 3));
        assertEquals(-1, TomeLockData.targetWearFor(null, 3));
    }

    @Test void timedTomeLocksExpireExactlyAtTargetWear() {
        assertFalse(TomeLockData.isExpired(TomeLockData.Mode.NEXT_CHARGE, 2, 3));
        assertTrue(TomeLockData.isExpired(TomeLockData.Mode.NEXT_CHARGE, 2, 2));

        // Round 4: tome-page knobs parse from the tome_pages JSON section.
        var cfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.fromJson(
            com.google.gson.JsonParser.parseString(
                "{\"tome_pages\":{\"sacrifice_required\":5,\"locked_shelf_breakable\":false,\"early_unlock_grit_count\":0}}"
            ).getAsJsonObject());
        assertEquals(5, cfg.sacrificeRequired());
        assertFalse(cfg.lockedShelfBreakable());
        assertTrue(cfg.lockedBookExtractionBlocked());
        assertEquals(0, cfg.earlyUnlockGritCount());
        assertEquals(1, cfg.earlyUnlockXpLevels());
        assertEquals(4, cfg.tomePageRequiredShelves());
        assertTrue(TomeLockData.isExpired(TomeLockData.Mode.FULL_CHARGE, 0, 0));
        assertFalse(TomeLockData.isExpired(TomeLockData.Mode.PERMANENT, -1, 0));
    }

    @Test void tomeLockEtaIncludesCurrentAndRemainingCharges() {
        assertEquals(900, TomeLockData.ticksToNextCharge(3, 100L, 1000));
        assertEquals(2900, TomeLockData.ticksToFullCharge(3, 100L, 1000));
        assertEquals(-1, TomeLockData.ticksToNextCharge(0, 100L, 1000));
        assertEquals(-1, TomeLockData.ticksToFullCharge(3, 100L, Integer.MAX_VALUE));
        assertEquals(TomeLockData.Mode.FULL_CHARGE, TomeLockData.Mode.fromToken("full_charge"));
        assertEquals(TomeLockData.Mode.PERMANENT, TomeLockData.Mode.fromToken("unknown"));
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

    // ---------- 2026-10-25 balance & audit additions ----------

    @Test void wearImmunityCurveDecaysSmoothlyAndGenerationCapStripsImmunity() {
        var cfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;
        assertEquals(4, cfg.wearImmuneFullWaiverMaxEnchants());
        assertEquals(0.06, cfg.wearImmuneOverloadWaiverDecay(), 1e-9);
        assertEquals(2, cfg.wearImmuneMaxGeneration());
        assertEquals(3.5, cfg.maxMasteryUnitsPerSlot(), 1e-9);

        // N = 1..4 -> 100% waiver; N = 5 -> 94%; N = 10 -> 64%; N = 20 -> clamped at floor 30%
        for (int n = 1; n <= 4; n++) {
            assertEquals(1.0, TomeWearData.computeWearImmuneWaiverChance(cfg, n), 1e-9);
        }
        assertEquals(0.94, TomeWearData.computeWearImmuneWaiverChance(cfg, 5), 1e-9);
        assertEquals(0.88, TomeWearData.computeWearImmuneWaiverChance(cfg, 6), 1e-9);
        assertEquals(0.64, TomeWearData.computeWearImmuneWaiverChance(cfg, 10), 1e-9);
        assertEquals(0.30, TomeWearData.computeWearImmuneWaiverChance(cfg, 20), 1e-9);

        assertTrue(TomeWearData.hasEffectiveWearImmunity(true, 0, cfg.wearImmuneMaxGeneration()));
        assertTrue(TomeWearData.hasEffectiveWearImmunity(true, 2, cfg.wearImmuneMaxGeneration()));
        assertFalse(TomeWearData.hasEffectiveWearImmunity(true, 3, cfg.wearImmuneMaxGeneration()));

        // Bug 1 (gen3_deduct_one): #wear_immune books keep 4/4 max uses across Gen 0..2,
        // then deduct 1 per generation starting at Gen 3 (Gen 3 -> 3/4, Gen 4 -> 2/4, Gen 5+ -> 1/4).
        assertEquals(4, TomeWearData.effectiveMaxUses(4, 0, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(4, TomeWearData.effectiveMaxUses(4, 1, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(4, TomeWearData.effectiveMaxUses(4, 2, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(3, TomeWearData.effectiveMaxUses(4, 3, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(2, TomeWearData.effectiveMaxUses(4, 4, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(1, TomeWearData.effectiveMaxUses(4, 5, true, cfg.wearImmuneMaxGeneration()));
        assertEquals(1, TomeWearData.effectiveMaxUses(4, 12, true, cfg.wearImmuneMaxGeneration()));
        // Mortal books deduct from Gen 1 as before (4 -> 3 -> 2 -> 1).
        assertEquals(4, TomeWearData.effectiveMaxUses(4, 0, false, cfg.wearImmuneMaxGeneration()));
        assertEquals(3, TomeWearData.effectiveMaxUses(4, 1, false, cfg.wearImmuneMaxGeneration()));
        assertEquals(2, TomeWearData.effectiveMaxUses(4, 2, false, cfg.wearImmuneMaxGeneration()));
        assertEquals(1, TomeWearData.effectiveMaxUses(4, 3, false, cfg.wearImmuneMaxGeneration()));
    }

    @Test void cetCustomDataStampsSchemaAndReadsLegacyUnversionedTags() {
        var legacyTag = new net.minecraft.nbt.CompoundTag();
        legacyTag.putInt("cet_tome_wear", 2);
        legacyTag.putInt("cet_generation", 3);
        assertEquals(0, chiseled_enchanting_table.utils.CetCustomData.readSchemaVersion(legacyTag));
        assertEquals(2, legacyTag.getIntOr("cet_tome_wear", 0));

        chiseled_enchanting_table.utils.CetCustomData.normalizeSchemaTag(legacyTag);
        assertEquals(
            chiseled_enchanting_table.utils.CetCustomData.CURRENT_SCHEMA_VERSION,
            chiseled_enchanting_table.utils.CetCustomData.readSchemaVersion(legacyTag)
        );

        legacyTag.remove("cet_tome_wear");
        legacyTag.remove("cet_generation");
        chiseled_enchanting_table.utils.CetCustomData.normalizeSchemaTag(legacyTag);
        assertFalse(legacyTag.contains(chiseled_enchanting_table.utils.CetCustomData.NBT_SCHEMA_KEY));
    }

    @Test void queuedInscriptionCodecRoundTripsAndMatchesLegacyStringEncoding() {
        var steps = java.util.List.of(
            new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription(
                net.minecraft.resources.Identifier.withDefaultNamespace("sharpness"), 5, 240, true, false, 12, false
            ),
            new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription(
                net.minecraft.resources.Identifier.withDefaultNamespace("infinity"), 1, 600, false, true, 30, true
            )
        );
        var listCodec = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.QueuedInscription.CODEC.listOf();
        var encoded = listCodec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, steps).getOrThrow();
        var decoded = listCodec.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(steps, decoded);
        String legacyWire = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.serializeQueue(steps);
        assertEquals(steps, chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.deserializeQueue(legacyWire));
    }

    @Test void perSlotMasteryCapPreventsSingleSuperBookFromMaxingGlobalDiscount() {
        var cfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config.DEFAULT;
        // One super-book carrying 16 max-level enchants in a single slot is capped at 3.5 units (+4% instead of +20%)
        double singleSlotCapped = chiseled_enchanting_table.mechanics.resonance.LibraryResonance
            .capSingleSlotMasteryUnits(cfg, 16.0);
        assertEquals(3.5, singleSlotCapped, 1e-9);
        assertEquals(4, chiseled_enchanting_table.mechanics.resonance.LibraryResonance
            .globalMasteryDiscountPercent(cfg, singleSlotCapped));

        // Spreading max-level enchants across 5 separate slots reaches 17.5 units -> full 20% global mastery discount
        double fiveSlotsSum = 0.0;
        for (int i = 0; i < 5; i++) {
            fiveSlotsSum += chiseled_enchanting_table.mechanics.resonance.LibraryResonance
                .capSingleSlotMasteryUnits(cfg, 3.5);
        }
        assertEquals(20, chiseled_enchanting_table.mechanics.resonance.LibraryResonance
            .globalMasteryDiscountPercent(cfg, fiveSlotsSum));
    }

    @Test void queueScaledSacrificeSkipAndActiveRitualVictimPriority() {
        var rCfg = chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config.DEFAULT;
        // Single step caps at 15 levels; 4 queued steps cap at 15 + 3 * 4 = 27 levels; huge queue caps at 255 levels
        assertEquals(15, chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
            .computeSacrificeXpLevels(rCfg, 500_000L, 1));
        assertEquals(27, chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
            .computeSacrificeXpLevels(rCfg, 500_000L, 4));
        assertEquals(255, chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics
            .computeSacrificeXpLevels(rCfg, 10_000_000L, 100));

        // Cheap unrelated scapegoat book (backsActiveRitual = false) must NOT shield the tome backing the active ritual!
        var unrelatedDecoy = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.TomeCandidate(
            net.minecraft.core.BlockPos.ZERO, 0, 1, 0.10, 0.05, false, false
        );
        var activeBackingTome = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.TomeCandidate(
            net.minecraft.core.BlockPos.ZERO, 1, 3, 1.00, 0.80, false, true
        );
        assertEquals(
            activeBackingTome,
            chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.selectDegradationVictim(
                java.util.List.of(unrelatedDecoy, activeBackingTome)
            ),
            "Active-ritual-backing tome must degrade before an unrelated decoy book"
        );
    }
}
