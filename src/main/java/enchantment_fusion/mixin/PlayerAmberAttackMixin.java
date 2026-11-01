package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.AmberSealSkill;
import enchantment_fusion.mechanics.signatures.GlazedStrikeEffect;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.MomentumSealSkills;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.FireSweepSkills;
import enchantment_fusion.mechanics.signatures.WindSweepSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.mechanics.signatures.ConfirmedMelee;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.mechanics.signatures.MeleeWorkSkills;
import enchantment_fusion.mechanics.signatures.MeleeMendingSkills;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures exact player damage calls for both ordered Fire Aspect/Silk Touch skills. */
@Mixin(Player.class)
public abstract class PlayerAmberAttackMixin {
    @WrapMethod(method = "attack")
    private void ef$glazeSwing(Entity target, Operation<Void> original) {
        Player player = (Player) (Object) this;
        MeleeWearSkills.Attack wear = MeleeWearSkills.beginAttack(player, player.getWeaponItem(), target, EquipmentSlot.MAINHAND);
        try {
            MeleeWorkSkills.Attack work = MeleeWorkSkills.beginAttack(player, player.getWeaponItem(), target, EquipmentSlot.MAINHAND);
            try {
                MeleeImpulseSkills.Attack impulse = MeleeImpulseSkills.beginAttack(player, player.getWeaponItem(), target, EquipmentSlot.MAINHAND);
                try {
                    GrapnelImpact.Attack impact = GrapnelImpact.beginAttack(player, player.getWeaponItem(), target, EquipmentSlot.MAINHAND);
                    try {
                        GlazedStrikeEffect.Swing swing = GlazedStrikeEffect.beginSwing(player, target);
                        try {
                            SoulThreadSkills.Swing soulSwing = SoulThreadSkills.beginSwing(player, target);
                            try {
                                FireSweepSkills.Swing fireSweep = FireSweepSkills.beginSwing(player);
                                try {
                                    ItemStack weapon = player.getWeaponItem();
                                    SoulBladeSkills.Attack soulBlade = SoulBladeSkills.beginAttack(player, weapon, target,
                                        SoulThreadSkills.swingFull(player, weapon, target));
                                    try {
                                        SegmentThreadSkills.Attack segment = SegmentThreadSkills.beginAttack(player, weapon, target,
                                            SoulThreadSkills.swingFull(player, weapon, target));
                                        try {
                                            WindSweepSkills.Attack wind = WindSweepSkills.beginAttack(player, weapon, target);
                                            try {
                                                HeavyCrushSkill.Swing heavySwing = HeavyCrushSkill.beginSwing(player, target);
                                                try {
                                                    MomentumSealSkills.Attack momentum = MomentumSealSkills.beginAttack(player, weapon, target);
                                                    try { original.call(target); } finally { MomentumSealSkills.endAttack(momentum); }
                                                } finally { HeavyCrushSkill.endSwing(heavySwing); }
                                            } finally { WindSweepSkills.endAttack(wind); }
                                        } finally { SegmentThreadSkills.endAttack(segment); }
                                    } finally { SoulBladeSkills.endAttack(soulBlade); }
                                } finally { FireSweepSkills.endSwing(fireSweep); }
                            } finally { SoulThreadSkills.endSwing(soulSwing); }
                        } finally { GlazedStrikeEffect.endSwing(swing); }
                    } finally { GrapnelImpact.endAttack(impact); }
                } finally { MeleeImpulseSkills.endAttack(impulse); }
            } finally { MeleeWorkSkills.endAttack(work); }
        } finally { MeleeWearSkills.endAttack(wear); }
    }

    @WrapMethod(method = "stabAttack")
    private boolean ef$momentumStabFrame(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage,
                                          boolean dealsKnockback, boolean dismounts, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        ItemStack weapon = player.getItemBySlot(slot);
        MeleeWearSkills.Attack wear = MeleeWearSkills.beginAttack(player, weapon, target, slot);
        try {
            MeleeWorkSkills.Attack work = MeleeWorkSkills.beginAttack(player, weapon, target, slot);
            try {
                MeleeImpulseSkills.Attack impulse = MeleeImpulseSkills.beginAttack(player, weapon, target, slot);
                try {
                    GrapnelImpact.Attack impact = GrapnelImpact.beginAttack(player, weapon, target, slot);
                    try {
                        SoulBladeSkills.Attack soulBlade = SoulBladeSkills.beginAttack(player, weapon, target, SoulThreadSkills.stabFull(player, slot, weapon));
                        try {
                            SegmentThreadSkills.Attack segment = SegmentThreadSkills.beginAttack(player, weapon, target, SoulThreadSkills.stabFull(player, slot, weapon));
                            try {
                                WindSweepSkills.Attack wind = WindSweepSkills.beginAttack(player, weapon, target);
                                try {
                                    MomentumSealSkills.Attack frame = MomentumSealSkills.beginAttack(player, weapon, target);
                                    try { return original.call(slot, target, damage, dealsDamage, dealsKnockback, dismounts); }
                                    finally { MomentumSealSkills.endAttack(frame); }
                                } finally { WindSweepSkills.endAttack(wind); }
                            } finally { SegmentThreadSkills.endAttack(segment); }
                        } finally { SoulBladeSkills.endAttack(soulBlade); }
                    } finally { GrapnelImpact.endAttack(impact); }
                } finally { MeleeImpulseSkills.endAttack(impulse); }
            } finally { MeleeWorkSkills.endAttack(work); }
        } finally { MeleeWearSkills.endAttack(wear); }
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$amberMelee(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        GlazedStrikeEffect.prepareSwing(player, target);
        AmberSealSkill.AttackFrame frame = AmberSealSkill.beginAttack(player, player.getWeaponItem(), target, source);
        FireThornsSkills.AttackSnapshot thorns = FireThornsSkills.beforeAttack(player, target, source);
        boolean succeeded = false;
        try {
            ItemStack weapon = player.getWeaponItem();
            boolean full = SoulThreadSkills.swingFull(player, weapon, target);
            ConfirmedMelee.Observation confirmed = ConfirmedMelee.before(player, weapon, EquipmentSlot.MAINHAND, target, source, full);
            FireSweepSkills.Hit fireSweep = FireSweepSkills.beginHit(player, weapon, target, source, full);
            WindSweepSkills.Hit wind = WindSweepSkills.beforeDamage(player, weapon, target, source);
            SoulBladeSkills.Hit soulBlade = SoulBladeSkills.beforeDamage(player, weapon, target, source);
            SegmentThreadSkills.Hit segment = SegmentThreadSkills.beforeDamage(player, weapon, target, source);
            SoulThreadSkills.Hit soulHit = SoulThreadSkills.beginHit(player, weapon, target, source, full);
            try {
                HeavyCrushSkill.Hit heavyHit = HeavyCrushSkill.beginMelee(player, weapon, target, source, false);
                try {
                    MeleeMendingSkills.Damage mending = MeleeMendingSkills.beginDamage(confirmed, damage);
                    try {
                        succeeded = original.call(target, source, mending.damage());
                        ConfirmedMelee.after(confirmed, succeeded);
                        return succeeded;
                    } finally { MeleeMendingSkills.endDamage(mending); }
                } finally {
                    try { SegmentThreadSkills.afterDamage(segment, succeeded); }
                    finally {
                        try { SoulBladeSkills.afterDamage(soulBlade, succeeded); }
                        finally {
                            try { WindSweepSkills.afterDamage(wind, succeeded); }
                            finally {
                                try { FireSweepSkills.endHit(fireSweep, succeeded); }
                                finally {
                                    try { FireThornsSkills.afterAttack(thorns, player, target, source, succeeded); }
                                    finally {
                                        try { MomentumSealSkills.damageResult(player, target, source, succeeded); }
                                        finally { HeavyCrushSkill.endHit(heavyHit, succeeded); }
                                    }
                                }
                            }
                        }
                    }
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
        FireThornsSkills.AttackSnapshot thorns = FireThornsSkills.beforeAttack(player, target, source);
        boolean succeeded = false;
        try {
            ItemStack weapon = player.getItemBySlot(slot);
            boolean full = SoulThreadSkills.stabFull(player, slot, weapon);
            ConfirmedMelee.Observation confirmed = ConfirmedMelee.before(player, weapon, slot, target, source, full);
            FireSweepSkills.Hit fireSweep = FireSweepSkills.beginHit(player, weapon, target, source, full);
            WindSweepSkills.Hit wind = WindSweepSkills.beforeDamage(player, weapon, target, source);
            SoulBladeSkills.Hit soulBlade = SoulBladeSkills.beforeDamage(player, weapon, target, source);
            SegmentThreadSkills.Hit segment = SegmentThreadSkills.beforeDamage(player, weapon, target, source);
            SoulThreadSkills.Hit soulHit = SoulThreadSkills.beginHit(player, weapon, target, source, full);
            try {
                HeavyCrushSkill.Hit heavyHit = HeavyCrushSkill.beginMelee(player, weapon, target, source, true);
                try {
                    MeleeMendingSkills.Damage mending = MeleeMendingSkills.beginDamage(confirmed, damage);
                    try {
                        succeeded = original.call(target, source, mending.damage());
                        ConfirmedMelee.after(confirmed, succeeded);
                        return succeeded;
                    } finally { MeleeMendingSkills.endDamage(mending); }
                } finally {
                    try { SegmentThreadSkills.afterDamage(segment, succeeded); }
                    finally {
                        try { SoulBladeSkills.afterDamage(soulBlade, succeeded); }
                        finally {
                            try { WindSweepSkills.afterDamage(wind, succeeded); }
                            finally {
                                try { FireSweepSkills.endHit(fireSweep, succeeded); }
                                finally {
                                    try { FireThornsSkills.afterAttack(thorns, player, target, source, succeeded); }
                                    finally {
                                        try { MomentumSealSkills.damageResult(player, target, source, succeeded); }
                                        finally { HeavyCrushSkill.endHit(heavyHit, succeeded); }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally { SoulThreadSkills.endHit(soulHit, succeeded); }
        } finally { AmberSealSkill.endAttack(frame, succeeded); }
    }

    @WrapOperation(method = "itemAttackInteraction", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;doPostAttackEffectsWithItemSource(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/item/ItemStack;)V"))
    private void ef$soulThreadPostAttack(ServerLevel level, Entity victim, DamageSource source, ItemStack weapon, Operation<Void> original) {
        original.call(level, victim, source, weapon);
        SoulThreadSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
        SoulBladeSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
        SegmentThreadSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
        MeleeAttackThemes.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
        MeleeImpulseSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
        FireSweepSkills.nonNativePostAttack(level, (Player) (Object) this, weapon, victim, source);
    }

    @Inject(method = "doSweepAttack", at = @At("HEAD"))
    private void ef$windCoreSweep(Entity target, float baseDamage, DamageSource source, float charge, CallbackInfo ci) {
        WindSweepSkills.afterRealSweep((Player) (Object) this, target, source);
    }

    @WrapOperation(method = "doSweepAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ef$recordRealSweep(LivingEntity victim, ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        float health = victim.getHealth(), absorption = victim.getAbsorptionAmount();
        boolean accepted = original.call(victim, level, source, damage);
        FireSweepSkills.afterSweep((Player) (Object) this, victim, source, health, absorption, accepted);
        return accepted;
    }

    @WrapOperation(method = {"attack", "stabAttack"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F"))
    private float ef$windPrimaryKnockback(Player player, Entity target, DamageSource source, Operation<Float> original) {
        float vanilla = WindSweepSkills.replayKnockback(player, target, source, original.call(player, target, source));
        float others = MeleeImpulseSkills.replayKnockback(player, target, source, vanilla);
        if (!(player instanceof ServerPlayer owner)) return others;
        return GrapnelImpact.project(GrapnelImpact.Kind.KNOCKBACK, owner.level(), player.getWeaponItem(), target, source, others * 2.0F) / 2.0F;
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
        WindSweepSkills.afterKnockback((Player) (Object) this, target, before);
        MeleeImpulseSkills.afterKnockback((Player) (Object) this, target, before);
        GrapnelImpact.afterKnockback((Player) (Object) this, target, before);
        MomentumSealSkills.afterLivingKnockback((Player) (Object) this, target, before);
    }
}
