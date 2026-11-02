package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import enchantment_fusion.mechanics.signatures.NativeFrostWaterGuard;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.MeleeGuardCapture;
import enchantment_fusion.mechanics.signatures.GrapnelDefenseCapture;
import enchantment_fusion.mechanics.signatures.ProtectionProjection;
import enchantment_fusion.mechanics.signatures.GrapnelQuickCharge;
import enchantment_fusion.mechanics.signatures.MeleeFireSkills;
import enchantment_fusion.mechanics.signatures.ConfirmedWindBurst;
import enchantment_fusion.mechanics.signatures.GrapnelThorns;
import enchantment_fusion.mechanics.signatures.GrapnelChanneling;
import enchantment_fusion.mechanics.signatures.GrapnelLoyalty;
import enchantment_fusion.mechanics.signatures.GrapnelFrost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;

/** Observe real enchantment deltas and replace only selected conflicting frost location effects. */
@Mixin(Enchantment.class)
public abstract class EnchantmentFrostEffectsMixin {
    @WrapMethod(method = "modifyDamageProtection")
    private void ef$observeContribution(ServerLevel level, int enchantmentLevel, ItemStack item,
                                        Entity victim, DamageSource source, MutableFloat protection,
                                        Operation<Void> original) {
        Enchantment enchantment = (Enchantment) (Object) this;
        int effective = ProtectionProjection.effectiveLevel(enchantment, enchantmentLevel, item, victim, source);
        float before = protection.floatValue();
        original.call(level, effective, item, victim, source, protection);
        ProtectionProjection.observe(enchantment, effective, item, victim, source);
        FrostLandingCapture.observeEnchantment(enchantment, level, item, victim,
            source, before, protection.floatValue());
        BlastDefenseSkills.observeProtection(enchantment, level, item, victim,
            source, before, protection.floatValue());
        MeleeGuardCapture.observe(enchantment, level, effective, item, victim,
            source, before, protection.floatValue());
        GrapnelDefenseCapture.observe(enchantment, level, effective, item, victim,
            source, before, protection.floatValue());
    }

    @WrapMethod(method = "modifyTridentReturnToOwnerAcceleration")
    private void ef$observeLoyaltyReturn(ServerLevel level, int rank, ItemStack stack, Entity trident,
                                         MutableFloat acceleration, Operation<Void> original) {
        GrapnelLoyalty.Effect effect = GrapnelLoyalty.beginEffect((Enchantment) (Object) this,
            level, rank, stack, trident, acceleration.floatValue());
        boolean completed = false;
        try { original.call(level, rank, stack, trident, acceleration); completed = true; }
        finally { GrapnelLoyalty.endEffect(effect, acceleration.floatValue(), completed); }
    }

    @WrapMethod(method = "modifyCrossbowChargeTime")
    private void ef$observeQuickCharge(RandomSource random, int enchantmentLevel, MutableFloat time, Operation<Void> original) {
        float before = time.floatValue();
        original.call(random, enchantmentLevel, time);
        GrapnelQuickCharge.observe((Enchantment) (Object) this, enchantmentLevel, before, time.floatValue());
    }

    @WrapMethod(method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V")
    private void ef$deferMeleeThorns(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                     EnchantmentTarget side, Entity victim, DamageSource source, Operation<Void> original) {
        Enchantment enchantment = (Enchantment) (Object) this;
        GrapnelThorns.Scope thorns = GrapnelThorns.begin(enchantment, level, enchantmentLevel, item, side, victim, source);
        GrapnelChanneling.Effect channel = GrapnelChanneling.beginPostEffect(enchantment, level, enchantmentLevel, item, side, victim, source);
        boolean completed = false;
        try {
            if (!FireThornsSkills.suppressInstantThorns(enchantment, level, item, side, victim, source)) {
                int fireBefore = victim instanceof LivingEntity living ? living.getRemainingFireTicks() : 0;
                Vec3 windBefore = item.owner() instanceof LivingEntity living ? living.getDeltaMovement() : null;
                original.call(level, enchantmentLevel, item, side, victim, source);
                MeleeFireSkills.observePrimaryFire(enchantment, level, enchantmentLevel, item, side, victim, source, fireBefore);
                ConfirmedWindBurst.observe(enchantment, level, enchantmentLevel, item, side, victim, source, windBefore);
            }
            completed = true;
        } finally {
            try { GrapnelChanneling.endEffect(channel, completed); }
            finally { GrapnelThorns.finish(thorns, completed); }
        }
    }

    @WrapMethod(method = "onHitBlock")
    private void ef$observeChannelingBlock(ServerLevel level, int rank, EnchantedItemInUse item, Entity projectile,
                                           Vec3 position, BlockState state, Operation<Void> original) {
        GrapnelChanneling.Effect effect = GrapnelChanneling.beginBlockEffect((Enchantment) (Object) this,
            level, rank, item, projectile, position, state);
        boolean completed = false;
        try { original.call(level, rank, item, projectile, position, state); completed = true; }
        finally { GrapnelChanneling.endEffect(effect, completed); }
    }

    @WrapMethod(method = "runLocationChangedEffects")
    private void ef$scopeNativeWaterWrites(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                           LivingEntity wearer, Operation<Void> original) {
        Enchantment enchantment = (Enchantment) (Object) this;
        GrapnelFrost.Scope scope = GrapnelFrost.begin(enchantment, level, enchantmentLevel, item, wearer);
        boolean guarded = FrostFallSkills.suppressNativeWaterFreeze(enchantment, level, item, wearer);
        boolean entered = NativeFrostWaterGuard.enter(guarded), completed = false;
        try {
            original.call(level, enchantmentLevel, item, wearer); completed = true;
        } finally {
            try { NativeFrostWaterGuard.exit(entered); }
            finally { GrapnelFrost.end(scope, completed); }
        }
    }
}
