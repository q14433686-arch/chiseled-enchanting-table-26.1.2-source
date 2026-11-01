package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelThorns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.effects.DamageEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe the result of the data-pack's actual reflected-damage call without changing it. */
@Mixin(DamageEntity.class)
public abstract class DamageEntityGrapnelMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeThornsResult(Entity target, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        boolean accepted = false, completed = false;
        try {
            accepted = original.call(target, level, source, amount);
            completed = true;
            return accepted;
        } finally { GrapnelThorns.damageResult(target, source, completed, accepted); }
    }
}
