package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.HarvestDropCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe new entities only; never discover candidates by scanning neighboring loot. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelHarvestCaptureMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void ef$captureFreshHarvestEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
            HarvestDropCapture.record((ServerLevel) (Object) this, entity);
        }
    }
}
