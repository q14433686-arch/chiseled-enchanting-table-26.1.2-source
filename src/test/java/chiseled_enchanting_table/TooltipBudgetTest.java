package chiseled_enchanting_table;

import chiseled_enchanting_table.gui.TooltipBudget;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TooltipBudgetTest {
    @Test void summaryKeepsTitleAndLateFailureBeforeOptionalMechanics() {
        assertEquals(List.of(0, 11, 1, 2, 3), TooltipBudget.summaryIndices(15, List.of(11), 5));
    }
    @Test void summaryNeverDuplicatesCriticalRowsOrAcceptsBadIndices() {
        assertEquals(List.of(0, 3, 1), TooltipBudget.summaryIndices(4, List.of(-1, 3, 3, 99), 3));
    }
    @Test void emptyAndZeroBudgetsAreSafe() {
        assertEquals(List.of(), TooltipBudget.summaryIndices(0, List.of(), 5));
        assertEquals(List.of(), TooltipBudget.summaryIndices(10, List.of(), 0));
    }
    @Test void shortTooltipIsNotPaddedOrExpanded() {
        assertEquals(List.of(0, 1), TooltipBudget.summaryIndices(2, List.of(), 5));
    }
    @Test void wrappingUsesViewportRatherThanUnboundedTextWidth() {
        assertEquals(280, TooltipBudget.width(1920));
        assertEquals(176, TooltipBudget.width(200));
    }
    @Test void compactAndExpandedHeightRemainBoundedOnSmallGuiScales() {
        assertEquals(8, TooltipBudget.rows(360, false));
        assertEquals(22, TooltipBudget.rows(360, true));
        assertEquals(5, TooltipBudget.rows(100, true));
    }
}
