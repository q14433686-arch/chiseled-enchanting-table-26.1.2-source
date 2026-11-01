package enchantment_fusion.mechanics.signatures;

/** Bounded, server-side defaults; rolls are made once and never used to invent loot. */
public final class MeleeFortuneMath {
    private MeleeFortuneMath() {}
    public static int chance(int basePercent, int perLevel, int fortuneLevel) {
        return Math.clamp(basePercent + Math.clamp(perLevel, 0, 50) * Math.clamp(fortuneLevel, 1, 10), 0, 90);
    }
    public static float heavyDamage(float damage, int attackLevel) {
        if (!Float.isFinite(damage) || damage <= 0) return damage;
        return Math.max(damage, Math.min(1024.0F, damage * (1.25F + 0.05F * Math.clamp(attackLevel, 1, 10))));
    }
    public static float scatterDamage(float actualLoss, int fortuneLevel) {
        if (!Float.isFinite(actualLoss) || actualLoss <= 0) return 0;
        return Math.min(8.0F, actualLoss * (0.20F + 0.03F * Math.clamp(fortuneLevel, 1, 10)));
    }
    public static int chargeTicks(float delay) {
        if (!Float.isFinite(delay) || delay <= 0 || delay > 200) return 0;
        return Math.clamp((int) Math.ceil(delay), 1, 200);
    }
}
