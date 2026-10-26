package chiseled_enchanting_table.gui;

/** Single geometry contract for painting, clipping, mouse hit regions and client probes. */
public final class TomeDirectoryLayout {
    private TomeDirectoryLayout() {}
    public static final int WIDTH = 151, HEIGHT = 59, GAP = 2, FRAME = 4, BUTTON_INSET = 3;
    public static final int POPUP_Y = HEIGHT + GAP, POPUP_ROW_HEIGHT = 12, POPUP_LIMIT = 5;
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
        if(i<0 || i>4) throw new IllegalArgumentException("Unknown lock button");
        return new Rect(214+9+i*20,18+38,19,13);
    }
    public static int listOffset() { return HEIGHT + GAP; }
}
