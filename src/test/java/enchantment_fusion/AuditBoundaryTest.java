package enchantment_fusion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.mechanics.signatures.SignatureCatalog;
import enchantment_fusion.mechanics.transmute.TransmuteCatalog;
import enchantment_fusion.menu.FusionNetworking;
import enchantment_fusion.menu.FusionNetworking.CompendiumDeltaPayload;
import enchantment_fusion.menu.FusionNetworking.CompendiumSyncPayload;
import enchantment_fusion.menu.FusionNetworking.MatrixSyncPayload;
import enchantment_fusion.model.FusedPair;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class AuditBoundaryTest {
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }

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
