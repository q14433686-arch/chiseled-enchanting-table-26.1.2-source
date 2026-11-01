package enchantment_fusion.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla player attack ticker exists on LivingEntity on both logical sides. */
@Mixin(LivingEntity.class)
public interface LivingEntityAttackChargeAccessor {
    @Accessor("attackStrengthTicker") void ef$setAttackStrengthTicker(int ticks);
    @Accessor("attackStrengthTicker") int ef$getAttackStrengthTicker();
}
