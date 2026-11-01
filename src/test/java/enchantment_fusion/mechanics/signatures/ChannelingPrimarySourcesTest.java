package enchantment_fusion.mechanics.signatures;

import static org.junit.jupiter.api.Assertions.*;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ChannelingPrimarySourcesTest {
    @Test void siblingProjectionsUseOneHighestLevel() {
        assertEquals(0, ChannelingTridentPrimary.highestLevel(0, -1));
        assertEquals(4, ChannelingTridentPrimary.highestLevel(4, 2));
        assertEquals(4, ChannelingTridentPrimary.highestLevel(2, 4));
        assertEquals(3, ChannelingTridentPrimary.highestLevel(3, 3));
    }
    @Test void competingChannelingSourcesDoNotClaimOneStrike() {
        FusedPair lure = FusedPair.clean("minecraft:channeling", 1, "minecraft:lure", 2);
        FusedPair otherPrimary = FusedPair.clean("minecraft:channeling", 2, "minecraft:smite", 3);
        FusedPair otherSecondary = FusedPair.clean("minecraft:smite", 3, "minecraft:channeling", 1);
        FusedPair impaling = FusedPair.clean("minecraft:impaling", 4, "minecraft:lure", 2);
        assertFalse(GrapnelChanneling.unambiguous(List.of(), List.of()));
        assertTrue(GrapnelChanneling.unambiguous(List.of(lure), List.of()));
        assertTrue(GrapnelChanneling.unambiguous(List.of(lure, impaling), List.of()));
        assertFalse(GrapnelChanneling.unambiguous(List.of(lure, otherPrimary), List.of()));
        assertFalse(GrapnelChanneling.unambiguous(List.of(lure, otherSecondary), List.of()));
        assertFalse(GrapnelChanneling.unambiguous(List.of(lure), List.of(new RetainedEnchant("minecraft:channeling", 1))));
        assertTrue(GrapnelChanneling.unambiguous(List.of(lure), List.of(new RetainedEnchant("minecraft:unbreaking", 1))));
    }
}
