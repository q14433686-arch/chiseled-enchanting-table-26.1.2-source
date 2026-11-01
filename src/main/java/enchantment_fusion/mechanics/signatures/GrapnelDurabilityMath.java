package enchantment_fusion.mechanics.signatures;

/** Credit only a whole, actually avoided wear request rather than a partial or synthetic reduction. */
public final class GrapnelDurabilityMath {
    private GrapnelDurabilityMath() {}
    public static boolean fullySaved(int requested, int result, int observedSaved) {
        return requested > 0 && result == 0 && observedSaved == requested;
    }
    /** A conditional repair effect that made no net improvement cannot claim the repair. */
    public static boolean improvedRepair(int input, int output) { return input > 0 && output > input; }
}
