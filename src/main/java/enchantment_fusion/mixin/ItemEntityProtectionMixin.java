package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.transmute.DroppedItemOwnerAccess;
import enchantment_fusion.mechanics.transmute.RelicWorldItemTransmutes;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source-specific dropped-item protection; never marks the entity globally invulnerable. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityProtectionMixin implements DroppedItemOwnerAccess {
    @Override
    @Accessor("target")
    public abstract @Nullable UUID ef$pickupTarget();

    @Override
    @Accessor("thrower")
    public abstract @Nullable EntityReference<Entity> ef$throwerReference();

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void ef$protectSelectedDamageTypes(ServerLevel level, DamageSource source, float amount,
                                               CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (RelicWorldItemTransmutes.protectsDroppedItem(self.getItem(), source)) cir.setReturnValue(false);
    }
}
