package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.GrapnelLunge;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;

/** One real multi-target spear attack; no synthetic strike or extra collision is introduced. */
@Mixin(PiercingWeapon.class)
public abstract class PiercingWeaponGrapnelMixin {
    @WrapMethod(method = "attack")
    private void ef$scopeSpearAttack(LivingEntity attacker, EquipmentSlot slot, Operation<Void> original) {
        GrapnelLunge.Attack frame = GrapnelLunge.begin(attacker, slot, this);
        boolean completed = false;
        try { original.call(attacker, slot); completed = true; }
        finally { GrapnelLunge.end(frame, completed); }
    }
}
