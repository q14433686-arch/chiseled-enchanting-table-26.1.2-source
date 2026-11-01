package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills;
import enchantment_fusion.mechanics.signatures.MeleeLightningSkills;
import enchantment_fusion.mechanics.signatures.MeleeGuardSkills;
import enchantment_fusion.mechanics.signatures.GrapnelSkills;
import enchantment_fusion.mechanics.signatures.GrapnelVolley;
import enchantment_fusion.mechanics.signatures.GrapnelProjectilePrimary;
import enchantment_fusion.mechanics.signatures.GrapnelDefense;
import enchantment_fusion.mechanics.signatures.GrapnelThorns;
import enchantment_fusion.mechanics.signatures.GrapnelQuickCharge;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.GrapnelLunge;
import enchantment_fusion.mechanics.signatures.PrimaryLocationProjection;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the same native-effect ordering for both orientations of Density/Breach. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperHeavyCrushMixin {
    @WrapMethod(method = "doPostPiercingAttackEffects")
    private static void ef$replayLunge(ServerLevel level, LivingEntity user, Operation<Void> original) {
        original.call(level, user);
        GrapnelLunge.replayMissing(level, user);
    }

    @WrapMethod(method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V")
    private static void ef$conditionalLocation(ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (PrimaryLocationProjection.replaying()) {
            Object suspended = PrimaryLocationProjection.suspend();
            try { original.call(level, entity); } finally { PrimaryLocationProjection.resume(suspended); }
            return;
        }
        PrimaryLocationProjection.reconcile(entity);
        original.call(level, entity);
        PrimaryLocationProjection.locationChanged(level, entity);
    }

    @WrapMethod(method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V")
    private static void ef$conditionalLocationItem(ServerLevel level, ItemStack stack, LivingEntity entity,
                                                   EquipmentSlot slot, Operation<Void> original) {
        if (PrimaryLocationProjection.replaying()) {
            Object suspended = PrimaryLocationProjection.suspend();
            try { original.call(level, stack, entity, slot); } finally { PrimaryLocationProjection.resume(suspended); }
            return;
        }
        PrimaryLocationProjection.reconcile(entity);
        original.call(level, stack, entity, slot);
        PrimaryLocationProjection.locationChangedItem(level, stack, entity, slot);
    }

    @WrapMethod(method = "stopLocationBasedEffects(Lnet/minecraft/world/entity/LivingEntity;)V")
    private static void ef$stopConditionalLocation(LivingEntity entity, Operation<Void> original) {
        PrimaryLocationProjection.stopAll(entity);
        Object suspended = PrimaryLocationProjection.suspend();
        try { original.call(entity); } finally { PrimaryLocationProjection.resume(suspended); }
    }

    @WrapMethod(method = "stopLocationBasedEffects(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V")
    private static void ef$stopConditionalLocationItem(ItemStack stack, LivingEntity entity, EquipmentSlot slot, Operation<Void> original) {
        PrimaryLocationProjection.stopSlot(entity, slot);
        Object suspended = PrimaryLocationProjection.suspend();
        try { original.call(stack, entity, slot); } finally { PrimaryLocationProjection.resume(suspended); }
    }

    @WrapMethod(method = "tickEffects")
    private static void ef$conditionalTick(ServerLevel level, LivingEntity entity, Operation<Void> original) {
        if (PrimaryLocationProjection.replaying()) {
            Object suspended = PrimaryLocationProjection.suspend();
            try { original.call(level, entity); } finally { PrimaryLocationProjection.resume(suspended); }
            return;
        }
        PrimaryLocationProjection.reconcile(entity);
        original.call(level, entity);
        PrimaryLocationProjection.tick(level, entity);
    }

    @WrapMethod(method = "modifyDamage")
    private static float ef$soulThreadDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                              float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        float themed = MeleeAttackThemes.nonNativeDamage(level, weapon, victim, source,
            SegmentThreadSkills.modifyDamage(level, weapon, victim, source,
                SoulBladeSkills.modifyDamage(level, weapon, victim, source,
                    SoulThreadSkills.nonNativeDamage(level, weapon, victim, source, vanilla))));
        float knockbackPrimary = MeleeImpulseSkills.nonNativePrimaryDamage(level, weapon, victim, source, themed);
        float wear = MeleeWearSkills.modifyDamage(level, weapon, victim, source, knockbackPrimary);
        float projectile = GrapnelSkills.nonNativeProjectileDamage(level, weapon, victim, source, wear);
        return MeleeLightningSkills.nonNativeTridentDamage(level, weapon, victim, source, projectile);
    }

    @WrapMethod(method = "modifyCrossbowChargingTime")
    private static float ef$grapnelQuickCharge(ItemStack weapon, LivingEntity user, float input, Operation<Float> original) {
        float vanilla = original.call(weapon, user, input);
        float result = GrapnelQuickCharge.chargeTime(weapon, user, vanilla);
        GrapnelQuickCharge.finishTime(weapon, user, input, result);
        return result;
    }

    @WrapMethod(method = "getPiercingCount")
    private static int ef$grapnelPiercingCount(ServerLevel level, ItemStack weapon, ItemStack ammo, Operation<Integer> original) {
        return GrapnelProjectilePrimary.piercing(level, weapon, ammo, original.call(level, weapon, ammo));
    }

    @WrapMethod(method = "processProjectileCount")
    private static int ef$grapnelProjectileCount(ServerLevel level, ItemStack weapon, Entity shooter, int value,
                                                  Operation<Integer> original) {
        return GrapnelVolley.count(level, weapon, shooter, original.call(level, weapon, shooter, value));
    }

    @WrapMethod(method = "processProjectileSpread")
    private static float ef$grapnelProjectileSpread(ServerLevel level, ItemStack weapon, Entity shooter, float value,
                                                    Operation<Float> original) {
        return GrapnelVolley.spread(level, weapon, shooter, original.call(level, weapon, shooter, value));
    }

    @WrapMethod(method = "doPostAttackEffectsWithItemSourceOnBreak")
    private static void ef$guardVictimPostEffects(ServerLevel level, Entity victim, DamageSource source,
                                                  ItemStack weapon, Consumer<Item> attackerlessOnBreak,
                                                  Operation<Void> original) {
        try {
            original.call(level, victim, source, weapon, attackerlessOnBreak);
            MeleeGuardSkills.replayVictimPostAttack(level, victim, source);
            GrapnelDefense.replayVictimPostAttack(level, victim, source);
            GrapnelThorns.replayVictimPostAttack(level, victim, source);
            MeleeLightningSkills.nonNativeTridentPost(level, weapon, victim, source);
        } finally { GrapnelThorns.afterPostAttack(level, victim, source); }
    }

    @WrapMethod(method = "onHitBlock")
    private static void ef$tridentBlockPrimary(ServerLevel level, ItemStack weapon, LivingEntity owner, Entity projectile,
                                              EquipmentSlot slot, Vec3 position, BlockState state, Consumer<Item> onBreak,
                                              Operation<Void> original) {
        original.call(level, weapon, owner, projectile, slot, position, state, onBreak);
        MeleeLightningSkills.nonNativeTridentBlock(level, weapon, projectile, position, state, onBreak);
    }

    @WrapMethod(method = "modifyArmorEffectiveness")
    private static float ef$heavyArmor(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        float heavy = HeavyCrushSkill.armorEffect(level, weapon, victim, source, vanilla);
        return GrapnelImpact.project(GrapnelImpact.Kind.ARMOR, level, weapon, victim, source, heavy);
    }

    @WrapMethod(method = "modifyFallBasedDamage")
    private static float ef$heavySmash(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        float heavy = HeavyCrushSkill.fallEffect(level, weapon, victim, source, vanilla);
        return GrapnelImpact.project(GrapnelImpact.Kind.FALL, level, weapon, victim, source, heavy);
    }
}
