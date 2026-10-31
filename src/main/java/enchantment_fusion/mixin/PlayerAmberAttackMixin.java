package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures exact player damage calls for both ordered Fire Aspect/Silk Touch skills. */
@Mixin(Player.class)
public abstract class PlayerAmberAttackMixin {
    @WrapMethod(method = "attack")
    private void ef$glazeSwing(Entity target, Operation<Void> original) {
        Player player = (Player) (Object) this;
        GlazedStrikeEffect.Swing swing = GlazedStrikeEffect.beginSwing(player, target);
        try {
            original.call(target);
        } finally {
            GlazedStrikeEffect.endSwing(swing);
        }
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$amberMelee(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        GlazedStrikeEffect.prepareSwing(player, target);
        AmberSealSkill.AttackFrame frame = AmberSealSkill.beginAttack(player, player.getWeaponItem(), target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            AmberSealSkill.endAttack(frame, succeeded);
        }
    }

    @WrapOperation(method = "stabAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$amberStab(Entity target, DamageSource source, float damage, Operation<Boolean> original,
                                 EquipmentSlot slot, Entity originalTarget, float baseDamage,
                                 boolean dealsDamage, boolean dealsKnockback, boolean dismounts) {
        Player player = (Player) (Object) this;
        GlazedStrikeEffect.prepareStab(player, slot, target);
        AmberSealSkill.AttackFrame frame = AmberSealSkill.beginAttack(player, player.getItemBySlot(slot), target, source);
        boolean succeeded = false;
        try {
            succeeded = original.call(target, source, damage);
            return succeeded;
        } finally {
            AmberSealSkill.endAttack(frame, succeeded);
        }
    }
}
