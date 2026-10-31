package enchantment_fusion.mechanics.signatures;

/** Bounds for saved real wear. This class does not roll Unbreaking or mutate equipment. */
public final class DurabilityReserveMath {
    private DurabilityReserveMath() {}
    public static int savedByEffect(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after) || before <= after || before <= 0.0F) return 0;
        long a = (long) Math.max(0.0F, before), b = (long) Math.max(0.0F, after);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, a - b));
    }
    public static int acceptedSaved(int observed, int requested, int result) {
        long net = Math.max(0L, (long) requested - Math.max(0L, (long) result));
        return (int) Math.min(Math.max(0L, (long) observed), net);
    }
    public static int add(int current, int saved, int costPerCharge, int maxCharges) {
        int cost = Math.clamp(costPerCharge, 1, 64), cap = Math.clamp(maxCharges, 1, 10);
        return (int) Math.min((long) cost * cap, Math.max(0L, (long) current) + Math.max(0L, (long) saved));
    }
}
