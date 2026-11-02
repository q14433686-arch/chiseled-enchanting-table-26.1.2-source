package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.signatures.ThunderRailSkills;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ThunderRailTest {
    @Test void directionAndActualUseCarrierAreNotSymmetric() {
        SignatureCatalog.reloadFromJson(null);
        FusedPair rail = FusedPair.clean(ThunderRailSkills.RIPTIDE, 3, ThunderRailSkills.CHANNELING, 1);
        assertTrue(ThunderRailSkills.matches(rail));
        assertFalse(ThunderRailSkills.matches(FusedPair.clean(ThunderRailSkills.CHANNELING, 1, ThunderRailSkills.RIPTIDE, 3)));
        assertTrue(ThunderRailSkills.supportsCarrier(Set.of(FusionCarrier.TRIDENT)));
        assertFalse(ThunderRailSkills.supportsCarrier(Set.of(FusionCarrier.SPEAR, FusionCarrier.SWORD)));
    }
    @Test void onlyFiniteConnectedAndBoundedDisplacementCanBecomeARail() {
        Vec3 start = Vec3.ZERO;
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), 3, 0));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(0.5, 0, 0), 0.5, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(25, 0, 0), 25, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), 2, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), Double.NaN, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(Double.NaN, 0, 0), 3, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), 33, 1));
        assertFalse(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), 3, 65));
        assertTrue(ThunderRailSkills.validRailMetrics(start, new Vec3(3, 0, 0), 3, 3));
        assertTrue(ThunderRailSkills.validRailMetrics(start, new Vec3(24, 0, 0), 24, 64));
    }
}
