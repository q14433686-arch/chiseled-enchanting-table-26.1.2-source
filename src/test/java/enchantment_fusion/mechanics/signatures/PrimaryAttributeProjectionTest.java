package enchantment_fusion.mechanics.signatures;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

final class PrimaryAttributeProjectionTest {
    private static AttributeModifier mod(String id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(Identifier.parse("test:" + id), amount, operation);
    }
    @Test void counterfactualUsesVanillaOperationOrderAndClampWithoutMutation() {
        Holder<Attribute> attribute = Holder.direct(new RangedAttribute("test.speed", 0.3D, 0.0D, 1.0D));
        var instance = new AttributeInstance(attribute, ignored -> {});
        var primary = mod("primary", 0.2D, AttributeModifier.Operation.ADD_VALUE);
        var base = mod("base", 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        var total = mod("total", 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        instance.addTransientModifier(primary); instance.addTransientModifier(base); instance.addTransientModifier(total);
        assertEquals(1.0D, instance.getValue(), 1.0E-8D);
        assertEquals(0.675D, PrimaryAttributeProjection.valueWithout(instance, primary.id()), 1.0E-8D);
        assertEquals(1.0D, instance.getValue(), 1.0E-8D);
        assertEquals(primary, instance.getModifier(primary.id()));
        assertEquals(0.75D, PrimaryAttributeProjection.valueWithout(instance, base.id()), 1.0E-8D);
    }
    @Test void anAlreadySaturatedOrCancelledModifierCannotBeCredited() {
        var instance = new AttributeInstance(Holder.direct(new RangedAttribute("test.cap", 0.0D, 0.0D, 1.0D)), ignored -> {});
        var primary = mod("primary", 0.4D, AttributeModifier.Operation.ADD_VALUE);
        instance.addTransientModifier(mod("other", 1.0D, AttributeModifier.Operation.ADD_VALUE));
        instance.addTransientModifier(primary);
        assertEquals(instance.getValue(), PrimaryAttributeProjection.valueWithout(instance, primary.id()), 1.0E-8D);
        instance.addTransientModifier(mod("zero", -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        assertEquals(instance.getValue(), PrimaryAttributeProjection.valueWithout(instance, primary.id()), 1.0E-8D);
    }
}
