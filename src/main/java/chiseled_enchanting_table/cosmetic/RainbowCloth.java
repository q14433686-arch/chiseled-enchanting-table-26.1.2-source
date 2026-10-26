package chiseled_enchanting_table.cosmetic;

/** Stable key and bounded animation clock. Rendering uses this version's vanilla sheep colour lerper. */
public final class RainbowCloth {
    private RainbowCloth() {}
    public static final String ITEM_FLAG = "rainbowCloth";
    public static final int PERIOD = 25 * 16;
    public static boolean magicName(String name) { return "jeb_".equals(name); }
    public static float phase(long ticks, float partial) {
        float safePartial=Float.isFinite(partial) ? Math.max(0,Math.min(1,partial)) : 0;
        return (Math.floorMod(ticks,PERIOD)+safePartial)%PERIOD;
    }
}
