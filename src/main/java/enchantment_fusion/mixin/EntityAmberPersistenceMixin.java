package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Save a fire fallback for sealed entities even if serialization precedes entity unload. */
@Mixin(Entity.class)
public abstract class EntityAmberPersistenceMixin {
    @WrapOperation(method = "saveWithoutId", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/storage/ValueOutput;putShort(Ljava/lang/String;S)V"))
    private void ef$preserveSealedFire(ValueOutput output, String key, short value, Operation<Void> original) {
        short saved = "Fire".equals(key) ? AmberSealSkill.fireTicksForSave((Entity) (Object) this, value) : value;
        original.call(output, key, saved);
    }
}
