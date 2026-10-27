package chiseled_enchanting_table;
import chiseled_enchanting_table.catalog.TomeCatalog;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TomeCatalogTest {
    private TomeCatalog model() {
        var m=new TomeCatalog();
        m.update(List.of(row(1, List.of("a","b"),3,4,false,false),row(2,List.of("b"),0,8,true,true),row(3,List.of("c"),2,4,true,false)));
        return m;
    }
    private TomeCatalog.Row row(int id,List<String> es,int left,int max,boolean lock,boolean mine) {
        return new TomeCatalog.Row(new TomeCatalog.Key(id,0),es,"Book"+id,left,max,lock,mine);
    }
    @Test void secondaryEnchantmentsAreFilterable() { var m=model();m.enchantment="b";m.refresh();assertEquals(2,m.visible().size()); }
    @Test void filtersIntersect() { var m=model();m.enchantment="b";m.remaining=0;m.total=8;m.lock=TomeCatalog.Lock.MINE;m.refresh();assertEquals(2,m.visible().getFirst().key().shelf()); }
    @Test void ownershipFilterExcludesUnlocked() { var m=model();m.lock=TomeCatalog.Lock.OTHER;m.refresh();assertEquals(3,m.visible().getFirst().key().shelf()); }
    @Test void numericSortAndReverse() { var m=model();m.sort=TomeCatalog.Sort.REMAINING;m.refresh();assertEquals(List.of(0,2,3),m.visible().stream().map(TomeCatalog.Row::remaining).toList());m.descending=true;m.refresh();assertEquals(List.of(3,2,0),m.visible().stream().map(TomeCatalog.Row::remaining).toList()); }
    @Test void shiftSelectionTogglesWithoutLosingOtherSelections() { var m=model();var a=m.visible().get(0).key();var b=m.visible().get(1).key();m.select(a,false);m.select(b,true);assertEquals(2,m.selected().size());m.select(a,true);assertEquals(Set.of(b),m.selected()); }
    @Test void normalClickReplacesSelection() { var m=model();m.selectVisible();m.select(m.visible().getFirst().key(),false);assertEquals(1,m.selected().size()); }
    @Test void reorderPreservesIdentity() { var m=model();var key=m.visible().getFirst().key();m.select(key,false);m.descending=true;m.refresh();assertEquals(Set.of(key),m.selected()); }
    @Test void hiddenSelectionCannotBeActedUpon() { var m=model();m.selectVisible();m.enchantment="c";m.refresh();assertEquals(Set.of(new TomeCatalog.Key(3,0)),m.selected()); }
    @Test void removedBookCannotRemainSelected() { var m=model();m.selectVisible();m.update(List.of());assertTrue(m.selected().isEmpty()); }
    @Test void clearReallyClearsAndResetRestoresView() { var m=model();m.selectVisible();m.clearSelection();assertTrue(m.selected().isEmpty());m.enchantment="missing";m.refresh();assertTrue(m.visible().isEmpty());m.reset();assertEquals(3,m.visible().size()); }
}
