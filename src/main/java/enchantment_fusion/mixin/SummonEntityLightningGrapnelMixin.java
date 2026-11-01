package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelChanneling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.SummonEntityEffect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe the actual summon result; LightningBolt.cause is set later by the vanilla effect. */
@Mixin(SummonEntityEffect.class)
public abstract class SummonEntityLightningGrapnelMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
    private Entity ef$observeSummonedEntity(EntityType<?> type, ServerLevel spawnLevel, BlockPos pos, EntitySpawnReason reason,
                                            Operation<Entity> original, ServerLevel level, int rank, EnchantedItemInUse item,
                                            Entity affected, Vec3 position) {
        Entity spawned = original.call(type, spawnLevel, pos, reason);
        GrapnelChanneling.summoned(level, item, affected, position, spawned);
        return spawned;
    }
}
