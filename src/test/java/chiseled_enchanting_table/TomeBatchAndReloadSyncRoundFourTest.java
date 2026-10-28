package chiseled_enchanting_table;

import static org.junit.jupiter.api.Assertions.*;

import chiseled_enchanting_table.catalog.TomeActionQueue;
import chiseled_enchanting_table.catalog.TomeCatalog;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.SyncRitualStatePayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeSlotRef;
import chiseled_enchanting_table.menu.EnchantingProtocol;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import chiseled_enchanting_table.menu.TableVisualEventPayload;
import chiseled_enchanting_table.menu.TableVisualEventPayload.Kind;
import chiseled_enchanting_table.menu.TableVisualEvents;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class TomeBatchAndReloadSyncRoundFourTest {

    @Test
    void tomePageActionPayloadRoundTripsExtraLocateTargetsAndEnforcesBounds() {
        assertEquals(8, EnchantingProtocol.CURRENT_REVISION_NUMBER);
        BlockPos primary = new BlockPos(10, 64, -20);
        List<TomeSlotRef> extras = List.of(
            new TomeSlotRef(new BlockPos(11, 65, -20), 2),
            new TomeSlotRef(new BlockPos(9, 64, -22), 5)
        );
        TomePageActionPayload original = new TomePageActionPayload(
            7, Action.LOCATE_PING, primary, 0, extras
        );
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            TomePageActionPayload.CODEC.encode(buf, original);
            TomePageActionPayload decoded = TomePageActionPayload.CODEC.decode(buf);
            assertEquals(7, decoded.containerId());
            assertEquals(Action.LOCATE_PING, decoded.action());
            assertEquals(primary, decoded.shelf_pos());
            assertEquals(0, decoded.slot());
            assertEquals(extras, decoded.extra_targets());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new TomeSlotRef(primary, -1));
        assertThrows(IllegalArgumentException.class, () -> new TomeSlotRef(primary, 6));

        List<TomeSlotRef> tooMany = new ArrayList<>();
        for (int i = 0; i <= TomeSlotRef.MAX_BATCH_SLOTS; i++) {
            tooMany.add(new TomeSlotRef(new BlockPos(i, 64, 0), i % 6));
        }
        assertThrows(
            IllegalArgumentException.class,
            () -> new TomePageActionPayload(1, Action.LOCATE_PING, primary, 0, tooMany)
        );
    }

    @Test
    void syncRitualStatePayloadRoundTripsActiveTomeSlots() {
        List<EnchantmentWithLevel> steps = List.of(
            new EnchantmentWithLevel(Identifier.fromNamespaceAndPath("minecraft", "sharpness"), 5),
            new EnchantmentWithLevel(Identifier.fromNamespaceAndPath("minecraft", "mending"), 1)
        );
        List<TomeSlotRef> activeSlots = List.of(
            new TomeSlotRef(new BlockPos(4, 70, -8), 1),
            new TomeSlotRef(new BlockPos(5, 70, -8), 4)
        );
        SyncRitualStatePayload original = new SyncRitualStatePayload(
            12, 99887766L, steps, true, true, false, activeSlots
        );
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            SyncRitualStatePayload.CODEC.encode(buf, original);
            SyncRitualStatePayload decoded = SyncRitualStatePayload.CODEC.decode(buf);
            assertEquals(12, decoded.containerId());
            assertEquals(99887766L, decoded.session());
            assertEquals(steps, decoded.queuedSteps());
            assertTrue(decoded.usedInfinityTome());
            assertTrue(decoded.hasAnyMasterTome());
            assertFalse(decoded.ritualBlocked());
            assertEquals(activeSlots, decoded.activeTomeSlots());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void tomeLocateVisualEventKindRoundTrips() {
        BlockPos table = new BlockPos(0, 64, 0);
        BlockPos shelf = new BlockPos(2, 65, -1);
        TableVisualEventPayload payload = new TableVisualEventPayload(
            Kind.TOME_LOCATE, table, shelf, 3, 2, TableVisualEvents.COLOR_LOCATE_CYAN
        );
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            TableVisualEventPayload.CODEC.encode(buf, payload);
            TableVisualEventPayload decoded = TableVisualEventPayload.CODEC.decode(buf);
            assertEquals(Kind.TOME_LOCATE, decoded.kind());
            assertEquals(shelf, decoded.targetPos());
            assertEquals(3, decoded.slot());
            assertEquals(TableVisualEvents.COLOR_LOCATE_CYAN, decoded.argbColor());
        } finally {
            buf.release();
        }
    }

    @Test
    void ctrlClickWithImplicitFirstRowSelectsFirstAndClickedRow() {
        TomeCatalog catalog = new TomeCatalog();
        var k0 = new TomeCatalog.Key(100L, 0);
        var k1 = new TomeCatalog.Key(100L, 1);
        var k2 = new TomeCatalog.Key(100L, 2);
        catalog.update(List.of(
            new TomeCatalog.Row(k0, List.of("minecraft:sharpness"), "Sharpness V", 4, 4, false, false),
            new TomeCatalog.Row(k1, List.of("minecraft:unbreaking"), "Unbreaking III", 3, 4, false, false),
            new TomeCatalog.Row(k2, List.of("minecraft:mending"), "Mending", 4, 4, false, false)
        ));
        assertTrue(catalog.selected().isEmpty());
        catalog.selectInteractive(k2, false, true);
        assertEquals(Set.of(k0, k2), catalog.selected());

        TomeActionQueue q = new TomeActionQueue();
        TomeBookEntry b0 = new TomeBookEntry(new BlockPos(1, 64, 1), 0, "", List.of(), 1, 3, 4, 0, false, false, 0, 0, -1, "", "", 0, false, "", 0, 0, "", 0);
        TomeBookEntry b2 = new TomeBookEntry(new BlockPos(1, 64, 1), 2, "", List.of(), 0, 4, 4, 0, true, false, 0, 0, -1, "", "", 0, false, "", 0, 0, "", 0);
        assertTrue(TomeActionQueue.isEligibleFor(Action.MEND_ONE, b0));
        assertFalse(TomeActionQueue.isEligibleFor(Action.MEND_ONE, b2));
        assertTrue(TomeActionQueue.isEligibleFor(Action.LOCATE_PING, b0));
        assertTrue(TomeActionQueue.isEligibleFor(Action.LOCATE_PING, b2));
        assertTrue(q.submit(Action.LOCATE_PING, List.of(b0, b2)));
        assertEquals(List.of(b0, b2), q.drainPendingForImmediateBatch(Action.LOCATE_PING));
    }
}
