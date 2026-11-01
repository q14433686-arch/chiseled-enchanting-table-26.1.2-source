package enchantment_fusion.mechanics.signatures;

/** Finite, bounded credit and effects paid by actual experience-orb repair. */
public final class RangedMendingMath {
    public static final int MAX_FORGE = 12, MAX_SPEND = 4;
    public static final long FORGE_TICKS = 600L, ARMED_TICKS = 200L, FLIGHT_TICKS = 200L;
    private RangedMendingMath() {}
    public static int addForge(int previous, int repaired) {
        return (int) Math.min(MAX_FORGE, Math.max(0L, (long) previous) + Math.max(0L, (long) repaired));
    }
    public static int spend(int credit) { return Math.clamp(credit, 0, MAX_SPEND); }
    public static float damageBonus(int level, int spent) {
        return spent <= 0 ? 0 : Math.min(5.0F, 0.75F + .3F * Math.clamp(level, 1, 6) + .3F * Math.clamp(spent, 1, MAX_SPEND));
    }
    public static float knockbackBonus(int level, int spent) {
        return spent <= 0 ? 0 : Math.min(3.0F, .25F * Math.clamp(level, 1, 6) + .15F * Math.clamp(spent, 1, MAX_SPEND));
    }
    public static float fireSeconds(int level, int spent) {
        return spent <= 0 ? 0 : Math.min(8.0F, 2.5F + .5F * Math.clamp(level, 1, 6) + .5F * Math.clamp(spent, 1, MAX_SPEND));
    }
    /** Match the real XP-orb integer accounting without manufacturing an extra orb or XP. */
    public static int remainingXp(int amount, int capacity, int repaired) {
        if (amount <= 0) return Math.max(0, amount);
        if (capacity <= 0 || repaired <= 0) return amount;
        int actual = Math.min(repaired, capacity);
        return amount - (int) Math.min(amount, (long) actual * amount / capacity);
    }
}
