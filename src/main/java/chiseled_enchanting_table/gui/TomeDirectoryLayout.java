package chiseled_enchanting_table.gui;

/** Single geometry contract for painting, clipping, mouse hit regions and client probes. */
public final class TomeDirectoryLayout {
    private TomeDirectoryLayout() {}
    public static final int CONTENT_X=7, CONTENT_Y=17, CONTENT_HEIGHT=141;
    public static final int PANEL_X=214, PANEL_Y=18, PANEL_W=119, PANEL_H=61;
    public static final int WIDTH = 151, HEIGHT = 59, GAP = 2, FRAME = 4, BUTTON_INSET = 3;
    public static final int POPUP_Y = HEIGHT + GAP, POPUP_ROW_HEIGHT = 12, POPUP_LIMIT = 5;
    public static final int INSCRIBE_BAR_HEIGHT = 15, INSCRIBE_LIST_OFFSET = INSCRIBE_BAR_HEIGHT + GAP;
    public static final int CLEANSE_BAR_HEIGHT = 15, CLEANSE_LIST_OFFSET = CLEANSE_BAR_HEIGHT + GAP;
    public static final int TOME_BUTTON_COUNT = 8, RITUAL_BUTTON_COUNT = 4;
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public Rect inset(int n) { return new Rect(x+n, y+n, Math.max(0,width-2*n), Math.max(0,height-2*n)); }
    }
    public static Rect bounds() { return new Rect(0,0,WIDTH,HEIGHT); }
    public static Rect safeBounds() { return bounds().inset(FRAME); }
    public static Rect selection() { return new Rect(4,42,111,13); }
    public static Rect button(int i) {
        return switch(i) {
            case 0 -> new Rect(4,4,93,11); case 1 -> new Rect(99,4,48,11);
            case 2 -> new Rect(4,16,70,11); case 3 -> new Rect(76,16,71,11);
            case 4 -> new Rect(4,28,111,13); case 5 -> new Rect(118,28,13,13);
            case 6 -> new Rect(134,28,13,13); case 7 -> new Rect(118,42,13,13);
            case 8 -> new Rect(134,42,13,13);
            default -> throw new IllegalArgumentException("Unknown catalogue button: " + i);
        };
    }
    public static Rect popup(int count) { return new Rect(0,POPUP_Y,WIDTH,Math.min(POPUP_LIMIT,Math.max(0,count))*POPUP_ROW_HEIGHT+8); }
    public static Rect popupRow(int i) { return new Rect(4,POPUP_Y+4+i*POPUP_ROW_HEIGHT,WIDTH-8,POPUP_ROW_HEIGHT); }
    public static Rect lockButton(int i) {
        if(i<0 || i>=TOME_BUTTON_COUNT) throw new IllegalArgumentException("Unknown lock button: " + i);
        int col = i % 4, row = i / 4;
        return new Rect(PANEL_X+9+col*25, PANEL_Y+24+row*15, 23, 13);
    }
    public static Rect ritualButton(int i) {
        if(i<0 || i>=RITUAL_BUTTON_COUNT) throw new IllegalArgumentException("Unknown ritual button: " + i);
        return new Rect(PANEL_X+14+i*24, PANEL_Y+33, 16, 16);
    }
    public static Rect inscribeSearchBox() { return new Rect(0, 0, 115, INSCRIBE_BAR_HEIGHT); }
    public static Rect inscribeAffordableButton() { return new Rect(118, 0, INSCRIBE_BAR_HEIGHT, INSCRIBE_BAR_HEIGHT); }
    public static Rect inscribeExpandButton() { return new Rect(136, 0, INSCRIBE_BAR_HEIGHT, INSCRIBE_BAR_HEIGHT); }
    public static Rect cleanseSearchBox() { return new Rect(0, 0, 97, CLEANSE_BAR_HEIGHT); }
    public static Rect cleanseModeButton() { return new Rect(100, 0, CLEANSE_BAR_HEIGHT, CLEANSE_BAR_HEIGHT); }
    public static Rect cleanseAffordableButton() { return new Rect(118, 0, CLEANSE_BAR_HEIGHT, CLEANSE_BAR_HEIGHT); }
    public static Rect cleanseExpandButton() { return new Rect(136, 0, CLEANSE_BAR_HEIGHT, CLEANSE_BAR_HEIGHT); }
    public static int listOffset() { return HEIGHT + GAP; }
    public static int inscribeListOffset() { return INSCRIBE_LIST_OFFSET; }
    public static int cleanseListOffset() { return CLEANSE_LIST_OFFSET; }
}
