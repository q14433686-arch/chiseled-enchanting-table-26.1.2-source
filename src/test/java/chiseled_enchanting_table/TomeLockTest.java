package chiseled_enchanting_table;

import chiseled_enchanting_table.mechanics.resonance.TomeLock;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeLockPayload;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeRef;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.UnlockCleansePayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure rules of the «纂刻的书» tome locks: expiry semantics, countdown math, wire. */
class TomeLockTest {

    @Test void timedLocksExpireExactlyOnTheirChargeTarget() {
        // +1 lock: any wear decrease (or reaching full) ends it.
        assertTrue(TomeLock.shouldExpire(TomeLock.Mode.UNTIL_NEXT_CHARGE, 4, 3));
        assertTrue(TomeLock.shouldExpire(TomeLock.Mode.UNTIL_NEXT_CHARGE, 1, 0));
        assertFalse(TomeLock.shouldExpire(TomeLock.Mode.UNTIL_NEXT_CHARGE, 4, 4));
        // Full lock: only a complete refill ends it.
        assertFalse(TomeLock.shouldExpire(TomeLock.Mode.UNTIL_FULL, 4, 3));
        assertTrue(TomeLock.shouldExpire(TomeLock.Mode.UNTIL_FULL, 1, 0));
        // Permanent locks never expire through regeneration.
        assertFalse(TomeLock.shouldExpire(TomeLock.Mode.PERMANENT, 4, 0));
        assertFalse(TomeLock.shouldExpire(TomeLock.Mode.NONE, 4, 0));
        assertFalse(TomeLock.shouldExpire(null, 4, 0));
    }

    @Test void timedLockOnFullBookReadsAsExpired() {
        assertTrue(TomeLock.isEffectivelyLocked(TomeLock.Mode.UNTIL_NEXT_CHARGE, 3));
        assertFalse(TomeLock.isEffectivelyLocked(TomeLock.Mode.UNTIL_NEXT_CHARGE, 0));
        assertFalse(TomeLock.isEffectivelyLocked(TomeLock.Mode.UNTIL_FULL, 0));
        assertTrue(TomeLock.isEffectivelyLocked(TomeLock.Mode.PERMANENT, 0));
        assertFalse(TomeLock.isEffectivelyLocked(TomeLock.Mode.NONE, 5));
    }

    @Test void countdownMathMatchesIntervals() {
        assertEquals(600L, TomeLock.ticksToNextCharge(3600, 3000L));
        assertEquals(3600L, TomeLock.ticksToNextCharge(3600, 0L));
        assertEquals(0L, TomeLock.ticksToNextCharge(3600, 9999L));
        assertEquals(Long.MAX_VALUE, TomeLock.ticksToNextCharge(Integer.MAX_VALUE, 0L));
        assertEquals(Long.MAX_VALUE, TomeLock.ticksToNextCharge(0, 0L));
        // 3 worn charges: 600 to the next, then two full intervals.
        assertEquals(600L + 2L * 3600L, TomeLock.ticksToFullCharge(3, 3600, 3000L));
        assertEquals(0L, TomeLock.ticksToFullCharge(0, 3600, 0L));
        assertEquals(Long.MAX_VALUE, TomeLock.ticksToFullCharge(2, Integer.MAX_VALUE, 0L));
    }

    @Test void modeIdsRoundTripAndClamp() {
        for (TomeLock.Mode mode : TomeLock.Mode.values()) {
            assertEquals(mode, TomeLock.Mode.fromId(mode.id()));
        }
        assertEquals(TomeLock.Mode.NONE, TomeLock.Mode.fromId(-5));
        assertEquals(TomeLock.Mode.NONE, TomeLock.Mode.fromId(99));
    }

    @Test void lockPayloadsRoundTrip() {
        var payload = new TomeLockPayload(42, 2, List.of(
            new TomeRef(new BlockPos(1, 2, 3), 0),
            new TomeRef(new BlockPos(-10, 64, 99), 5)));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        try {
            TomeLockPayload.CODEC.encode(buf, payload);
            assertEquals(payload, TomeLockPayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
            UnlockCleansePayload.CODEC.encode(buf, new UnlockCleansePayload());
            assertEquals(new UnlockCleansePayload(), UnlockCleansePayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
        // Out-of-range mode/slot are clamped at construction, never trusted raw.
        var clamped = new TomeLockPayload(1, 99, List.of(new TomeRef(BlockPos.ZERO, 42)));
        assertEquals(3, clamped.mode());
        assertEquals(5, clamped.refs().get(0).slot());
    }
}
