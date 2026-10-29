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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FusionModelAndMatrixTest {
    private static FusionMatrixRegistry.Snapshot snapshot;

    @BeforeAll
    static void loadRealDatapackMatrix() throws Exception {
        Path matrixPath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/matrix_profiles.json");
        Path repulsivePath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/repulsive_pairs.json");
        Path economyPath = Path.of("src/main/resources/data/enchantment_fusion/enchantment_fusion/economy.json");
        JsonObject matrixJson = JsonParser.parseString(Files.readString(matrixPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject repulsiveJson = JsonParser.parseString(Files.readString(repulsivePath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject economyJson = JsonParser.parseString(Files.readString(economyPath, StandardCharsets.UTF_8)).getAsJsonObject();
        snapshot = FusionMatrixRegistry.parseFromJson(matrixJson, repulsiveJson, economyJson);
        FusionMatrixRegistry.publish(snapshot);
    }

    @Test
    void directionalOrderAndLevelTruncationInvariants() {
        FusedPair ab = new FusedPair("minecraft:sharpness", 5, "minecraft:fire_aspect", 2, "");
        FusedPair ba = ab.flipped();

        assertNotEquals(ab, ba);
        assertEquals("minecraft:fire_aspect", ba.primaryId());
        assertEquals(2, ba.primaryLevel());
        assertEquals("minecraft:sharpness", ba.secondaryId());
        assertEquals(5, ba.secondaryLevel());
        assertFalse(ab.isAberrant());

        // DATA-01: Constructor clamps levels to [1, 255] symmetrically with StreamCodec
        FusedPair clamped = new FusedPair("minecraft:sharpness", 999, "minecraft:loyalty", 0, null);
        assertEquals(255, clamped.primaryLevel());
        assertEquals(1, clamped.secondaryLevel());
        assertEquals("", clamped.aberrationId());

        // CPL-1 / NET-1: withoutAberration() strips aberrationId while preserving pure [A ▷ B], and fingerprint() distinguishes pairs
        FusedPair mutated = new FusedPair("sharpness", 5, "minecraft:smite", 5, "blood_frenzy");
        assertEquals("minecraft:sharpness", mutated.primaryId());
        assertTrue(mutated.isAberrant());
        FusedPair purified = mutated.withoutAberration();
        assertFalse(purified.isAberrant());
        assertEquals("minecraft:sharpness", purified.primaryId());
        assertEquals("minecraft:smite", purified.secondaryId());
        assertNotEquals(mutated.fingerprint(), purified.fingerprint());
        assertNotEquals(ab.fingerprint(), ba.fingerprint());
    }

    @Test
    void fusedEnchantmentListSupportsImmutableCrudTruncationAndAberrantDetection() {
        FusedPair p1 = new FusedPair("minecraft:efficiency", 5, "minecraft:loyalty", 3, "");
        FusedPair p2 = new FusedPair("minecraft:silk_touch", 1, "minecraft:fortune", 3, "ab_greedy_abyss");
        FusedPair p3 = new FusedPair("minecraft:power", 5, "minecraft:flame", 1, "");

        FusedEnchantmentList list = FusedEnchantmentList.EMPTY.append(p1);
        assertEquals(1, list.size());
        assertFalse(list.hasAberration());
        assertTrue(list.containsEnchantmentId("minecraft:efficiency"));
        assertTrue(list.containsEnchantmentId("minecraft:loyalty"));

        FusedEnchantmentList withAb = list.append(p2);
        assertEquals(2, withAb.size());
        assertTrue(withAb.hasAberration());

        // DATA-01: Hard max 2 vessel slots enforced even on oversized raw lists
        FusedEnchantmentList truncated = new FusedEnchantmentList(List.of(p1, p2, p3));
        assertEquals(2, truncated.size());

        FusedEnchantmentList afterRemoveFirst = withAb.removeAt(0);
        assertEquals(1, afterRemoveFirst.size());
        assertEquals("ab_greedy_abyss", afterRemoveFirst.pairs().get(0).aberrationId());
    }

    @Test
    void datapackCoversAllEightTriggersFourteenPayloadsAndReplaceSemantics() {
        assertTrue(snapshot.profiles().size() >= 43, "Expected all 43 vanilla 26.1.2 enchantment profiles");

        Set<TriggerType> seenTriggers = EnumSet.noneOf(TriggerType.class);
        Set<PayloadType> seenPayloads = EnumSet.noneOf(PayloadType.class);
        for (var profile : snapshot.profiles().values()) {
            seenTriggers.add(profile.trigger());
            seenPayloads.add(profile.payload());
        }
        assertEquals(EnumSet.allOf(TriggerType.class), seenTriggers, "All 8 triggers must be populated in matrix_profiles.json");
        assertEquals(EnumSet.allOf(TriggerType.class).size(), 8);
        assertEquals(EnumSet.allOf(PayloadType.class), seenPayloads, "All 14 payloads must be populated in matrix_profiles.json");

        // DATA-04: Explicit empty exclusive_groups with replace:true clears repulsive groups cleanly
        JsonObject clearRepulsive = JsonParser.parseString("{\"replace\": true, \"exclusive_groups\": []}").getAsJsonObject();
        var clearedSnap = FusionMatrixRegistry.parseFromJson(null, clearRepulsive, null);
        assertTrue(clearedSnap.exclusiveGroups().isEmpty());
        assertFalse(clearedSnap.areRepulsiveExclusives("minecraft:sharpness", "minecraft:smite"));
    }

    @Test
    void thirdPartyUnknownEnchantmentReceivesDeterministicFallback() {
        var modA = snapshot.resolve("mythic_enchants:solar_strike");
        var modA2 = snapshot.resolve("mythic_enchants:solar_strike");
        assertNotNull(modA.trigger());
        assertNotNull(modA.payload());
        assertEquals(modA, modA2, "Fallback profile for third-party enchantments must be deterministic");
        assertEquals(TriggerType.ON_UTILITY_CYCLE, modA.trigger());
        assertEquals(PayloadType.MEND, modA.payload());
        assertEquals(modA, snapshot.resolve("other_mod:unclassified"));
        assertTrue(modA.payload().effectKey(HostForm.MELEE).startsWith("fusion.enchantment_fusion.effect."));
    }

    @Test
    void repulsiveExclusiveGroupsIncludeChannelingRiptideAndAreSymmetric() {
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:sharpness", "minecraft:smite"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:smite", "minecraft:sharpness"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:silk_touch", "minecraft:fortune"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:infinity", "minecraft:mending"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:multishot", "minecraft:piercing"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:density", "minecraft:breach"));
        assertTrue(snapshot.areRepulsiveExclusives("minecraft:channeling", "minecraft:riptide"), "DATA-05: channeling x riptide must be repulsive");
        assertFalse(snapshot.areRepulsiveExclusives("minecraft:sharpness", "minecraft:loyalty"));
    }

    @Test
    void economySchemaV2CeilingsCooldownsAndClientServerIsolation() {
        var econ = snapshot.economy();
        assertEquals(25, econ.purifyAberrantXp());
        assertEquals(3, econ.purifyAberrantStabilizers());
        assertEquals(0.55, econ.meleeMinCharge(), 1e-6);
        assertEquals(0.90, econ.meleeFullCharge(), 1e-6);
        assertEquals(8, econ.triggerCooldowns().size());
        assertEquals(60, econ.triggerCooldowns().get(TriggerType.ON_DAMAGE_TAKEN));

        // Configurable ceiling > default allows higher datapack failure chance when ceiling is raised
        JsonObject customEcon = JsonParser.parseString(
            "{\"schema_version\":2,\"fail_ceiling_mid\":0.80,\"mid_fail_chance\":0.72}"
        ).getAsJsonObject();
        var customSnap = FusionMatrixRegistry.parseFromJson(null, null, customEcon);
        assertEquals(0.72, customSnap.economy().midFailChance(), 1e-6);

        // Client snapshot override never mutates serverSnapshot()
        try {
            FusionMatrixRegistry.setClientSnapshot(customSnap);
            assertEquals(0.18, FusionMatrixRegistry.serverSnapshot().economy().midFailChance(), 1e-6);
            assertEquals(0.72, FusionMatrixRegistry.clientView().economy().midFailChance(), 1e-6);
        } finally {
            FusionMatrixRegistry.setClientSnapshot(null);
        }
        assertEquals(0.18, FusionMatrixRegistry.clientView().economy().midFailChance(), 1e-6);
    }
}
