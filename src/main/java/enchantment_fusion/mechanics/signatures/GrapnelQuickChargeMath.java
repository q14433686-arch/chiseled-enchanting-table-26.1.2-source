package enchantment_fusion.mechanics.signatures;

/** Pure, conservative proof rules; never perform a second datapack/random calculation. */
public final class GrapnelQuickChargeMath {
    private GrapnelQuickChargeMath() {}
    public static boolean contribution(int calls, int observedLevel, int expectedLevel, float before, float after) {
        return calls == 1 && observedLevel == expectedLevel && Float.isFinite(before) && Float.isFinite(after)
            && Math.max(0.0F, after) + 1.0E-5F < Math.max(0.0F, before);
    }
    public static boolean faster(int calls, boolean contribution, float baseline, float result) {
        return calls == 1 && contribution && Float.isFinite(baseline) && Float.isFinite(result)
            && result >= 0 && result + 1.0E-5F < baseline;
    }
}
