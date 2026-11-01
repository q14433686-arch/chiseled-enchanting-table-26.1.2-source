package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.ConfirmedLivingDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe real living damage and non-player spear attacks without mistaking a new mark for a second hit. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFireThornsMixin {
    @WrapMethod(method = "actuallyHurt")
    private void ef$observeLivingDamage(ServerLevel level, DamageSource source, float damage, Operation<Void> original) {
        LivingEntity victim = (LivingEntity) (Object) this;
        // Player overrides this method and has its own wrapper; avoid duplicate observation if a mod calls super.
        if (victim instanceof Player) { original.call(level, source, damage); return; }
        float beforeHealth = victim.getHealth(), beforeAbsorption = victim.getAbsorptionAmount();
        original.call(level, source, damage);
        FireThornsSkills.observeDamage(victim, source, beforeHealth, beforeAbsorption);
        ConfirmedLivingDamage.observed(victim, source, beforeHealth, beforeAbsorption);
    }

    @WrapOperation(method = "stabAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$observeNonPlayerStab(Entity victim, ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        LivingEntity attacker = (LivingEntity) (Object) this;
        FireThornsSkills.AttackSnapshot snapshot = FireThornsSkills.beforeAttack(attacker, victim, source);
        boolean accepted = false;
        try { accepted = original.call(victim, level, source, damage); return accepted; }
        finally { FireThornsSkills.afterAttack(snapshot, attacker, victim, source, accepted); }
    }
}
