package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.ConfirmedWindBurst;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelThorns;
import enchantment_fusion.mechanics.signatures.GrapnelAttribute;
import enchantment_fusion.mechanics.signatures.GrapnelBreath;
import enchantment_fusion.mechanics.signatures.GrapnelMotion;
import enchantment_fusion.mechanics.signatures.GrapnelSweep;
import enchantment_fusion.mechanics.signatures.MeleeWindSkills;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GrapnelActionSourcesTest {
    @Test void directedCarriersAndExactPriorityAreExplicit() {
        SignatureCatalog.reloadFromJson(null);
        try {
            FusedPair wind = FusedPair.clean("minecraft:wind_burst", 1, "minecraft:lure", 2);
            FusedPair thorns = FusedPair.clean("minecraft:thorns", 3, "minecraft:lure", 2);
            assertTrue(GrapnelSkills.matches(wind)); assertTrue(GrapnelSkills.matches(thorns));
            assertFalse(GrapnelSkills.matches(wind.flipped())); assertFalse(GrapnelSkills.matches(thorns.flipped()));
            assertTrue(GrapnelSkills.supportsCarrier(wind, Set.of(FusionCarrier.MACE)));
            assertFalse(GrapnelSkills.supportsCarrier(wind, Set.of(FusionCarrier.SWORD)));
            assertTrue(GrapnelSkills.supportsCarrier(thorns, Set.of(FusionCarrier.CHEST_ARMOR)));
            assertTrue(GrapnelSkills.supportsCarrier(thorns, Set.of(FusionCarrier.SHIELD)));
            assertFalse(GrapnelSkills.supportsCarrier(thorns, Set.of(FusionCarrier.SWORD)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:thorns","secondary":"minecraft:lure"},
                    "spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject());
            assertFalse(GrapnelSkills.matches(thorns)); assertTrue(GrapnelSkills.matches(wind));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void attributeDrivenCarriersAndPriorityAreDirectionSpecific() {
        SignatureCatalog.reloadFromJson(null);
        try {
            FusedPair swift = FusedPair.clean(GrapnelAttribute.SWIFT, 3, "minecraft:lure", 2);
            FusedPair depth = FusedPair.clean(GrapnelAttribute.DEPTH, 3, "minecraft:lure", 2);
            FusedPair sweep = FusedPair.clean(GrapnelAttribute.SWEEP, 3, "minecraft:lure", 2);
            FusedPair soul = FusedPair.clean(GrapnelAttribute.SOUL, 3, "minecraft:lure", 2);
            FusedPair aqua = FusedPair.clean(GrapnelAttribute.AQUA, 1, "minecraft:lure", 2);
            FusedPair breath = FusedPair.clean(GrapnelAttribute.RESPIRATION, 3, "minecraft:lure", 2);
            for (FusedPair pair : new FusedPair[]{swift, depth, sweep, soul, aqua, breath}) {
                assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            }
            assertTrue(GrapnelSkills.supportsCarrier(swift, Set.of(FusionCarrier.LEG_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(swift, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertTrue(GrapnelSkills.supportsCarrier(depth, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(depth, Set.of(FusionCarrier.LEG_ARMOR)));
            assertTrue(GrapnelSkills.supportsCarrier(soul, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(soul, Set.of(FusionCarrier.LEG_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(soul, Set.of(FusionCarrier.SWORD)));
            assertTrue(GrapnelSkills.supportsCarrier(aqua, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(aqua, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(aqua, Set.of(FusionCarrier.PICKAXE)));
            assertTrue(GrapnelSkills.supportsCarrier(breath, Set.of(FusionCarrier.HEAD_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(breath, Set.of(FusionCarrier.FOOT_ARMOR)));
            assertFalse(GrapnelSkills.supportsCarrier(breath, Set.of(FusionCarrier.PICKAXE)));
            assertTrue(GrapnelSkills.supportsCarrier(sweep, Set.of(FusionCarrier.SWORD)));
            assertFalse(GrapnelSkills.supportsCarrier(sweep, Set.of(FusionCarrier.AXE)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:swift_sneak","secondary":"minecraft:lure"},
                    "spirit_grapnel":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject());
            assertFalse(GrapnelSkills.matches(swift)); assertTrue(GrapnelSkills.matches(depth));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void movementMustFollowInputAndSweepMustActuallyIncreaseInitialDamage() {
        Vec3 forward = new Vec3(1, 0, 0);
        assertTrue(GrapnelMotion.matchesInput(new Vec3(0.1, 0, 0.02), forward));
        assertFalse(GrapnelMotion.matchesInput(Vec3.ZERO, forward));
        assertFalse(GrapnelMotion.matchesInput(new Vec3(-0.1, 0, 0), forward));
        assertFalse(GrapnelMotion.matchesInput(new Vec3(0, 0, 0.1), forward));
        assertFalse(GrapnelMotion.matchesInput(new Vec3(0.1, 0, 0), Vec3.ZERO));
        assertFalse(GrapnelMotion.matchesInput(new Vec3(Double.NaN, 0, 0), forward));
        assertTrue(GrapnelSweep.improvesSweep(4, 1, 0.6, 0));
        assertFalse(GrapnelSweep.improvesSweep(4, 1, 0.6, 0.6));
        assertFalse(GrapnelSweep.improvesSweep(0, 1, 0.6, 0));
        assertFalse(GrapnelSweep.improvesSweep(4, 0, 0.6, 0));
        assertFalse(GrapnelSweep.improvesSweep(4, 1, Double.NaN, 0));
    }
    @Test void breathCreditUsesTheSameRandomRollAndTheCounterfactualThreshold() {
        assertTrue(GrapnelBreath.savedByPrimary(0.5, 3, 0));
        assertTrue(GrapnelBreath.savedByPrimary(0.25, 3, 0));
        assertFalse(GrapnelBreath.savedByPrimary(0.249, 3, 0));
        assertTrue(GrapnelBreath.savedByPrimary(0.4, 3, 1));
        assertFalse(GrapnelBreath.savedByPrimary(0.5, 3, 1));
        assertFalse(GrapnelBreath.savedByPrimary(0.8, 3, 1));
        assertFalse(GrapnelBreath.savedByPrimary(0.5, 1, 1));
        assertFalse(GrapnelBreath.savedByPrimary(1, 3, 0));
        assertFalse(GrapnelBreath.savedByPrimary(-0.1, 3, 0));
        assertFalse(GrapnelBreath.savedByPrimary(Double.NaN, 3, 0));
        assertFalse(GrapnelBreath.savedByPrimary(0.5, Double.POSITIVE_INFINITY, 0));
    }
    @Test void reflectedDamageNeedsOneUnambiguousCompletedNetLoss() {
        GrapnelThorns.Evidence confirmed = new GrapnelThorns.Evidence();
        confirmed.damage(true, true, 2); confirmed.result(true, true, true, true);
        assertTrue(confirmed.confirmed(true, 10, 2, 10, 0));
        assertFalse(confirmed.confirmed(false, 10, 2, 10, 0));
        assertFalse(confirmed.confirmed(true, 10, 2, 10, 2));
        assertFalse(confirmed.confirmed(true, 10, 2, Float.NaN, 0));
        GrapnelThorns.Evidence wrong = new GrapnelThorns.Evidence();
        wrong.damage(true, false, 2); wrong.result(true, true, true, true);
        assertFalse(wrong.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence other = new GrapnelThorns.Evidence();
        other.damage(false, true, 2); other.result(false, true, true, true);
        assertFalse(other.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence cancelled = new GrapnelThorns.Evidence();
        cancelled.damage(true, true, 0); cancelled.result(true, true, true, false);
        assertFalse(cancelled.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence rejectedAfterDamage = new GrapnelThorns.Evidence();
        rejectedAfterDamage.damage(true, true, 2); rejectedAfterDamage.result(true, true, true, false);
        assertFalse(rejectedAfterDamage.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence incomplete = new GrapnelThorns.Evidence();
        incomplete.damage(true, true, 2); incomplete.result(true, true, false, true);
        assertFalse(incomplete.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence repeated = new GrapnelThorns.Evidence();
        repeated.damage(true, true, 1); repeated.damage(true, true, 1); repeated.result(true, true, true, true);
        assertFalse(repeated.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence repeatedResult = new GrapnelThorns.Evidence();
        repeatedResult.damage(true, true, 2); repeatedResult.result(true, true, true, true); repeatedResult.result(true, true, true, true);
        assertFalse(repeatedResult.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence nested = new GrapnelThorns.Evidence();
        nested.damage(true, true, 2); nested.result(true, true, true, true); nested.nested();
        assertFalse(nested.confirmed(true, 10, 0, 8, 0));
    }
    @Test void conditionalLocationReplayRequiresMissingNativeAndMatchingSlot() {
        assertTrue(PrimaryLocationProjection.needsProjection(0, true));
        assertFalse(PrimaryLocationProjection.needsProjection(1, true));
        assertFalse(PrimaryLocationProjection.needsProjection(0, false));
        assertFalse(PrimaryLocationProjection.needsProjection(3, false));
    }
    @Test void primaryReplaysAreSharedAndNeverDuplicateNative() {
        assertTrue(GrapnelThorns.needsReplay(0, true));
        assertTrue(GrapnelThorns.needsReplay(2, false));
        assertFalse(GrapnelThorns.needsReplay(2, true));
        assertEquals(0, MeleeWindSkills.primaryReplayLevel(1, 3, 4));
        assertEquals(4, MeleeWindSkills.primaryReplayLevel(0, 3, 4));
        assertEquals(3, MeleeWindSkills.primaryReplayLevel(0, 3, 1));
        assertEquals(0, MeleeWindSkills.primaryReplayLevel(0, 0, 0));
        assertTrue(ConfirmedWindBurst.upwardImpulse(Vec3.ZERO, new Vec3(0, 0.2, 0)));
        assertFalse(ConfirmedWindBurst.upwardImpulse(Vec3.ZERO, Vec3.ZERO));
        assertFalse(ConfirmedWindBurst.upwardImpulse(Vec3.ZERO, new Vec3(0, Double.POSITIVE_INFINITY, 0)));
    }
}
