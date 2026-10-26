package chiseled_enchanting_table.gui;

import java.util.LinkedHashSet;
import java.util.List;

/** Deterministic tooltip budgets; independent of rendering, locale and native key state. */
public final class TooltipBudget {
    private TooltipBudget() {}
    public static int width(int viewportWidth) { return Math.max(48, Math.min(280, viewportWidth - 24)); }
    public static int rows(int viewportHeight, boolean expanded) {
        return Math.max(2, Math.min(expanded ? 22 : 8, (viewportHeight - 32) / 12));
    }
    public static List<Integer> summaryIndices(int count, List<Integer> critical, int limit) {
        if (count <= 0 || limit <= 0) return List.of();
        var selected = new LinkedHashSet<Integer>();
        selected.add(0);
        for (int index : critical) {
            if (selected.size() >= limit) break;
            if (index > 0 && index < count) selected.add(index);
        }
        for (int i = 1; i < count && selected.size() < limit; i++) selected.add(i);
        return List.copyOf(selected);
    }
}
