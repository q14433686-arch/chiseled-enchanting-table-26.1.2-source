package enchantment_fusion.mechanics.signatures;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Pure counterpart of vanilla's attribute arithmetic; caller supplies the attribute's own clamp. */
public final class AttributeContributionMath {
    public enum Operation { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }
    public record Modifier(double amount, Operation operation) {}
    private AttributeContributionMath() {}

    /** Same item and primary share one native level; unrelated ID collisions retain last-entry ordering. */
    public static boolean replaceProjection(boolean samePrimaryOnItem, int previousLevel, int nextLevel) {
        return !samePrimaryOnItem || nextLevel >= previousLevel;
    }

    public static double calculate(double baseValue, List<Modifier> modifiers, DoubleUnaryOperator sanitize) {
        double base = baseValue;
        for (Modifier modifier : modifiers) if (modifier.operation() == Operation.ADD_VALUE) base += modifier.amount();
        double result = base;
        for (Modifier modifier : modifiers)
            if (modifier.operation() == Operation.ADD_MULTIPLIED_BASE) result += base * modifier.amount();
        for (Modifier modifier : modifiers)
            if (modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL) result *= 1.0D + modifier.amount();
        return sanitize.applyAsDouble(result);
    }
}
