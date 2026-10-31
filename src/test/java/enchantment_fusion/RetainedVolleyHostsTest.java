package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import enchantment_fusion.mechanics.signatures.RetainedVolleyHosts;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RetainedVolleyHostsTest {
    @Test void orderAndCapabilitiesAreDistinct() {
        var ranged = FusedPair.clean("minecraft:efficiency", 4, "minecraft:multishot", 1);
        var mining = ranged.flipped();
        assertTrue(RetainedVolleyHosts.matches(ranged, RetainedVolleyHosts.RANGED));
        assertTrue(RetainedVolleyHosts.matches(mining, RetainedVolleyHosts.MINING));
        assertFalse(RetainedVolleyHosts.matches(mining, RetainedVolleyHosts.RANGED));
        assertTrue(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.BOW), RetainedVolleyHosts.RANGED));
        assertTrue(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.CROSSBOW), RetainedVolleyHosts.RANGED));
        assertFalse(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.SWORD), RetainedVolleyHosts.RANGED));
        assertTrue(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.PICKAXE), RetainedVolleyHosts.MINING));
        assertTrue(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.SHEARS), RetainedVolleyHosts.MINING));
        assertFalse(RetainedVolleyHosts.supportsRoles(Set.of(FusionCarrier.BOW), RetainedVolleyHosts.MINING));
    }
}
