package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.GrapnelImpact;
import enchantment_fusion.mechanics.signatures.HeavyCrushMath;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defer only the selected pair's relevant native effect; the helper replays it once. */
@Mixin(Enchantment.class)
public abstract class EnchantmentHeavyCrushMixin {
    @WrapMethod(method = "modifyKnockback")
    private void ef$observeKnockback(ServerLevel level, int rank, ItemStack weapon, Entity victim,
                                     DamageSource source, MutableFloat value, Operation<Void> original) {
        GrapnelImpact.Effect effect = GrapnelImpact.beginEffect(GrapnelImpact.Kind.KNOCKBACK, level,
            (Enchantment) (Object) this, rank, weapon, victim, source, value.floatValue());
        boolean completed = false;
        try { original.call(level, rank, weapon, victim, source, value); completed = true; }
        finally { GrapnelImpact.endEffect(effect, value.floatValue(), completed); }
    }

    @WrapMethod(method = "modifyArmorEffectivness")
    private void ef$observeArmor(ServerLevel level, int rank, ItemStack weapon, Entity victim,
                                 DamageSource source, MutableFloat value, Operation<Void> original) {
        GrapnelImpact.Effect effect = GrapnelImpact.beginEffect(GrapnelImpact.Kind.ARMOR, level,
            (Enchantment) (Object) this, rank, weapon, victim, source, value.floatValue());
        boolean completed = false;
        try { original.call(level, rank, weapon, victim, source, value); completed = true; }
        finally { GrapnelImpact.endEffect(effect, value.floatValue(), completed); }
    }

    @WrapMethod(method = "modifyFallBasedDamage")
    private void ef$observeFall(ServerLevel level, int rank, ItemStack weapon, Entity victim,
                                DamageSource source, MutableFloat value, Operation<Void> original) {
        GrapnelImpact.Effect effect = GrapnelImpact.beginEffect(GrapnelImpact.Kind.FALL, level,
            (Enchantment) (Object) this, rank, weapon, victim, source, value.floatValue());
        boolean completed = false;
        try { original.call(level, rank, weapon, victim, source, value); completed = true; }
        finally { GrapnelImpact.endEffect(effect, value.floatValue(), completed); }
    }

    @Inject(method = "modifyArmorEffectivness", at = @At("HEAD"), cancellable = true)
    private void ef$deferBreach(ServerLevel level, int enchantmentLevel, ItemStack weapon, Entity victim,
                                DamageSource source, MutableFloat value, CallbackInfo ci) {
        if (HeavyCrushSkill.deferNative((Enchantment) (Object) this, level, weapon, victim, source, HeavyCrushMath.BREACH)) {
            ci.cancel();
        }
    }

    @Inject(method = "modifyFallBasedDamage", at = @At("HEAD"), cancellable = true)
    private void ef$deferDensity(ServerLevel level, int enchantmentLevel, ItemStack weapon, Entity victim,
                                 DamageSource source, MutableFloat value, CallbackInfo ci) {
        if (HeavyCrushSkill.deferNative((Enchantment) (Object) this, level, weapon, victim, source, HeavyCrushMath.DENSITY)) {
            ci.cancel();
        }
    }
}
