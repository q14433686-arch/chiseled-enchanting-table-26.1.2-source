package enchantment_fusion.mechanics.signatures;

/** The first confirmed arthropod hit creates a cocoon; later hits build pressure. */
public final class SoulThreadMath {
    private SoulThreadMath() {}
    public record Step(int pressure, boolean release) {}
    public static Step advance(int oldPressure, int threshold, boolean fullCharge) {
        int required = Math.clamp(threshold, 1, 20);
        if (oldPressure < 0) return new Step(0, false);
        int current = oldPressure >= required ? required : oldPressure + 1;
        return new Step(current, fullCharge && current >= required);
    }
}
