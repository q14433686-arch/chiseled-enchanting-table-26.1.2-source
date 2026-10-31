package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Alter the real explosion impulse, including the vector sent to the affected player. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionBlastDefenseMixin {
    @WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double ef$blastResistance(LivingEntity entity, Holder<Attribute> attribute, Operation<Double> original) {
        double vanilla = original.call(entity, attribute);
        return entity instanceof ServerPlayer player ? BlastDefenseSkills.explosionResistance(player, vanilla) : vanilla;
    }
}
