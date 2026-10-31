package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.HeavyCrushMath;
import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** One and a half times the actual drawing time, on both logical sides. */
@Mixin(BowItem.class)
public abstract class BowHeavyCrushMixin {
    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float ef$heavyDraw(int elapsed, Operation<Float> original, ItemStack weapon, Level level,
                               LivingEntity user, int remaining) {
        return original.call(HeavyCrushSkill.selected(weapon) == null ? elapsed : HeavyCrushMath.slowedUseTicks(elapsed));
    }
}
