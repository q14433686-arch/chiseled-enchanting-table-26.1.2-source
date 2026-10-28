package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.HostForm;
import enchantment_fusion.model.PayloadType;
import enchantment_fusion.model.TriggerType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FusionModelAndMatrixTest {
    private static FusionMatrixRegistry.Snapshot snapshot;

    @BeforeAll
    static void loadRealDatapackMatrix() throws Exception {
        Path matrixPath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json");
        Path repulsivePath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/repulsive_pairs.json");
        JsonObject matrixJson = JsonParser.parseString(Files.readString(matrixPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject repulsiveJson = JsonParser.parseString(Files.readString(repulsivePath, StandardCharsets.UTF_8)).getAsJsonObject();
        snapshot = FusionMatrixRegistry.parseFromJson(matrixJson, repulsiveJson);
        FusionMatrixRegistry.publish(snapshot);
    }

    @Test
    void directionalOrderIsStrictlyDistinct() {
        FusedPair ab = new FusedPair("minecraft:sharpness", 5, "minecraft:fire_aspect", 2, "");
        FusedPair ba = ab.flipped();

        assertNotEquals(ab, ba);
        assertEquals("minecraft:fire_aspect", ba.primaryId());
        assertEquals(2, ba.primaryLevel());
        assertEquals("minecraft:sharpness", ba.secondaryId());
        assertEquals(5, ba.secondaryLevel());
        assertFalse(ab.isAberrant());
    }

    @Test
    void fusedEnchantmentListSupportsImmutableCrudAndAberrantDetection() {
        FusedPair p1 = new FusedPair("minecraft:efficiency", 5, "minecraft:loyalty", 3, "");
        FusedPair p2 = new FusedPair("minecraft:silk_touch", 1, "minecraft:fortune", 3, "schrodinger_pick");

        FusedEnchantmentList list = FusedEnchantmentList.EMPTY.append(p1);
        assertEquals(1, list.size());
        assertFalse(list.hasAberration());
        assertTrue(list.containsEnchantmentId("minecraft:efficiency"));
        assertTrue(list.containsEnchantmentId("minecraft:loyalty"));

        FusedEnchantmentList withAb = list.append(p2);
        assertEquals(2, withAb.size());
        assertTrue(withAb.hasAberration());

        FusedEnchantmentList afterRemoveFirst = withAb.removeAt(0);
        assertEquals(1, afterRemoveFirst.size());
        assertEquals("schrodinger_pick", afterRemoveFirst.pairs().get(0).aberrationId());
    }

    @Test
    void datapackCoversAllEightTriggersAndFourteenPayloads() {
        assertTrue(snapshot.profiles().size() >= 38, "Expected >= 38 non-curse vanilla enchantment profiles");

        Set<TriggerType> seenTriggers = EnumSet.noneOf(TriggerType.class);
        Set<PayloadType> seenPayloads = EnumSet.noneOf(PayloadType.class);
        for (var profile : snapshot.profiles().values()) {
            seenTriggers.add(profile.trigger());
            seenPayloads.add(profile.payload());
        }
        assertEquals(EnumSet.allOf(TriggerType.class), seenTriggers, "All 8 triggers must be populated in matrix_profiles.json");
        assertEquals(EnumSet.allOf(PayloadType.class), seenPayloads, "All 14 payloads must be populated in matrix_profiles.json");

        // Verify iconic cross-domain mappings from GDD §5
        assertEquals(TriggerType.ON_BLOCK_HARVEST, snapshot.resolve("minecraft:efficiency").trigger());
        assertEquals(PayloadType.RETURN, snapshot.resolve("minecraft:loyalty").payload());
        assertEquals(TriggerType.ON_MOVEMENT_OR_LAND, snapshot.resolve("minecraft:feather_falling").trigger());
        assertEquals(PayloadType.GRAVITY, snapshot.resolve("minecraft:density").payload());
        assertEquals(PayloadType.SCATTER, snapshot.resolve("minecraft:multishot").payload());
    }

    @Test
    void thirdPartyUnknownEnchantmentReceivesDeterministicFallback() {
        var modA = snapshot.resolve("mythic_enchants:solar_strike");
        var modA2 = snapshot.resolve("mythic_enchants:solar_strike");
        assertNotNull(modA.trigger());
        assertNotNull(modA.payload());
        assertEquals(modA, modA2, "Fallback profile for third-party enchantments must be deterministic");
        assertTrue(modA.payload().effectKey(HostForm.MELEE).startsWith("fusion.enchantment_fusion.effect."));
    }

    @Test
    void repulsiveExclusiveGroupsAreSymmetric() {
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:sharpness", "minecraft:smite"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:smite", "minecraft:sharpness"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:silk_touch", "minecraft:fortune"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:infinity", "minecraft:mending"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:multishot", "minecraft:piercing"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:density", "minecraft:breach"));
        assertFalse(snapshot.areRepulsiveExclusives("minecraft:sharpness", "minecraft:loyalty"));
    }
}
