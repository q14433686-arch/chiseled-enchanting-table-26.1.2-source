package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import enchantment_fusion.mechanics.signatures.SoulThreadSkills;
import enchantment_fusion.mechanics.signatures.SoulBladeSkills;
import enchantment_fusion.mechanics.signatures.SegmentThreadSkills;
import enchantment_fusion.mechanics.signatures.MeleeAttackThemes;
import enchantment_fusion.mechanics.signatures.MeleeWearSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the same native-effect ordering for both orientations of Density/Breach. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperHeavyCrushMixin {
    @WrapMethod(method = "modifyDamage")
    private static float ef$soulThreadDamage(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                              float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        float themed = MeleeAttackThemes.nonNativeDamage(level, weapon, victim, source,
            SegmentThreadSkills.modifyDamage(level, weapon, victim, source,
                SoulBladeSkills.modifyDamage(level, weapon, victim, source,
                    SoulThreadSkills.nonNativeDamage(level, weapon, victim, source, vanilla))));
        return MeleeWearSkills.modifyDamage(level, weapon, victim, source, themed);
    }

    @WrapMethod(method = "modifyArmorEffectiveness")
    private static float ef$heavyArmor(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        return HeavyCrushSkill.armorEffect(level, weapon, victim, source, vanilla);
    }

    @WrapMethod(method = "modifyFallBasedDamage")
    private static float ef$heavySmash(ServerLevel level, ItemStack weapon, Entity victim, DamageSource source,
                                       float value, Operation<Float> original) {
        float vanilla = original.call(level, weapon, victim, source, value);
        return HeavyCrushSkill.fallEffect(level, weapon, victim, source, vanilla);
    }
}
