package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import enchantment_fusion.mechanics.signatures.ConfirmedPlayerMove;
import enchantment_fusion.mechanics.signatures.MeleeWindSkills;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class MeleeWindTest {
    @Test void sixDirectionsHaveDistinctCarriersAndRespectExactPriority() {
        SignatureCatalog.reloadFromJson(null);
        try {
            for (String attack : new String[]{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"}) {
                FusedPair forward = FusedPair.clean(attack, 3, MeleeWindSkills.WIND, 1), reverse = forward.flipped();
                assertTrue(MeleeWindSkills.matches(forward)); assertTrue(MeleeWindSkills.matches(reverse));
                assertTrue(MeleeWindSkills.supportsCarrier(forward, Set.of(FusionCarrier.SWORD)));
                assertTrue(MeleeWindSkills.supportsCarrier(forward, Set.of(FusionCarrier.MACE)));
                assertFalse(MeleeWindSkills.supportsCarrier(forward, Set.of(FusionCarrier.TRIDENT)));
                assertTrue(MeleeWindSkills.supportsCarrier(reverse, Set.of(FusionCarrier.MACE)));
                assertFalse(MeleeWindSkills.supportsCarrier(reverse, Set.of(FusionCarrier.SWORD)));
                assertFalse(MeleeWindSkills.supportsCarrier(reverse, Set.of(FusionCarrier.FOOT_ARMOR)));
            }
            SignatureCatalog.reloadFromJson(JsonParser.parseString("""
                {"signatures":{"custom":{"primary":"minecraft:smite","secondary":"minecraft:wind_burst"}}}
                """).getAsJsonObject());
            assertFalse(MeleeWindSkills.matches(FusedPair.clean("minecraft:smite", 1, MeleeWindSkills.WIND, 1)));
            assertTrue(MeleeWindSkills.matches(FusedPair.clean(MeleeWindSkills.WIND, 1, "minecraft:smite", 1)));
        } finally { SignatureCatalog.reloadFromJson(null); }
    }
    @Test void onlyContinuousBoundedObservedMovementSettles() {
        Vec3 start = new Vec3(0, 5, 0), middle = new Vec3(0.4, 5, 0), end = new Vec3(0.8, 5, 0);
        var a = new ConfirmedPlayerMove.Step(start, middle); var b = new ConfirmedPlayerMove.Step(middle, end);
        assertTrue(ConfirmedPlayerMove.connected(start, end, List.of(a, b)));
        assertTrue(ConfirmedPlayerMove.connected(start, start, List.of()));
        assertFalse(ConfirmedPlayerMove.connected(start, end, List.of(a)));
        assertFalse(ConfirmedPlayerMove.connected(start, end, List.of(b)));
        assertFalse(ConfirmedPlayerMove.connected(start, new Vec3(8, 5, 0), List.of(new ConfirmedPlayerMove.Step(start, new Vec3(8, 5, 0)))));
        assertFalse(ConfirmedPlayerMove.connected(start, new Vec3(Double.NaN, 0, 0), List.of()));
        assertFalse(ConfirmedPlayerMove.connected(start, end, List.of(a, b, a, b, a)));
    }
    @Test void updraftAndDamageAreBounded() {
        Vec3 origin = new Vec3(0, 10, 0);
        assertTrue(MeleeWindSkills.insideUpdraft(origin, new Vec3(3, 16, 0)));
        assertTrue(MeleeWindSkills.insideUpdraft(origin, new Vec3(0, 8, 0)));
        assertFalse(MeleeWindSkills.insideUpdraft(origin, new Vec3(3.01, 10, 0)));
        assertFalse(MeleeWindSkills.insideUpdraft(origin, new Vec3(0, 16.01, 0)));
        assertFalse(MeleeWindSkills.insideUpdraft(origin, new Vec3(Double.NaN, 10, 0)));
        assertTrue(MeleeWindSkills.upwardImpulse(Vec3.ZERO, new Vec3(0, 0.2, 0)));
        assertFalse(MeleeWindSkills.upwardImpulse(Vec3.ZERO, Vec3.ZERO));
        assertFalse(MeleeWindSkills.upwardImpulse(Vec3.ZERO, new Vec3(Double.NaN, 0.2, 0)));
        assertFalse(MeleeWindSkills.upwardImpulse(null, Vec3.ZERO));
        assertEquals(2.5F, MeleeWindSkills.virtualDamage(1, 1), 0.0001F);
        assertEquals(7.0F, MeleeWindSkills.virtualDamage(100, 100), 0.0001F);
    }
}
