package chiseled_enchanting_table;

import chiseled_enchanting_table.gui.TomeDirectoryLayout;
import chiseled_enchanting_table.gui.TomeDirectoryLayout.Rect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TomeDirectoryLayoutTest {
    private void inside(Rect child,Rect parent) {
        assertTrue(child.x()>=parent.x() && child.y()>=parent.y() && child.right()<=parent.right() && child.bottom()<=parent.bottom(),child+" escapes "+parent);
    }
    private boolean overlaps(Rect a,Rect b) {
        return a.x()<b.right() && a.right()>b.x() && a.y()<b.bottom() && a.bottom()>b.y();
    }
    @Test void allControlsAreInsideThePanelInnerFrame() {
        for(int i=0;i<9;i++) inside(TomeDirectoryLayout.button(i),TomeDirectoryLayout.safeBounds());
        inside(TomeDirectoryLayout.selection(),TomeDirectoryLayout.safeBounds());
    }
    @Test void controlsAndReadOnlyCounterNeverOverlap() {
        for(int i=0;i<9;i++) {
            assertFalse(overlaps(TomeDirectoryLayout.button(i),TomeDirectoryLayout.selection()));
            for(int j=i+1;j<9;j++)assertFalse(overlaps(TomeDirectoryLayout.button(i),TomeDirectoryLayout.button(j)));
        }
    }
    @Test void iconInkHasSevenPixelsWithoutCoveringItsBorder() {
        for(int i=5;i<9;i++) {
            var ink=TomeDirectoryLayout.button(i).inset(TomeDirectoryLayout.BUTTON_INSET);
            assertTrue(ink.width()>=7 && ink.height()>=7);
        }
    }
    @Test void textInkIsInsetFromEveryButtonEdge() {
        for(int i=0;i<5;i++) {
            var b=TomeDirectoryLayout.button(i); var ink=b.inset(TomeDirectoryLayout.BUTTON_INSET);
            assertTrue(ink.x()>b.x() && ink.y()>b.y() && ink.right()<b.right() && ink.bottom()<b.bottom());
            assertTrue(ink.width()>0 && ink.height()>=5);
        }
    }
    @Test void listStartsBelowPanelAndKeepsOriginalBottom() {
        assertTrue(TomeDirectoryLayout.listOffset()>TomeDirectoryLayout.HEIGHT);
        int height=141-TomeDirectoryLayout.listOffset();
        assertTrue(height>=75); assertEquals(158,17+TomeDirectoryLayout.listOffset()+height);
    }
    @Test void popupRowsStayInsideTheirOwnInnerBorder() {
        for(int count=1;count<=TomeDirectoryLayout.POPUP_LIMIT;count++)
            for(int row=0;row<count;row++) inside(TomeDirectoryLayout.popupRow(row),TomeDirectoryLayout.popup(count).inset(4));
    }
    @Test void longestPopupCannotCoverTheListBottomFrame() {
        assertTrue(TomeDirectoryLayout.popup(1000).bottom()<=141-TomeDirectoryLayout.FRAME);
        assertTrue(TomeDirectoryLayout.popup(1000).y()>TomeDirectoryLayout.HEIGHT);
    }
    @Test void decorativeOuterBorderIsNotAButton() {
        for(int i=0;i<9;i++) {
            assertFalse(TomeDirectoryLayout.button(i).contains(0,0));
            assertFalse(TomeDirectoryLayout.button(i).contains(TomeDirectoryLayout.WIDTH-1,TomeDirectoryLayout.HEIGHT-1));
        }
    }
    @Test void lockIconsFitInsideExistingPanelAndBelowRateText() {
        var safe=new Rect(214+9,18+9,101,43);
        for(int i=0;i<TomeDirectoryLayout.TOME_BUTTON_COUNT;i++) {
            var b=TomeDirectoryLayout.lockButton(i); inside(b,safe);
            assertTrue(b.y()>=18+24); assertTrue(b.inset(3).height()>=7);
            for(int j=i+1;j<TomeDirectoryLayout.TOME_BUTTON_COUNT;j++)
                assertFalse(overlaps(b,TomeDirectoryLayout.lockButton(j)));
        }
    }
    @Test void ritualConsoleButtonsFitInsidePanelAndNeverOverlap() {
        var safe=new Rect(214+9,18+9,101,43);
        for(int i=0;i<TomeDirectoryLayout.RITUAL_BUTTON_COUNT;i++) {
            var b=TomeDirectoryLayout.ritualButton(i); inside(b,safe);
            assertEquals(18,b.width()); assertEquals(18,b.height());
            assertTrue(b.y()>=18+34);
            for(int j=i+1;j<TomeDirectoryLayout.RITUAL_BUTTON_COUNT;j++)
                assertFalse(overlaps(b,TomeDirectoryLayout.ritualButton(j)));
        }
    }
    @Test void inscribeFilterBarStaysCompactAndLeavesFullListRoom() {
        var bar=new Rect(0,0,TomeDirectoryLayout.WIDTH,TomeDirectoryLayout.INSCRIBE_BAR_HEIGHT);
        var search=TomeDirectoryLayout.inscribeSearchBox();
        var afford=TomeDirectoryLayout.inscribeAffordableButton();
        var expand=TomeDirectoryLayout.inscribeExpandButton();
        inside(search,bar); inside(afford,bar); inside(expand,bar);
        assertFalse(overlaps(search,afford)); assertFalse(overlaps(afford,expand)); assertFalse(overlaps(search,expand));
        assertTrue(TomeDirectoryLayout.inscribeListOffset()>TomeDirectoryLayout.INSCRIBE_BAR_HEIGHT);
        assertTrue(TomeDirectoryLayout.CONTENT_HEIGHT-TomeDirectoryLayout.inscribeListOffset()>=120);
    }
    @Test void unknownButtonsFailInsteadOfTargetingClearOrBatchAction() {
        assertThrows(IllegalArgumentException.class,()->TomeDirectoryLayout.button(9));
        assertThrows(IllegalArgumentException.class,()->TomeDirectoryLayout.lockButton(-1));
        assertThrows(IllegalArgumentException.class,()->TomeDirectoryLayout.lockButton(TomeDirectoryLayout.TOME_BUTTON_COUNT));
        assertThrows(IllegalArgumentException.class,()->TomeDirectoryLayout.ritualButton(-1));
        assertThrows(IllegalArgumentException.class,()->TomeDirectoryLayout.ritualButton(TomeDirectoryLayout.RITUAL_BUTTON_COUNT));
    }
}
