package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.GrapnelLoyalty;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.LoyaltyGuard;
import enchantment_fusion.mechanics.signatures.RealInventoryInsertion;
import enchantment_fusion.mechanics.signatures.RealReturnObservation;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GrapnelLoyaltyTest {
    @Test void orderedLureRequiresARealTridentAndDoesNotReplaceTheRescue() {
        var previous = SignatureCatalog.parseFromJson(null);
        try {
            SignatureCatalog.reloadFromJson(null);
            FusedPair lure = FusedPair.clean(GrapnelLoyalty.LOYALTY, 3, GrapnelSkills.LURE, 2);
            FusedPair rescue = FusedPair.clean(LoyaltyGuard.PRIMARY, 3, LoyaltyGuard.SECONDARY, 2);
            assertTrue(GrapnelSkills.matches(lure)); assertFalse(GrapnelSkills.matches(lure.flipped()));
            assertTrue(GrapnelSkills.supportsCarrier(lure, Set.of(FusionCarrier.TRIDENT)));
            assertFalse(GrapnelSkills.supportsCarrier(lure, Set.of(FusionCarrier.SPEAR)));
            assertFalse(GrapnelSkills.supportsCarrier(lure, Set.of(FusionCarrier.FISHING_ROD)));
            assertTrue(LoyaltyGuard.isPair(rescue)); assertFalse(GrapnelLoyalty.isPair(rescue));
            assertFalse(GrapnelSkills.matches(rescue));
            SignatureCatalog.publish(SignatureCatalog.parseFromJson(JsonParser.parseString("""
                {"signatures":{"exact":{"primary":"minecraft:loyalty","secondary":"minecraft:lure"},
                               "wild":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject()));
            assertFalse(GrapnelSkills.matches(lure));
        } finally { SignatureCatalog.publish(previous); }
    }
    @Test void returnRequiresBoundedFiniteMovementTowardsTheCapturedOwner() {
        Vec3 eye = Vec3.ZERO, distant = new Vec3(8, 0, 0);
        assertTrue(GrapnelLoyalty.directedReturn(distant, new Vec3(7, 0, 0), eye));
        assertFalse(GrapnelLoyalty.directedReturn(distant, distant, eye));
        assertFalse(GrapnelLoyalty.directedReturn(distant, new Vec3(9, 0, 0), eye));
        assertFalse(GrapnelLoyalty.directedReturn(new Vec3(30, 0, 0), Vec3.ZERO, eye));
        assertFalse(GrapnelLoyalty.directedReturn(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, eye));
        assertFalse(GrapnelLoyalty.directedReturn(distant, Vec3.ZERO, new Vec3(Double.POSITIVE_INFINITY, 0, 0)));
        assertFalse(GrapnelLoyalty.directedReturn(null, Vec3.ZERO, eye));
    }

    private static RealReturnObservation observed(int rank, float before, float after) {
        RealReturnObservation value = new RealReturnObservation();
        value.enter(true); value.record(true, rank, before, after, true); value.exit(); return value;
    }
    @Test void actualSingleLoyaltyEffectMustCauseTheCachedAcceleration() {
        assertTrue(observed(3, 0, 3).proved(3, 3));
        assertFalse(observed(3, 0, 0).proved(3, 1));
        assertFalse(observed(2, 0, 3).proved(3, 3));
        assertFalse(observed(3, 1, 3).proved(3, 3));
        assertFalse(observed(3, 0, Float.NaN).proved(3, 3));
        assertFalse(observed(3, 0, 3).proved(3, 2));
        RealReturnObservation foreign = observed(3, 0, 3);
        foreign.enter(true); foreign.record(false, 1, 3, 4, true); foreign.exit();
        assertFalse(foreign.proved(3, 4));
        RealReturnObservation nested = new RealReturnObservation();
        nested.enter(true); nested.enter(true); nested.record(true, 3, 0, 3, true); nested.exit(); nested.exit();
        assertFalse(nested.proved(3, 3));
        RealReturnObservation duplicate = observed(3, 0, 3);
        duplicate.enter(true); duplicate.record(true, 3, 3, 6, true); duplicate.exit();
        assertFalse(duplicate.proved(3, 6));
        RealReturnObservation wrongContext = new RealReturnObservation();
        wrongContext.enter(false); wrongContext.record(true, 3, 0, 3, true); wrongContext.exit();
        assertFalse(wrongContext.proved(3, 3));
    }

    private static final class Slot {
        String value;
        Slot(String value) { this.value = value; }
    }
    private static int insertion(List<Slot> before, List<String> values, List<Slot> after) {
        return RealInventoryInsertion.uniqueInsertionIndex(before, values, after,
            String::isEmpty, (slot, value) -> slot.value.equals(value), slot -> slot.value.equals("trident:original-components"));
    }
    @Test void receiptRequiresOneNewPhysicalStackAndUnchangedOtherSlots() {
        Slot empty = new Slot(""), equalBefore = new Slot("trident:original-components"), filler = new Slot("stone");
        List<Slot> before = List.of(empty, equalBefore, filler), after = new ArrayList<>(before);
        List<String> values = List.of("", equalBefore.value, filler.value);
        Slot received = new Slot(equalBefore.value); after.set(0, received);
        assertEquals(0, insertion(before, values, after));
        assertEquals(-1, insertion(before, values, before)); // existing equal item is not a return
        after.set(1, new Slot(equalBefore.value)); assertEquals(-1, insertion(before, values, after));
        after.set(1, equalBefore); filler.value = "changed"; assertEquals(-1, insertion(before, values, after));
        filler.value = "stone"; after.set(2, received); assertEquals(-1, insertion(before, values, after));
        assertEquals(-1, insertion(before, values, after.subList(0, 2)));
    }
    @Test void ambiguousMultipleInsertionsAndSameObjectAreRejected() {
        Slot first = new Slot(""), second = new Slot("");
        List<Slot> before = List.of(first, second); List<String> values = List.of("", "");
        assertEquals(-1, insertion(before, values, List.of(new Slot("trident:original-components"), new Slot("trident:original-components"))));
        first.value = "trident:original-components";
        assertEquals(-1, insertion(before, values, List.of(first, second)));
        assertEquals(-1, insertion(before, values, List.of(new Slot("trident:wrong-components"), second)));
    }
}
