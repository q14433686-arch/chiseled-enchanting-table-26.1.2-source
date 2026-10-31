package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.FrostFallSkills;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import enchantment_fusion.mechanics.signatures.NativeFrostWaterGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;

/** Observe real enchantment deltas and replace only selected conflicting frost location effects. */
@Mixin(Enchantment.class)
public abstract class EnchantmentFrostEffectsMixin {
    @WrapMethod(method = "modifyDamageProtection")
    private void ef$observeContribution(ServerLevel level, int enchantmentLevel, ItemStack item,
                                        Entity victim, DamageSource source, MutableFloat protection,
                                        Operation<Void> original) {
        float before = protection.floatValue();
        original.call(level, enchantmentLevel, item, victim, source, protection);
        FrostLandingCapture.observeEnchantment((Enchantment) (Object) this, level, item, victim,
            source, before, protection.floatValue());
    }

    @WrapMethod(method = "runLocationChangedEffects")
    private void ef$scopeNativeWaterWrites(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item,
                                           LivingEntity wearer, Operation<Void> original) {
        boolean guarded = FrostFallSkills.suppressNativeWaterFreeze((Enchantment) (Object) this, level, item, wearer);
        boolean entered = NativeFrostWaterGuard.enter(guarded);
        try {
            original.call(level, enchantmentLevel, item, wearer);
        } finally {
            NativeFrostWaterGuard.exit(entered);
        }
    }
}
