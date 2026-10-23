package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.LibraryResonance;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.*;
import chiseled_enchanting_table.menu.EnchantCostTablePayload;
import chiseled_enchanting_table.menu.PayloadLimits;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real codecs, not source-string checks. No item/registry bootstrap required for these fixtures. */
class NetworkCompatibilityTest {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("audit", path); }

    private static <T> void roundTrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            codec.encode(buf, value);
            assertEquals(value, codec.decode(buf));
            assertEquals(0, buf.readableBytes(), "Unconsumed bytes: encoder/decoder field drift");
        } finally { buf.release(); }
    }

    @Test void openingAndLiveShelfCodecsPreserveAllFields() {
        var unlocked = Set.of(new EnchantmentWithLevel(id("one"), 1), new EnchantmentWithLevel(id("two"), 255));
        var masters = Map.of(id("one"), 7, id("two"), 255);
        var display = new TransformationDisplay(id("rule"), id("input_tag"), true, id("out"), 2, 30, 3);
        roundTrip(AvailableEnchantmentPayload.AVAILABLE_CHISELED_ENCHANTMENT_CODEC,
            new AvailableEnchantmentPayload(unlocked, List.of(display), 37, 38, 123, 17, -123456789, masters, true));
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(27, unlocked, 37, 38, 123, 17, masters, true));
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(3, Set.of(), 0, 0, 0, 0, Map.of(), false));
    }

    @Test void liveUpdatesOnlyTargetTheMatchingMenu() {
        var live = new LiveShelfStatePayload(27, Set.of(), 0, 0, 0, 0, Map.of(), false);
        assertTrue(live.targets(27));
        assertFalse(live.targets(28));
        assertFalse(live.targets(0));
    }

    @Test void allActionTokensAndNoBodyPacketsRoundTrip() {
        for (OptionType type : OptionType.values()) {
            roundTrip(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC,
                new ApplyEnchantmentPayload(type, id("action"), 255));
        }
        roundTrip(RerollEpiphanyPayload.CODEC, new RerollEpiphanyPayload());
        roundTrip(FreezeEpiphanyPayload.CODEC, new FreezeEpiphanyPayload());
    }

    @Test void costTableConfigurationAndRowsRoundTrip() {
        // No ItemStack fields in this fixture: validates every config field/order headlessly.
        var display = new TransformationDisplay(id("custom_rule"), id("tag"), true, id("custom_output"), 7, 29, 2);
        roundTrip(EnchantCostTablePayload.CODEC, table(List.of(display)));
        roundTrip(EnchantCostTablePayload.CODEC, table(List.of()));
    }

    private static EnchantCostTablePayload table(List<TransformationDisplay> displays) {
        var gates = new BookshelfProgression.Config(false, 47, 1.37,
            BookshelfProgression.BlankMaxTierMode.FULL_CURVE, 4, 3,
            Map.of(id("override"), List.of(0, 3, 11, 23, 47)), 2, 19, 0.41, 9, 7, 61,
            BookshelfProgression.VanillaTableMode.BOOKS, true, 6, false);
        // Distinct values catch field swaps hidden by equal default config values.
        var resonance = new LibraryResonance.Config(13, 39, 21, 8, 29, 12, 1.13, 41,
            1.21, 1.31, 0.73, 0.83, 0.43, 0.53, 0.63, 1.03, 2.13, 3.23,
            0.67, 81.0, 0.17, 0.57, 111.0, 0.27, 0.87,
            chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR, 16, 4,
            chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.DepleteBehavior.STEP_DOWN);
        return new EnchantCostTablePayload(true, false, true, false, 0.5, 0.25,
            33, 0.13, 0.02, 0.03, 0.9, 4, 12, 1234, 500, 1200, 7, List.of(),
            11, List.of(), Set.of(id("treasure")), gates, resonance,
            List.of(new EnchantCostTablePayload.CostEntry(OptionType.PURGE_CURSE, id("curse"), 3, 42, List.of())), displays);
    }

    @Test void emptyServerCatalogIsNotLocalFallbackAndDisconnectClearsIt() {
        chiseled_enchanting_table.compat.SyncedEnchantmentCosts.apply(table(List.of()));
        try {
            assertTrue(chiseled_enchanting_table.compat.SyncedEnchantmentCosts.hasServerData());
            assertTrue(chiseled_enchanting_table.compat.SyncedEnchantmentCosts.transformationDisplays().orElseThrow().isEmpty());
        } finally {
            chiseled_enchanting_table.compat.SyncedEnchantmentCosts.clear();
        }
        assertFalse(chiseled_enchanting_table.compat.SyncedEnchantmentCosts.hasServerData());
        assertTrue(chiseled_enchanting_table.compat.SyncedEnchantmentCosts.transformationDisplays().isEmpty());
    }

    @Test void unknownActionsCannotSilentlyBecomeEnchantment() {
        assertThrows(IllegalArgumentException.class, () -> OptionType.fromToken("new_or_mismatched_action"));
    }

    @Test void countsAreValidatedBeforeAllocationAndIteration() {
        for (int count : new int[] {-1, Integer.MAX_VALUE, PayloadLimits.MAX_COLLECTION_ENTRIES + 1}) {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            try {
                buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> PayloadLimits.readCount(buf));
            } finally { buf.release(); }
        }
        var truncated = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            truncated.writeVarInt(1); // missing first enchantment ID, must fail rather than accept empty state
            assertThrows(RuntimeException.class, () -> LiveShelfStatePayload.CODEC.decode(truncated));
        } finally { truncated.release(); }
    }
    @Test void protocolCapabilityRejectsOlderSameVersionBuildsButNotUnmoddedPeers() {
        assertTrue(chiseled_enchanting_table.menu.EnchantingProtocol.incompatible(true, false));
        assertFalse(chiseled_enchanting_table.menu.EnchantingProtocol.incompatible(true, true));
        assertFalse(chiseled_enchanting_table.menu.EnchantingProtocol.incompatible(false, false));
        assertNotEquals(chiseled_enchanting_table.menu.EnchantingProtocol.LEGACY_COST_TYPE.id(), EnchantCostTablePayload.TYPE.id());
        assertTrue(EnchantCostTablePayload.TYPE.id().getPath().endsWith(chiseled_enchanting_table.menu.EnchantingProtocol.REVISION));
        assertTrue(LiveShelfStatePayload.TYPE.id().getPath().endsWith(chiseled_enchanting_table.menu.EnchantingProtocol.REVISION));
        roundTrip(chiseled_enchanting_table.menu.EnchantingProtocol.Capability.CODEC,
            new chiseled_enchanting_table.menu.EnchantingProtocol.Capability());
    }

    @Test void runtimeConfigRequestAndAcknowledgementRoundTrip() {
        var config = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT.toJsonString();
        roundTrip(chiseled_enchanting_table.config.ModConfigNetworking.UpdateRuntimeConfigPayload.CODEC,
            new chiseled_enchanting_table.config.ModConfigNetworking.UpdateRuntimeConfigPayload(config, 8123456789L));
        for (boolean accepted : new boolean[] {false, true}) {
            roundTrip(chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload.CODEC,
                new chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload(config, true, 8123456789L, accepted));
        }
    }

}
