package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.DirectionalHostRules;
import enchantment_fusion.mechanics.FusionRuleEngine.RejectReason;
import enchantment_fusion.mechanics.HarvestDropCapture;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.menu.FusionNetworking.CompendiumDeltaPayload;
import enchantment_fusion.menu.FusionNetworking.CompendiumSyncPayload;
import enchantment_fusion.menu.FusionNetworking.MatrixSyncPayload;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class AuditBoundaryTest {
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }

    @Test
    void loyaltyExecutionTagsAvoidDurabilityDamageWithoutBypassingTotemOrProtectionHooks() throws Exception {
        var data = java.nio.file.Path.of("src/main/resources/data");
        var damage = data.resolve("enchantment_fusion/damage_type/loyalty_guard.json");
        assertTrue(java.nio.file.Files.isRegularFile(damage));
        var tags = data.resolve("minecraft/tags/damage_type");
        for (String tag : List.of("bypasses_armor", "bypasses_shield", "bypasses_resistance", "bypasses_cooldown")) {
            var values = JsonParser.parseString(java.nio.file.Files.readString(tags.resolve(tag + ".json")))
                .getAsJsonObject().getAsJsonArray("values");
            assertTrue(values.toString().contains("enchantment_fusion:loyalty_guard"), tag);
        }
        for (String tag : List.of("bypasses_invulnerability", "bypasses_enchantments")) {
            var path = tags.resolve(tag + ".json");
            assertTrue(!java.nio.file.Files.exists(path)
                || !java.nio.file.Files.readString(path).contains("enchantment_fusion:loyalty_guard"), tag);
        }
    }

    @Test
    void retainedSecondariesHaveStrictAndMatchingPersistentAndNetworkCodecs() {
        RetainedEnchant kept = new RetainedEnchant("protection", 4);
        assertEquals("minecraft:protection", kept.id());
        String one = RetainedEnchant.selectionToken(List.of(kept), 0);
        String two = RetainedEnchant.selectionToken(List.of(kept, kept), 0);
        assertTrue(one.startsWith("retained:0:") && one.length() < 128);
        assertNotEquals(one, two, "A replay after removing the first of two identical entries must go stale");
        assertNotEquals(two, RetainedEnchant.selectionToken(List.of(kept, kept), 1));
        assertNotEquals(one, RetainedEnchant.selectionToken(List.of(new RetainedEnchant("protection", 3)), 0));
        assertEquals("", RetainedEnchant.selectionToken(List.of(kept), 1));
        assertEquals(FusionNetworking.StationAction.EXTRACT_RETAINED, FusionNetworking.StationAction.fromWireId(22));
        var encoded = RetainedEnchant.CODEC.encodeStart(JsonOps.INSTANCE, kept).result().orElseThrow();
        assertEquals(kept, RetainedEnchant.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        assertTrue(RetainedEnchant.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"id\":\"minecraft:protection\",\"level\":0}")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new RetainedEnchant("protection", 256));
        var bytes = Unpooled.buffer();
        try {
            RetainedEnchant.STREAM_CODEC.encode(bytes, kept);
            assertEquals(kept, RetainedEnchant.STREAM_CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
        var malformed = Unpooled.buffer();
        try {
            net.minecraft.network.codec.ByteBufCodecs.stringUtf8(FusedPair.MAX_ENCHANT_ID_LENGTH)
                .encode(malformed, "minecraft:protection");
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(malformed, 0);
            assertThrows(DecoderException.class, () -> RetainedEnchant.STREAM_CODEC.decode(malformed));
        } finally { malformed.release(); }
    }

    @Test
    void identifiersAreBoundedAfterTheDefaultNamespaceIsAdded() {
        String largestPath = "a".repeat(118);
        assertEquals(128, FusedPair.canonicalizeEnchantId(largestPath).length());
        assertThrows(IllegalArgumentException.class, () -> FusedPair.canonicalizeEnchantId("a".repeat(119)));
        FusedPair pair = FusedPair.clean(largestPath, 1, "minecraft:sharpness", 1);
        var bytes = Unpooled.buffer();
        try {
            FusedPair.STREAM_CODEC.encode(bytes, pair);
            assertEquals(pair, FusedPair.STREAM_CODEC.decode(bytes));
        } finally { bytes.release(); }
    }

    @Test
    void matrixSyncRoundTripsAndRejectsCountsWithoutWritingPartialPackets() {
        FusionMatrixRegistry.Snapshot snapshot = FusionMatrixRegistry.serverSnapshot();
        FriendlyByteBuf good = buffer();
        try {
            MatrixSyncPayload.STREAM_CODEC.encode(good, new MatrixSyncPayload(snapshot));
            var restored = MatrixSyncPayload.STREAM_CODEC.decode(good).snapshot();
            assertNotNull(restored);
            assertEquals(snapshot.profiles(), restored.profiles());
            assertEquals(snapshot.exclusiveGroups(), restored.exclusiveGroups());
            assertEquals(snapshot.economy().midFailChance(), restored.economy().midFailChance(), 1e-9);
            assertEquals(0, good.readableBytes());
        } finally { good.release(); }

        for (int[] counts : List.of(
            new int[]{MatrixSyncPayload.MAX_PROFILES + 1},
            new int[]{-1},
            new int[]{0, MatrixSyncPayload.MAX_GROUPS + 1},
            new int[]{0, 1, MatrixSyncPayload.MAX_GROUP_MEMBERS + 1}
        )) {
            FriendlyByteBuf malformed = buffer();
            try {
                malformed.writeVarInt(FusionNetworking.MATRIX_SYNC_PROTOCOL_VERSION);
                for (int count : counts) malformed.writeVarInt(count);
                assertThrows(DecoderException.class, () -> MatrixSyncPayload.STREAM_CODEC.decode(malformed));
            } finally { malformed.release(); }
        }
        Map<String, FusionMatrixRegistry.MatrixProfile> tooMany = new HashMap<>();
        var profile = snapshot.profiles().values().iterator().next();
        for (int i = 0; i <= MatrixSyncPayload.MAX_PROFILES; i++) tooMany.put("test:" + i, profile);
        var oversized = new FusionMatrixRegistry.Snapshot(tooMany, List.of(), snapshot.economy());
        FriendlyByteBuf output = buffer();
        try {
            assertThrows(EncoderException.class, () -> MatrixSyncPayload.STREAM_CODEC.encode(output, new MatrixSyncPayload(oversized)));
            assertEquals(0, output.writerIndex());
        } finally { output.release(); }
        Set<String> oversizedGroup = new HashSet<>();
        for (int i = 0; i <= MatrixSyncPayload.MAX_GROUP_MEMBERS; i++) oversizedGroup.add("test:" + i);
        assertThrows(IllegalArgumentException.class, () -> MatrixSyncPayload.validateSnapshot(
            new FusionMatrixRegistry.Snapshot(Map.of(), List.of(oversizedGroup), snapshot.economy())));
    }

    @Test
    void thirdPartyFusionNeedsExplicitMeaningAndFusionHostsWhileVanillaDoesNot() {
        FusedPair pair = FusedPair.clean("example:storm_edge", 1, "minecraft:silk_touch", 1);
        var defaults = FusionMatrixRegistry.createBuiltInSnapshot();
        assertEquals(RejectReason.UNCLASSIFIED_ENCHANTMENT,
            DirectionalHostRules.validateClassifications(pair, Set.of(FusionCarrier.SWORD), defaults));
        assertTrue(FusionMatrixRegistry.isBuiltInVanillaEnchantment("minecraft:silk_touch"));
        assertFalse(FusionMatrixRegistry.isBuiltInVanillaEnchantment("minecraft:datapack_custom"));

        var incomplete = FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"trigger\":\"ON_MELEE_STRIKE\",\"payload\":\"VOLT\"}}}"
        ).getAsJsonObject(), null);
        assertEquals(RejectReason.UNCLASSIFIED_ENCHANTMENT,
            DirectionalHostRules.validateClassifications(pair, Set.of(FusionCarrier.SWORD), incomplete));
        var invalidMeaning = FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"trigger\":\"unknown\",\"payload\":\"VOLT\",\"fusion_hosts\":[\"SWORD\"]}}}"
        ).getAsJsonObject(), null);
        assertEquals(RejectReason.UNCLASSIFIED_ENCHANTMENT,
            DirectionalHostRules.validateClassifications(pair, Set.of(FusionCarrier.SWORD), invalidMeaning));
        var classified = FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"trigger\":\"ON_MELEE_STRIKE\",\"payload\":\"VOLT\",\"fusion_hosts\":[\"SWORD\",\"AXE\"]}}}"
        ).getAsJsonObject(), null);
        assertEquals(RejectReason.NONE, DirectionalHostRules.validateClassifications(pair, Set.of(FusionCarrier.SWORD), classified));
        assertEquals(RejectReason.NONE, DirectionalHostRules.validateClassifications(pair.flipped(), Set.of(FusionCarrier.AXE), classified));
        assertEquals(RejectReason.UNSUPPORTED_DIRECTION_HOST,
            DirectionalHostRules.validateClassifications(pair, Set.of(FusionCarrier.PICKAXE), classified));
        assertEquals(RejectReason.NONE, DirectionalHostRules.validateClassifications(pair,
            Set.of(FusionCarrier.PICKAXE, FusionCarrier.SWORD), classified), "A hybrid item's explicit capability must count");
        assertThrows(IllegalArgumentException.class, () -> FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"fusion_hosts\":[\"TYPO\"]}}}"
        ).getAsJsonObject(), null));
        assertThrows(IllegalArgumentException.class, () -> FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"fusion_hosts\":[\"AXE\",\"AXE\"]}}}"
        ).getAsJsonObject(), null));
    }

    @Test
    void matrixWireIncludesThirdPartyCarriersAndRejectsMalformedHostCounts() {
        var classified = FusionMatrixRegistry.parseFromJson(JsonParser.parseString(
            "{\"profiles\":{\"example:storm_edge\":{\"trigger\":\"ON_MELEE_STRIKE\",\"payload\":\"VOLT\",\"fusion_hosts\":[\"SWORD\",\"AXE\"]}}}"
        ).getAsJsonObject(), null);
        FriendlyByteBuf data = buffer();
        try {
            MatrixSyncPayload.STREAM_CODEC.encode(data, new MatrixSyncPayload(classified));
            var decoded = MatrixSyncPayload.STREAM_CODEC.decode(data).snapshot();
            assertNotNull(decoded);
            assertEquals(classified.profiles(), decoded.profiles());
            assertTrue(decoded.profiles().get("example:storm_edge").classifiedForFusion());
        } finally { data.release(); }
        FriendlyByteBuf malformed = buffer();
        try {
            malformed.writeVarInt(FusionNetworking.MATRIX_SYNC_PROTOCOL_VERSION);
            malformed.writeVarInt(1);
            malformed.writeUtf("example:storm_edge", 128);
            malformed.writeUtf("ON_MELEE_STRIKE", 64);
            malformed.writeUtf("VOLT", 64);
            malformed.writeBoolean(true);
            malformed.writeVarInt(FusionCarrier.values().length + 1);
            assertThrows(DecoderException.class, () -> MatrixSyncPayload.STREAM_CODEC.decode(malformed));
        } finally { malformed.release(); }
    }

    @Test
    void matrixWireReadsVersionTwoProfilesAsUnclassified() {
        var full = FusionMatrixRegistry.createBuiltInSnapshot();
        var profile = full.profiles().get("minecraft:sharpness");
        var one = new FusionMatrixRegistry.Snapshot(Map.of("example:legacy", profile), List.of(), full.economy());
        FriendlyByteBuf modern = buffer();
        FriendlyByteBuf old = buffer();
        try {
            MatrixSyncPayload.STREAM_CODEC.encode(modern, new MatrixSyncPayload(one));
            assertEquals(3, modern.readVarInt());
            assertEquals(1, modern.readVarInt());
            String id = modern.readUtf(128), trigger = modern.readUtf(64), payload = modern.readUtf(64);
            assertFalse(modern.readBoolean());
            assertEquals(0, modern.readVarInt());
            old.writeVarInt(2); old.writeVarInt(1);
            old.writeUtf(id, 128); old.writeUtf(trigger, 64); old.writeUtf(payload, 64);
            old.writeBytes(modern);
            var decoded = MatrixSyncPayload.STREAM_CODEC.decode(old).snapshot();
            assertNotNull(decoded);
            assertFalse(decoded.profiles().get("example:legacy").classifiedForFusion());
            assertEquals(0, old.readableBytes());
        } finally { modern.release(); old.release(); }
    }

    @Test
    void capturedHarvestOwnershipRejectsBothConflictingThrowerAndPickupTarget() {
        UUID miner = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(HarvestDropCapture.ownerCompatible(miner, null, null));
        assertTrue(HarvestDropCapture.ownerCompatible(miner, miner, miner));
        assertFalse(HarvestDropCapture.ownerCompatible(miner, other, null));
        assertFalse(HarvestDropCapture.ownerCompatible(miner, null, other));
        assertFalse(HarvestDropCapture.ownerCompatible(miner, miner, other));
        assertFalse(HarvestDropCapture.ownerCompatible(null, null, null));
    }

    @Test
    void replenishPoliciesArePerCategoryBoundedAndDatapackConfigurable() {
        var parsed = TransmuteCatalog.parseFromJson(JsonParser.parseString(
            "{\"variants\":{},\"replenish_settings\":{\"food\":{\"xp_interval\":31,\"group_cap\":12},\"potion\":{\"xp_interval\":80}}}"
        ).getAsJsonObject());
        assertEquals(new TransmuteCatalog.ReplenishPolicy(31, 12), parsed.replenishPolicies().get(TransmuteCatalog.ReplenishCategory.FOOD));
        assertEquals(new TransmuteCatalog.ReplenishPolicy(80, 4), parsed.replenishPolicies().get(TransmuteCatalog.ReplenishCategory.POTION));
        assertEquals(new TransmuteCatalog.ReplenishPolicy(22, 64), parsed.replenishPolicies().get(TransmuteCatalog.ReplenishCategory.THROWABLE));
        for (String bad : List.of(
            "{\"food\":{\"xp_interval\":0}}", "{\"food\":{\"group_cap\":257}}",
            "{\"food\":{\"xp_interval\":1.5}}", "{\"unrecognized\":{\"group_cap\":1}}"
        )) {
            assertThrows(IllegalArgumentException.class, () -> TransmuteCatalog.parseFromJson(JsonParser.parseString(
                "{\"variants\":{},\"replenish_settings\":" + bad + "}"
            ).getAsJsonObject()));
        }
    }

    @Test
    void longValidPairDiscoveryFitsBothCompendiumCodecs() {
        String pairKey = "a".repeat(128) + ">" + "b".repeat(128);
        var bytes = Unpooled.buffer();
        try {
            CompendiumDeltaPayload delta = new CompendiumDeltaPayload(pairKey, 2);
            CompendiumDeltaPayload.STREAM_CODEC.encode(bytes, delta);
            assertEquals(delta, CompendiumDeltaPayload.STREAM_CODEC.decode(bytes));
            CompendiumSyncPayload sync = new CompendiumSyncPayload(List.of(pairKey + "=2"));
            CompendiumSyncPayload.STREAM_CODEC.encode(bytes, sync);
            assertEquals(sync, CompendiumSyncPayload.STREAM_CODEC.decode(bytes));
        } finally { bytes.release(); }
    }

    @Test
    void ruleCatalogsNormalizeIdsFreezeInnerMapsAndRejectConflictingRoles() {
        var matrix = FusionMatrixRegistry.parseFromJson(
            JsonParser.parseString("{\"profiles\":{\"sharpness\":{\"trigger\":\"ON_MELEE_STRIKE\",\"payload\":\"EDGE\"}}}").getAsJsonObject(),
            JsonParser.parseString("{\"replace\":true,\"exclusive_groups\":[[\"sharpness\",\"smite\"]]}").getAsJsonObject());
        assertTrue(matrix.profiles().containsKey("minecraft:sharpness"));
        assertTrue(matrix.areRepulsiveExclusives("minecraft:sharpness", "minecraft:smite"));

        var sigs = SignatureCatalog.parseFromJson(JsonParser.parseString(
            "{\"signatures\":{\"test\":{\"primary\":\"sharpness\",\"secondary\":\"smite\"}}}").getAsJsonObject());
        assertTrue(sigs.directMap().containsKey("minecraft:sharpness>minecraft:smite"));

        assertThrows(IllegalArgumentException.class, () -> SignatureCatalog.parseFromJson(JsonParser.parseString(
            "{\"signatures\":{\"a\":{\"primary\":\"sharpness\",\"secondary\":\"smite\"},"
                + "\"b\":{\"primary\":\"minecraft:sharpness\",\"secondary\":\"minecraft:smite\"}}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> SignatureCatalog.parseFromJson(JsonParser.parseString(
            "{\"signatures\":{\"a\":{\"primary\":\"*\",\"secondary\":\"lure\"},"
                + "\"b\":{\"primary\":\"*\",\"secondary\":\"minecraft:lure\"}}}").getAsJsonObject()));

        var trans = TransmuteCatalog.parseFromJson(JsonParser.parseString(
            "{\"variants\":{\"a\":{\"source\":\"punch\",\"roles\":[\"CROSSBOW\"]}}}").getAsJsonObject());
        var byRole = trans.matrix().get("minecraft:punch");
        assertEquals("a", byRole.get(TargetRole.CROSSBOW).variant().id());
        assertThrows(UnsupportedOperationException.class, () -> byRole.clear());
        assertThrows(IllegalArgumentException.class, () -> TransmuteCatalog.parseFromJson(JsonParser.parseString(
            "{\"variants\":{\"a\":{\"source\":\"punch\",\"roles\":[\"CROSSBOW\"]},"
                + "\"b\":{\"source\":\"minecraft:punch\",\"roles\":[\"CROSSBOW\"]}}}").getAsJsonObject()));
    }
}
