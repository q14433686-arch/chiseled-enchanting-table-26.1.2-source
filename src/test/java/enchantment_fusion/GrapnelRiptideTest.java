package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.ConfirmedPlayerMove;
import enchantment_fusion.mechanics.signatures.GrapnelRiptide;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GrapnelRiptideTest {
    @Test void riptideUsesRealTridentsAndHonorsOrderedPriority() {
        SignatureCatalog.reloadFromJson(null);
        try {
            FusedPair pair = FusedPair.clean(GrapnelRiptide.RIPTIDE, 3, GrapnelSkills.LURE, 2);
            assertTrue(GrapnelSkills.matches(pair)); assertFalse(GrapnelSkills.matches(pair.flipped()));
            assertTrue(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.TRIDENT)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.SPEAR)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.BOW)));
            assertFalse(GrapnelSkills.supportsCarrier(pair, Set.of(FusionCarrier.FISHING_ROD)));
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"explicit":{"primary":"minecraft:riptide","secondary":"minecraft:lure"},
                               "wild":{"primary":"*","secondary":"minecraft:lure"}}}
                """).getAsJsonObject());
            assertFalse(GrapnelSkills.matches(pair));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void impulseAndMovementMustAgreeIncludingVerticalDashes() {
        Vec3 impulse = new Vec3(2, 1, 0);
        assertTrue(GrapnelRiptide.matchesDirection(new Vec3(0.5, 0.2, 0), impulse));
        assertTrue(GrapnelRiptide.matchesDirection(new Vec3(0, 1, 0), new Vec3(0, 2, 0)));
        assertFalse(GrapnelRiptide.matchesDirection(Vec3.ZERO, impulse));
        assertFalse(GrapnelRiptide.matchesDirection(new Vec3(-1, 0, 0), impulse));
        assertFalse(GrapnelRiptide.matchesDirection(new Vec3(0, 0, 1), impulse));
        assertFalse(GrapnelRiptide.matchesDirection(new Vec3(Double.NaN, 1, 0), impulse));
        assertFalse(GrapnelRiptide.matchesDirection(impulse, new Vec3(Double.POSITIVE_INFINITY, 0, 0)));
        assertFalse(GrapnelRiptide.matchesDirection(null, impulse));
    }
    @Test void dashLimitDoesNotRelaxTheOrdinaryTwoBlockObservers() {
        Vec3 start = Vec3.ZERO, three = new Vec3(3, 0, 0), thirteen = new Vec3(13, 0, 0);
        var fast = new ConfirmedPlayerMove.Step(start, three); var tooFar = new ConfirmedPlayerMove.Step(start, thirteen);
        assertFalse(ConfirmedPlayerMove.connected(start, three, List.of(fast)));
        assertTrue(ConfirmedPlayerMove.connected(start, three, List.of(fast), 12));
        assertFalse(ConfirmedPlayerMove.connected(start, thirteen, List.of(tooFar), 12));
        assertFalse(ConfirmedPlayerMove.connected(start, three, List.of(fast), 13));
        assertFalse(ConfirmedPlayerMove.connected(start, three, List.of(fast), Double.NaN));
        assertFalse(ConfirmedPlayerMove.connected(start, three, List.of(), 12));
        assertFalse(ConfirmedPlayerMove.connected(start, three, List.of(new ConfirmedPlayerMove.Step(new Vec3(1, 0, 0), three)), 12));
    }
}
