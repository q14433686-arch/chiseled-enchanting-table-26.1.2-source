package enchantment_fusion.mechanics.signatures;

/** Bounds for real-shot sequence and accumulated next-load reduction. */
public final class PiercingChainMath {
    private PiercingChainMath() {}
    public static boolean canFollow(long previousSequence, long currentSequence, long now, long expires, double alignment) {
        return previousSequence > 0 && currentSequence > previousSequence && now < expires
            && Double.isFinite(alignment) && alignment >= 0.7D;
    }
    public static int accumulate(int current, int quickLevel, int perLevel, int cap) {
        int safeCap = Math.clamp(cap, 0, 20);
        return (int) Math.min(safeCap, (long) Math.max(0, current)
            + (long) Math.clamp(quickLevel, 1, 10) * Math.clamp(perLevel, 0, 10));
    }
    public static int duration(int original, int saved) {
        return Math.max(1, original - Math.clamp(saved, 0, 20));
    }
}
