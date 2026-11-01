package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelBreath;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe one real base-tick air calculation without drawing or applying any extra randomness. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBreathCaptureMixin {
    @WrapMethod(method = "baseTick")
    private void ef$scopeBreathingTick(Operation<Void> original) {
        GrapnelBreath.Tick tick = GrapnelBreath.beginTick((LivingEntity) (Object) this);
        boolean completed = false;
        try { original.call(); completed = true; }
        finally { GrapnelBreath.endTick(tick, completed); }
    }
    @WrapMethod(method = "decreaseAirSupply")
    private int ef$scopeAirCalculation(int air, Operation<Integer> original) {
        GrapnelBreath.Tick tick = GrapnelBreath.beginCalculation((LivingEntity) (Object) this, air);
        int result = air; boolean completed = false;
        try { result = original.call(air); completed = true; return result; }
        finally { GrapnelBreath.endCalculation(tick, result, completed); }
    }
    @WrapOperation(method = "decreaseAirSupply", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D"))
    private double ef$observeActualOxygenRoll(RandomSource random, Operation<Double> original) {
        double result = original.call(random);
        GrapnelBreath.recordRoll((LivingEntity) (Object) this, result);
        return result;
    }
}
