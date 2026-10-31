package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.signatures.HeavyCrushSkill;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ordinary ten-tick trident threshold becomes fifteen ticks for this fusion. */
@Mixin(TridentItem.class)
public abstract class TridentChargeHeavyCrushMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void ef$heavyThrow(ItemStack weapon, Level level, LivingEntity user, int remaining,
                               CallbackInfoReturnable<Boolean> cir) {
        if (user instanceof Player && HeavyCrushSkill.selected(weapon) != null
            && weapon.getUseDuration(user) - remaining < 15) {
            cir.setReturnValue(false);
        }
    }
}
