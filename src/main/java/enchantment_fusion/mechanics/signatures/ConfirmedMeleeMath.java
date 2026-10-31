package enchantment_fusion.mechanics.signatures;

/** Conservative net loss across one actual damage call, including absorption. */
public final class ConfirmedMeleeMath {
    private ConfirmedMeleeMath() {}
    public static float actualLoss(float healthBefore, float absorptionBefore, float healthAfter, float absorptionAfter) {
        if (!Float.isFinite(healthBefore) || !Float.isFinite(absorptionBefore) || !Float.isFinite(healthAfter)
            || !Float.isFinite(absorptionAfter) || healthBefore < 0 || absorptionBefore < 0 || healthAfter < 0 || absorptionAfter < 0) return 0;
        float result = (healthBefore - healthAfter) + (absorptionBefore - absorptionAfter);
        return Float.isFinite(result) && result > 0 ? result : 0;
    }
}
