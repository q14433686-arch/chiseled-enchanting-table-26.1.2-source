package chiseled_enchanting_table.gui;

import java.util.function.IntFunction;

/** Frame-scoped panel values. Geometry reads are explicit; lifecycle and input stay in Screen. */
record TablePanelContext(TableTooltipContext tooltips, int left, int top, String title,
    boolean showSanctum, boolean showShelfBadge, boolean inscribeTab, int nextShelf,
    IntFunction<int[]> tomeRect) {}
