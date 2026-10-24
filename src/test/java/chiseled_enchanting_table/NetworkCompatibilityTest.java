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
        var display = new TransformationDisplay(id("rule"), id("input_tag"), true, id("out"), 2, 30, 3, true);
        roundTrip(AvailableEnchantmentPayload.AVAILABLE_CHISELED_ENCHANTMENT_CODEC,
            new AvailableEnchantmentPayload(unlocked, List.of(display), 37, 38, 123, 17, -123456789, masters, true));
        // Round 2: persistent page state (unlock flags + sacrifice progress) rides the
        // opening payload and must survive the codec bit-exactly.
        var openingState = new AvailableEnchantmentPayload(unlocked, List.of(display), 37, 38, 123, 17, -123456789,
            masters, true, 9, 2, Map.of(new EnchantmentWithLevel(id("one"), 1), 3), true, false, 7, 12, 4);
        roundTrip(AvailableEnchantmentPayload.AVAILABLE_CHISELED_ENCHANTMENT_CODEC, openingState);
        assertTrue(openingState.cleanse_unlocked());
        assertFalse(openingState.tome_page_unlocked());
        assertEquals(7, openingState.sacrifice_progress());
        assertEquals(12, openingState.sacrifice_required());
        assertEquals(4, openingState.tome_page_required_shelves());
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(27, unlocked, 37, 38, 123, 17, masters, true));
        roundTrip(LiveShelfStatePayload.CODEC,
            new LiveShelfStatePayload(3, Set.of(), 0, 0, 0, 0, Map.of(), false));
    }

    @Test void tomeDashboardAndActionPayloadsRoundTrip() {
        var entry = new TomeBookEntry(new net.minecraft.core.BlockPos(1, 64, -3), 5, "Archivist",
            List.of(new EnchantmentWithLevel(id("mending"), 1)), 2, 3, 5, 1,
            true, false, 100, 500, 1, "owner-id", "Owner", 0, true,
            "minecraft:mending", 1, 2, "minecraft:mending", 1);
        var live = new LiveShelfStatePayload(27, Set.of(), 1, 2, 3, 4, Map.of(), false,
            5, 6, Map.of(), true, true, 7, 12, 4, 4, 42, 1, 4, 72000, List.of(entry));
        roundTrip(LiveShelfStatePayload.CODEC, live);
        assertTrue(live.cleanse_unlocked());
        assertTrue(live.tome_page_unlocked());
        assertEquals(7, live.sacrifice_progress());
        assertEquals(entry, live.tome_books().getFirst());
        assertEquals("minecraft:mending", entry.last_used_enchant());
        assertEquals(2, entry.deplete_outcome());
        assertEquals(1, entry.deplete_level());
        for (TomePageActionPayload.Action action : TomePageActionPayload.Action.values()) {
            var payload = new TomePageActionPayload(42, action, entry.shelf_pos(), entry.slot());
            roundTrip(TomePageActionPayload.CODEC, payload);
            assertTrue(payload.targets(42));
            assertFalse(payload.targets(43));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new TomePageActionPayload(42, TomePageActionPayload.Action.UNLOCK, entry.shelf_pos(), 6));
        assertThrows(IllegalArgumentException.class,
            () -> new TomePageActionPayload(-2, TomePageActionPayload.Action.UNLOCK, entry.shelf_pos(), 0));
    }

    @Test void liveUpdatesOnlyTargetTheMatchingMenu() {
        var live = new LiveShelfStatePayload(27, Set.of(), 0, 0, 0, 0, Map.of(), false);
        assertTrue(live.targets(27));
        assertFalse(live.targets(28));
        assertFalse(live.targets(0));
    }

    @Test void allActionTokensAndMenuTargetedPacketsRoundTrip() {
        for (OptionType type : OptionType.values()) {
            var apply = new ApplyEnchantmentPayload(42, type, id("action"), 255);
            roundTrip(ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_CODEC, apply);
            assertTrue(apply.targets(42));
            assertFalse(apply.targets(43));
        }
        var reroll = new RerollEpiphanyPayload(42);
        var freeze = new FreezeEpiphanyPayload(42);
        roundTrip(RerollEpiphanyPayload.CODEC, reroll);
        roundTrip(FreezeEpiphanyPayload.CODEC, freeze);
        assertTrue(reroll.targets(42) && !reroll.targets(7));
        assertTrue(freeze.targets(42) && !freeze.targets(7));
    }

    @Test void costTableConfigurationAndRowsRoundTrip() {
        // No ItemStack fields in this fixture: validates every config field/order headlessly.
        var display = new TransformationDisplay(id("custom_rule"), id("tag"), true, id("custom_output"), 7, 29, 2, true);
        roundTrip(EnchantCostTablePayload.CODEC, table(List.of(display)));
        roundTrip(EnchantCostTablePayload.CODEC, table(List.of()));
    }

    private static EnchantCostTablePayload table(List<TransformationDisplay> displays) {
        var gates = new BookshelfProgression.Config(false, 47, 1.37,
            BookshelfProgression.BlankMaxTierMode.FULL_CURVE, 4, 3,
            Map.of(id("override"), List.of(0, 3, 11, 23, 47)), 2, 19, 0.41, 9, 7, 61,
            BookshelfProgression.VanillaTableMode.BOOKS, true, 6, false);
        // Distinct values catch field swaps hidden by equal default config values (all 50 fields).
        var resonance = new LibraryResonance.Config(13, 39, 21, 8, 29, 12, 1.13, 41,
            1.21, 1.31, 0.73, 0.83, 0.43, 0.53, 0.63, 1.03, 2.13, 3.23,
            0.67, 81.0, 0.17, 0.57, 111.0, 0.27, 0.87,
            chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.ConsumptionMode.WEAR, 6, 3,
            chiseled_enchanting_table.mechanics.resonance.MasterTomeWear.DepleteBehavior.STEP_DOWN,
            true, 5400, 34, 2400, 44, 900,
            0.09, 0.27, true, List.of(50, 30, 12, 8),
            chiseled_enchanting_table.mechanics.resonance.TomeWearData.ScapegoatPricing.FLAT,
            false, false, 7, 3, 17, 9,
            4.5, 3, 5, 0.07);
        var slipCfg = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.CrosslineSlipConfig(
            3.2, 0.037, 0.17,
            31, 0.011, 37, 0.021, 43, 0.031,
            1.1, 3.1, 2, 1.05, 5, 0.55, 11, 0.45, 0.62, 0.52, 3, 1.15,
            0.68, 0.26, 0.06, 0.52
        );
        var waitCurve = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.WaitCurve(
            0.31, 0.69, 0.29, 0.71, 1.27, 0.88, 137);
        var ritual = new chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics.Config(
            270, 12, 14, 0.48, 0.68, 0.17, 51.0, 27.0, slipCfg, waitCurve, 6, 225.0, 195.0);
        return new EnchantCostTablePayload(true, false, true, false, 0.5, 0.25,
            33, 0.13, 0.02, 0.03, 0.9, 4, 12, 1234, 500, 1200, 7, List.of(),
            11, List.of(), Set.of(id("treasure")), gates, resonance, ritual,
            List.of(new EnchantCostTablePayload.CostEntry(OptionType.PURGE_CURSE, id("curse"), 3, 42, List.of())), displays, true);
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

    @Test void applyPayloadRejectsOutOfRangeFieldsAtConstruction() {
        // Audit NET-03: bounds live in the compact constructor (decode boundary).
        assertThrows(IllegalArgumentException.class, () -> new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload(-2, OptionType.ENCHANTMENT, id("sharpness"), 1));
        assertThrows(IllegalArgumentException.class, () -> new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload(0, OptionType.ENCHANTMENT, id("sharpness"), -1));
        assertThrows(IllegalArgumentException.class, () -> new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload(0, OptionType.ENCHANTMENT, id("sharpness"), 256));
        assertThrows(IllegalArgumentException.class, () -> new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload(0, null, id("sharpness"), 1));
        // The DISENCHANT_ALL sentinel (containerless, level 0) stays constructible.
        var sentinel = new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload(-1, OptionType.DISENCHANT_ALL, id("disenchant_all"), 0);
        assertEquals(-1, sentinel.containerId());
        assertEquals(0, sentinel.enchantment_level());
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
        // Audit NET-02: every business channel carries the revision suffix; the bare
        // pre-suffix apply id survives only as a query-only probe.
        String revision = chiseled_enchanting_table.menu.EnchantingProtocol.REVISION;
        assertTrue(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload.APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID.id().getPath().endsWith(revision));
        assertTrue(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.RerollEpiphanyPayload.TYPE.id().getPath().endsWith(revision));
        assertTrue(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.FreezeEpiphanyPayload.TYPE.id().getPath().endsWith(revision));
        assertTrue(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ReopenTablePayload.TYPE.id().getPath().endsWith(revision));
        assertTrue(chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SyncTransformationStatePayload.TYPE.id().getPath().endsWith(revision));
        assertEquals("apply_chiseled_enchantment",
            chiseled_enchanting_table.menu.EnchantingProtocol.LEGACY_APPLY_TYPE.id().getPath());
        assertTrue(chiseled_enchanting_table.menu.EnchantingProtocol.LEGACY_APPLY_PROBES.size() >= 7);
        assertTrue(chiseled_enchanting_table.menu.EnchantingProtocol.stackSizeIncompatible(16, 64));
        assertFalse(chiseled_enchanting_table.menu.EnchantingProtocol.stackSizeIncompatible(64, 64));
        roundTrip(chiseled_enchanting_table.menu.EnchantingProtocol.Capability.CODEC,
            new chiseled_enchanting_table.menu.EnchantingProtocol.Capability(32));
        var display = new TransformationDisplay(id("delta_rule"), id("input_tag"), true, id("out"), 2, 15, 2, false);
        var txPayload = new SyncTransformationStatePayload(42, List.of(display));
        roundTrip(SyncTransformationStatePayload.CODEC, txPayload);
        assertTrue(txPayload.targets(42) && !txPayload.targets(43));
    }

    @Test void runtimeConfigRequestAndAcknowledgementRoundTrip() {
        var config = chiseled_enchanting_table.config.ModRuntimeConfig.DEFAULT.toJsonString();
        for (boolean closeContainer : new boolean[] {false, true}) {
            roundTrip(chiseled_enchanting_table.config.ModConfigNetworking.UpdateRuntimeConfigPayload.CODEC,
                new chiseled_enchanting_table.config.ModConfigNetworking.UpdateRuntimeConfigPayload(config, 8123456789L, closeContainer));
        }
        for (boolean accepted : new boolean[] {false, true}) {
            roundTrip(chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload.CODEC,
                new chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload(config, true, 8123456789L, accepted));
        }
    }

}
