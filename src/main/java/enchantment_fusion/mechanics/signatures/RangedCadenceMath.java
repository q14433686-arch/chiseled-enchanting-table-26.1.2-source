package enchantment_fusion.mechanics.signatures;

/** Bounded resource arithmetic; never creates or consumes an arrow itself. */
public final class RangedCadenceMath {
    private RangedCadenceMath() {}

    public static int drawCredit(int quickChargeLevel, int ticksPerLevel, int maximum) {
        int level = Math.clamp(quickChargeLevel, 1, 10);
        int perLevel = Math.clamp(ticksPerLevel, 0, 10);
        return Math.min(Math.clamp(maximum, 0, 19), level * perLevel);
    }

    public static int nextLoad(int current, int capacity) {
        int cap = Math.clamp(capacity, 1, 10);
        return Math.min(cap, Math.clamp(current, 0, cap) + 1);
    }

    public static double arrowMultiplier(int stacks, int powerLevel, int percentPerLevel) {
        int count = Math.clamp(stacks, 0, 10), level = Math.clamp(powerLevel, 1, 10);
        int percent = Math.clamp(percentPerLevel, 0, 50);
        return Math.min(5.0D, 1.0D + count * level * percent / 100.0D);
    }

    /** Compact view includes a generation so a delayed packet cannot animate the next draw twice. */
    public static int drawView(int ticks, boolean offhand, boolean active, int generation) {
        int safe = Math.clamp(ticks, 0, 19), serial = Math.clamp(generation, 1, 32767);
        return safe == 0 ? 0 : (serial << 7) | (safe << 2) | (offhand ? 2 : 0) | (active ? 1 : 0);
    }
    public static int viewTicks(int value) {
        int ticks = (value >>> 2) & 31;
        return value > 0 && viewGeneration(value) > 0 && ticks <= 19 ? ticks : 0;
    }
    public static int viewGeneration(int value) { return value > 0 ? (value >>> 7) & 32767 : 0; }
    public static boolean viewOffhand(int value) { return (value & 2) != 0; }
    public static boolean viewActive(int value) { return (value & 1) != 0; }
}
