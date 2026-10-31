package enchantment_fusion.mechanics.signatures;

/** Fraction of post-protection fire damage that can be transferred to non-breaking wear. */
public final class FireTradeoffMath {
    private FireTradeoffMath() {}
    public record Plan(float prevented, int requestedWear) {}
    public static Plan plan(float damage, int percent, int wearPerDamage, int remainingDurability) {
        if (!Float.isFinite(damage) || damage <= 0.0F || remainingDurability <= 1) return new Plan(0, 0);
        int rate = Math.clamp(wearPerDamage, 1, 100), budget = Math.max(0, remainingDurability - 1);
        double wanted = damage * Math.clamp(percent, 0, 100) / 100.0D;
        double possible = Math.min(wanted, (double) budget / rate);
        if (!(possible > 0.0D)) return new Plan(0, 0);
        int wear = (int) Math.min(budget, Math.ceil(possible * rate));
        return new Plan((float) possible, wear);
    }
    public static float addHeat(float current, float prevented, int cap) {
        if (!Float.isFinite(current) || current < 0) current = 0;
        if (!Float.isFinite(prevented) || prevented <= 0) return Math.min(current, Math.max(0, cap));
        return Math.min(Math.max(0, cap), current + prevented);
    }
}
