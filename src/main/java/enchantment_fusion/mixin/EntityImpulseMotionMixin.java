package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Capture real collision-clipped movement; settling happens later after network corrections. */
@Mixin(Entity.class)
public abstract class EntityImpulseMotionMixin {
    @WrapMethod(method = "move")
    private void ef$observeImpulseMovement(MoverType type, Vec3 requested, Operation<Void> original) {
        MeleeImpulseSkills.Move move = MeleeImpulseSkills.beginMove((Entity) (Object) this, type, requested);
        boolean completed = false;
        try {
            original.call(type, requested);
            completed = true;
        } finally {
            MeleeImpulseSkills.endMove(move, completed);
        }
    }
}
