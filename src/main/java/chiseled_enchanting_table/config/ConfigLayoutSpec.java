package chiseled_enchanting_table.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic, testable geometry specification for the redesigned
 * L0-L3 Chiseled Enchanting Table configuration modal and Rule Trace view.
 *
 * <p>Every structural region, decorative safe-inset, plaque inner-border clearance,
 * typography scale floor, and sibling gap is computed here without OpenGL/Minecraft
 * screen state so automated tests can verify zero overlap, zero border-crossing,
 * and cross-locale text budgets across arbitrary viewport dimensions.
 */
public final class ConfigLayoutSpec {
    private ConfigLayoutSpec() {}

    /** Preferred modal bounds in logical GUI pixels (~520x340 target on standard viewports). */
    public static final int MODAL_TARGET_W = 520;
    public static final int MODAL_TARGET_H = 340;
    public static final int MODAL_MIN_W = 304;
    public static final int MODAL_MIN_H = 216;
    public static final int SCREEN_MARGIN_X = 12;
    public static final int SCREEN_MARGIN_Y = 10;

    /**
     * Inner safe inset inside {@code modal_frame.png} (96x96 logical at 2x HD).
     * Measured inner brass/rune frame edge is 11px horizontal / 12px vertical;
     * 15px guarantees >= 3px clearance on all 4 sides.
     */
    public static final int MODAL_SAFE_INSET = 15;

    /**
     * Horizontal safe padding inside {@code row_plaque*.png} (96x24 logical at 2x HD).
     * Measured inner vertical brass pillar ends at 6.5px; 10px guarantees 3.5px clearance.
     */
    public static final int ROW_SAFE_PAD_X = 10;

    /** Hard typography floors mandated by the UX specification. */
    public static final float PRIMARY_TEXT_MIN_SCALE = 0.85f;
    public static final float SECONDARY_TEXT_MIN_SCALE = 0.75f;
    public static final float PRIMARY_TEXT_SCALE = 0.88f;
    public static final float SECONDARY_TEXT_SCALE = 0.78f;

    /** Row metrics calibrated so 7-9 rows fit cleanly inside the 340px modal list viewport. */
    public static final int SECTION_HEADER_H = 20;
    public static final int OPTION_ROW_H = 26;
    public static final int ROW_GAP = 2;
    public static final int TRACE_BANNER_H = 44;
    public static final int TRACE_CARD_H = 40;
    public static final int TRACE_CARD_GAP = 3;
    public static final int SCROLLBAR_GUTTER = 8;

    public record Box(int x, int y, int w, int h) {
        public int right() {
            return this.x + this.w;
        }

        public int bottom() {
            return this.y + this.h;
        }

        public float centerX() {
            return this.x + this.w * 0.5f;
        }

        public float centerY() {
            return this.y + this.h * 0.5f;
        }

        public boolean contains(double px, double py) {
            return px >= this.x && px < right() && py >= this.y && py < bottom();
        }

        public boolean containsBox(Box inner) {
            return inner.x >= this.x
                && inner.y >= this.y
                && inner.right() <= this.right()
                && inner.bottom() <= this.bottom();
        }

        public boolean overlaps(Box other) {
            return this.x < other.right()
                && this.right() > other.x
                && this.y < other.bottom()
                && this.bottom() > other.y;
        }
    }

    public record OptionRowLayout(
        Box plaque,
        Box modifiedPip,
        Box labelBox,
        Box scopeBadgeBox,
        Box controlBox,
        Box resetBox
    ) {}

    public record SliderControlLayout(
        Box valuePill,
        Box minusBtn,
        Box trackArea,
        Box plusBtn
    ) {}

    public record TraceCardLayout(
        Box card,
        Box titleBox,
        Box domainJumpBox,
        Box toggleActionBox,
        Box classicPillBox,
        Box modernPillBox
    ) {}

    public record ModalLayout(
        int screenW,
        int screenH,
        boolean compactMode,
        Box modal,
        Box backdropArea,
        Box safeInterior,
        Box headerBar,
        Box headerTitleBox,
        Box modifiedFilterBtn,
        Box sessionBadgeBox,
        Box topDivider,
        Box toolbarBar,
        List<Box> presetButtons,
        Box searchBox,
        Box railViewport,
        List<Box> railTabBoxes,
        int railContentHeight,
        Box listViewport,
        Box listContentColumn,
        Box scrollbarTrack,
        Box bottomDivider,
        Box footerBar,
        Box resetPageBtn,
        Box resetAllBtn,
        Box footerStatusBox,
        Box closeBtn,
        Box saveBtn
    ) {}

    public static ModalLayout compute(int screenW, int screenH, int presetCount, int railItemCount) {
        int availW = Math.max(MODAL_MIN_W, screenW - SCREEN_MARGIN_X * 2);
        int availH = Math.max(MODAL_MIN_H, screenH - SCREEN_MARGIN_Y * 2);
        int modalW = clamp(availW, MODAL_MIN_W, MODAL_TARGET_W);
        int modalH = clamp(availH, MODAL_MIN_H, MODAL_TARGET_H);
        if (modalW > screenW - 4) {
            modalW = Math.max(268, screenW - 4);
        }
        if (modalH > screenH - 4) {
            modalH = Math.max(198, screenH - 4);
        }

        int modalX = (screenW - modalW) / 2;
        int modalY = (screenH - modalH) / 2;
        Box modal = new Box(modalX, modalY, modalW, modalH);
        Box backdropArea = new Box(modalX + 10, modalY + 10, Math.max(160, modalW - 20), Math.max(120, modalH - 20));

        int inset = MODAL_SAFE_INSET;
        Box safe = new Box(
            modalX + inset,
            modalY + inset,
            Math.max(180, modalW - inset * 2),
            Math.max(140, modalH - inset * 2)
        );
        boolean compact = safe.w() < 410 || safe.h() < 245;

        // L0 Top Header: Title + Modified Filter Chip + Session Governance Badge
        int headerH = compact ? 18 : 20;
        Box headerBar = new Box(safe.x(), safe.y(), safe.w(), headerH);
        int modePillW = clamp(Math.round(safe.w() * 0.30f), 88, 152);
        Box sessionBadge = new Box(headerBar.right() - modePillW, headerBar.y() + 1, modePillW, headerH - 2);
        int modChipW = clamp(Math.round(safe.w() * 0.11f), 36, 52);
        Box modifiedFilter = new Box(sessionBadge.x() - 4 - modChipW, headerBar.y() + 1, modChipW, headerH - 2);
        Box headerTitle = new Box(
            headerBar.x(),
            headerBar.y(),
            Math.max(60, modifiedFilter.x() - 6 - headerBar.x()),
            headerH
        );

        // L0 Secondary Toolbar: 4 Presets + Global Search Box
        int toolbarY = headerBar.bottom() + 2;
        int toolbarH = compact ? 18 : 20;
        Box toolbarBar = new Box(safe.x(), toolbarY, safe.w(), toolbarH);
        int pCount = Math.max(1, presetCount);
        int presetGap = 3;
        int presetsTotalW = clamp(Math.round(safe.w() * 0.52f), pCount * 34, pCount * 62);
        int singlePresetW = Math.max(30, (presetsTotalW - (pCount - 1) * presetGap) / pCount);
        List<Box> presetButtons = new ArrayList<>(pCount);
        int px = toolbarBar.x();
        for (int i = 0; i < pCount; i++) {
            presetButtons.add(new Box(px, toolbarBar.y() + 1, singlePresetW, toolbarH - 2));
            px += singlePresetW + presetGap;
        }
        int searchX = px + 3;
        int searchW = Math.max(48, toolbarBar.right() - searchX);
        Box searchBox = new Box(searchX, toolbarBar.y() + 1, searchW, toolbarH - 2);

        Box topDivider = new Box(safe.x(), toolbarBar.bottom() + 2, safe.w(), 5);

        // Footer Bar (anchored cleanly above bottom safe border)
        int footerH = compact ? 20 : 22;
        Box footerBar = new Box(safe.x(), safe.bottom() - footerH, safe.w(), footerH);
        Box bottomDivider = new Box(safe.x(), footerBar.y() - 6, safe.w(), 5);

        int btnH = footerH - 4;
        int btnY = footerBar.y() + 2;
        int actionBtnW = clamp(Math.round(safe.w() * 0.15f), 46, 72);
        int saveBtnW = clamp(Math.round(safe.w() * 0.18f), 58, 88);

        Box resetPageBtn = new Box(footerBar.x(), btnY, actionBtnW, btnH);
        Box resetAllBtn = new Box(resetPageBtn.right() + 3, btnY, actionBtnW, btnH);
        Box saveBtn = new Box(footerBar.right() - saveBtnW, btnY, saveBtnW, btnH);
        Box closeBtn = new Box(saveBtn.x() - 3 - actionBtnW, btnY, actionBtnW, btnH);
        int statusX = resetAllBtn.right() + 5;
        int statusW = Math.max(12, closeBtn.x() - 5 - statusX);
        Box footerStatus = new Box(statusX, btnY, statusW, btnH);

        // Body Region (L1 Rail + L2/L3 Content Panel) — leave crisp breathing room clear of divider gems
        int bodyTop = topDivider.bottom() + 4;
        int bodyBottom = bottomDivider.y() - 4;
        int bodyH = Math.max(60, bodyBottom - bodyTop);

        int railW = compact
            ? clamp(Math.round(safe.w() * 0.25f), 82, 110)
            : clamp(Math.round(safe.w() * 0.27f), 108, 136);
        Box railViewport = new Box(safe.x(), bodyTop, railW, bodyH);

        int rCount = Math.max(1, railItemCount);
        int railGap = 3;
        int tabH = 22;
        List<Box> railTabs = new ArrayList<>(rCount);
        int ry = railViewport.y() + 2;
        for (int i = 0; i < rCount; i++) {
            if (i == rCount - 1) {
                ry += 3; // Extra breathing gap above Rule Trace tab
            }
            railTabs.add(new Box(railViewport.x() + 2, ry, railViewport.w() - 4, tabH));
            ry += tabH + railGap;
        }
        int railContentHeight = Math.max(bodyH, (ry - railGap + 2) - railViewport.y());

        int contentX = railViewport.right() + 5;
        int contentW = Math.max(120, safe.right() - contentX);
        Box listViewport = new Box(contentX, bodyTop, contentW, bodyH);
        Box listContentColumn = new Box(contentX, bodyTop, Math.max(108, contentW - SCROLLBAR_GUTTER), bodyH);
        Box scrollbarTrack = new Box(
            listViewport.right() - 5,
            listViewport.y() + 1,
            4,
            Math.max(16, listViewport.h() - 2)
        );

        return new ModalLayout(
            screenW, screenH, compact,
            modal, backdropArea, safe,
            headerBar, headerTitle, modifiedFilter, sessionBadge,
            topDivider,
            toolbarBar, List.copyOf(presetButtons), searchBox,
            railViewport, List.copyOf(railTabs), railContentHeight,
            listViewport, listContentColumn, scrollbarTrack,
            bottomDivider, footerBar,
            resetPageBtn, resetAllBtn, footerStatus, closeBtn, saveBtn
        );
    }

    public static OptionRowLayout computeOptionRow(int x, int y, int rowW) {
        Box plaque = new Box(x, y, rowW, OPTION_ROW_H);
        int padX = ROW_SAFE_PAD_X;
        Box pip = new Box(x + padX, y + 8, 5, OPTION_ROW_H - 16);

        int resetW = 14;
        Box reset = new Box(plaque.right() - padX - resetW, y + 6, resetW, OPTION_ROW_H - 12);

        int ctrlW = clamp(Math.round(rowW * 0.43f), 84, 144);
        Box control = new Box(reset.x() - 3 - ctrlW, y + 4, ctrlW, OPTION_ROW_H - 8);

        int badgeW = 16;
        Box badge = new Box(control.x() - 3 - badgeW, y + 6, badgeW, OPTION_ROW_H - 12);

        int labelX = pip.right() + 3;
        int labelW = Math.max(28, badge.x() - 4 - labelX);
        Box label = new Box(labelX, y + 4, labelW, OPTION_ROW_H - 8);

        return new OptionRowLayout(plaque, pip, label, badge, control, reset);
    }

    /**
     * Splits a numeric slider control into a dedicated left readout pill, {@code [-]} step button,
     * unobstructed track + knob zone, and {@code [+]} step button so the numeric readout never
     * collides with the sliding knob at any value percentage.
     */
    public static SliderControlLayout computeSliderControl(Box controlBox) {
        int pillW = clamp(Math.round(controlBox.w() * 0.28f), 26, 38);
        Box pill = new Box(controlBox.x(), controlBox.y() + 1, pillW, controlBox.h() - 2);
        int stepW = 13;
        Box minus = new Box(pill.right() + 2, controlBox.y(), stepW, controlBox.h());
        Box plus = new Box(controlBox.right() - stepW, controlBox.y(), stepW, controlBox.h());
        int trackX = minus.right() + 2;
        int trackW = Math.max(22, plus.x() - 2 - trackX);
        Box track = new Box(trackX, controlBox.y(), trackW, controlBox.h());
        return new SliderControlLayout(pill, minus, track, plus);
    }

    public static TraceCardLayout computeTraceCard(int x, int y, int cardW) {
        Box card = new Box(x, y, cardW, TRACE_CARD_H);
        int padX = ROW_SAFE_PAD_X;
        int topY = y + 4;
        int topH = 14;
        int toggleW = clamp(Math.round(cardW * 0.18f), 44, 62);
        Box toggle = new Box(card.right() - padX - toggleW, topY, toggleW, topH);
        int jumpW = clamp(Math.round(cardW * 0.22f), 50, 78);
        Box jump = new Box(toggle.x() - 3 - jumpW, topY, jumpW, topH);
        int titleX = x + padX;
        int titleW = Math.max(36, jump.x() - 4 - titleX);
        Box title = new Box(titleX, topY, titleW, topH);

        int botY = y + 21;
        int botH = 14;
        int innerW = Math.max(60, cardW - padX * 2);
        int pillGap = 4;
        int halfW = (innerW - pillGap) / 2;
        Box classicPill = new Box(titleX, botY, halfW, botH);
        Box modernPill = new Box(classicPill.right() + pillGap, botY, innerW - halfW - pillGap, botH);

        return new TraceCardLayout(card, title, jump, toggle, classicPill, modernPill);
    }

    /**
     * Verifies structural layout invariants:
     * 1. Every region stays strictly inside {@code safeInterior} (never crosses decorative outer borders).
     * 2. Sibling controls in header, toolbar, rail, content, and footer never overlap.
     * 3. OptionRowLayout, SliderControlLayout, and TraceCardLayout sub-boxes stay strictly inside
     *    their plaque's inner decorative brass pillars ({@code ROW_SAFE_PAD_X}) and never overlap.
     */
    public static List<String> verifyInvariants(ModalLayout layout) {
        List<String> violations = new ArrayList<>();
        Box safe = layout.safeInterior();

        checkContained(safe, layout.headerBar(), "headerBar", violations);
        checkContained(safe, layout.toolbarBar(), "toolbarBar", violations);
        checkContained(safe, layout.topDivider(), "topDivider", violations);
        checkContained(safe, layout.railViewport(), "railViewport", violations);
        checkContained(safe, layout.listViewport(), "listViewport", violations);
        checkContained(safe, layout.bottomDivider(), "bottomDivider", violations);
        checkContained(safe, layout.footerBar(), "footerBar", violations);

        checkDisjoint(
            List.of(layout.headerTitleBox(), layout.modifiedFilterBtn(), layout.sessionBadgeBox()),
            "header",
            violations
        );
        if (layout.headerBar().overlaps(layout.toolbarBar())) {
            violations.add("headerBar overlaps toolbarBar");
        }
        if (layout.railViewport().overlaps(layout.listViewport())) {
            violations.add("railViewport overlaps listViewport");
        }
        if (layout.listContentColumn().overlaps(layout.scrollbarTrack())) {
            violations.add("listContentColumn overlaps scrollbarTrack");
        }
        if (layout.listViewport().overlaps(layout.footerBar())) {
            violations.add("listViewport overlaps footerBar");
        }

        // Toolbar siblings
        List<Box> toolbarItems = new ArrayList<>(layout.presetButtons());
        toolbarItems.add(layout.searchBox());
        checkDisjoint(toolbarItems, "toolbar", violations);

        // Footer siblings
        List<Box> footerItems = List.of(
            layout.resetPageBtn(),
            layout.resetAllBtn(),
            layout.footerStatusBox(),
            layout.closeBtn(),
            layout.saveBtn()
        );
        checkDisjoint(footerItems, "footer", violations);

        // Option row + slider control internal disjointness & inner-border clearance
        int rowW = layout.listContentColumn().w();
        OptionRowLayout row = computeOptionRow(layout.listContentColumn().x(), layout.listContentColumn().y(), rowW);
        Box rowInnerSafe = new Box(
            row.plaque().x() + ROW_SAFE_PAD_X - 1,
            row.plaque().y() + 3,
            row.plaque().w() - (ROW_SAFE_PAD_X - 1) * 2,
            row.plaque().h() - 6
        );
        List<Box> rowItems = List.of(
            row.modifiedPip(), row.labelBox(), row.scopeBadgeBox(), row.controlBox(), row.resetBox()
        );
        for (int i = 0; i < rowItems.size(); i++) {
            checkContained(rowInnerSafe, rowItems.get(i), "optionRow[" + i + "]", violations);
        }
        checkDisjoint(rowItems, "optionRow", violations);

        SliderControlLayout slider = computeSliderControl(row.controlBox());
        checkDisjoint(
            List.of(slider.valuePill(), slider.minusBtn(), slider.trackArea(), slider.plusBtn()),
            "sliderControl",
            violations
        );

        // Trace card internal disjointness & inner-border clearance
        TraceCardLayout trace = computeTraceCard(layout.listContentColumn().x(), layout.listContentColumn().y(), rowW);
        Box traceInnerSafe = new Box(
            trace.card().x() + ROW_SAFE_PAD_X - 1,
            trace.card().y() + 3,
            trace.card().w() - (ROW_SAFE_PAD_X - 1) * 2,
            trace.card().h() - 6
        );
        List<Box> traceTop = List.of(trace.titleBox(), trace.domainJumpBox(), trace.toggleActionBox());
        List<Box> traceBot = List.of(trace.classicPillBox(), trace.modernPillBox());
        for (int i = 0; i < traceTop.size(); i++) {
            checkContained(traceInnerSafe, traceTop.get(i), "traceCardTop[" + i + "]", violations);
        }
        for (int i = 0; i < traceBot.size(); i++) {
            checkContained(traceInnerSafe, traceBot.get(i), "traceCardBot[" + i + "]", violations);
        }
        checkDisjoint(traceTop, "traceCardTop", violations);
        checkDisjoint(traceBot, "traceCardBot", violations);
        if (trace.titleBox().overlaps(trace.classicPillBox())) {
            violations.add("traceCard top overlaps bottom pills");
        }

        return violations;
    }

    private static void checkContained(Box outer, Box inner, String label, List<String> violations) {
        if (!outer.containsBox(inner)) {
            violations.add(label + " overflows safe container: inner=" + inner + " outer=" + outer);
        }
    }

    private static void checkDisjoint(List<Box> boxes, String group, List<String> violations) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                if (boxes.get(i).overlaps(boxes.get(j))) {
                    violations.add(group + "[" + i + "] overlaps " + group + "[" + j + "]");
                }
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
