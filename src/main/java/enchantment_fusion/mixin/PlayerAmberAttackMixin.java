package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
            SoulThreadSkills.Swing soulSwing = SoulThreadSkills.beginSwing(player, target);
            try {
                HeavyCrushSkill.Swing heavySwing = HeavyCrushSkill.beginSwing(player, target);
                try {
                    MomentumSealSkills.Attack momentum = MomentumSealSkills.beginAttack(player, player.getWeaponItem(), target);
                    try { original.call(target); } finally { MomentumSealSkills.endAttack(momentum); }
                } finally { HeavyCrushSkill.endSwing(heavySwing); }
            } finally { SoulThreadSkills.endSwing(soulSwing); }
        } finally { GlazedStrikeEffect.endSwing(swing); }
    }

    @WrapMethod(method = "stabAttack")
    private boolean ef$momentumStabFrame(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage,
                                          boolean dealsKnockback, boolean dismounts, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        MomentumSealSkills.Attack frame = MomentumSealSkills.beginAttack(player, player.getItemBySlot(slot), target);
        try {
            return original.call(slot, target, damage, dealsDamage, dealsKnockback, dismounts);
        } finally {
            MomentumSealSkills.endAttack(frame);
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
            ItemStack weapon = player.getWeaponItem();
            SoulThreadSkills.Hit soulHit = SoulThreadSkills.beginHit(player, weapon, target, source,
                SoulThreadSkills.swingFull(player, weapon, target));
            try {
                HeavyCrushSkill.Hit heavyHit = HeavyCrushSkill.beginMelee(player, weapon, target, source, false);
                try {
                    succeeded = original.call(target, source, damage);
                    return succeeded;
                } finally {
                    try { MomentumSealSkills.damageResult(player, target, source, succeeded); }
                    finally { HeavyCrushSkill.endHit(heavyHit, succeeded); }
                }
            } finally { SoulThreadSkills.endHit(soulHit, succeeded); }
        } finally { AmberSealSkill.endAttack(frame, succeeded); }
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
            ItemStack weapon = player.getItemBySlot(slot);
            SoulThreadSkills.Hit soulHit = SoulThreadSkills.beginHit(player, weapon, target, source,
                SoulThreadSkills.stabFull(player, slot, weapon));
            try {
                HeavyCrushSkill.Hit heavyHit = HeavyCrushSkill.beginMelee(player, weapon, target, source, true);
                try {
                    succeeded = original.call(target, source, damage);
                    return succeeded;
                } finally {
                    try { MomentumSealSkills.damageResult(player, target, source, succeeded); }
                    finally { HeavyCrushSkill.endHit(heavyHit, succeeded); }
                }
            } finally { SoulThreadSkills.endHit(soulHit, succeeded); }
        } finally { AmberSealSkill.endAttack(frame, succeeded); }
    }

    @WrapOperation(method = "itemAttackInteraction", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;doPostAttackEffectsWithItemSource(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/item/ItemStack;)V"))
    private void ef$soulThreadPostAttack(ServerLevel level, Entity victim, DamageSource source, ItemStack weapon, Operation<Void> original) {
        original.call(level, victim, source, weapon);
        SoulThreadSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
    }

    @WrapOperation(method = {"attack", "stabAttack"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V"))
    private void ef$momentumCause(Player player, Entity target, float amount, Vec3 oldMovement, Operation<Void> original) {
        float adjusted = MomentumSealSkills.adjustKnockback(player, target, amount);
        try {
            original.call(player, target, adjusted, oldMovement);
        } finally {
            MomentumSealSkills.afterKnockbackCall(player, target);
        }
    }

    @WrapOperation(method = "causeExtraKnockback", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void ef$observeMomentum(LivingEntity target, double power, double x, double z, Operation<Void> original) {
        Vec3 before = target.getDeltaMovement();
        original.call(target, power, x, z);
        MomentumSealSkills.afterLivingKnockback((Player) (Object) this, target, before);
    }
}
