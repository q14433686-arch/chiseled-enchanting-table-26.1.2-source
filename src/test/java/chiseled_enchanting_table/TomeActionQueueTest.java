package chiseled_enchanting_table;
import chiseled_enchanting_table.catalog.TomeActionQueue;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomeBookEntry;
import chiseled_enchanting_table.menu.ChiseledEnchantingMenuPayloads.TomePageActionPayload.Action;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TomeActionQueueTest {
    private TomeBookEntry book(int slot, String name, int mode) {
        return new TomeBookEntry(new BlockPos(1,64,1),slot,name,List.of(),1,3,4,0,false,false,20,20,mode,"owner","owner",0,true,"",0,0,"",0);
    }
    @Test void multipleBooksRequireSameSelectionAndActionWithinDeadline() {
        var clock=new AtomicLong();var q=new TomeActionQueue(clock::get);var books=List.of(book(0,"a",-1),book(1,"b",-1));
        assertFalse(q.submit(Action.LOCK_FULL,books));assertEquals("confirm",q.status().key());
        clock.set(6_000_000_000L);assertFalse(q.submit(Action.LOCK_FULL,books));
        assertFalse(q.submit(Action.LOCK_PERMANENT,books));assertTrue(q.submit(Action.LOCK_PERMANENT,books));
    }
    @Test void pacedSubmissionDoesNotMutateAuthoritativeEntries() {
        var q=new TomeActionQueue();var books=List.of(book(0,"a",-1),book(1,"b",-1));
        q.submit(Action.LOCK_FULL,books);q.submit(Action.LOCK_FULL,books);var sent=new ArrayList<TomeBookEntry>();
        q.tick(books,(action,book)->sent.add(book));q.tick(books,(action,book)->sent.add(book));assertEquals(1,sent.size());
        for(int i=0;i<3;i++)q.tick(books,(action,book)->sent.add(book));assertEquals(2,sent.size());assertFalse(books.getFirst().locked());
    }
    @Test void replacedBookInSameSlotIsSkipped() {
        var q=new TomeActionQueue();q.submit(Action.LOCK_PERMANENT,List.of(book(0,"old",-1)));
        q.tick(List.of(book(0,"replacement",-1)),(a,b)->fail("retargeted replaced book"));assertNull(q.status());
    }
    @Test void cancellationPreventsQueuedRequests() {
        var q=new TomeActionQueue();var b=book(0,"a",-1);q.submit(Action.LOCK_FULL,List.of(b));q.cancel();q.tick(List.of(b),(a,v)->fail("cancelled request sent"));assertNull(q.status());
    }
    @Test void unlockOnlyTargetsLockedEntriesAndRejectsReentrantBatch() {
        var q=new TomeActionQueue();var locked=book(0,"a",2);var free=book(1,"b",-1);
        assertTrue(q.submit(Action.UNLOCK,List.of(locked,free)));assertFalse(q.submit(Action.UNLOCK,List.of(locked)));
        q.tick(List.of(locked,free),(a,b)->{assertEquals(Action.UNLOCK,a);assertEquals(locked,b);});
    }
}
