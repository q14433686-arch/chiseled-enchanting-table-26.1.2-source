package enchantment_fusion.mechanics.signatures;

import static org.junit.jupiter.api.Assertions.*;
import static enchantment_fusion.mechanics.signatures.AttributeContributionMath.Operation.*;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

final class PrimaryAttributeProjectionTest {
    private static final DoubleUnaryOperator CLAMP = value -> Double.isNaN(value) ? 0 : Math.clamp(value, 0.0D, 1.0D);
    private static AttributeContributionMath.Modifier mod(double amount, AttributeContributionMath.Operation operation) {
        return new AttributeContributionMath.Modifier(amount, operation);
    }
    @Test void counterfactualUsesVanillaOperationOrderAndClampWithoutMutation() {
        var primary = mod(0.2D, ADD_VALUE); var base = mod(0.5D, ADD_MULTIPLIED_BASE);
        var total = mod(0.5D, ADD_MULTIPLIED_TOTAL); var all = List.of(primary, base, total);
        assertEquals(1.0D, AttributeContributionMath.calculate(0.3D, all, CLAMP), 1.0E-8D);
        assertEquals(0.675D, AttributeContributionMath.calculate(0.3D, List.of(base, total), CLAMP), 1.0E-8D);
        assertEquals(0.75D, AttributeContributionMath.calculate(0.3D, List.of(primary, total), CLAMP), 1.0E-8D);
        assertEquals(List.of(primary, base, total), all);
    }
    @Test void anAlreadySaturatedOrCancelledModifierCannotBeCredited() {
        var primary = mod(0.4D, ADD_VALUE); var other = mod(1.0D, ADD_VALUE);
        assertEquals(AttributeContributionMath.calculate(0, List.of(other), CLAMP),
            AttributeContributionMath.calculate(0, List.of(other, primary), CLAMP), 1.0E-8D);
        var zero = mod(-1.0D, ADD_MULTIPLIED_TOTAL);
        assertEquals(AttributeContributionMath.calculate(0, List.of(other, zero), CLAMP),
            AttributeContributionMath.calculate(0, List.of(other, primary, zero), CLAMP), 1.0E-8D);
    }
}
