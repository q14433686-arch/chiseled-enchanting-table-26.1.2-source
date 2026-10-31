package enchantment_fusion.mechanics.signatures;

/** Bounded arithmetic for the mending/unbreaking pair; no inventory or game side effects. */
public final class DurabilityEconomyMath {
    private static final int MAX_SAFE_XP_LEVEL = 1_000_000;
    private DurabilityEconomyMath() {}

    public static int growthCap(int originalMax, int capPercent) {
        if (originalMax <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) originalMax * Math.clamp(capPercent, 100, 500) / 100);
    }

    public static int growthStep(int originalMax, int perThousand) {
        if (originalMax <= 0) return 0;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
            ((long) originalMax * Math.clamp(perThousand, 1, 50) + 999L) / 1000L));
    }

    public static int growthChance(int basePercent, int unbreakingLevel) {
        return (int) Math.min(100L, Math.max(0L, (long) Math.clamp(basePercent, 0, 100)
            + 5L * (Math.clamp(unbreakingLevel, 1, 255) - 1L)));
    }

    /** One-percent crossing is based on damage that actually passed vanilla durability rules. */
    public static boolean crossedOnePercent(int max, int beforeDamage, int attemptedDamage) {
        if (max <= 0 || attemptedDamage <= beforeDamage) return false;
        long threshold = Math.max(1L, max / 100L);
        long before = (long) max - Math.clamp(beforeDamage, 0, max);
        long after = (long) max - Math.clamp(attemptedDamage, 0, max);
        return before > threshold && after <= threshold;
    }

    public static int conversionXp(int maximum, int xpCap) {
        if (maximum <= 0) return 0;
        return Math.min(Math.clamp(xpCap, 1, 256), Math.max(1, maximum / 100));
    }

    /** Exact, proportional allocation; null means no payment may be taken at all. */
    public static int[] splitWear(int totalCost, int[] safeCapacities) {
        if (totalCost <= 0 || safeCapacities == null || safeCapacities.length == 0) return null;
        long total = 0;
        for (int cap : safeCapacities) {
            if (cap < 0) return null;
            total += cap;
        }
        if (total < totalCost) return null;
        int[] out = new int[safeCapacities.length];
        int remaining = totalCost;
        for (int i = 0; i < safeCapacities.length; i++) {
            out[i] = (int) Math.min(safeCapacities[i], (long) totalCost * safeCapacities[i] / total);
            remaining -= out[i];
        }
        for (int i = 0; i < safeCapacities.length && remaining > 0; i++) {
            int add = Math.min(remaining, safeCapacities[i] - out[i]);
            out[i] += add;
            remaining -= add;
        }
        return remaining == 0 ? out : null;
    }

    public static int rescueTarget(int maximum, int percent, int cap) {
        if (maximum <= 0) return 0;
        long roundedUp = ((long) maximum * Math.clamp(percent, 1, 100) + 99L) / 100L;
        return (int) Math.min(maximum, Math.min(Math.clamp(cap, 1, 10_000), Math.max(1L, roundedUp)));
    }

    /** Vanilla totalExperience is lifetime-like and is not the available level balance. */
    public static long spendableXp(int level, float progress, int nextLevelCost) {
        if (level < 0 || level > MAX_SAFE_XP_LEVEL || nextLevelCost <= 0
            || !Float.isFinite(progress) || progress < 0.0F || progress >= 1.0F) return -1L;
        long l = level;
        long atLevel = level <= 16 ? l * l + 6L * l
            : level <= 31 ? (5L * l * l - 81L * l + 720L) / 2L
            : (9L * l * l - 325L * l + 4440L) / 2L;
        long within = Math.min(nextLevelCost - 1L, Math.max(0L,
            (long) Math.floor((double) progress * nextLevelCost + 0.0001D)));
        return atLevel + within;
    }
}
