package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the same native-effect ordering for both orientations of Density/Breach. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperHeavyCrushMixin {
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
