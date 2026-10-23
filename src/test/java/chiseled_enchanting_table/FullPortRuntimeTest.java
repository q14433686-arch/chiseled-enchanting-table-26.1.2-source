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
            draft.anvilFlatRepairCost = false;
            var policy = new ModRuntimeConfig.PackPolicy(true, true, Set.of("max_tome_uses"), Set.of());
            ChiseledEnchantingTableData.applyRuntimeConfig(draft.build(), policy, () -> {});
            var frame = ChiseledEnchantingTableData.runtimeFrame();
            assertEquals(16, frame.configuration().maxTomeUses());
            assertEquals(frame.configuration().maxTomeUses(), ChiseledEnchantingTableData.libraryResonanceConfig().maxTomeUses());
            assertTrue(ModRuntimeConfig.effective().anvilFlatRepairCost());
            assertEquals(16, frame.rules().rule("max_tome_uses").effective());
            assertThrows(IllegalStateException.class, () -> ChiseledEnchantingTableData.applyRuntimeConfig(
                draft.build(), ModRuntimeConfig.PackPolicy.NONE, () -> { throw new IllegalStateException("disk error"); }));
            assertSame(frame, ChiseledEnchantingTableData.runtimeFrame());
        } finally {
            ChiseledEnchantingTableData.applyRuntimeConfig(original.configuration(), original.policy(), () -> {});
        }
    }

    @Test void ritualWirePreservesMenuSessionAndQueue() {
        var step = new EnchantmentWithLevel(Identifier.withDefaultNamespace("sharpness"), 5);
        var sync = new SyncRitualStatePayload(27, 987654321L, List.of(step), true, false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            SyncRitualStatePayload.CODEC.encode(buffer, sync);
            assertEquals(sync, SyncRitualStatePayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            var skip = new SkipRitualPayload(27, 987654321L, true);
            SkipRitualPayload.CODEC.encode(buffer, skip);
            assertEquals(skip, SkipRitualPayload.CODEC.decode(buffer));
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
