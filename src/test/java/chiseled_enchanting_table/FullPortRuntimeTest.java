package chiseled_enchanting_table;

import chiseled_enchanting_table.config.ModRuntimeConfig;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.mechanics.ritual.InscriptionRitualMechanics;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.*;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FullPortRuntimeTest {
    @Test void effectiveDomainAndDirectConsumerAgreeAndFailedSaveDoesNotPublish() {
        var original = ChiseledEnchantingTableData.runtimeFrame();
        try {
            var draft = ModRuntimeConfig.DEFAULT.toDraft();
            draft.maxTomeUses = 128;
            draft.anvilFlatRepairCost = true; // non-default since fresh installs default it off
            var policy = new ModRuntimeConfig.PackPolicy(true, true, Set.of("max_tome_uses"), Set.of());
            ChiseledEnchantingTableData.applyRuntimeConfig(draft.build(), policy, () -> {});
            var frame = ChiseledEnchantingTableData.runtimeFrame();
            assertEquals(4, frame.configuration().maxTomeUses());
            assertEquals(frame.configuration().maxTomeUses(), ChiseledEnchantingTableData.libraryResonanceConfig().maxTomeUses());
            assertFalse(ModRuntimeConfig.effective().anvilFlatRepairCost()); // enforced datapack domain wins over draft
            assertEquals(4, frame.rules().rule("max_tome_uses").effective());
            assertThrows(IllegalStateException.class, () -> ChiseledEnchantingTableData.applyRuntimeConfig(
                draft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> { throw new IllegalStateException("disk error"); }));
            assertSame(frame, ChiseledEnchantingTableData.runtimeFrame());
        } finally {
            ChiseledEnchantingTableData.applyRuntimeConfig(original.configuration(), original.policy(), () -> {});
        }
    }

    @Test void ritualWirePreservesMenuSessionQueueAndBlockedState() {
        var step = new EnchantmentWithLevel(Identifier.withDefaultNamespace("sharpness"), 5);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            for (boolean blocked : new boolean[] {false, true}) {
                var sync = new SyncRitualStatePayload(27, 987654321L, List.of(step), true, false, blocked);
                SyncRitualStatePayload.CODEC.encode(buffer, sync);
                assertEquals(sync, SyncRitualStatePayload.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            }
            var skip = new SkipRitualPayload(27, 987654321L, true);
            SkipRitualPayload.CODEC.encode(buffer, skip);
            assertEquals(skip, SkipRitualPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void configurationWireDoesNotConfuseStoredPreferencesWithEffectiveValues() {
        var payload = new chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload(
            "preferences", "policy", "effective", true, 1234L, false);
        var buffer = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
        try {
            chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, chiseled_enchanting_table.config.ModConfigNetworking.SyncRuntimeConfigPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void liveShelfWirePreservesChargeSentinelsAndCounts() {
        var enchantment = new EnchantmentWithLevel(Identifier.withDefaultNamespace("mending"), 1);
        var tomeEntry = new chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.ShelfTomeEntry(
            new net.minecraft.core.BlockPos(3, 64, -7), 4,
            Identifier.withDefaultNamespace("mending"), 1, 2, 3, 16, 7200, 1234, 2, true, false);
        var payload = new LiveShelfStatePayload(23, Set.of(enchantment), 10, 12, 5, 4,
            Map.of(enchantment.enchantment_id(), 1), false, 2, 1, Map.of(enchantment, -1),
            java.util.List.of(tomeEntry), false, true, 7);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            LiveShelfStatePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, LiveShelfStatePayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void malformedPolicyNeverBecomesAnUnlockedDefault() {
        assertThrows(IllegalArgumentException.class, () -> ModRuntimeConfig.PackPolicy.fromJsonString("{\"lock_in_game_config\":\"typo\"}"));
        assertThrows(IllegalArgumentException.class, () -> ModRuntimeConfig.PackPolicy.fromJsonString("{\"locked_keys\":[\"misspelled_rule\"]}"));
        assertThrows(IllegalArgumentException.class, () -> ModRuntimeConfig.PackPolicy.fromJsonString("{\"locked_keys\":false}"));
    }

    @Test void persistedQueueHasFiniteResourceBounds() {
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue("x".repeat(32001)));
        var step = new InscriptionRitualMechanics.QueuedInscription(Identifier.withDefaultNamespace("mending"),1,0,true,false,0,false);
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue("minecraft:mending|9999|0|1|0|0|0"));
        assertThrows(IllegalArgumentException.class, () -> InscriptionRitualMechanics.deserializeQueue("not a queue"));
        String token = step.encode();
        assertThrows(IllegalArgumentException.class,
            () -> InscriptionRitualMechanics.deserializeQueue(String.join(";", Collections.nCopies(257, token))));
    }
}
