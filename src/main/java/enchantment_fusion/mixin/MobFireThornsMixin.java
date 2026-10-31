package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Also confirm accepted ordinary mob attacks against non-living targets. */
@Mixin(Mob.class)
public abstract class MobFireThornsMixin {
    @WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeMobAttack(Entity victim, ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        Mob attacker = (Mob) (Object) this;
        FireThornsSkills.AttackSnapshot snapshot = FireThornsSkills.beforeAttack(attacker, victim, source);
        boolean accepted = false;
        try { accepted = original.call(victim, level, source, damage); return accepted; }
        finally { FireThornsSkills.afterAttack(snapshot, attacker, victim, source, accepted); }
    }
}
