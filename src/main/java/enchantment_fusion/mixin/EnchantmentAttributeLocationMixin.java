package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Change IDs only inside our explicit conditional-primary replay; native effects remain untouched. */
@Mixin(EnchantmentAttributeEffect.class)
public abstract class EnchantmentAttributeLocationMixin {
    @WrapMethod(method = "onChangedBlock")
    private void ef$projectLocationAttribute(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                             Entity entity, Vec3 position, boolean becameActive, Operation<Void> original) {
        EnchantmentAttributeEffect effect = (EnchantmentAttributeEffect) (Object) this;
        if (!PrimaryLocationProjection.activateAttribute(effect, enchantmentLevel, item, entity, becameActive))
            original.call(level, enchantmentLevel, item, entity, position, becameActive);
    }
    @WrapMethod(method = "onDeactivated")
    private void ef$removeLocationAttribute(EnchantedItemInUse item, Entity entity, Vec3 position,
                                            int enchantmentLevel, Operation<Void> original) {
        EnchantmentAttributeEffect effect = (EnchantmentAttributeEffect) (Object) this;
        if (!PrimaryLocationProjection.deactivateAttribute(effect, enchantmentLevel, item, entity))
            original.call(item, entity, position, enchantmentLevel);
    }
}
