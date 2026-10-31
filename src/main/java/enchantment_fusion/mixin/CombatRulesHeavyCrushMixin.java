package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.HeavyCrushProjectiles;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rockets and tridents without a DamageSource weapon need their actual launch/pickup stack. */
@Mixin(CombatRules.class)
public abstract class CombatRulesHeavyCrushMixin {
    @Inject(method = "getDamageAfterAbsorb", at = @At("RETURN"), cancellable = true)
    private static void ef$rocketArmor(LivingEntity victim, float damage, DamageSource source, float armor,
                                        float toughness, CallbackInfoReturnable<Float> cir) {
        if (!(damage > 0.0F) || source.getWeaponItem() != null || !(victim.level() instanceof ServerLevel level)) return;
        ItemStack weapon = source.getDirectEntity() instanceof ThrownTrident trident
            ? trident.getPickupItemStackOrigin()
            : HeavyCrushProjectiles.rocketWeaponForDirectHit(source, victim);
        if (weapon == null || weapon.isEmpty() || HeavyCrushSkill.selected(weapon) == null) return;
        float armorFraction = 1.0F - cir.getReturnValueF() / damage;
        float adjusted = HeavyCrushSkill.armorEffect(level, weapon, victim, source, armorFraction);
        cir.setReturnValue(damage * (1.0F - Mth.clamp(adjusted, 0.0F, 1.0F)));
    }
}
