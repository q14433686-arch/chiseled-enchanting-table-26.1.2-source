package chiseled_enchanting_table;

import chiseled_enchanting_table.config.rules.RuleBook;
import chiseled_enchanting_table.mechanics.resonance.RegenerationScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleArchitectureTest {
    private RuleBook book() {
        return new RuleBook(Map.of(
            "wear", new RuleBook.Definition("wear", 16, false),
            "anvil", new RuleBook.Definition("anvil", true, false),
            "stack", new RuleBook.Definition("stack", 64, true)), Map.of("stack", 32));
    }

    @Test void effectiveValueAndOriginComeFromTheSameSnapshot() {
        RuleBook book = book();
        var prepared = book.prepare(Map.of("wear", 16), Map.of("wear", 8), false, RuleBook.Policy.NONE);
        assertEquals(16, book.snapshot().rule("wear").effective());
        var snapshot = book.commit(prepared, () -> {});
        assertEquals(8, snapshot.rule("wear").effective());
        assertEquals(RuleBook.Origin.DATAPACK, snapshot.rule("wear").origin());
        assertFalse(snapshot.rule("wear").editable());
        assertEquals(32, snapshot.rule("stack").effective());
        assertEquals(RuleBook.Origin.STARTUP, snapshot.rule("stack").origin());
    }

    @Test void fullLockResolvesEveryDefinedRuntimeKeyNotJustDatapackKeys() {
        RuleBook book = book();
        var snapshot = book.prepare(Map.of("anvil", false, "wear", 128), Map.of("wear", 8), true,
            new RuleBook.Policy(false, true, Set.of())).snapshot();
        assertEquals(true, snapshot.rule("anvil").effective());
        assertEquals(8, snapshot.rule("wear").effective());
        assertEquals(RuleBook.Origin.LOCKED_DEFAULT, snapshot.rule("anvil").origin());
        assertFalse(snapshot.rule("anvil").editable());
        assertEquals(32, snapshot.rule("stack").effective());
    }

    @Test void configurationPriorityCannotDefeatAnEnforcedDatapack() {
        RuleBook book = book();
        assertEquals(16, book.prepare(Map.of("wear", 16), Map.of("wear", 8), true,
            RuleBook.Policy.NONE).snapshot().rule("wear").effective());
        assertEquals(8, book.prepare(Map.of("wear", 16), Map.of("wear", 8), true,
            new RuleBook.Policy(true, false, Set.of())).snapshot().rule("wear").effective());
    }

    @Test void failedPersistenceDoesNotPublishAndStaleProposalsDoNotWrite() {
        RuleBook book = book();
        var before = book.snapshot();
        var proposal = book.prepare(Map.of("wear", 8), Map.of(), false, RuleBook.Policy.NONE);
        assertThrows(IllegalStateException.class, () -> book.commit(proposal, () -> {
            assertSame(before, book.snapshot());
            throw new IllegalStateException("disk failure");
        }));
        assertSame(before, book.snapshot());
        book.commit(proposal, () -> {});
        AtomicBoolean wrote = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> book.commit(proposal, () -> wrote.set(true)));
        assertFalse(wrote.get());
    }

    @Test void foreignAndReentrantTransactionsCannotWrite() {
        RuleBook a = book(); RuleBook b = book();
        var proposal = a.prepare(Map.of(), Map.of(), false, RuleBook.Policy.NONE);
        AtomicBoolean wrote = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> b.commit(proposal, () -> wrote.set(true)));
        assertFalse(wrote.get());
        var original = a.snapshot();
        assertThrows(IllegalStateException.class, () -> a.commit(proposal,
            () -> a.commit(proposal, () -> wrote.set(true))));
        assertSame(original, a.snapshot());
        assertFalse(wrote.get());
    }

    @Test void invalidInputsAndBootstrapOverridesAreRejected() {
        RuleBook book = book();
        assertThrows(IllegalArgumentException.class, () -> book.prepare(Map.of("unknown", 1), Map.of(), false, RuleBook.Policy.NONE));
        assertThrows(IllegalArgumentException.class, () -> book.prepare(Map.of("wear", "8"), Map.of(), false, RuleBook.Policy.NONE));
        assertThrows(IllegalArgumentException.class, () -> book.prepare(Map.of(), Map.of("stack", 1), false, RuleBook.Policy.NONE));
        assertThrows(IllegalArgumentException.class, () -> book.prepare(Map.of(), Map.of(), false,
            new RuleBook.Policy(false, false, Set.of("stack"))));
        assertThrows(IllegalArgumentException.class, () -> new RuleBook.Definition("bad", new ArrayList<>(), false));
        assertThrows(IllegalArgumentException.class, () -> new RuleBook.Definition("bad", Double.NaN, false));
        assertThrows(UnsupportedOperationException.class, () -> book.snapshot().values().clear());
    }

    private RegenerationScheduler.Observation<String> observation(Object book, int wear, int interval) {
        return new RegenerationScheduler.Observation<>("world/shelf/slot", book, wear, interval);
    }

    @Test void overlapUsesFastestRateIndependentOfProposalOrder() {
        Object book = new Object();
        for (boolean reverse : List.of(false, true)) {
            var scheduler = new RegenerationScheduler<String>();
            var fast = observation(book, 16, 40); var slow = observation(book, 16, 200);
            var observations = reverse ? List.of(slow, fast) : List.of(fast, slow);
            assertTrue(scheduler.settle(0, observations).isEmpty());
            assertTrue(scheduler.settle(1, observations).isEmpty());
            var result = scheduler.settle(2, observations).get("world/shelf/slot");
            assertEquals(1, result.points());
            assertEquals(15, result.remainingWear());
            assertSame(book, result.expectedIdentity());
        }
    }

    @Test void missingWindowCannotCreditBackpackOrUnloadedTime() {
        var scheduler = new RegenerationScheduler<String>(); Object book = new Object();
        scheduler.settle(1000, List.of(observation(book, 16, 200)));
        assertTrue(scheduler.settle(1100, List.of(observation(book, 16, 200))).isEmpty());
        assertTrue(scheduler.settle(1101, List.of()).isEmpty());
        assertEquals(0, scheduler.trackedSlots());
        assertTrue(scheduler.settle(1102, List.of(observation(book, 16, 200))).isEmpty());
    }

    @Test void replacementAndConflictingIdentityDoNotInheritProgress() {
        var scheduler = new RegenerationScheduler<String>(); Object a = new Object(); Object b = new Object();
        scheduler.settle(0, List.of(observation(a, 16, 40)));
        scheduler.settle(1, List.of(observation(a, 16, 40)));
        assertTrue(scheduler.settle(2, List.of(observation(b, 16, 40))).isEmpty());
        assertTrue(scheduler.settle(3, List.of(observation(a, 16, 40), observation(b, 16, 40))).isEmpty());
        assertEquals(0, scheduler.trackedSlots());
    }

    @Test void rateChangesDoNotRetroactivelyRepriceOldTime() {
        var scheduler = new RegenerationScheduler<String>(); Object book = new Object();
        scheduler.settle(0, List.of(observation(book, 16, 200)));
        scheduler.settle(1, List.of(observation(book, 16, 200)));
        assertTrue(scheduler.settle(2, List.of(observation(book, 16, 20))).isEmpty());
        assertEquals(1, scheduler.settle(3, List.of(observation(book, 16, 20))).get("world/shelf/slot").points());
    }

    @Test void acknowledgedReplacementCarriesOnlyItsOwnResidencyForward() {
        var scheduler = new RegenerationScheduler<String>(); Object before = new Object(); Object after = new Object();
        scheduler.settle(0, List.of(observation(before, 16, 20)));
        scheduler.settle(1, List.of(observation(before, 16, 20)));
        scheduler.acknowledgeReplacement("world/shelf/slot", before, after, 15);
        assertEquals(1, scheduler.settle(2, List.of(observation(after, 15, 20))).get("world/shelf/slot").points());
        assertThrows(IllegalStateException.class,
            () -> scheduler.acknowledgeReplacement("world/shelf/slot", before, after, 14));
    }

    @Test void fullyRestoredBooksAreEvictedWithoutOngoingDirtyWrites() {
        var scheduler = new RegenerationScheduler<String>(); Object book = new Object();
        scheduler.settle(0, List.of(observation(book, 1, 20)));
        assertEquals(0, scheduler.settle(1, List.of(observation(book, 1, 20))).get("world/shelf/slot").remainingWear());
        assertEquals(0, scheduler.trackedSlots());
    }

    @Test void sessionResetAndDuplicateSettlementAreSafe() {
        var scheduler = new RegenerationScheduler<String>(); Object book = new Object();
        scheduler.settle(500, List.of(observation(book, 16, 20)));
        assertThrows(IllegalArgumentException.class, () -> scheduler.settle(500, List.of()));
        scheduler.clear();
        assertTrue(scheduler.settle(0, List.of(observation(book, 16, 20))).isEmpty());
    }
}
