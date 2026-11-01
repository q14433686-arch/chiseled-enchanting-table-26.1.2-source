package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.GrapnelLooting;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** The full virtual call must succeed after the victim subclass and death-loot processing return. */
@Mixin(Entity.class)
public abstract class EntityLootActionMixin {
    @WrapMethod(method = "hurtOrSimulate")
    private boolean ef$fullLootDamage(DamageSource source, float damage, Operation<Boolean> original) {
        GrapnelLooting.Action action = GrapnelLooting.beginAction((Entity) (Object) this, source);
        boolean accepted = false;
        try { accepted = original.call(source, damage); return accepted; }
        finally { GrapnelLooting.endAction(action, accepted); }
    }
}
