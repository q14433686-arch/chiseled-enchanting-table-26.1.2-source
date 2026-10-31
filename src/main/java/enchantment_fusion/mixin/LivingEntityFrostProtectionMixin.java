package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe, but do not modify, the actual combined protection calculation. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFrostProtectionMixin {
    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"))
    private float ef$observeProtection(float damage, float protection, Operation<Float> original,
                                       DamageSource source, float originalDamage) {
        float result = original.call(damage, protection);
        FrostLandingCapture.observeMagic((LivingEntity) (Object) this, source, damage, protection, result);
        return result;
    }
}
