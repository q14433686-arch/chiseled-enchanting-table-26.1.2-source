package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.ConfirmedWindBurst;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelThorns;
import enchantment_fusion.mechanics.signatures.MeleeWindSkills;
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
    @Test void reflectedDamageNeedsOneUnambiguousCompletedNetLoss() {
        GrapnelThorns.Evidence confirmed = new GrapnelThorns.Evidence();
        confirmed.damage(true, true, 2);
        assertTrue(confirmed.confirmed(true, 10, 2, 10, 0));
        assertFalse(confirmed.confirmed(false, 10, 2, 10, 0));
        assertFalse(confirmed.confirmed(true, 10, 2, 10, 2));
        assertFalse(confirmed.confirmed(true, 10, 2, Float.NaN, 0));
        GrapnelThorns.Evidence wrong = new GrapnelThorns.Evidence();
        wrong.damage(true, false, 2); assertFalse(wrong.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence other = new GrapnelThorns.Evidence();
        other.damage(false, true, 2); assertFalse(other.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence cancelled = new GrapnelThorns.Evidence();
        cancelled.damage(true, true, 0); assertFalse(cancelled.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence repeated = new GrapnelThorns.Evidence();
        repeated.damage(true, true, 1); repeated.damage(true, true, 1);
        assertFalse(repeated.confirmed(true, 10, 0, 8, 0));
        GrapnelThorns.Evidence nested = new GrapnelThorns.Evidence();
        nested.damage(true, true, 2); nested.nested(); assertFalse(nested.confirmed(true, 10, 0, 8, 0));
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
