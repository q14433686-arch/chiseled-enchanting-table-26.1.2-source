package enchantment_fusion.mechanics.signatures;

/** Pure fishing-choice math; it changes loot luck and waiting time, never creates loot. */
public final class FishingTideMath {
    private FishingTideMath() {}
    public static float waitingLuck(int biteTicks, int lureLevel) {
        int steps = Math.clamp(biteTicks / 20, 0, 4);
        return Math.min(6.0F, steps * 0.5F * Math.clamp(lureLevel, 1, 6));
    }
    public static int shortenedWait(int original, int luckLevel, int basePercent, int perLevelPercent, int capPercent) {
        int reduction = Math.min(Math.clamp(capPercent, 0, 75),
            Math.clamp(basePercent, 0, 75) + Math.clamp(perLevelPercent, 0, 25) * Math.clamp(luckLevel, 1, 10));
        return Math.max(1, (int) Math.ceil(Math.max(1, original) * (100.0D - reduction) / 100.0D));
    }
}
