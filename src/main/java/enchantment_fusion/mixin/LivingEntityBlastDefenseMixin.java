package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.BlastArmorCapture;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Change only the selected armor's own explosion calculation; preserve vanilla damage and wear. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlastDefenseMixin {
    @WrapMethod(method = "getDamageAfterArmorAbsorb")
    private float ef$captureArmor(DamageSource source, float damage, Operation<Float> original) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(source, damage);
        BlastArmorCapture.Frame frame = BlastArmorCapture.begin(player, source);
        try { return original.call(source, damage); } finally { BlastArmorCapture.end(frame); }
    }

    @WrapOperation(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
    private float ef$excludeOnlySelectedArmor(LivingEntity victim, float damage, DamageSource source,
                                               float armor, float toughness, Operation<Float> original) {
        float effective = victim instanceof ServerPlayer player ? BlastArmorCapture.currentArmor(player, source, armor) : armor;
        return original.call(victim, damage, source, effective, toughness);
    }

    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)F"))
    private float ef$mergeProtection(ServerLevel level, LivingEntity victim, DamageSource source, Operation<Float> original) {
        if (!(victim instanceof ServerPlayer player)) return original.call(level, victim, source);
        BlastDefenseSkills.ProtectionFrame frame = BlastDefenseSkills.beginProtection(player, source);
        try {
            float vanilla = original.call(level, victim, source);
            return frame == null ? vanilla : BlastDefenseSkills.finishProtection(frame, vanilla, level);
        } finally { BlastDefenseSkills.endProtection(frame); }
    }
}
