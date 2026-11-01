package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.GrapnelRiptide;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;

/** See the single real value operation; do not re-roll Riptide for attribution. */
@Mixin(Enchantment.class)
public abstract class EnchantmentRiptideObservationMixin {
    @WrapMethod(method = "modifyTridentSpinAttackStrength")
    private void ef$observeRiptide(RandomSource random, int level, MutableFloat strength, Operation<Void> original) {
        float before = strength.floatValue();
        original.call(random, level, strength);
        GrapnelRiptide.observed((Enchantment) (Object) this, random, level, before, strength.floatValue());
    }
}
