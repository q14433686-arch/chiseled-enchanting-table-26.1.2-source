package chiseled_enchanting_table.config;

/**
 * Pure, codepoint-safe single-line text editor buffer for search inputs (GUI-01 / GUI-02).
 *
 * <p>All cursor and selection-anchor indices are kept strictly aligned on Unicode code-point
 * boundaries (never splitting a UTF-16 surrogate pair such as CJK Unified Ideographs Extension B
 * {@code U+20000+} or supplementary-plane symbols), and maximum length is enforced by
 * {@link String#codePointCount(int, int)} rather than UTF-16 {@code char} units.</p>
 */
public final class SearchTextBuffer {
    public static final int DEFAULT_MAX_CODE_POINTS = 40;

    private final int maxCodePoints;
    private String text = "";
    private int cursor = 0;
    private int anchor = 0;

    public SearchTextBuffer() {
        this(DEFAULT_MAX_CODE_POINTS);
    }

    public SearchTextBuffer(int maxCodePoints) {
        this.maxCodePoints = Math.max(1, maxCodePoints);
    }

    public String text() {
        return this.text;
    }

    public boolean isEmpty() {
        return this.text.isEmpty();
    }

    public int codePointLength() {
        return this.text.codePointCount(0, this.text.length());
    }

    public int cursor() {
        return this.cursor;
    }

    public int anchor() {
        return this.anchor;
    }

    public boolean hasSelection() {
        return this.cursor != this.anchor;
    }

    public int selectionStart() {
        return Math.min(this.cursor, this.anchor);
    }

    public int selectionEnd() {
        return Math.max(this.cursor, this.anchor);
    }

    public String selectedText() {
        return hasSelection() ? this.text.substring(selectionStart(), selectionEnd()) : "";
    }

    public void setAll(String raw) {
        String sanitized = sanitizePrintable(raw, this.maxCodePoints);
        this.text = sanitized;
        this.cursor = sanitized.length();
        this.anchor = this.cursor;
    }

    public void clear() {
        this.text = "";
        this.cursor = 0;
        this.anchor = 0;
    }

    public void selectAll() {
        this.anchor = 0;
        this.cursor = this.text.length();
    }

    public void collapseSelection() {
        this.anchor = this.cursor;
    }

    public void moveHome(boolean extendSelection) {
        this.cursor = 0;
        if (!extendSelection) {
            this.anchor = 0;
        }
    }

    public void moveEnd(boolean extendSelection) {
        this.cursor = this.text.length();
        if (!extendSelection) {
            this.anchor = this.cursor;
        }
    }

    public void moveByCodePoints(int deltaCodePoints, boolean extendSelection) {
        if (!extendSelection && hasSelection() && Math.abs(deltaCodePoints) == 1) {
            this.cursor = deltaCodePoints < 0 ? selectionStart() : selectionEnd();
            this.anchor = this.cursor;
            return;
        }
        int next = stepCodePoints(this.text, this.cursor, deltaCodePoints);
        this.cursor = next;
        if (!extendSelection) {
            this.anchor = next;
        }
    }

    public boolean insertCodePoint(int codePoint) {
        if (codePoint < 32 || codePoint == 127 || !Character.isValidCodePoint(codePoint)) {
            return false;
        }
        return insertText(Character.toString(codePoint));
    }

    public boolean insertText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        int selStart = selectionStart();
        int selEnd = selectionEnd();
        String prefix = this.text.substring(0, selStart);
        String suffix = this.text.substring(selEnd);
        int usedCodePoints = prefix.codePointCount(0, prefix.length()) + suffix.codePointCount(0, suffix.length());
        int room = Math.max(0, this.maxCodePoints - usedCodePoints);
        String insertable = sanitizePrintable(raw, room);
        if (insertable.isEmpty() && selStart == selEnd) {
            return false;
        }
        this.text = prefix + insertable + suffix;
        this.cursor = prefix.length() + insertable.length();
        this.anchor = this.cursor;
        return true;
    }

    public boolean deleteBackward() {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (this.cursor <= 0) {
            return false;
        }
        int prev = stepCodePoints(this.text, this.cursor, -1);
        this.text = this.text.substring(0, prev) + this.text.substring(this.cursor);
        this.cursor = prev;
        this.anchor = prev;
        return true;
    }

    public boolean deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (this.cursor >= this.text.length()) {
            return false;
        }
        int next = stepCodePoints(this.text, this.cursor, 1);
        this.text = this.text.substring(0, this.cursor) + this.text.substring(next);
        this.anchor = this.cursor;
        return true;
    }

    public String cutSelection() {
        if (!hasSelection()) {
            return "";
        }
        String out = selectedText();
        deleteSelection();
        return out;
    }

    private void deleteSelection() {
        int s = selectionStart();
        int e = selectionEnd();
        this.text = this.text.substring(0, s) + this.text.substring(e);
        this.cursor = s;
        this.anchor = s;
    }

    public String renderWithCaret(boolean focused) {
        if (!focused) {
            return this.text;
        }
        if (hasSelection()) {
            int s = selectionStart();
            int e = selectionEnd();
            return this.text.substring(0, s) + "[" + this.text.substring(s, e) + "]" + this.text.substring(e);
        }
        return this.text.substring(0, this.cursor) + "|" + this.text.substring(this.cursor);
    }

    private static int stepCodePoints(String s, int index, int delta) {
        int aligned = alignBoundary(s, index);
        int count = s.length();
        if (delta < 0) {
            int cur = aligned;
            for (int i = 0; i < -delta && cur > 0; i++) {
                cur = s.offsetByCodePoints(cur, -1);
            }
            return cur;
        }
        int cur = aligned;
        for (int i = 0; i < delta && cur < count; i++) {
            cur = s.offsetByCodePoints(cur, 1);
        }
        return cur;
    }

    private static int alignBoundary(String s, int index) {
        int clamped = Math.clamp(index, 0, s.length());
        if (clamped > 0 && clamped < s.length()
            && Character.isLowSurrogate(s.charAt(clamped))
            && Character.isHighSurrogate(s.charAt(clamped - 1))) {
            return clamped - 1;
        }
        return clamped;
    }

    public static String sanitizePrintable(String raw, int maxCodePoints) {
        if (raw == null || raw.isEmpty() || maxCodePoints <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), maxCodePoints * 2));
        int added = 0;
        for (int i = 0; i < raw.length() && added < maxCodePoints; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 32 && cp != 127 && !Character.isSurrogate((char) cp)) {
                sb.appendCodePoint(cp);
                added++;
            }
        }
        return sb.toString();
    }
}
